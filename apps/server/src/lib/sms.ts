import { createHmac, timingSafeEqual } from "node:crypto";
import type { Config } from "../config.ts";
import type { Db } from "../db/client.ts";
import { type SmsConnection, smsConnections } from "../db/schema.ts";
import { decryptSecret } from "./secrets.ts";

const TIMEOUT_MS = 10_000;

/** The Twilio account a text goes out on, and where it was set. */
export type SmsSender = {
  source: "own" | "default";
  accountSid: string;
  apiKeySid: string;
  apiKeySecret: string;
  /** A number with its country code, or a messaging service (MG…). */
  sender: string;
};

export type SmsSend = {
  ok: boolean;
  status: number | null;
  error: string | null;
  durationMs: number;
  response: string | null;
  messageId: string | null;
  /** Whether trying again later could work. */
  retry: boolean;
};

/** What Twilio says about one message: how far it got and what it cost. */
export type SmsMessageState = {
  /** sent, delivered, read or failed; null while it's still on its way to the carrier. */
  status: string | null;
  error: string | null;
  /** A positive amount, e.g. "0.05920"; null until Twilio has priced it. */
  cost: string | null;
  currency: string | null;
};

type TwilioAnswer = { status: number | null; json: Record<string, unknown>; text: string };

const asRecord = (value: unknown) =>
  value !== null && typeof value === "object" ? (value as Record<string, unknown>) : {};
const asText = (value: unknown) => (typeof value === "string" && value ? value : null);

/** Refusals worth a plain word; any other comes back as twilio_<code>. */
const REFUSALS: Record<number, string> = {
  20003: "sms_login",
  21211: "sms_invalid_phone",
  21408: "sms_country_blocked",
  21610: "sms_unsubscribed",
  21612: "sms_country_blocked",
  21614: "sms_not_mobile",
  21606: "sms_sender_invalid",
  21659: "sms_sender_invalid",
  21660: "sms_sender_invalid",
};

/** Twilio's statuses in the words the delivery history uses. */
const PROGRESS: Record<string, string> = {
  sent: "sent",
  delivered: "delivered",
  read: "read",
  undelivered: "failed",
  failed: "failed",
  canceled: "failed",
};

/** Why Twilio refused a call, from its answer. */
function refusal(answer: TwilioAnswer): string {
  if (answer.status === 401 || answer.status === 403) return "sms_login";
  const code = typeof answer.json.code === "number" ? answer.json.code : null;
  if (code !== null) return REFUSALS[code] ?? `twilio_${code}`;
  return answer.status === null ? answer.text || "sms_unreachable" : `HTTP ${answer.status}`;
}

export type Sms = ReturnType<typeof createSms>;

/** Sends the install's text messages: which Twilio account to use, and sending on it. */
export function createSms({ db, config }: { db: Db; config: Config }) {
  const findConnection = async () => {
    const [connection] = await db.select().from(smsConnections).limit(1);
    return connection ?? null;
  };

  /** The saved secret, unless a change of SECRET made it unreadable. */
  const readSecret = (stored: string | null) => {
    if (!stored) return null;
    try {
      return decryptSecret(config.secret, stored);
    } catch {
      return null;
    }
  };

  /** Calls Twilio's API. Never throws: a network failure is an answer without status. */
  const twilio = async (
    sender: Pick<SmsSender, "accountSid" | "apiKeySid" | "apiKeySecret">,
    path: string,
    form?: Record<string, string>,
  ): Promise<TwilioAnswer> => {
    const login = Buffer.from(`${sender.apiKeySid}:${sender.apiKeySecret}`).toString("base64");
    try {
      const res = await fetch(
        `${config.twilioApiUrl}/2010-04-01/Accounts/${encodeURIComponent(sender.accountSid)}/${path}`,
        {
          method: form ? "POST" : "GET",
          redirect: "manual",
          signal: AbortSignal.timeout(TIMEOUT_MS),
          headers: {
            authorization: `Basic ${login}`,
            ...(form ? { "content-type": "application/x-www-form-urlencoded" } : {}),
          },
          ...(form ? { body: new URLSearchParams(form).toString() } : {}),
        },
      );
      const text = await res.text();
      let json: Record<string, unknown> = {};
      try {
        json = asRecord(JSON.parse(text));
      } catch {
        // Not JSON: the status says what happened.
      }
      return { status: res.status, json, text };
    } catch (error) {
      const message =
        error instanceof Error
          ? error.name === "TimeoutError"
            ? "timeout"
            : ((error.cause as { code?: string } | undefined)?.code ?? error.message)
          : String(error);
      return { status: null, json: {}, text: message.slice(0, 300) };
    }
  };

  const ok = (answer: TwilioAnswer) =>
    answer.status !== null && answer.status >= 200 && answer.status < 300;

  const defaultSender = (): SmsSender | null => config.sms && { source: "default", ...config.sms };

  /** The connection's credentials, if they can be read. */
  const ownSender = (connection: SmsConnection | null): SmsSender | null => {
    const apiKeySecret = readSecret(connection?.encryptedApiKeySecret ?? null);
    if (!connection || !apiKeySecret) return null;
    return {
      source: "own",
      accountSid: connection.accountSid,
      apiKeySid: connection.apiKeySid,
      apiKeySecret,
      sender: connection.sender,
    };
  };

  /** Signs a delivery's id, so only Twilio, given the address, can report on it. */
  const statusToken = (deliveryId: string) =>
    createHmac("sha256", config.secret)
      .update(`sms-status:${deliveryId}`)
      .digest("base64url")
      .slice(0, 32);

  return {
    findConnection,
    readSecret,
    defaultSender,
    ownSender,

    /** What the environment's account sends from, to tell the owner what's in use. */
    defaultNumber: () => config.sms?.sender ?? null,

    /** The account connected in the install, or the one from the environment, or null. */
    async sender(): Promise<SmsSender | null> {
      const own = await findConnection();
      if (own?.status === "connected") {
        const sender = ownSender(own);
        if (sender) return sender;
      }
      return defaultSender();
    },

    /** Where Twilio reports on a delivery's message. */
    statusUrl: (deliveryId: string) =>
      `${config.appUrl}/api/public/sms/status/${encodeURIComponent(deliveryId)}?token=${statusToken(deliveryId)}`,

    validStatusToken(deliveryId: string, given: string | undefined) {
      if (!given) return false;
      const a = Buffer.from(given);
      const b = Buffer.from(statusToken(deliveryId));
      return a.length === b.length && timingSafeEqual(a, b);
    },

    /**
     * Checks the account without sending anything: the key works and, for a number, it's
     * the account's own and can send texts.
     */
    async verify(sender: SmsSender): Promise<{ ok: true } | { ok: false; error: string }> {
      if (!sender.sender.startsWith("+")) {
        const answer = await twilio(sender, "Messages.json?PageSize=1");
        return ok(answer) ? { ok: true } : { ok: false, error: refusal(answer) };
      }
      const answer = await twilio(
        sender,
        `IncomingPhoneNumbers.json?${new URLSearchParams({ PhoneNumber: sender.sender })}`,
      );
      if (!ok(answer)) return { ok: false, error: refusal(answer) };
      const numbers = Array.isArray(answer.json.incoming_phone_numbers)
        ? (answer.json.incoming_phone_numbers as unknown[]).map(asRecord)
        : [];
      const [number] = numbers;
      if (!number) return { ok: false, error: "sms_number_not_found" };
      if (asRecord(number.capabilities).sms === false)
        return { ok: false, error: "sms_number_no_sms" };
      return { ok: true };
    },

    async send(
      sender: SmsSender,
      message: { to: string; body: string; statusUrl?: string },
    ): Promise<SmsSend> {
      const startedAt = performance.now();
      const from: Record<string, string> = sender.sender.startsWith("+")
        ? { From: sender.sender }
        : { MessagingServiceSid: sender.sender };
      const answer = await twilio(sender, "Messages.json", {
        To: message.to,
        Body: message.body,
        ...from,
        ...(message.statusUrl ? { StatusCallback: message.statusUrl } : {}),
      });
      const sent = ok(answer);
      return {
        ok: sent,
        status: answer.status,
        error: sent ? null : refusal(answer),
        durationMs: Math.round(performance.now() - startedAt),
        response: answer.text.slice(0, 2_000),
        messageId: sent ? asText(answer.json.sid) : null,
        retry: !sent && (answer.status === null || answer.status >= 500 || answer.status === 429),
      };
    },

    /**
     * Asks Twilio how a message did and what it cost, on the account it most likely went out
     * on: the one in use, else the environment's.
     */
    async messageState(messageId: string): Promise<SmsMessageState | null> {
      const candidates = [await this.sender(), defaultSender()].filter(
        (item, index, all): item is SmsSender =>
          item !== null &&
          all.findIndex((other) => other?.accountSid === item.accountSid) === index,
      );
      for (const sender of candidates) {
        const answer = await twilio(sender, `Messages/${encodeURIComponent(messageId)}.json`);
        if (!ok(answer)) continue;
        const state = asText(answer.json.status) ?? "";
        const code = typeof answer.json.error_code === "number" ? answer.json.error_code : null;
        const price = asText(answer.json.price);
        return {
          status: PROGRESS[state] ?? null,
          error: code === null ? null : (REFUSALS[code] ?? `twilio_${code}`),
          cost: price === null ? null : price.replace(/^-/, ""),
          currency: asText(answer.json.price_unit),
        };
      }
      return null;
    },
  };
}

export type { SmsConnection };
