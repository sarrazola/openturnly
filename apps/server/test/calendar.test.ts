import { addDays, todayIn, weekdayOf } from "@openturnly/core";
import { describe, expect, it } from "vitest";
import { createBusiness, createTestApp, setUpOwner } from "./helpers.ts";

/** The next Saturday at least two days ahead, in Bogotá (default hours: 9–13). */
function nextSaturday(): string {
  let date = addDays(todayIn("America/Bogota"), 2);
  while (weekdayOf(date) !== 6) date = addDays(date, 1);
  return date;
}

describe("calendar file", () => {
  it("gives a booking's event to the customer's calendar, without who booked it", async () => {
    const app = await createTestApp();
    const owner = await setUpOwner(app);
    const business = await createBusiness(owner);
    await owner.patch(`/api/businesses/${business.id}`, {
      phone: "+573001234567",
      address: "Calle 10 # 20-30",
    });
    const base = `/api/businesses/${business.id}`;
    const haircut = (
      await owner.post(`${base}/services`, { name: "Corte", durationMin: 60, priceAmount: 25_000 })
    ).body.service;
    const visitor = app.client();
    const booked = (
      await visitor.post(`/api/public/businesses/${business.slug}/bookings`, {
        serviceId: haircut.id,
        resourceId: null,
        date: nextSaturday(),
        time: "10:00",
        customer: { firstName: "Ana", lastName: "Pérez", phone: "+57 300 123 4567" },
      })
    ).body.appointment;
    const path = `/api/public/businesses/${business.slug}/appointments`;

    const res = await visitor.raw("GET", `${path}/${booked.code.toLowerCase()}/calendar.ics`);
    expect(res.status).toBe(200);
    expect(res.headers.get("content-type")).toBe("text/calendar; charset=utf-8");
    expect(res.headers.get("content-disposition")).toBe(`inline; filename="${booked.code}.ics"`);
    const file = (await res.text()).replace(/\r\n /g, "");
    const start = new Date(booked.startsAt).toISOString().replace(/[-:]|\.\d{3}/g, "");
    expect(file).toContain(`UID:${booked.id}@openturnly\r\n`);
    expect(file).toContain(`DTSTART:${start}\r\n`);
    expect(file).toContain(`SUMMARY:Corte en ${business.name.replace(/,/g, "\\,")}\r\n`);
    expect(file).toContain(`Código de reserva: ${booked.code}`);
    expect(file).toContain("Teléfono: +57 300 1234567");
    expect(file).toMatch(/LOCATION:.*Calle 10 # 20-30\r\n/);
    expect(file).not.toContain("Ana");
    expect(file).not.toContain("300 123 4567"); // the customer's number, as typed

    // A code from nowhere, or a cancelled booking, has no event.
    expect((await visitor.raw("GET", `${path}/ZZZZZZ/calendar.ics`)).status).toBe(404);
    await owner.post(`${base}/appointments/${booked.id}/cancel`, {});
    expect((await visitor.raw("GET", `${path}/${booked.code}/calendar.ics`)).status).toBe(404);
  });
});
