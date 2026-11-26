import { createServer, type Server } from "node:http";
import type { AddressInfo } from "node:net";
import { addDays, todayIn, weekdayOf } from "@openturnly/core";
import { afterEach, describe, expect, it } from "vitest";
import {
  createBusiness,
  createTestApp,
  setUpOwner,
  type TestApp,
  type TestClient,
} from "./helpers.ts";

const servers: Server[] = [];
afterEach(() => {
  for (const server of servers.splice(0)) server.close();
});

const CALENDAR_SCOPE = "https://www.googleapis.com/auth/calendar.app.created";

type FakeEvent = {
  id: string;
  calendarId: string;
  summary: string;
  description: string;
  location: string;
  start: { dateTime: string };
  attendees?: { email: string }[];
  hangoutLink?: string;
};

/** Stands in for Google: tokens, calendars and events, with switches to make it fail. */
async function fakeGoogle() {
  const calendars = new Map<string, { summary: string; timeZone: string }>();
  const events = new Map<string, FakeEvent>();
  const log: { method: string; path: string; sendUpdates: string | null }[] = [];
  const state = {
    accounts: { "code-owner": "owner@gmail.com", "code-juan": "juan@gmail.com" } as Record<
      string,
      string
    >,
    grantCalendar: true,
    expiresIn: 3600,
    revoked: false,
    failEvents: false,
    revokedTokens: [] as string[],
  };
  let counter = 0;
  const server = createServer((req, res) => {
    let body = "";
    req.on("data", (chunk) => {
      body += chunk;
    });
    req.on("end", () => {
      const url = new URL(req.url ?? "", "http://google.test");
      const path = url.pathname;
      const reply = (status: number, value?: unknown) => {
        res.writeHead(status, { "content-type": "application/json" });
        res.end(value === undefined ? "" : JSON.stringify(value));
      };
      log.push({
        method: req.method ?? "",
        path,
        sendUpdates: url.searchParams.get("sendUpdates"),
      });

      if (path === "/token") {
        const form = new URLSearchParams(body);
        if (form.get("grant_type") === "refresh_token") {
          if (state.revoked) return reply(400, { error: "invalid_grant" });
          return reply(200, { access_token: `at-${++counter}`, expires_in: state.expiresIn });
        }
        const email = state.accounts[form.get("code") ?? ""];
        if (!email) return reply(400, { error: "invalid_grant" });
        const idToken = [
          "e30",
          Buffer.from(JSON.stringify({ sub: `sub-${email}`, email })).toString("base64url"),
          "sig",
        ].join(".");
        return reply(200, {
          access_token: `at-${++counter}`,
          refresh_token: `rt-${email}`,
          expires_in: state.expiresIn,
          scope: ["openid", "https://www.googleapis.com/auth/userinfo.email"]
            .concat(state.grantCalendar ? [CALENDAR_SCOPE] : [])
            .join(" "),
          id_token: idToken,
        });
      }
      if (path === "/revoke") {
        state.revokedTokens.push(new URLSearchParams(body).get("token") ?? "");
        return reply(200, {});
      }
      if (!req.headers.authorization?.startsWith("Bearer at-")) return reply(401, {});

      const json = body ? JSON.parse(body) : {};
      if (path === "/calendar/v3/calendars" && req.method === "POST") {
        const id = `cal-${++counter}@group.calendar.google.com`;
        calendars.set(id, json);
        return reply(200, { id, ...json });
      }
      const calendarMatch = /^\/calendar\/v3\/calendars\/([^/]+)$/.exec(path);
      if (calendarMatch) {
        const id = decodeURIComponent(calendarMatch[1] ?? "");
        if (!calendars.has(id)) return reply(404, { error: { errors: [{ reason: "notFound" }] } });
        if (req.method === "DELETE") {
          calendars.delete(id);
          for (const [key, event] of events) if (event.calendarId === id) events.delete(key);
          return reply(204);
        }
        calendars.set(id, { ...calendars.get(id), ...json });
        return reply(200, { id, ...calendars.get(id) });
      }
      const eventsMatch = /^\/calendar\/v3\/calendars\/([^/]+)\/events(?:\/([^/]+))?$/.exec(path);
      if (eventsMatch) {
        const calendarId = decodeURIComponent(eventsMatch[1] ?? "");
        if (!calendars.has(calendarId)) {
          return reply(404, { error: { errors: [{ reason: "notFound" }] } });
        }
        if (state.failEvents) return reply(503, { error: { message: "backend error" } });
        const meeting =
          url.searchParams.get("conferenceDataVersion") === "1" &&
          json.conferenceData?.createRequest;
        const eventId = eventsMatch[2] ? decodeURIComponent(eventsMatch[2]) : null;
        if (!eventId && req.method === "POST") {
          const id = `evt-${++counter}`;
          const event: FakeEvent = {
            ...json,
            id,
            calendarId,
            ...(meeting ? { hangoutLink: `https://meet.google.com/abc-${id}` } : {}),
          };
          delete (event as Record<string, unknown>).conferenceData;
          events.set(id, event);
          return reply(200, event);
        }
        const event = eventId ? events.get(eventId) : undefined;
        if (!event) return reply(404, { error: { errors: [{ reason: "notFound" }] } });
        if (req.method === "GET") return reply(200, event);
        if (req.method === "DELETE") {
          events.delete(event.id);
          return reply(204);
        }
        const { conferenceData: _ignored, ...fields } = json;
        Object.assign(event, fields);
        if (meeting && !event.hangoutLink)
          event.hangoutLink = `https://meet.google.com/abc-${event.id}`;
        return reply(200, event);
      }
      return reply(404, {});
    });
  });
  await new Promise<void>((resolve) => server.listen(0, "127.0.0.1", resolve));
  servers.push(server);
  const { port } = server.address() as AddressInfo;
  return { url: `http://127.0.0.1:${port}`, calendars, events, log, state };
}

function nextSaturday(): string {
  let date = addDays(todayIn("America/Bogota"), 2);
  while (weekdayOf(date) !== 6) date = addDays(date, 1);
  return date;
}

/** Goes through Google's consent as `client`, with Google answering `code`. */
async function connect(client: TestClient, code: string, returnTo = "/admin/account") {
  const start = await client.raw("GET", `/api/integrations/google/start?returnTo=${returnTo}`);
  expect(start.status).toBe(302);
  const consent = new URL(start.headers.get("location") ?? "");
  expect(consent.origin).toBe("https://accounts.google.com");
  expect(consent.searchParams.get("scope")).toContain(CALENDAR_SCOPE);
  expect(consent.searchParams.get("redirect_uri")).toBe(
    "http://localhost:3000/api/integrations/google/callback",
  );
  const state = consent.searchParams.get("state") ?? "";
  const back = await client.raw(
    "GET",
    `/api/integrations/google/callback?code=${code}&state=${encodeURIComponent(state)}`,
  );
  expect(back.status).toBe(302);
  return { location: back.headers.get("location") ?? "", state };
}

async function shop() {
  const google = await fakeGoogle();
  const app = await createTestApp({
    google: {
      clientId: "client-id.apps.googleusercontent.com",
      clientSecret: "client-secret",
      redirectUrl: "http://localhost:3000/api/integrations/google/callback",
    },
    googleUrl: google.url,
  });
  const owner = await setUpOwner(app);
  const business = await createBusiness(owner);
  const base = `/api/businesses/${business.id}`;
  const service = (
    await owner.post(`${base}/services`, { name: "Corte", durationMin: 60, priceAmount: 20_000 })
  ).body.service;
  const date = nextSaturday();
  const customer = app.client();
  const book = (serviceId: string, time = "10:00", resourceId: string | null = null) =>
    customer.post("/api/public/businesses/fte-barber/bookings", {
      serviceId,
      resourceId,
      date,
      time,
      customer: {
        firstName: "Ana",
        lastName: "Pérez",
        phone: "+57 300 123 4567",
        email: "ana@example.com",
      },
    });
  return { app, google, owner, business, base, service, date, book };
}

async function signIn(app: TestApp, email: string, password: string) {
  const client = app.client();
  const res = await client.post("/api/auth/sign-in/email", { email, password });
  expect(res.status).toBe(200);
  return client;
}

describe("connecting a calendar", () => {
  it("is off without the install's Google client", async () => {
    const app = await createTestApp();
    const owner = await setUpOwner(app);
    expect((await owner.get("/api/me/calendar")).body).toMatchObject({
      available: false,
      connection: null,
    });
    expect((await owner.raw("GET", "/api/integrations/google/start")).status).toBe(404);
  });

  it("connects, makes a calendar named after the business and copies appointments", async () => {
    const { owner, google, service, book } = await shop();
    const first = await book(service.id, "09:00");
    expect(first.status).toBe(201);

    const { location } = await connect(owner, "code-owner");
    expect(location).toBe("/admin/account?calendar=connected");
    const view = (await owner.get("/api/me/calendar")).body;
    expect(view.connection).toMatchObject({ email: "owner@gmail.com", status: "connected" });
    expect(view.businesses).toEqual([
      expect.objectContaining({ name: "FTE Barber Studio", role: "owner", enabled: true }),
    ]);

    // The appointment booked before connecting is already there.
    expect([...google.calendars.values()]).toEqual([
      { summary: "FTE Barber Studio", timeZone: "America/Bogota" },
    ]);
    expect([...google.events.values()]).toEqual([
      expect.objectContaining({
        summary: "Ana Pérez · Corte",
        description: expect.stringContaining(`Código de reserva: ${first.body.appointment.code}`),
      }),
    ]);

    // New ones arrive while the booking is made, never with the customer as a guest.
    await book(service.id, "11:00");
    expect(google.events.size).toBe(2);
    expect([...google.events.values()].every((event) => !event.attendees)).toBe(true);
    expect(JSON.stringify([...google.events.values()])).not.toContain("conferenceData");
    // The tokens never leave the server.
    expect(JSON.stringify(view)).not.toContain("rt-owner");
  });

  it("refuses a consent that isn't theirs, or without the calendar", async () => {
    const { app, owner, google } = await shop();
    const start = await owner.raw("GET", "/api/integrations/google/start");
    const state = new URL(start.headers.get("location") ?? "").searchParams.get("state") ?? "";

    const tampered = await owner.raw(
      "GET",
      `/api/integrations/google/callback?code=code-owner&state=${state}x`,
    );
    expect(tampered.headers.get("location")).toBe("/admin/account?calendar=expired");

    const stranger = app.client();
    const other = await stranger.raw(
      "GET",
      `/api/integrations/google/callback?code=code-owner&state=${encodeURIComponent(state)}`,
    );
    expect(other.headers.get("location")).toBe("/admin/account?calendar=signed_out");

    const denied = await owner.raw(
      "GET",
      `/api/integrations/google/callback?error=access_denied&state=${encodeURIComponent(state)}`,
    );
    expect(denied.headers.get("location")).toBe("/admin/account?calendar=denied");

    google.state.grantCalendar = false;
    const { location } = await connect(owner, "code-owner");
    expect(location).toBe("/admin/account?calendar=calendar_not_allowed");
    expect(google.state.revokedTokens).toEqual(["rt-owner@gmail.com"]);
    expect((await owner.get("/api/me/calendar")).body.connection).toBeNull();

    // Only paths of this install to come back to.
    google.state.grantCalendar = true;
    expect((await connect(owner, "code-owner", "//evil.example")).location).toBe(
      "/admin/account?calendar=connected",
    );
  });
});

describe("keeping the copies up to date", () => {
  it("moves and removes the copy with the appointment, and follows a rename", async () => {
    const { owner, google, base, business, service, date, book } = await shop();
    await connect(owner, "code-owner");
    const booked = (await book(service.id, "09:00")).body.appointment;
    const [event] = [...google.events.values()];

    const moved = await owner.patch(`${base}/appointments/${booked.id}`, {
      serviceId: service.id,
      resourceId: null,
      date,
      time: "11:00",
    });
    expect(moved.status).toBe(200);
    expect(google.events.get(event?.id ?? "")?.start.dateTime).toBe(
      moved.body.appointment.startsAt,
    );

    await owner.patch(`/api/businesses/${business.id}`, { name: "FTE Barbería" });
    expect([...google.calendars.values()][0]?.summary).toBe("FTE Barbería");

    expect((await owner.post(`${base}/appointments/${booked.id}/cancel`)).status).toBe(200);
    expect(google.events.size).toBe(0);
  });

  it("tries again later when Google fails, without failing the booking", async () => {
    const { app, owner, google, service, book } = await shop();
    await connect(owner, "code-owner");
    google.state.failEvents = true;
    expect((await book(service.id)).status).toBe(201);
    expect(google.events.size).toBe(0);

    google.state.failEvents = false;
    // Not due yet: nothing happens.
    await app.app.automations.sweep(Date.now());
    expect(google.events.size).toBe(0);
    await app.app.automations.sweep(Date.now() + 2 * 60_000);
    expect(google.events.size).toBe(1);
  });

  it("notices when the person took the access back", async () => {
    const { owner, google, service, book } = await shop();
    google.state.expiresIn = 0;
    await connect(owner, "code-owner");
    google.state.revoked = true;
    expect((await book(service.id)).status).toBe(201);
    expect((await owner.get("/api/me/calendar")).body.connection.status).toBe("disconnected");

    // Connecting again catches up.
    google.state.revoked = false;
    await connect(owner, "code-owner");
    expect((await owner.get("/api/me/calendar")).body.connection.status).toBe("connected");
    expect(google.events.size).toBe(1);
  });

  it("disconnects: deletes the calendars and revokes the access", async () => {
    const { owner, google, service, book } = await shop();
    await connect(owner, "code-owner");
    await book(service.id);
    const after = await owner.delete("/api/me/calendar");
    expect(after.status).toBe(200);
    expect(after.body.connection).toBeNull();
    expect(google.calendars.size).toBe(0);
    expect(google.state.revokedTokens).toEqual(["rt-owner@gmail.com"]);
  });
});

describe("online services", () => {
  it("the professional's calendar makes the call and invites the customer", async () => {
    const { app, owner, google, base, book } = await shop();
    const online = (
      await owner.post(`${base}/services`, {
        name: "Asesoría",
        durationMin: 60,
        priceAmount: 50_000,
        mode: "online",
      })
    ).body.service;
    expect(online.mode).toBe("online");
    const juan = (
      await owner.post(`${base}/professionals`, { name: "Juan", serviceIds: [online.id] })
    ).body.professional;
    await owner.post(`${base}/professionals`, { name: "Pepe", serviceIds: [online.id] });
    await owner.post(`${base}/members`, {
      name: "Juan",
      email: "juan@example.com",
      password: "juan-password-1",
      role: "professional",
      resourceId: juan.id,
    });

    // Nobody can host the call yet: the booking page doesn't offer it.
    const before = (await app.client().get("/api/public/businesses/fte-barber")).body;
    expect(before.services.map((service: { name: string }) => service.name)).not.toContain(
      "Asesoría",
    );

    const juanClient = await signIn(app, "juan@example.com", "juan-password-1");
    await connect(juanClient, "code-juan");
    await connect(owner, "code-owner");

    const page = (await app.client().get("/api/public/businesses/fte-barber")).body;
    expect(page.services).toContainEqual(
      expect.objectContaining({ name: "Asesoría", mode: "online" }),
    );
    // Only Juan, whose calendar is connected, takes it.
    expect(
      page.professionals
        .filter((member: { serviceIds: string[] }) => member.serviceIds.includes(online.id))
        .map((member: { name: string }) => member.name),
    ).toEqual(["Juan"]);

    const booked = await book(online.id, "10:00");
    expect(booked.status).toBe(201);
    const link = booked.body.appointment.meetingUrl;
    expect(link).toMatch(/^https:\/\/meet\.google\.com\//);

    const copies = [...google.events.values()];
    expect(copies).toHaveLength(2);
    const hosted = copies.find((event) => event.hangoutLink);
    expect(hosted?.attendees).toEqual([expect.objectContaining({ email: "ana@example.com" })]);
    // The customer sees the copy they're invited to: no admin link in it.
    expect(hosted?.description).not.toContain("/admin/");
    const ownerCopy = copies.find((event) => !event.hangoutLink);
    expect(ownerCopy?.summary).toBe("Ana Pérez · Asesoría (Juan)");
    expect(ownerCopy?.description).toContain(`Videollamada: ${link}`);
    expect(
      google.log.find(
        (entry) =>
          entry.method === "POST" && entry.path.endsWith("/events") && entry.sendUpdates === "all",
      ),
    ).toBeDefined();

    // Cancelling tells the customer through Google too.
    await owner.post(`${base}/appointments/${booked.body.appointment.id}/cancel`);
    expect(google.events.size).toBe(0);
    expect(
      google.log.filter((entry) => entry.method === "DELETE" && entry.sendUpdates === "all"),
    ).toHaveLength(1);
  });
});

describe("online services without a host", () => {
  it("the team can still book it: no call, and no copy promises one", async () => {
    const { owner, google, base, date } = await shop();
    const online = (
      await owner.post(`${base}/services`, {
        name: "Asesoría",
        durationMin: 60,
        priceAmount: 50_000,
        mode: "online",
      })
    ).body.service;
    const pepe = (
      await owner.post(`${base}/professionals`, { name: "Pepe", serviceIds: [online.id] })
    ).body.professional;
    await connect(owner, "code-owner");

    const booked = await owner.post(`${base}/appointments`, {
      serviceId: online.id,
      resourceId: pepe.id,
      date,
      time: "10:00",
      customer: { firstName: "Ana", lastName: "Pérez", phone: "+57 300 123 4567" },
    });
    expect(booked.status).toBe(201);
    expect(booked.body.appointment.meetingUrl).toBeNull();
    const [copy] = [...google.events.values()];
    expect(copy?.hangoutLink).toBeUndefined();
    expect(copy?.attendees).toBeUndefined();
    expect(copy?.description).not.toContain("Videollamada");
  });
});
