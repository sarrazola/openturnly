import { createServer } from "node:http";
import type { AddressInfo } from "node:net";
import { addDays, todayIn, weekdayOf } from "@openturnly/core";
import { afterEach, describe, expect, it } from "vitest";
import { addMember, createBusiness, createTestApp, setUpOwner } from "./helpers.ts";

/** The next Saturday at least two days ahead, in Bogotá (default hours: 9–13). */
function nextSaturday(): string {
  let date = addDays(todayIn("America/Bogota"), 2);
  while (weekdayOf(date) !== 6) date = addDays(date, 1);
  return date;
}

const servers: { close: () => void }[] = [];
afterEach(() => {
  for (const server of servers.splice(0)) server.close();
});

async function receiver() {
  const server = createServer((req, res) => {
    req.resume();
    req.on("end", () => res.writeHead(200).end("ok"));
  });
  await new Promise<void>((resolve) => server.listen(0, "127.0.0.1", resolve));
  servers.push(server);
  return `http://127.0.0.1:${(server.address() as AddressInfo).port}/hook`;
}

describe("send log", () => {
  it("shows the install's owner every business's sends, filtered and totalled", async () => {
    const app = await createTestApp();
    const owner = await setUpOwner(app);
    const url = await receiver();
    const today = todayIn("America/Bogota");
    const shops = [];
    for (const [name, slug] of [
      ["Barbería Norte", "norte"],
      ["Spa Sur", "spa-sur"],
    ] as const) {
      const business = await createBusiness(owner, { name, slug });
      const base = `/api/businesses/${business.id}`;
      const service = (
        await owner.post(`${base}/services`, { name: "Corte", durationMin: 60, priceAmount: 1000 })
      ).body.service;
      await owner.post(`${base}/automations`, {
        name: `Aviso ${name}`,
        trigger: "created",
        actions: [{ id: "hook", type: "webhook", url, fields: ["appointment.code"] }],
      });
      await app.client().post(`/api/public/businesses/${slug}/bookings`, {
        serviceId: service.id,
        resourceId: null,
        date: nextSaturday(),
        time: "10:00",
        customer: { firstName: "Ana", lastName: name, phone: "+57 300 123 4567" },
      });
      shops.push(business);
    }
    await app.app.automations.deliverDue();

    const period = `from=${addDays(today, -1)}&to=${today}&timezone=America/Bogota`;
    const all = await owner.get(`/api/deliveries?${period}`);
    expect(all.status).toBe(200);
    expect(all.body.totals).toEqual({ sends: 2, charged: [] });
    expect(all.body.more).toBe(false);
    expect(
      all.body.deliveries.map((item: { business: { name: string } }) => item.business.name).sort(),
    ).toEqual(["Barbería Norte", "Spa Sur"]);
    expect(all.body.deliveries[0]).toMatchObject({
      channel: "webhook",
      status: "sent",
      responseStatus: 200,
      automationName: expect.stringMatching(/^Aviso /),
      customer: { firstName: "Ana" },
      appointment: { code: expect.any(String) },
    });

    const one = await owner.get(`/api/deliveries?${period}&businessId=${shops[1]?.id}`);
    expect(one.body.deliveries).toEqual([
      expect.objectContaining({ business: expect.objectContaining({ name: "Spa Sur" }) }),
    ]);
    expect((await owner.get(`/api/deliveries?${period}&channel=whatsapp`)).body.deliveries).toEqual(
      [],
    );
    const tomorrow = addDays(today, 1);
    expect(
      (await owner.get(`/api/deliveries?from=${tomorrow}&to=${tomorrow}&timezone=America/Bogota`))
        .body.totals.sends,
    ).toBe(0);
    expect((await owner.get("/api/deliveries?from=x&to=y")).status).toBe(400);

    // Only whoever runs the install sees every business's sends.
    const admin = await addMember(app, owner, shops[0]?.id ?? "");
    expect((await admin.get(`/api/deliveries?${period}`)).status).toBe(403);
  });
});
