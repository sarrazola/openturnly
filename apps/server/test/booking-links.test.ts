import { addDays, todayIn, weekdayOf } from "@openturnly/core";
import { describe, expect, it } from "vitest";
import { createBusiness, createTestApp, setUpOwner, type TestApp } from "./helpers.ts";

/** The next Saturday at least two days ahead, in Bogotá (default hours: 9–13). */
function nextSaturday(): string {
  let date = addDays(todayIn("America/Bogota"), 2);
  while (weekdayOf(date) !== 6) date = addDays(date, 1);
  return date;
}

async function shop() {
  const app = await createTestApp();
  const owner = await setUpOwner(app);
  const business = await createBusiness(owner);
  const base = `/api/businesses/${business.id}`;
  const service = async (input: Record<string, unknown>) =>
    (await owner.post(`${base}/services`, { durationMin: 30, priceAmount: 20_000, ...input })).body
      .service;
  const date = nextSaturday();
  const customer = app.client();
  const page = (query = "") => customer.get(`/api/public/businesses/fte-barber${query}`);
  const free = async (serviceId: string, extra = "") => {
    const res = await customer.get(
      `/api/public/businesses/fte-barber/availability?serviceId=${serviceId}&from=${date}&to=${date}${extra}`,
    );
    return { status: res.status, times: (res.body.days?.[date] ?? []) as string[] };
  };
  let n = 0;
  const book = (serviceId: string, time: string, extra: Record<string, unknown> = {}) =>
    customer.post("/api/public/businesses/fte-barber/bookings", {
      serviceId,
      resourceId: null,
      date,
      time,
      customer: { firstName: "Ana", lastName: `Pérez ${++n}`, phone: `+57 300 123 45${10 + n}` },
      ...extra,
    });
  return { app, owner, business, base, service, date, page, free, book };
}

async function signIn(app: TestApp, email: string, password: string) {
  const client = app.client();
  await client.post("/api/auth/sign-in/email", { email, password });
  return client;
}

describe("margins before and after", () => {
  it("keeps the time around an appointment free, and remembers the margins it was booked with", async () => {
    const { owner, base, service, free, book } = await shop();
    const session = await service({ name: "Sesión", bufferBeforeMin: 15, bufferAfterMin: 15 });
    expect(session).toMatchObject({ bufferBeforeMin: 15, bufferAfterMin: 15 });

    expect((await book(session.id, "10:00")).status).toBe(201);
    // 09:30 would end where the margin before 10:00 starts; 10:30 starts inside the one after.
    const { times } = await free(session.id);
    expect(times).not.toContain("09:30");
    expect(times).not.toContain("10:30");
    expect(times).toContain("09:00");
    expect(times).toContain("11:00");

    // Changing the service later doesn't free the time around what's booked.
    await owner.patch(`${base}/services/${session.id}`, { bufferBeforeMin: 0, bufferAfterMin: 0 });
    const plain = await service({ name: "Corte" });
    expect((await free(plain.id)).times).not.toContain("10:30");
  });
});

describe("most appointments a day", () => {
  it("closes the day on the booking page at the limit; the team can still book", async () => {
    const { owner, base, business, service, date, free, book } = await shop();
    const cut = await service({ name: "Corte" });
    const juan = (await owner.post(`${base}/professionals`, { name: "Juan", serviceIds: [cut.id] }))
      .body.professional;
    await owner.patch(`/api/businesses/${business.id}`, { maxDailyAppointments: 1 });

    expect((await book(cut.id, "09:00")).status).toBe(201);
    expect((await free(cut.id)).times).toEqual([]);

    // A professional's own limit wins over the business's.
    await owner.patch(`${base}/professionals/${juan.id}`, { maxDailyAppointments: 2 });
    expect((await free(cut.id)).times).toContain("10:00");

    await owner.patch(`${base}/professionals/${juan.id}`, { maxDailyAppointments: null });
    const staff = await owner.post(`${base}/appointments`, {
      serviceId: cut.id,
      resourceId: juan.id,
      date,
      time: "11:00",
      customer: { firstName: "Leo", lastName: "Ruiz", phone: "+57 300 999 0000" },
    });
    expect(staff.status).toBe(201);
  });
});

describe("look busy", () => {
  it("hides some free times, the same ones every time, and they can't be booked", async () => {
    const { owner, business, service, free, book } = await shop();
    const cut = await service({ name: "Corte" });
    const all = (await free(cut.id)).times;
    expect(all.length).toBeGreaterThan(4);

    await owner.patch(`/api/businesses/${business.id}`, { hiddenSlotsPercent: 50 });
    const shown = (await free(cut.id)).times;
    expect(shown.length).toBeLessThan(all.length);
    expect(shown.length).toBeGreaterThan(0);
    expect((await free(cut.id)).times).toEqual(shown);

    const hidden = all.find((time) => !shown.includes(time)) ?? "";
    expect((await book(cut.id, hidden)).status).toBe(409);
    expect((await book(cut.id, shown[0] ?? "")).status).toBe(201);

    // Too much is refused.
    expect(
      (await owner.patch(`/api/businesses/${business.id}`, { hiddenSlotsPercent: 95 })).status,
    ).toBe(400);
  });
});

describe("booking links", () => {
  it("offer their own services on the same agenda, and the appointment says where it came from", async () => {
    const { app, owner, base, service, page, free, book } = await shop();
    const session = await service({ name: "Sesión" });
    const discovery = await service({ name: "Llamada inicial", listed: false });

    // The main page leaves out what's only offered through links.
    const main = await page();
    expect(main.body.link).toBeNull();
    expect(main.body.services.map((item: { name: string }) => item.name)).toEqual(["Sesión"]);
    expect((await free(discovery.id)).status).toBe(404);
    expect((await book(discovery.id, "09:00")).status).toBe(404);

    const created = await owner.post(`${base}/booking-links`, {
      name: "Interesados",
      slug: "interesados",
      description: "Una primera llamada sin costo.",
      serviceIds: [discovery.id, "not-a-service"],
    });
    expect(created.status).toBe(201);
    expect(created.body.link.serviceIds).toEqual([discovery.id]);
    expect(
      (
        await owner.post(`${base}/booking-links`, {
          name: "Otro",
          slug: "interesados",
          serviceIds: [session.id],
        })
      ).status,
    ).toBe(409);
    expect(
      (
        await owner.post(`${base}/booking-links`, {
          name: "Entrar",
          slug: "login",
          serviceIds: [session.id],
        })
      ).status,
    ).toBe(400);

    const linked = await page("?link=interesados");
    expect(linked.body.link).toEqual({
      slug: "interesados",
      name: "Interesados",
      description: "Una primera llamada sin costo.",
    });
    expect(linked.body.services.map((item: { name: string }) => item.name)).toEqual([
      "Llamada inicial",
    ]);
    expect((await page("?link=nope")).status).toBe(404);
    // A link only books its own services.
    expect((await book(session.id, "09:00", { link: "interesados" })).status).toBe(404);

    const booked = await book(discovery.id, "09:00", { link: "interesados" });
    expect(booked.status).toBe(201);
    // Same agenda: that time is gone for the main page's services too.
    expect((await free(session.id)).times).not.toContain("09:00");

    const list = await owner.get(
      `${base}/appointments?from=${new Date(Date.now() - 86_400_000).toISOString()}&to=${new Date(Date.now() + 30 * 86_400_000).toISOString()}`,
    );
    expect(list.body.appointments[0]).toMatchObject({ bookingLinkName: "Interesados" });

    // Turned off, the link is gone; deleted, its appointments keep its name.
    await owner.patch(`${base}/booking-links/${created.body.link.id}`, { active: false });
    expect((await page("?link=interesados")).status).toBe(404);
    expect((await owner.delete(`${base}/booking-links/${created.body.link.id}`)).status).toBe(204);
    const after = await owner.get(
      `${base}/appointments?from=${new Date(Date.now() - 86_400_000).toISOString()}&to=${new Date(Date.now() + 30 * 86_400_000).toISOString()}`,
    );
    expect(after.body.appointments[0]).toMatchObject({ bookingLinkName: "Interesados" });

    // Professionals don't manage links.
    const juan = (
      await owner.post(`${base}/professionals`, { name: "Juan", serviceIds: [session.id] })
    ).body.professional;
    await owner.post(`${base}/members`, {
      name: "Juan",
      email: "juan@example.com",
      password: "juan-password-1",
      role: "professional",
      resourceId: juan.id,
    });
    const professional = await signIn(app, "juan@example.com", "juan-password-1");
    expect((await professional.get(`${base}/booking-links`)).status).toBe(403);
  });
});
