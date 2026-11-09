import { addDays, todayIn, weekdayOf } from "@openturnly/core";
import { describe, expect, it } from "vitest";
import type { PushMessage, PushProvider } from "../src/lib/push.ts";
import { createBusiness, createTestApp, OWNER, setUpOwner, type TestApp } from "./helpers.ts";

const APP_URL = "http://localhost:3000";

/** The next Saturday at least two days ahead, in Bogotá (default hours: 9–13). */
function nextSaturday(): string {
  let date = addDays(todayIn("America/Bogota"), 2);
  while (weekdayOf(date) !== 6) date = addDays(date, 1);
  return date;
}

/** A provider that keeps what it was asked to send. */
function recordingPush(invalidTokens: string[] = []) {
  const sent: PushMessage[] = [];
  const provider: PushProvider = {
    name: "test",
    async send(message) {
      sent.push(message);
      return { invalidTokens };
    },
  };
  return { sent, provider };
}

/** Signs in like the phone app: a bearer token, no cookie, no origin. */
async function phone(app: TestApp, email: string, password: string) {
  const signIn = await app.client().post("/api/mobile/sign-in", { email, password });
  expect(signIn.status).toBe(200);
  const token = signIn.body.token as string;
  return async (method: string, path: string, body?: unknown) => {
    const res = await app.app.request(`${APP_URL}${path}`, {
      method,
      headers: {
        authorization: `Bearer ${token}`,
        ...(body ? { "content-type": "application/json" } : {}),
      },
      body: body ? JSON.stringify(body) : undefined,
    });
    const text = await res.text();
    return { status: res.status, body: text ? JSON.parse(text) : null };
  };
}

async function shop(push: PushProvider | null) {
  const app = await createTestApp({ push });
  const owner = await setUpOwner(app);
  const business = await createBusiness(owner);
  const base = `/api/businesses/${business.id}`;
  const haircut = (
    await owner.post(`${base}/services`, { name: "Corte", durationMin: 30, priceAmount: 20_000 })
  ).body.service;
  const juan = (
    await owner.post(`${base}/professionals`, { name: "Juan", serviceIds: [haircut.id] })
  ).body.professional;
  const pepe = (
    await owner.post(`${base}/professionals`, { name: "Pepe", serviceIds: [haircut.id] })
  ).body.professional;
  for (const [name, email, resourceId] of [
    ["Juan", "juan@example.com", juan.id],
    ["Pepe", "pepe@example.com", pepe.id],
  ]) {
    await owner.post(`${base}/members`, {
      name,
      email,
      password: `${name.toLowerCase()}-password-1`,
      role: "professional",
      resourceId,
    });
  }
  let phoneNumber = 3_000_000_000;
  const book = (time: string, resourceId: string | null) =>
    app.client().post(`/api/public/businesses/${business.slug}/bookings`, {
      serviceId: haircut.id,
      resourceId,
      date: nextSaturday(),
      time,
      customer: { firstName: "Ana", lastName: "Pérez", phone: `+57 ${phoneNumber++}` },
    });
  return { app, owner, business, base, haircut, juan, pepe, book };
}

describe("notifications of new bookings", () => {
  it("reach the owner and the professional booked, not the other professionals", async () => {
    const { sent, provider } = recordingPush();
    const { app, business, juan, book } = await shop(provider);
    const ownerPhone = await phone(app, OWNER.email, OWNER.password);
    const juanPhone = await phone(app, "juan@example.com", "juan-password-1");
    const pepePhone = await phone(app, "pepe@example.com", "pepe-password-1");
    for (const [call, token] of [
      [ownerPhone, "owner-token"],
      [juanPhone, "juan-token"],
      [pepePhone, "pepe-token"],
    ] as const) {
      expect((await call("POST", "/api/mobile/devices", { token, platform: "ios" })).status).toBe(
        200,
      );
    }
    expect((await ownerPhone("GET", "/api/mobile/settings")).body).toEqual({
      push: { enabled: true },
    });

    expect((await book("09:00", juan.id)).status).toBe(201);

    const toOwner = sent.find((m) => m.devices.some((d) => d.token === "owner-token"));
    const toJuan = sent.find((m) => m.devices.some((d) => d.token === "juan-token"));
    expect(sent).toHaveLength(2);
    expect(toOwner).toMatchObject({ title: "Nueva reserva", badge: 1 });
    expect(toOwner?.userId).toBeTruthy();
    expect(toJuan?.userId).not.toBe(toOwner?.userId);
    expect(toOwner?.body).toMatch(/^Ana Pérez · Corte · .+ · con Juan$/);
    // The professional knows it's theirs: no "with Juan".
    expect(toJuan?.body).toMatch(/^Ana Pérez · Corte · /);
    expect(toJuan?.body).not.toContain("con Juan");
    expect(toJuan?.data).toMatchObject({ kind: "appointment_booked", businessId: business.id });
    expect(toJuan?.data.appointmentId).toBeTruthy();
    expect(Date.parse(toJuan?.data.startsAt ?? "")).toBeGreaterThan(Date.now());

    // Pepe has nothing; the owner and Juan have it to read in the app.
    expect((await pepePhone("GET", "/api/me/notifications")).body.unreadTotal).toBe(0);
    const list = await juanPhone("GET", `/api/me/notifications?businessId=${business.id}`);
    expect(list.body).toMatchObject({ unread: 1, unreadTotal: 1 });
    expect(list.body.notifications[0]).toMatchObject({
      kind: "appointment_booked",
      readAt: null,
      appointmentId: toJuan?.data.appointmentId,
      appointmentStartsAt: toJuan?.data.startsAt,
    });
  });

  it("aren't sent to whoever booked it, and count up until they're read", async () => {
    const { sent, provider } = recordingPush();
    const { app, owner, base, business, haircut, juan, book } = await shop(provider);
    const juanPhone = await phone(app, "juan@example.com", "juan-password-1");
    await juanPhone("POST", "/api/mobile/devices", { token: "juan-token", platform: "android" });
    const booking = (time: string, phoneNumber: string) => ({
      serviceId: haircut.id,
      resourceId: juan.id,
      date: nextSaturday(),
      time,
      customer: { firstName: "Leo", lastName: "Gómez", phone: phoneNumber },
    });

    // Juan books his own: only the owner is told, and the owner has no phone signed in.
    expect(
      (await juanPhone("POST", `${base}/appointments`, booking("10:00", "+57 300 000 0002")))
        .status,
    ).toBe(201);
    expect(sent).toHaveLength(0);

    // The owner books one for Juan: Juan is told, the owner isn't.
    expect(
      (await owner.post(`${base}/appointments`, booking("11:00", "+57 300 000 0003"))).status,
    ).toBe(201);
    expect(sent).toHaveLength(1);
    expect(sent[0]).toMatchObject({ badge: 1, devices: [{ token: "juan-token" }] });

    await book("09:00", juan.id);
    expect(sent.at(-1)?.badge).toBe(2);

    // Read one, then the rest of the business.
    const list = await juanPhone("GET", `/api/me/notifications?businessId=${business.id}`);
    expect(list.body.notifications).toHaveLength(2);
    const first = list.body.notifications[0].id as string;
    const one = await juanPhone("POST", "/api/me/notifications/read", { ids: [first] });
    expect(one.body).toEqual({ unreadTotal: 1 });
    const rest = await juanPhone("POST", "/api/me/notifications/read", {
      businessId: business.id,
    });
    expect(rest.body).toEqual({ unreadTotal: 0 });

    // The owner kept theirs: Juan's own booking and the one from the booking page.
    expect((await owner.get("/api/me/notifications")).body.unreadTotal).toBe(2);
  });
});

describe("phones registered for push", () => {
  it("stop receiving when the session ends, and a token belongs to its last person", async () => {
    const { sent, provider } = recordingPush();
    const { app, juan, pepe, book } = await shop(provider);
    const juanPhone = await phone(app, "juan@example.com", "juan-password-1");
    const pepePhone = await phone(app, "pepe@example.com", "pepe-password-1");

    // The same phone, signed in as Juan, then as Pepe: the token is Pepe's now.
    await juanPhone("POST", "/api/mobile/devices", { token: "shared", platform: "ios" });
    await pepePhone("POST", "/api/mobile/devices", { token: "shared", platform: "ios" });
    await book("09:00", juan.id);
    expect(sent.filter((m) => m.devices.some((d) => d.token === "shared"))).toHaveLength(0);
    await book("09:30", pepe.id);
    expect(sent.at(-1)?.devices).toEqual([
      { token: "shared", platform: "ios", environment: "production" },
    ]);

    // Signing out ends it.
    expect((await pepePhone("POST", "/api/mobile/sign-out")).status).toBe(200);
    const before = sent.length;
    await book("10:00", pepe.id);
    expect(sent).toHaveLength(before);
  });

  it("are forgotten when switched off, and when the provider says a token is dead", async () => {
    const { sent, provider } = recordingPush(["dead-token"]);
    const { app, juan, book } = await shop(provider);
    const juanPhone = await phone(app, "juan@example.com", "juan-password-1");
    await juanPhone("POST", "/api/mobile/devices", {
      token: "dead-token",
      platform: "ios",
      environment: "sandbox",
    });
    await book("09:00", juan.id);
    expect(sent).toHaveLength(1);
    expect(sent[0]?.devices[0]?.environment).toBe("sandbox");
    await book("09:30", juan.id);
    expect(sent).toHaveLength(1);

    await juanPhone("POST", "/api/mobile/devices", { token: "live-token", platform: "android" });
    expect((await juanPhone("DELETE", "/api/mobile/devices", { token: "live-token" })).status).toBe(
      200,
    );
    await book("10:00", juan.id);
    expect(sent).toHaveLength(1);
    // The notifications themselves are still there to read.
    expect((await juanPhone("GET", "/api/me/notifications")).body.unreadTotal).toBe(3);
  });

  it("refuse bad input and anyone signed out", async () => {
    const { app } = await shop(null);
    const juanPhone = await phone(app, "juan@example.com", "juan-password-1");
    const bad = await juanPhone("POST", "/api/mobile/devices", { token: "", platform: "web" });
    expect(bad.status).toBe(400);
    const anonymous = await app.client().post("/api/mobile/devices", {
      token: "x",
      platform: "ios",
    });
    expect(anonymous.status).toBe(401);
  });
});

describe("without a push provider", () => {
  it("says so to the apps, and still keeps the notifications", async () => {
    const { app, juan, book } = await shop(null);
    const juanPhone = await phone(app, "juan@example.com", "juan-password-1");
    expect((await juanPhone("GET", "/api/mobile/settings")).body).toEqual({
      push: { enabled: false },
    });
    expect((await book("09:00", juan.id)).status).toBe(201);
    expect((await juanPhone("GET", "/api/me/notifications")).body.unreadTotal).toBe(1);
  });

  it("a provider that fails never fails the booking", async () => {
    const failing: PushProvider = {
      name: "failing",
      send: () => Promise.reject(new Error("down")),
    };
    const { app, juan, book } = await shop(failing);
    const juanPhone = await phone(app, "juan@example.com", "juan-password-1");
    await juanPhone("POST", "/api/mobile/devices", { token: "t", platform: "ios" });
    const errors = console.error;
    console.error = () => {};
    try {
      expect((await book("09:00", juan.id)).status).toBe(201);
    } finally {
      console.error = errors;
    }
    expect((await juanPhone("GET", "/api/me/notifications")).body.unreadTotal).toBe(1);
  });
});
