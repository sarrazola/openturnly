import { createServer } from "node:http";
import type { AddressInfo } from "node:net";
import { addDays, todayIn, weekdayOf } from "@openturnly/core";
import { afterEach, describe, expect, it } from "vitest";
import { createBusiness, createTestApp, setUpOwner } from "./helpers.ts";

const servers: { close: () => void }[] = [];
afterEach(() => {
  for (const server of servers.splice(0)) server.close();
});

type Call = { method: string; path: string; form: URLSearchParams; secret: string };

/** Stands in for Twilio's API: answers what the tests set and records what it receives. */
async function fakeTwilio() {
  const calls: Call[] = [];
  const state = {
    refuse: null as null | { status: number; code: number },
    numbers: [{ phone_number: "+15550001111", capabilities: { sms: true } }] as unknown[],
    message: {
      status: "delivered",
      error_code: null as number | null,
      price: "-0.05920" as string | null,
    },
  };
  const server = createServer((req, res) => {
    let body = "";
    req.on("data", (chunk) => {
      body += chunk;
    });
    req.on("end", () => {
      const url = new URL(req.url ?? "", "http://twilio.test");
      const login = Buffer.from(
        (req.headers.authorization ?? "").replace(/^Basic /, ""),
        "base64",
      ).toString();
      const secret = login.split(":")[1] ?? "";
      calls.push({
        method: req.method ?? "",
        path: url.pathname,
        form: new URLSearchParams(body),
        secret,
      });
      const reply = (status: number, value: unknown) => {
        res.writeHead(status, { "content-type": "application/json" });
        res.end(JSON.stringify(value));
      };
      if (secret === "bad-secret") return reply(401, { code: 20003, message: "Authenticate" });
      if (url.pathname.endsWith("/IncomingPhoneNumbers.json")) {
        return reply(200, { incoming_phone_numbers: state.numbers });
      }
      if (url.pathname.endsWith("/Messages.json") && req.method === "POST") {
        if (state.refuse) {
          return reply(state.refuse.status, { code: state.refuse.code, message: "No" });
        }
        const count = calls.filter((call) => call.method === "POST").length;
        return reply(201, {
          sid: `SM${String(count).padStart(32, "0")}`,
          status: "queued",
          price: null,
          price_unit: "USD",
        });
      }
      if (url.pathname.endsWith("/Messages.json")) return reply(200, { messages: [] });
      const sid = /\/Messages\/(SM\w+)\.json$/.exec(url.pathname)?.[1];
      if (sid) {
        return reply(200, { sid, ...state.message, price_unit: "USD" });
      }
      return reply(404, { code: 20404, message: "Not found" });
    });
  });
  await new Promise<void>((resolve) => server.listen(0, "127.0.0.1", resolve));
  servers.push(server);
  const { port } = server.address() as AddressInfo;
  const sends = () => calls.filter((call) => call.method === "POST").map((call) => call.form);
  return { url: `http://127.0.0.1:${port}`, calls, sends, state };
}

const INSTALL_ACCOUNT = {
  accountSid: `AC${"1".repeat(32)}`,
  apiKeySid: `SK${"1".repeat(32)}`,
  apiKeySecret: "install-secret",
  sender: "+15559990000",
};

const OWN_ACCOUNT = {
  accountSid: `AC${"2".repeat(32)}`,
  apiKeySid: `SK${"2".repeat(32)}`,
  apiKeySecret: "own-secret",
  sender: "+1 (555) 000-1111",
};

function nextSaturday(): string {
  let date = addDays(todayIn("America/Bogota"), 2);
  while (weekdayOf(date) !== 6) date = addDays(date, 1);
  return date;
}

async function shop(options: { account?: boolean; appUrl?: string } = {}) {
  const twilio = await fakeTwilio();
  const app = await createTestApp({
    sms: options.account ? INSTALL_ACCOUNT : null,
    twilioApiUrl: twilio.url,
    ...(options.appUrl ? { appUrl: options.appUrl } : {}),
  });
  const owner = await setUpOwner(app);
  const business = await createBusiness(owner);
  const base = `/api/businesses/${business.id}`;
  const haircut = (
    await owner.post(`${base}/services`, { name: "Corte", durationMin: 60, priceAmount: 25_000 })
  ).body.service;
  const book = () =>
    app.client().post("/api/public/businesses/fte-barber/bookings", {
      serviceId: haircut.id,
      resourceId: null,
      date: nextSaturday(),
      time: "10:00",
      customer: { firstName: "Ana", lastName: "Pérez", phone: "+57 300 123 4567" },
    });
  const smsOnBooking = (rest: Record<string, unknown> = {}) =>
    owner.post(`${base}/automations`, {
      name: "Aviso",
      trigger: "created",
      actions: [
        {
          id: "text",
          type: "sms",
          template: "appointment_confirmation",
          language: "es",
          ...rest,
        },
      ],
    });
  const deliveries = async (automationId: string) =>
    (await owner.get(`${base}/automations/${automationId}/deliveries`)).body.deliveries;
  return { app, owner, base, twilio, book, smsOnBooking, deliveries, engine: app.app.automations };
}

describe("sms", () => {
  it("texts the ready-made message on the shared account, without accents that double its cost", async () => {
    const { owner, base, twilio, book, smsOnBooking, engine, deliveries } = await shop({
      account: true,
    });
    const { automation } = (await smsOnBooking()).body;
    await book();
    await engine.deliverDue();

    const [form] = twilio.sends();
    expect(form?.get("To")).toBe("+573001234567");
    expect(form?.get("From")).toBe(INSTALL_ACCOUNT.sender);
    expect(form?.get("Body")).toMatch(
      /^Hola Ana, tu cita en FTE Barber Studio quedo agendada: .+\nCodigo: \w+$/,
    );
    // A server on localhost can't be reached: the sweep asks Twilio instead.
    expect(form?.get("StatusCallback")).toBeNull();
    expect((await deliveries(automation.id))[0]).toMatchObject({
      status: "sent",
      messageStatus: "sent",
      messageId: expect.stringMatching(/^SM/),
      cost: null,
    });
    expect((await owner.get(`${base}/channels`)).body.sms).toBe("default");
  });

  it("only lets its own account send its own words", async () => {
    const shared = await shop({ account: true });
    await shared.smsOnBooking({ body: "Compra ya" });
    await shared.book();
    await shared.engine.deliverDue();
    expect(shared.twilio.sends()[0]?.get("Body")).not.toContain("Compra ya");

    const own = await shop({ account: true });
    await own.owner.put("/api/channels/sms", OWN_ACCOUNT);
    await own.owner.post("/api/channels/sms/connect");
    await own.smsOnBooking({ body: "Hola {{customer.first_name}}, nos vemos" });
    await own.book();
    await own.engine.deliverDue();
    const [form] = own.twilio.sends();
    expect(form?.get("Body")).toBe("Hola Ana, nos vemos");
    expect(form?.get("From")).toBe("+15550001111");
  });

  it("records whether it arrived and what it cost, asking Twilio rather than trusting the report", async () => {
    const { app, twilio, book, smsOnBooking, engine, deliveries } = await shop({
      account: true,
      appUrl: "https://reservas.example.com",
    });
    const { automation } = (await smsOnBooking()).body;
    await book();
    await engine.deliverDue();
    const callback = twilio.sends()[0]?.get("StatusCallback") ?? "";
    const [delivery] = await deliveries(automation.id);
    expect(callback).toMatch(
      new RegExp(`^https://reservas\\.example\\.com/api/public/sms/status/${delivery.id}\\?token=`),
    );
    const path = callback.replace("https://reservas.example.com", "");

    const forged = await app.client().post(path.replace(/token=.*/, "token=nope"), {});
    expect(forged.status).toBe(403);
    expect((await deliveries(automation.id))[0].cost).toBeNull();

    const report = await app.client().post(path, {});
    expect(report.status).toBe(204);
    expect((await deliveries(automation.id))[0]).toMatchObject({
      status: "sent",
      messageStatus: "delivered",
      cost: "0.059200",
      costCurrency: "USD",
    });
  });

  it("asks again for a price that wasn't there yet, but not every minute", async () => {
    const { twilio, book, smsOnBooking, engine, deliveries } = await shop({ account: true });
    const { automation } = (await smsOnBooking()).body;
    twilio.state.message.price = null;
    await book();
    await engine.deliverDue();
    const now = Date.now();

    await engine.checkSmsPrices(now + 2 * 60_000);
    expect((await deliveries(automation.id))[0]).toMatchObject({
      messageStatus: "delivered",
      cost: null,
    });

    twilio.state.message.price = "-0.05920";
    await engine.checkSmsPrices(now + 5 * 60_000);
    expect((await deliveries(automation.id))[0].cost).toBeNull();

    await engine.checkSmsPrices(now + 15 * 60_000);
    expect((await deliveries(automation.id))[0].cost).toBe("0.059200");
  });

  it("fails a text the carrier didn't take, with Twilio's code", async () => {
    const { twilio, book, smsOnBooking, engine, deliveries } = await shop({ account: true });
    const { automation } = (await smsOnBooking()).body;
    twilio.state.message = { status: "undelivered", error_code: 30007, price: "-0.05920" };
    await book();
    await engine.deliverDue();
    await engine.checkSmsPrices(Date.now() + 2 * 60_000);
    expect((await deliveries(automation.id))[0]).toMatchObject({
      status: "failed",
      messageStatus: "failed",
      error: "twilio_30007",
      cost: "0.059200",
    });
  });

  it("fails a refusal at once with the reason, and retries a passing failure", async () => {
    const refused = await shop({ account: true });
    const flow = (await refused.smsOnBooking()).body.automation;
    refused.twilio.state.refuse = { status: 400, code: 21408 };
    await refused.book();
    await refused.engine.deliverDue();
    expect((await refused.deliveries(flow.id))[0]).toMatchObject({
      status: "failed",
      attempts: 1,
      error: "sms_country_blocked",
    });

    const passing = await shop({ account: true });
    const other = (await passing.smsOnBooking()).body.automation;
    passing.twilio.state.refuse = { status: 503, code: 20500 };
    await passing.book();
    await passing.engine.deliverDue();
    expect((await passing.deliveries(other.id))[0]).toMatchObject({
      status: "pending",
      attempts: 1,
    });
  });

  it("takes over only once Twilio accepts it, and never returns the secret", async () => {
    const { owner, twilio } = await shop({ account: true });
    const saved = await owner.put("/api/channels/sms", OWN_ACCOUNT);
    expect(saved.body).toMatchObject({
      active: "default",
      default: INSTALL_ACCOUNT.sender,
      connection: { sender: "+15550001111", hasApiKeySecret: true },
    });
    expect(JSON.stringify(saved.body)).not.toContain(OWN_ACCOUNT.apiKeySecret);

    twilio.state.numbers = [];
    const unknown = await owner.post("/api/channels/sms/connect");
    expect(unknown.body.connection).toMatchObject({
      status: "error",
      lastError: "sms_number_not_found",
    });
    expect(unknown.body.active).toBe("default");

    await owner.put("/api/channels/sms", { ...OWN_ACCOUNT, apiKeySecret: "bad-secret" });
    const refused = await owner.post("/api/channels/sms/connect");
    expect(refused.body.connection).toMatchObject({ status: "error", lastError: "sms_login" });

    twilio.state.numbers = [{ phone_number: "+15550001111", capabilities: { sms: true } }];
    await owner.put("/api/channels/sms", OWN_ACCOUNT);
    const connected = await owner.post("/api/channels/sms/connect");
    expect(connected.body).toMatchObject({ active: "own", connection: { status: "connected" } });

    const removed = await owner.delete("/api/channels/sms");
    expect(removed.body).toMatchObject({ active: "default", connection: null });
  });

  it("refuses a sender that isn't a number or a messaging service", async () => {
    const { owner } = await shop();
    const { status, body } = await owner.put("/api/channels/sms", {
      ...OWN_ACCOUNT,
      sender: "OpenTurnly",
    });
    expect(status).toBe(400);
    expect(body.error.fields).toEqual({ sender: "invalid_sms_sender" });
  });

  it("says why when there's nowhere to send", async () => {
    const { book, smsOnBooking, engine, deliveries, twilio } = await shop();
    const flow = (await smsOnBooking()).body.automation;
    await book();
    await engine.deliverDue();
    expect((await deliveries(flow.id))[0]).toMatchObject({
      status: "failed",
      error: "sms_not_connected",
    });
    expect(twilio.sends()).toHaveLength(0);
  });

  it("sends a test from Channels with example data", async () => {
    const { owner, twilio } = await shop({ account: true });
    const { body } = await owner.post("/api/channels/sms/test", {
      phone: "300 765 4321",
      template: "appointment_reminder",
      language: "es",
    });
    expect(body).toMatchObject({ ok: true, error: null });
    const [form] = twilio.sends();
    expect(form?.get("To")).toBe("+573007654321");
    expect(form?.get("Body")).toContain("te recordamos tu cita en FTE Barber Studio");
  });
});
