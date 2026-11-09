import { createServer } from "node:http";
import type { AddressInfo } from "node:net";
import { addDays, todayIn, weekdayOf, zonedTimeToInstant } from "@openturnly/core";
import { eq } from "drizzle-orm";
import { afterEach, describe, expect, it } from "vitest";
import { automationDeliveries, surveys } from "../src/db/schema.ts";
import { usesSurveyLink } from "../src/lib/surveys.ts";
import { createBusiness, createTestApp, setUpOwner } from "./helpers.ts";

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

/** A local endpoint that records the JSON it receives. */
async function receiver() {
  const received: Record<string, unknown>[] = [];
  const server = createServer((req, res) => {
    let body = "";
    req.on("data", (chunk) => {
      body += chunk;
    });
    req.on("end", () => {
      received.push(JSON.parse(body));
      res.writeHead(200);
      res.end("ok");
    });
  });
  await new Promise<void>((resolve) => server.listen(0, "127.0.0.1", resolve));
  servers.push(server);
  const { port } = server.address() as AddressInfo;
  return { url: `http://127.0.0.1:${port}/hook`, received };
}

async function shop() {
  const app = await createTestApp();
  const owner = await setUpOwner(app);
  const business = await createBusiness(owner);
  const base = `/api/businesses/${business.id}`;
  const haircut = (
    await owner.post(`${base}/services`, { name: "Corte", durationMin: 60, priceAmount: 25_000 })
  ).body.service;
  const saturday = nextSaturday();
  const book = async (time: string, phone = "+57 300 123 4567", firstName = "Ana") =>
    (
      await app.client().post("/api/public/businesses/fte-barber/bookings", {
        serviceId: haircut.id,
        resourceId: null,
        date: saturday,
        time,
        customer: { firstName, lastName: "Pérez", phone },
      })
    ).body.appointment;
  /** When an appointment booked at that time ends (each lasts an hour). */
  const endOf = (time: string) =>
    zonedTimeToInstant(saturday, Number(time.slice(0, 2)) * 60 + 60, "America/Bogota");
  const hook = await receiver();
  // The flow that runs when an appointment ends and sends its survey link.
  await owner.post(`${base}/automations`, {
    name: "Encuesta",
    trigger: "after",
    actions: [
      { id: "wait", type: "wait", minutes: 30 },
      { id: "link", type: "webhook", url: hook.url, fields: ["appointment.survey_link"] },
    ],
  });
  const engine = app.app.automations;
  /** Runs the flow for an appointment that ended at `end`: queued, then sent 30 min later. */
  const runAfter = async (end: number) => {
    await engine.sweep(end + 10_000);
    await engine.deliverDue(end + 31 * 60_000);
  };
  const lastCode = () => {
    const last = hook.received.at(-1) as { appointment: { survey_link: string } };
    return last.appointment.survey_link.split("/e/")[1] as string;
  };
  return { app, owner, base, saturday, book, endOf, hook, runAfter, lastCode, engine };
}

describe("surveys", () => {
  it("send one link after the appointment, take one answer and list it for the admins", async () => {
    const { app, owner, base, saturday, book, endOf, hook, runAfter, lastCode, engine } =
      await shop();
    expect((await owner.get(`${base}/surveys/settings`)).body).toEqual({
      enabled: false,
      validHours: 48,
    });
    const on = await owner.patch(`${base}/surveys/settings`, { enabled: true });
    expect(on.body).toEqual({ enabled: true, validHours: 48 });

    const visit = await book("10:00");
    // Queued when it ends, while the flow waits: the link exists but opens nothing yet.
    await engine.sweep(endOf("10:00") + 10_000);
    const [queued] = await app.db.select().from(surveys).where(eq(surveys.appointmentId, visit.id));
    expect(queued?.sentAt).toBeNull();
    const visitor = app.client();
    expect((await visitor.get(`/api/public/surveys/${queued?.code}`)).status).toBe(404);

    await engine.deliverDue(endOf("10:00") + 31 * 60_000);
    expect(hook.received).toHaveLength(1);
    const code = lastCode();
    expect(code).toMatch(/^[2-9A-HJ-NP-Z]{8}$/);
    expect(hook.received[0]).toEqual({
      appointment: { survey_link: `http://localhost:3000/e/${code}` },
    });

    const page = await visitor.get(`/api/public/surveys/${code.toLowerCase()}`);
    expect(page.body).toMatchObject({
      state: "open",
      business: { name: expect.any(String), slug: "fte-barber" },
      visit: { service: "Corte", startsAt: visit.startsAt },
      rating: null,
    });

    const tooMany = await visitor.post(`/api/public/surveys/${code}`, { rating: 6 });
    expect(tooMany.status).toBe(400);
    const answered = await visitor.post(`/api/public/surveys/${code}`, {
      rating: 5,
      comment: "  Excelente atención  ",
    });
    expect(answered.status).toBe(200);
    const again = await visitor.post(`/api/public/surveys/${code}`, { rating: 1 });
    expect(again.status).toBe(409);
    expect(again.body.error.code).toBe("survey_answered");
    expect((await visitor.get(`/api/public/surveys/${code}`)).body).toMatchObject({
      state: "answered",
      rating: 5,
    });

    // The same customer again the same day: no second survey within 30 days.
    await book("12:00");
    await runAfter(endOf("12:00"));
    expect(hook.received).toHaveLength(1);
    const skipped = await app.db
      .select({ status: automationDeliveries.status, error: automationDeliveries.error })
      .from(automationDeliveries)
      .where(eq(automationDeliveries.status, "skipped"));
    expect(skipped).toEqual([{ status: "skipped", error: "survey_recent" }]);

    const results = await owner.get(`${base}/surveys?from=${saturday}&to=${saturday}`);
    expect(results.body.summary).toEqual({
      sent: 1,
      answered: 1,
      average: 5,
      stars: [0, 0, 0, 0, 1],
    });
    expect(results.body.responses).toEqual([
      expect.objectContaining({
        rating: 5,
        comment: "Excelente atención",
        appointment: expect.objectContaining({ id: visit.id, serviceName: "Corte" }),
        customer: expect.objectContaining({ firstName: "Ana" }),
      }),
    ]);
    const otherDay = addDays(saturday, 1);
    expect(
      (await owner.get(`${base}/surveys?from=${otherDay}&to=${otherDay}`)).body.summary.sent,
    ).toBe(0);
  });

  it("go out only while they are on, and close after their hours", async () => {
    const { app, owner, base, book, endOf, hook, runAfter, lastCode } = await shop();
    // Off: the step is skipped and no link is made.
    await book("10:00");
    await runAfter(endOf("10:00"));
    expect(hook.received).toHaveLength(0);
    expect(await app.db.select().from(surveys)).toHaveLength(0);
    const [off] = await app.db
      .select({ error: automationDeliveries.error })
      .from(automationDeliveries);
    expect(off?.error).toBe("survey_off");

    await owner.patch(`${base}/surveys/settings`, { enabled: true, validHours: 24 });
    await book("11:00", "+57 300 765 4321", "Luis");
    await runAfter(endOf("11:00"));
    const code = lastCode();
    const [sent] = await app.db.select().from(surveys).where(eq(surveys.code, code));
    expect((sent?.expiresAt?.getTime() ?? 0) - (sent?.sentAt?.getTime() ?? 0)).toBe(24 * 3_600_000);

    // Its hours are over.
    await app.db
      .update(surveys)
      .set({ expiresAt: new Date(Date.now() - 1000) })
      .where(eq(surveys.code, code));
    const visitor = app.client();
    expect((await visitor.get(`/api/public/surveys/${code}`)).body.state).toBe("closed");
    const late = await visitor.post(`/api/public/surveys/${code}`, { rating: 4 });
    expect(late.status).toBe(410);
    expect(late.body.error.code).toBe("survey_closed");
    expect((await visitor.get("/api/public/surveys/EJEMPLO1")).status).toBe(404);
  });

  it("check their settings and the period asked for", async () => {
    const { owner, base } = await shop();
    const odd = await owner.patch(`${base}/surveys/settings`, { validHours: 12 });
    expect(odd.status).toBe(400);
    const week = await owner.patch(`${base}/surveys/settings`, { validHours: 168 });
    expect(week.body).toEqual({ enabled: false, validHours: 168 });
    expect((await owner.get(`${base}/surveys?from=2026-02-01&to=2026-01-01`)).status).toBe(400);
  });

  it("know which steps send the link", () => {
    expect(usesSurveyLink({ type: "email", template: "appointment_survey" })).toBe(true);
    expect(usesSurveyLink({ type: "sms", template: "appointment_reminder" })).toBe(false);
    expect(
      usesSurveyLink({
        type: "whatsapp",
        template: "gracias",
        variables: [{ name: "link", value: "{{appointment.survey_link}}" }],
      } as { type: string; template: string }),
    ).toBe(true);
  });
});
