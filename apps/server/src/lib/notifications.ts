/**
 * Who hears about what, and the unread count behind the number on the apps' icon.
 *
 * An appointment booked, moved or cancelled becomes one notification per person who should
 * know: the instance's owners, the business's admins and the professional it's with (when
 * it moves to someone else, the one it was with too). The person who made the change
 * doesn't get one. Each notification is kept, so the apps can list and count them, and
 * pushed to that person's phones when the install has a push provider.
 */

import { instantToZoned } from "@openturnly/core";
import { and, count, eq, gt, inArray, isNull } from "drizzle-orm";
import type { Db } from "../db/client.ts";
import {
  type Appointment,
  appointments,
  businesses,
  businessMembers,
  customers,
  devices,
  type Notification,
  notifications,
  sessions,
  users,
} from "../db/schema.ts";
import type { PushProvider } from "./push.ts";

export type Notifications = ReturnType<typeof createNotifications>;

export type NotificationKind = Notification["kind"];

/** Where an appointment was before it moved. */
export type PreviousSlot = Pick<Appointment, "startsAt" | "resourceId" | "resourceName">;

type Copy = {
  titles: Record<NotificationKind, string>;
  with: string;
  before: string;
};

const COPY: Record<string, Copy> = {
  es: {
    titles: {
      appointment_booked: "Nueva reserva",
      appointment_rescheduled: "Cita movida",
      appointment_cancelled: "Cita cancelada",
    },
    with: "con",
    before: "antes",
  },
  en: {
    titles: {
      appointment_booked: "New booking",
      appointment_rescheduled: "Appointment moved",
      appointment_cancelled: "Appointment cancelled",
    },
    with: "with",
    before: "was",
  },
};

/**
 * What a notification says, in the business's language:
 * "Ana Pérez · Corte · sáb, 14 nov, 9:00 a. m. · con Juan", and for a moved appointment
 * "Ana Pérez · Corte · sáb, 14 nov, 10:00 a. m. (antes: sáb, 14 nov, 9:00 a. m.) · con
 * Pepe (antes Juan)". The professional it's with isn't named to that same professional.
 */
export function notificationText(
  kind: NotificationKind,
  business: { locale: string; timezone: string },
  appointment: Pick<Appointment, "startsAt" | "serviceName" | "resourceId" | "resourceName">,
  customer: { firstName: string; lastName: string },
  showProfessional: boolean,
  previous?: PreviousSlot,
) {
  const copy = COPY[business.locale] ?? (COPY.en as Copy);
  const when = (instant: Date) => {
    const start = instantToZoned(instant.getTime(), business.timezone);
    return new Intl.DateTimeFormat(business.locale, {
      weekday: "short",
      day: "numeric",
      month: "short",
      hour: "numeric",
      minute: "2-digit",
      timeZone: "UTC",
    }).format(new Date(`${start.date}T${start.time}:00Z`));
  };
  const moved = kind === "appointment_rescheduled" && previous;
  let time = when(appointment.startsAt);
  if (moved && previous.startsAt.getTime() !== appointment.startsAt.getTime()) {
    time += ` (${copy.before}: ${when(previous.startsAt)})`;
  }
  const parts = [
    `${customer.firstName} ${customer.lastName}`.trim(),
    appointment.serviceName,
    time,
  ];
  if (showProfessional && appointment.resourceName) {
    let professional = `${copy.with} ${appointment.resourceName}`;
    if (moved && previous.resourceName && previous.resourceId !== appointment.resourceId) {
      professional += ` (${copy.before} ${previous.resourceName})`;
    }
    parts.push(professional);
  }
  return { title: copy.titles[kind], body: parts.join(" · ") };
}

export function createNotifications({ db, push }: { db: Db; push: PushProvider | null }) {
  const unreadCount = async (userId: string) => {
    const [row] = await db
      .select({ n: count() })
      .from(notifications)
      .where(and(eq(notifications.userId, userId), isNull(notifications.readAt)));
    return row?.n ?? 0;
  };

  /** The phones a person is signed in on right now. */
  const activeDevices = async (userId: string) =>
    db
      .select({
        token: devices.token,
        platform: devices.platform,
        environment: devices.environment,
      })
      .from(devices)
      .innerJoin(sessions, eq(sessions.id, devices.sessionId))
      .where(and(eq(devices.userId, userId), gt(sessions.expiresAt, new Date())));

  const deliver = async (
    userId: string,
    message: { title: string; body: string; data: Record<string, string> },
  ) => {
    if (!push) return;
    const list = await activeDevices(userId);
    if (list.length === 0) return;
    const result = await push.send({
      ...message,
      userId,
      badge: await unreadCount(userId),
      devices: list,
    });
    const invalid = result?.invalidTokens ?? [];
    if (invalid.length > 0) await db.delete(devices).where(inArray(devices.token, invalid));
  };

  /**
   * Tells the people who should know about a change to an appointment. Never fails the
   * change: a problem is logged and the change stands.
   */
  const notify = async (
    kind: NotificationKind,
    appointmentId: string,
    changedByUserId: string | null,
    previous?: PreviousSlot,
  ) => {
    try {
      const [row] = await db
        .select({ appointment: appointments, customer: customers, business: businesses })
        .from(appointments)
        .innerJoin(customers, eq(customers.id, appointments.customerId))
        .innerJoin(businesses, eq(businesses.id, appointments.businessId))
        .where(eq(appointments.id, appointmentId));
      if (!row) return;
      const { appointment, customer, business } = row;

      const owners = await db
        .select({ userId: users.id })
        .from(users)
        .where(eq(users.role, "owner"));
      const members = await db
        .select({
          userId: businessMembers.userId,
          role: businessMembers.role,
          resourceId: businessMembers.resourceId,
        })
        .from(businessMembers)
        .where(eq(businessMembers.businessId, business.id));

      // Owners and admins see the whole agenda; a professional, the appointments that are
      // theirs, or were theirs until this change.
      const professionals = new Set(
        [appointment.resourceId, previous?.resourceId].filter((id): id is string => !!id),
      );
      const recipients = new Map<string, { isTheProfessional: boolean }>();
      for (const { userId } of owners) recipients.set(userId, { isTheProfessional: false });
      for (const member of members) {
        if (member.role === "admin") {
          recipients.set(member.userId, { isTheProfessional: false });
        } else if (
          member.resourceId &&
          professionals.has(member.resourceId) &&
          !recipients.has(member.userId)
        ) {
          recipients.set(member.userId, {
            isTheProfessional: member.resourceId === appointment.resourceId,
          });
        }
      }
      if (changedByUserId) recipients.delete(changedByUserId);
      if (recipients.size === 0) return;

      const created = await db
        .insert(notifications)
        .values(
          [...recipients].map(([userId, { isTheProfessional }]) => ({
            userId,
            businessId: business.id,
            appointmentId: appointment.id,
            kind,
            ...notificationText(
              kind,
              business,
              appointment,
              customer,
              !isTheProfessional,
              previous,
            ),
          })),
        )
        .returning();

      for (const notification of created) {
        try {
          await deliver(notification.userId, {
            title: notification.title,
            body: notification.body,
            data: {
              kind: notification.kind,
              notificationId: notification.id,
              businessId: business.id,
              appointmentId: appointment.id,
              startsAt: appointment.startsAt.toISOString(),
            },
          });
        } catch (error) {
          console.error("notifications: could not push", error);
        }
      }
    } catch (error) {
      console.error("notifications: could not notify", kind, error);
    }
  };

  return {
    pushEnabled: push !== null,
    unreadCount,

    appointmentBooked: (appointmentId: string, bookedByUserId: string | null) =>
      notify("appointment_booked", appointmentId, bookedByUserId),

    appointmentRescheduled: (
      appointmentId: string,
      previous: PreviousSlot,
      movedByUserId: string | null,
    ) => notify("appointment_rescheduled", appointmentId, movedByUserId, previous),

    appointmentCancelled: (appointmentId: string, cancelledByUserId: string | null) =>
      notify("appointment_cancelled", appointmentId, cancelledByUserId),
  };
}
