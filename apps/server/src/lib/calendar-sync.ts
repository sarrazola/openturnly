import { createHash } from "node:crypto";
import { formatPhone, formatPrice } from "@openturnly/core";
import { and, asc, eq, gt, inArray, isNotNull, isNull, lte, ne } from "drizzle-orm";
import type { Config } from "../config.ts";
import type { Db } from "../db/client.ts";
import {
  type Appointment,
  appointments,
  type Business,
  businesses,
  businessMembers,
  type CalendarConnection,
  type CalendarEvent,
  type CalendarTarget,
  type Customer,
  calendarConnections,
  calendarEvents,
  calendarTargets,
  customers,
  type Location,
  locations,
  users,
} from "../db/schema.ts";
import {
  createGoogleCalendarApi,
  GoogleError,
  type GoogleEventInput,
  type GrantedTokens,
  refreshAccessToken,
  revokeToken,
} from "./google-calendar.ts";
import { decryptSecret, encryptSecret } from "./secrets.ts";

/**
 * Copies appointments into the calendars people connect. Each person connects their own
 * account; for every business they choose, a calendar named after the business is created in
 * it, and the appointments they should see go there: a professional's own, and for owners and
 * admins the professionals they pick. One way only: nothing is read back.
 *
 * An online appointment gets its video call from the professional's copy, which also invites
 * the customer; its link is kept on the appointment for the messages and the booking page.
 *
 * Every change is written while the request that made it waits, so the link is there before
 * the confirmation goes out; a copy Google doesn't take is tried again by `sweep()` with
 * backoff. Nothing here outlives the request that started it.
 */

/** Appointments that keep their copy: the ones that happened or will. */
const SHOWN_STATUSES: Appointment["status"][] = ["confirmed", "no_show"];
/** When a copy Google refused is tried again, by attempt; after the last one it's left. */
const BACKOFF_MINUTES = [1, 5, 15, 60, 180, 720];
/** Appointments `sweep()` brings up to date per round. */
const SWEEP_LIMIT = 25;
/** Appointments copied right away on connecting; the sweep does the rest. */
const INLINE_BACKFILL = 10;
/** On connecting, appointments copied from this long ago on. */
const BACKFILL_PAST_MS = 24 * 60 * 60_000;
/** Refreshed this long before Google's access token expires. */
const TOKEN_MARGIN_MS = 2 * 60_000;

const TEXT = {
  es: {
    customer: "Cliente",
    phone: "Teléfono",
    email: "Correo",
    service: "Servicio",
    professional: "Profesional",
    location: "Lugar",
    meeting: "Videollamada",
    meetingPending: "Videollamada: el enlace llega en un momento",
    price: "Precio",
    code: "Código de reserva",
    agenda: "Agenda",
  },
  en: {
    customer: "Customer",
    phone: "Phone",
    email: "Email",
    service: "Service",
    professional: "Professional",
    location: "Location",
    meeting: "Video call",
    meetingPending: "Video call: the link is on its way",
    price: "Price",
    code: "Booking code",
    agenda: "Agenda",
  },
} as const;

/** How a person whose calendar is connected works in the business. */
type Watcher = {
  target: CalendarTarget;
  connection: CalendarConnection;
  role: "owner" | "admin" | "professional" | null;
  /** A professional's own profile. */
  resourceId: string | null;
};

type Context = {
  appointment: Appointment;
  business: Business;
  customer: Customer;
  location: Location | null;
  watchers: Watcher[];
};

export type CalendarSync = ReturnType<typeof createCalendarSync>;

export function createCalendarSync({ db, config }: { db: Db; config: Config }) {
  const google = config.google;
  const api = createGoogleCalendarApi(config);

  // --- Who sees what ---------------------------------------------------------------------

  const watchersOf = async (businessId: string, targetId?: string): Promise<Watcher[]> => {
    const rows = await db
      .select({
        target: calendarTargets,
        connection: calendarConnections,
        userRole: users.role,
        memberRole: businessMembers.role,
        memberResourceId: businessMembers.resourceId,
      })
      .from(calendarTargets)
      .innerJoin(calendarConnections, eq(calendarConnections.id, calendarTargets.connectionId))
      .innerJoin(users, eq(users.id, calendarConnections.userId))
      .leftJoin(
        businessMembers,
        and(
          eq(businessMembers.businessId, calendarTargets.businessId),
          eq(businessMembers.userId, users.id),
        ),
      )
      .where(
        and(
          eq(calendarTargets.businessId, businessId),
          targetId ? eq(calendarTargets.id, targetId) : undefined,
        ),
      )
      .orderBy(asc(calendarTargets.createdAt));
    return rows.map((row) => ({
      target: row.target,
      connection: row.connection,
      role: row.userRole === "owner" ? "owner" : (row.memberRole ?? null),
      resourceId: row.userRole === "owner" ? null : (row.memberResourceId ?? null),
    }));
  };

  const wants = (watcher: Watcher, appointment: Appointment) => {
    if (watcher.connection.status !== "connected" || !watcher.role) return false;
    if (!SHOWN_STATUSES.includes(appointment.status)) return false;
    if (watcher.role === "professional") {
      return appointment.resourceId !== null && appointment.resourceId === watcher.resourceId;
    }
    const chosen = watcher.target.resourceIds;
    return !chosen || (appointment.resourceId !== null && chosen.includes(appointment.resourceId));
  };

  /**
   * Whose copy carries an online appointment's video call: the professional's own, or with
   * nobody assigned (a business without professionals), the first owner or admin connected.
   */
  const hostOf = (watchers: Watcher[], appointment: Appointment) => {
    if (appointment.mode !== "online") return null;
    const able = watchers.filter((watcher) => wants(watcher, appointment));
    if (appointment.resourceId) {
      return (
        able.find(
          (watcher) =>
            watcher.role === "professional" && watcher.resourceId === appointment.resourceId,
        ) ?? null
      );
    }
    return able.find((watcher) => watcher.role === "owner" || watcher.role === "admin") ?? null;
  };

  // --- Tokens ----------------------------------------------------------------------------

  const markDisconnected = async (connectionId: string, reason: string) => {
    await db
      .update(calendarConnections)
      .set({ status: "disconnected", lastError: reason })
      .where(eq(calendarConnections.id, connectionId));
  };

  const accessToken = async (connection: CalendarConnection, now = Date.now()) => {
    if (!google) throw new GoogleError(503, "not_configured", "Google isn't set up");
    if (
      connection.encryptedAccessToken &&
      connection.accessTokenExpiresAt &&
      connection.accessTokenExpiresAt.getTime() > now + TOKEN_MARGIN_MS
    ) {
      return decryptSecret(config.secret, connection.encryptedAccessToken);
    }
    try {
      const fresh = await refreshAccessToken(
        config,
        google,
        decryptSecret(config.secret, connection.encryptedRefreshToken),
        now,
      );
      const encrypted = encryptSecret(config.secret, fresh.accessToken);
      await db
        .update(calendarConnections)
        .set({ encryptedAccessToken: encrypted, accessTokenExpiresAt: fresh.expiresAt })
        .where(eq(calendarConnections.id, connection.id));
      connection.encryptedAccessToken = encrypted;
      connection.accessTokenExpiresAt = fresh.expiresAt;
      return fresh.accessToken;
    } catch (error) {
      if (error instanceof GoogleError && error.revoked) {
        await markDisconnected(connection.id, "revoked");
        connection.status = "disconnected";
      }
      throw error;
    }
  };

  // --- The calendar and its events -------------------------------------------------------

  /** The business's calendar in the person's account, created or renamed as needed. */
  const ensureCalendar = async (watcher: Watcher, business: Business, token: string) => {
    const { target } = watcher;
    if (target.externalCalendarId && target.calendarName === business.name) {
      return target.externalCalendarId;
    }
    if (target.externalCalendarId) {
      try {
        await api.updateCalendar(token, target.externalCalendarId, {
          summary: business.name,
          timeZone: business.timezone,
        });
        await db
          .update(calendarTargets)
          .set({ calendarName: business.name })
          .where(eq(calendarTargets.id, target.id));
        target.calendarName = business.name;
        return target.externalCalendarId;
      } catch (error) {
        if (!(error instanceof GoogleError && error.gone)) throw error;
        await forgetCalendar(target);
      }
    }
    const calendarId = await api.createCalendar(token, {
      summary: business.name,
      timeZone: business.timezone,
    });
    await db
      .update(calendarTargets)
      .set({ externalCalendarId: calendarId, calendarName: business.name })
      .where(eq(calendarTargets.id, target.id));
    target.externalCalendarId = calendarId;
    target.calendarName = business.name;
    return calendarId;
  };

  /** The person deleted the calendar in Google: it's made again, with every copy in it. */
  const forgetCalendar = async (target: CalendarTarget) => {
    await db
      .update(calendarTargets)
      .set({ externalCalendarId: null, calendarName: null })
      .where(eq(calendarTargets.id, target.id));
    await db
      .update(calendarEvents)
      .set({ externalEventId: null, syncedHash: null, dueAt: new Date(), attempts: 0 })
      .where(eq(calendarEvents.targetId, target.id));
    target.externalCalendarId = null;
    target.calendarName = null;
  };

  const eventFor = (
    context: Context,
    watcher: Watcher,
    hosting: boolean,
    /** Someone's calendar makes the call; without one, no link is coming. */
    hosted: boolean,
  ): GoogleEventInput => {
    const { appointment, business, customer, location } = context;
    const text = TEXT[business.locale === "en" ? "en" : "es"];
    const name = `${customer.firstName} ${customer.lastName}`.trim();
    const someoneElses =
      watcher.role !== "professional" && appointment.resourceName
        ? ` (${appointment.resourceName})`
        : "";
    const place = [
      location?.name === business.name ? null : location?.name,
      location?.address ?? business.address,
    ]
      .filter(Boolean)
      .join(", ");
    const online = appointment.mode === "online";
    // The call's own copy shows the call itself; the others carry its link.
    const where = online
      ? hosting
        ? null
        : appointment.meetingUrl
          ? `${text.meeting}: ${appointment.meetingUrl}`
          : hosted
            ? text.meetingPending
            : null
      : place
        ? `${text.location}: ${place}`
        : null;
    const lines = [
      `${text.customer}: ${name}`,
      customer.phone ? `${text.phone}: ${formatPhone(customer.phone)}` : null,
      customer.email ? `${text.email}: ${customer.email}` : null,
      `${text.service}: ${appointment.serviceName}`,
      appointment.resourceName ? `${text.professional}: ${appointment.resourceName}` : null,
      where,
      appointment.priceAmount !== null
        ? `${text.price}: ${formatPrice(appointment.priceAmount - appointment.discountAmount, business.currency)}`
        : null,
      `${text.code}: ${appointment.code}`,
      ...appointment.answers.map((answer) => `${answer.label}: ${answer.value}`),
      // The customer sees what the copy they're invited to says: nothing of the admin's.
      hosting ? null : `${text.agenda}: ${config.appUrl}/admin/${business.shortId}`,
    ];
    return {
      // Booked through a booking link: its name leads, so the agenda shows where it came from.
      summary: `${appointment.bookingLinkName ? `${appointment.bookingLinkName}: ` : ""}${name} · ${appointment.serviceName}${someoneElses}`,
      description: lines.filter(Boolean).join("\n"),
      location: online ? (hosting ? "" : (appointment.meetingUrl ?? "")) : place,
      start: { dateTime: appointment.startsAt.toISOString(), timeZone: business.timezone },
      end: { dateTime: appointment.endsAt.toISOString(), timeZone: business.timezone },
      ...(hosting && customer.email
        ? { attendees: [{ email: customer.email, displayName: name }] }
        : {}),
    };
  };

  const fingerprint = (event: GoogleEventInput) =>
    `${event.start.dateTime}|${event.end.dateTime}|${createHash("sha256").update(JSON.stringify(event)).digest("base64url")}`;

  const sameTimes = (a: string | null, b: string) =>
    a !== null && a.split("|").slice(0, 2).join("|") === b.split("|").slice(0, 2).join("|");

  // --- Writing one copy ------------------------------------------------------------------

  const loadContext = async (appointmentId: string): Promise<Context | null> => {
    const [row] = await db
      .select({
        appointment: appointments,
        business: businesses,
        customer: customers,
        location: locations,
      })
      .from(appointments)
      .innerJoin(businesses, eq(businesses.id, appointments.businessId))
      .innerJoin(customers, eq(customers.id, appointments.customerId))
      .leftJoin(locations, eq(locations.id, appointments.locationId))
      .where(eq(appointments.id, appointmentId));
    if (!row) return null;
    return { ...row, watchers: await watchersOf(row.business.id) };
  };

  const settle = async (row: CalendarEvent, changes: Partial<CalendarEvent>) => {
    await db
      .update(calendarEvents)
      .set({ dueAt: null, attempts: 0, lastError: null, ...changes })
      .where(eq(calendarEvents.id, row.id));
  };

  const failed = async (row: CalendarEvent, error: unknown) => {
    const message = error instanceof Error ? error.message : String(error);
    const revoked = error instanceof GoogleError && error.revoked;
    const attempts = row.attempts + 1;
    const wait = BACKOFF_MINUTES[attempts - 1];
    await db
      .update(calendarEvents)
      .set({
        attempts,
        lastError: message.slice(0, 500),
        // Without access it waits for the person to connect again, which retries it all.
        dueAt: revoked || wait === undefined ? null : new Date(Date.now() + wait * 60_000),
      })
      .where(eq(calendarEvents.id, row.id));
    if (!revoked) console.error("calendar: could not write a copy", row.id, message);
  };

  const removeCopy = async (row: CalendarEvent, watcher: Watcher | undefined) => {
    if (
      row.externalEventId &&
      watcher?.connection.status === "connected" &&
      watcher.target.externalCalendarId
    ) {
      try {
        const token = await accessToken(watcher.connection);
        await api.deleteEvent(
          token,
          watcher.target.externalCalendarId,
          row.externalEventId,
          row.withGuest ? "all" : "none",
        );
      } catch (error) {
        if (!(error instanceof GoogleError && (error.gone || error.revoked))) throw error;
      }
    }
    await db.delete(calendarEvents).where(eq(calendarEvents.id, row.id));
  };

  const writeCopy = async (
    row: CalendarEvent,
    watcher: Watcher,
    context: Context,
    hosting: boolean,
    hosted: boolean,
  ) => {
    const token = await accessToken(watcher.connection);
    let calendarId = await ensureCalendar(watcher, context.business, token);
    const event = eventFor(context, watcher, hosting, hosted);
    const hash = fingerprint(event);
    const withGuest = Boolean(event.attendees);
    // Asks for the call while the appointment has none yet, unless this copy already got one
    // that wasn't ready when it was written.
    let meeting = hosting && !context.appointment.meetingUrl;
    if (meeting && row.externalEventId) {
      try {
        const current = await api.getEvent(token, calendarId, row.externalEventId);
        if (current.hangoutLink) {
          meeting = false;
          await keepLink(context, hosting, current);
        }
      } catch (error) {
        if (!(error instanceof GoogleError && error.gone)) throw error;
      }
    }
    if (row.externalEventId && row.syncedHash === hash && !meeting) {
      await settle(row, {});
      return;
    }
    let result: { id: string; hangoutLink?: string } | null = null;
    if (row.externalEventId) {
      try {
        result = await api.patchEvent(token, calendarId, row.externalEventId, event, {
          meeting,
          // The customer hears about a new time, not about a reworded description.
          sendUpdates: withGuest && !sameTimes(row.syncedHash, hash) ? "all" : "none",
        });
      } catch (error) {
        // Deleted in Google: it's written again below.
        if (!(error instanceof GoogleError && error.gone)) throw error;
      }
    }
    if (!result) {
      const insert = () =>
        api.insertEvent(token, calendarId, event, {
          meeting,
          sendUpdates: withGuest ? "all" : "none",
        });
      try {
        result = await insert();
      } catch (error) {
        if (!(error instanceof GoogleError && error.gone)) throw error;
        await forgetCalendar(watcher.target);
        calendarId = await ensureCalendar(watcher, context.business, token);
        result = await insert();
      }
    }

    await keepLink(context, hosting, result);
    // Google answered without the call ready: it's looked up again later, with backoff.
    const wait = BACKOFF_MINUTES[row.attempts];
    const missingCall = hosting && !context.appointment.meetingUrl && wait !== undefined;
    await settle(row, {
      externalEventId: result.id,
      syncedHash: hash,
      withGuest,
      ...(missingCall
        ? { dueAt: new Date(Date.now() + wait * 60_000), attempts: row.attempts + 1 }
        : {}),
    });
  };

  /** The call's link, kept on the appointment for messages and the booking page. */
  const keepLink = async (
    context: Context,
    hosting: boolean,
    result: { hangoutLink?: string } | null,
  ) => {
    const link = result?.hangoutLink;
    if (!hosting || !link || link === context.appointment.meetingUrl) return;
    await db
      .update(appointments)
      .set({ meetingUrl: link })
      .where(eq(appointments.id, context.appointment.id));
    context.appointment.meetingUrl = link;
  };

  /** Brings every pending copy of one appointment up to date, the call's copy first. */
  const syncAppointment = async (appointmentId: string, onlyDue: boolean, now = Date.now()) => {
    const context = await loadContext(appointmentId);
    if (!context) return;
    const rows = await db
      .select()
      .from(calendarEvents)
      .where(eq(calendarEvents.appointmentId, appointmentId));
    const pending = rows.filter((row) => !onlyDue || (row.dueAt && row.dueAt.getTime() <= now));
    if (pending.length === 0) return;
    const host = hostOf(context.watchers, context.appointment);
    const byTarget = new Map(context.watchers.map((watcher) => [watcher.target.id, watcher]));

    const sync = async (row: CalendarEvent) => {
      const watcher = byTarget.get(row.targetId);
      try {
        if (!watcher || !wants(watcher, context.appointment)) await removeCopy(row, watcher);
        else await writeCopy(row, watcher, context, watcher === host, host !== null);
      } catch (error) {
        await failed(row, error);
      }
    };

    const hostRow = host ? pending.find((row) => row.targetId === host.target.id) : undefined;
    let others = pending.filter((row) => row !== hostRow);
    if (hostRow) {
      const before = context.appointment.meetingUrl;
      await sync(hostRow);
      // A new link: every other copy shows it, whether it was due or not.
      if (context.appointment.meetingUrl !== before) {
        others = await db
          .select()
          .from(calendarEvents)
          .where(
            and(eq(calendarEvents.appointmentId, appointmentId), ne(calendarEvents.id, hostRow.id)),
          );
      }
    }
    await Promise.all(others.map(sync));
  };

  /** Which copies an appointment should have, and which it shouldn't anymore. */
  const plan = async (appointmentId: string) => {
    const context = await loadContext(appointmentId);
    if (!context) return false;
    const now = new Date();
    const wanted = context.watchers.filter((watcher) => wants(watcher, context.appointment));
    if (wanted.length > 0) {
      await db
        .insert(calendarEvents)
        .values(
          wanted.map((watcher) => ({ targetId: watcher.target.id, appointmentId, dueAt: now })),
        )
        .onConflictDoUpdate({
          target: [calendarEvents.targetId, calendarEvents.appointmentId],
          set: { dueAt: now, attempts: 0 },
        });
    }
    const updated = await db
      .update(calendarEvents)
      .set({ dueAt: now, attempts: 0 })
      .where(eq(calendarEvents.appointmentId, appointmentId))
      .returning({ id: calendarEvents.id });
    return updated.length > 0;
  };

  /** Lines up one person's calendar for a business with its appointments from now on. */
  const refreshTarget = async (target: CalendarTarget) => {
    const [watcher] = await watchersOf(target.businessId, target.id);
    if (!watcher) return;
    const now = new Date();
    const upcoming = await db
      .select()
      .from(appointments)
      .where(
        and(
          eq(appointments.businessId, target.businessId),
          inArray(appointments.status, SHOWN_STATUSES),
          gt(appointments.endsAt, new Date(now.getTime() - BACKFILL_PAST_MS)),
        ),
      );
    const wanted = upcoming.filter((appointment) => wants(watcher, appointment));
    if (wanted.length > 0) {
      await db
        .insert(calendarEvents)
        .values(
          wanted.map((appointment) => ({
            targetId: target.id,
            appointmentId: appointment.id,
            dueAt: now,
          })),
        )
        .onConflictDoUpdate({
          target: [calendarEvents.targetId, calendarEvents.appointmentId],
          set: { dueAt: now, attempts: 0 },
        });
    }
    // Copies it shouldn't have anymore (another professional chosen, for example).
    await db
      .update(calendarEvents)
      .set({ dueAt: now, attempts: 0 })
      .where(eq(calendarEvents.targetId, target.id));
    const first = await db
      .selectDistinct({ appointmentId: calendarEvents.appointmentId, dueAt: calendarEvents.dueAt })
      .from(calendarEvents)
      .where(eq(calendarEvents.targetId, target.id))
      .orderBy(asc(calendarEvents.dueAt))
      .limit(INLINE_BACKFILL);
    for (const row of first) await syncAppointment(row.appointmentId, true);
  };

  /** Removes the business's calendar from the person's account, when it's still reachable. */
  const dropTarget = async (watcher: Watcher, { deleteCalendar }: { deleteCalendar: boolean }) => {
    if (
      deleteCalendar &&
      watcher.target.externalCalendarId &&
      watcher.connection.status === "connected"
    ) {
      try {
        const token = await accessToken(watcher.connection);
        await api.deleteCalendar(token, watcher.target.externalCalendarId);
      } catch (error) {
        if (!(error instanceof GoogleError && (error.gone || error.revoked))) {
          console.error("calendar: could not delete a calendar", watcher.target.id, error);
        }
      }
    }
    await db.delete(calendarTargets).where(eq(calendarTargets.id, watcher.target.id));
  };

  /** The businesses a person can see, and how they work in each. */
  const reachOf = async (userId: string) => {
    const [user] = await db.select({ role: users.role }).from(users).where(eq(users.id, userId));
    if (!user) return [];
    if (user.role === "owner") {
      const all = await db.select({ id: businesses.id }).from(businesses);
      return all.map((business) => ({
        businessId: business.id,
        role: "owner" as const,
        resourceId: null,
      }));
    }
    return db
      .select({
        businessId: businessMembers.businessId,
        role: businessMembers.role,
        resourceId: businessMembers.resourceId,
      })
      .from(businessMembers)
      .where(eq(businessMembers.userId, userId));
  };

  const connectionOf = async (userId: string) => {
    const [connection] = await db
      .select()
      .from(calendarConnections)
      .where(
        and(eq(calendarConnections.userId, userId), eq(calendarConnections.provider, "google")),
      );
    return connection ?? null;
  };

  const targetsOf = (connectionId: string) =>
    db.select().from(calendarTargets).where(eq(calendarTargets.connectionId, connectionId));

  return {
    /** Whether this install has a Google client set up. */
    get available() {
      return google !== null;
    },

    /**
     * Brings an appointment's copies up to date after it was booked, moved or cancelled.
     * Waits for Google, so an online appointment has its link before anything is sent.
     * Never fails the change: what Google doesn't take now is tried again later.
     */
    async appointmentChanged(appointmentId: string): Promise<{ meetingUrl: string | null }> {
      if (google) {
        try {
          if (await plan(appointmentId)) await syncAppointment(appointmentId, true);
        } catch (error) {
          console.error("calendar: could not sync an appointment", appointmentId, error);
        }
      }
      const [row] = await db
        .select({ meetingUrl: appointments.meetingUrl })
        .from(appointments)
        .where(eq(appointments.id, appointmentId));
      return { meetingUrl: row?.meetingUrl ?? null };
    },

    /**
     * The professionals who can take an online appointment in a business: the ones whose own
     * calendar is connected there. `null` stands for "nobody in particular", possible when an
     * owner or admin is connected (for businesses without professionals).
     */
    async meetingHosts(businessId: string): Promise<Set<string | null>> {
      const hosts = new Set<string | null>();
      if (!google) return hosts;
      for (const watcher of await watchersOf(businessId)) {
        if (watcher.connection.status !== "connected") continue;
        if (watcher.role === "professional" && watcher.resourceId) hosts.add(watcher.resourceId);
        if (watcher.role === "owner" || watcher.role === "admin") hosts.add(null);
      }
      return hosts;
    },

    /** The business was renamed: its calendars follow. */
    async businessRenamed(businessId: string) {
      if (!google) return;
      const [business] = await db.select().from(businesses).where(eq(businesses.id, businessId));
      if (!business) return;
      for (const watcher of await watchersOf(businessId)) {
        if (watcher.connection.status !== "connected" || !watcher.target.externalCalendarId)
          continue;
        try {
          await ensureCalendar(watcher, business, await accessToken(watcher.connection));
        } catch (error) {
          console.error("calendar: could not rename a calendar", watcher.target.id, error);
        }
      }
    },

    /** Someone's access to a business changed or ended: their calendar there follows. */
    async memberChanged(businessId: string, userId: string) {
      if (!google) return;
      try {
        const connection = await connectionOf(userId);
        if (!connection) return;
        const [target] = await db
          .select()
          .from(calendarTargets)
          .where(
            and(
              eq(calendarTargets.connectionId, connection.id),
              eq(calendarTargets.businessId, businessId),
            ),
          );
        if (!target) return;
        const [watcher] = await watchersOf(businessId, target.id);
        if (!watcher) return;
        if (!watcher.role) await dropTarget(watcher, { deleteCalendar: true });
        else await refreshTarget(target);
      } catch (error) {
        console.error("calendar: could not follow a change of access", error);
      }
    },

    /** The person's connection and, for each business they can see, what goes to it. */
    async overview(userId: string) {
      const connection = await connectionOf(userId);
      const reach = await reachOf(userId);
      const targets = connection ? await targetsOf(connection.id) : [];
      const problems = connection
        ? await db
            .select({ targetId: calendarEvents.targetId, lastError: calendarEvents.lastError })
            .from(calendarEvents)
            .innerJoin(calendarTargets, eq(calendarTargets.id, calendarEvents.targetId))
            .where(
              and(
                eq(calendarTargets.connectionId, connection.id),
                isNotNull(calendarEvents.lastError),
              ),
            )
        : [];
      return {
        connection: connection
          ? {
              provider: connection.provider,
              email: connection.accountEmail,
              status: connection.status,
              connectedAt: connection.createdAt,
            }
          : null,
        businesses: reach.map((place) => {
          const target = targets.find((row) => row.businessId === place.businessId);
          return {
            businessId: place.businessId,
            role: place.role,
            resourceId: place.resourceId,
            enabled: Boolean(target),
            resourceIds: target?.resourceIds ?? null,
            failing: target ? problems.filter((row) => row.targetId === target.id).length : 0,
          };
        }),
      };
    },

    /**
     * Stores a connection Google just granted. A different Google account than before starts
     * over: the calendars made in the old one stay there.
     */
    async connect(userId: string, tokens: GrantedTokens) {
      const existing = await connectionOf(userId);
      const values = {
        accountId: tokens.accountId,
        accountEmail: tokens.email,
        encryptedRefreshToken: encryptSecret(config.secret, tokens.refreshToken),
        encryptedAccessToken: encryptSecret(config.secret, tokens.accessToken),
        accessTokenExpiresAt: tokens.expiresAt,
        status: "connected" as const,
        lastError: null,
      };
      let connectionId: string;
      if (existing) {
        await db
          .update(calendarConnections)
          .set(values)
          .where(eq(calendarConnections.id, existing.id));
        connectionId = existing.id;
        if (existing.accountId !== tokens.accountId) {
          await db
            .update(calendarTargets)
            .set({ externalCalendarId: null, calendarName: null })
            .where(eq(calendarTargets.connectionId, existing.id));
          const targetIds = (await targetsOf(existing.id)).map((target) => target.id);
          if (targetIds.length > 0) {
            await db
              .update(calendarEvents)
              .set({ externalEventId: null, syncedHash: null, withGuest: false })
              .where(inArray(calendarEvents.targetId, targetIds));
          }
        }
      } else {
        const [created] = await db
          .insert(calendarConnections)
          .values({ userId, provider: "google", ...values })
          .returning({ id: calendarConnections.id });
        if (!created) throw new Error("connection not saved");
        connectionId = created.id;
      }

      let targets = await targetsOf(connectionId);
      if (targets.length === 0) {
        // First time: the businesses where they take appointments, or their only one.
        const reach = await reachOf(userId);
        const chosen =
          reach.length === 1 ? reach : reach.filter((place) => place.role === "professional");
        if (chosen.length > 0) {
          targets = await db
            .insert(calendarTargets)
            .values(chosen.map((place) => ({ connectionId, businessId: place.businessId })))
            .returning();
        }
      }
      for (const target of targets) {
        try {
          await refreshTarget(target);
        } catch (error) {
          console.error("calendar: could not fill a calendar", target.id, error);
        }
      }
    },

    /** Turns one business on or off for the person, or changes whose appointments go there. */
    async setTarget(
      userId: string,
      input: { businessId: string; enabled: boolean; resourceIds?: string[] | null },
    ) {
      const connection = await connectionOf(userId);
      if (!connection) return "not_connected" as const;
      const place = (await reachOf(userId)).find((row) => row.businessId === input.businessId);
      if (!place) return "business_not_found" as const;
      const [current] = await db
        .select()
        .from(calendarTargets)
        .where(
          and(
            eq(calendarTargets.connectionId, connection.id),
            eq(calendarTargets.businessId, input.businessId),
          ),
        );
      if (!input.enabled) {
        if (current) {
          const [watcher] = await watchersOf(input.businessId, current.id);
          if (watcher) await dropTarget(watcher, { deleteCalendar: true });
        }
        return "ok" as const;
      }
      // A professional's own appointments are theirs to get, and no one else's.
      const resourceIds = place.role === "professional" ? null : (input.resourceIds ?? null);
      let target = current;
      if (target) {
        if (input.resourceIds !== undefined) {
          [target] = await db
            .update(calendarTargets)
            .set({ resourceIds })
            .where(eq(calendarTargets.id, target.id))
            .returning();
        }
      } else {
        [target] = await db
          .insert(calendarTargets)
          .values({ connectionId: connection.id, businessId: input.businessId, resourceIds })
          .returning();
      }
      if (target && connection.status === "connected") await refreshTarget(target);
      return "ok" as const;
    },

    /**
     * Disconnects the person's calendar: their business calendars are deleted from it unless
     * they want to keep them, and the install's access is revoked.
     */
    async disconnect(userId: string, { deleteCalendars }: { deleteCalendars: boolean }) {
      const connection = await connectionOf(userId);
      if (!connection) return;
      if (connection.status === "connected") {
        for (const target of await targetsOf(connection.id)) {
          const [watcher] = await watchersOf(target.businessId, target.id);
          if (watcher) await dropTarget(watcher, { deleteCalendar: deleteCalendars });
        }
        await revokeToken(config, decryptSecret(config.secret, connection.encryptedRefreshToken));
      }
      await db.delete(calendarConnections).where(eq(calendarConnections.id, connection.id));
    },

    /**
     * Retries copies that are due and drops the calendars of people who lost access to a
     * business. Runs with the automations' sweep.
     */
    async sweep(now = Date.now()) {
      if (!google) return;
      const orphans = await db
        .select({ target: calendarTargets })
        .from(calendarTargets)
        .innerJoin(calendarConnections, eq(calendarConnections.id, calendarTargets.connectionId))
        .innerJoin(users, eq(users.id, calendarConnections.userId))
        .leftJoin(
          businessMembers,
          and(
            eq(businessMembers.businessId, calendarTargets.businessId),
            eq(businessMembers.userId, users.id),
          ),
        )
        .where(and(eq(users.role, "member"), isNull(businessMembers.userId)));
      for (const { target } of orphans) {
        const [watcher] = await watchersOf(target.businessId, target.id);
        if (watcher && !watcher.role) await dropTarget(watcher, { deleteCalendar: true });
      }

      const due = await db
        .selectDistinct({
          appointmentId: calendarEvents.appointmentId,
          dueAt: calendarEvents.dueAt,
        })
        .from(calendarEvents)
        .where(lte(calendarEvents.dueAt, new Date(now)))
        .orderBy(asc(calendarEvents.dueAt))
        .limit(SWEEP_LIMIT);
      const seen = new Set<string>();
      for (const { appointmentId } of due) {
        if (seen.has(appointmentId)) continue;
        seen.add(appointmentId);
        try {
          await syncAppointment(appointmentId, true, now);
        } catch (error) {
          console.error("calendar: sweep could not sync an appointment", appointmentId, error);
        }
      }
    },
  };
}
