import { createHash, createHmac, randomBytes, timingSafeEqual } from "node:crypto";
import type { Config, GoogleSettings } from "../config.ts";

/**
 * Google's side of connected calendars: the consent that grants access, the tokens, and the
 * few Calendar API calls the sync makes. Only `calendar.app.created` is asked for, so the
 * install reaches the calendars it creates in the person's account and nothing else: never
 * their own events. Plain `fetch`, no SDK.
 */

export const GOOGLE_CALENDAR_SCOPE = "https://www.googleapis.com/auth/calendar.app.created";
const SCOPES = ["openid", "email", GOOGLE_CALENDAR_SCOPE];
const AUTHORIZE_URL = "https://accounts.google.com/o/oauth2/v2/auth";
/** How long someone has to answer Google's consent screen. */
const STATE_TTL_MS = 15 * 60_000;
/** Any single call to Google; the sync tries again later when one is slow. */
const REQUEST_TIMEOUT_MS = 8_000;

export class GoogleError extends Error {
  readonly status: number;
  /** Google's reason ("invalid_grant", "notFound"…) when it gave one. */
  readonly reason: string;

  constructor(status: number, reason: string, message: string) {
    super(message);
    this.status = status;
    this.reason = reason;
  }

  /** The person took the access back, or it lapsed: only connecting again fixes it. */
  get revoked() {
    return this.reason === "invalid_grant" || this.reason === "unauthorized_client";
  }

  /** The calendar or the event isn't there anymore (deleted in Google, for example). */
  get gone() {
    return this.status === 404 || this.status === 410;
  }
}

// --- The consent round trip -------------------------------------------------------------

/** What the `state` parameter carries through Google's consent screen. */
export type ConsentState = {
  /** The person who started it: only they can finish it. */
  userId: string;
  /** The address the person started from, where they come back to. */
  origin: string;
  /** A path on that address to land on afterwards. */
  returnTo: string;
  expiresAt: number;
};

function stateKey(secret: string) {
  return createHash("sha256").update(`google-consent-state:${secret}`).digest();
}

/** Signed, so nobody can finish someone else's connection or send them somewhere else. */
export function signState(
  secret: string,
  state: Omit<ConsentState, "expiresAt">,
  now = Date.now(),
) {
  const payload = Buffer.from(
    JSON.stringify({
      u: state.userId,
      o: state.origin,
      r: state.returnTo,
      e: now + STATE_TTL_MS,
      n: randomBytes(8).toString("base64url"),
    }),
  ).toString("base64url");
  const signature = createHmac("sha256", stateKey(secret)).update(payload).digest("base64url");
  return `${payload}.${signature}`;
}

/** The state if it's this install's own, untouched and recent; null otherwise. */
export function readState(secret: string, state: string, now = Date.now()): ConsentState | null {
  const [payload, signature] = state.split(".");
  if (!payload || !signature) return null;
  const expected = createHmac("sha256", stateKey(secret)).update(payload).digest();
  const given = Buffer.from(signature, "base64url");
  if (given.length !== expected.length || !timingSafeEqual(given, expected)) return null;
  const claims = parseClaims(payload);
  if (!claims || claims.expiresAt < now) return null;
  return claims;
}

/**
 * The address a consent started from, read without checking the signature. Google answers
 * on the one registered redirect address; an install reached under several hostnames reads
 * this there to pass the answer on to where the person was. The callback, on arrival, checks
 * everything again.
 */
export function stateOrigin(state: string): string | null {
  const claims = parseClaims(state.split(".")[0] ?? "");
  return claims?.origin ?? null;
}

function parseClaims(payload: string): ConsentState | null {
  try {
    const raw = JSON.parse(Buffer.from(payload, "base64url").toString("utf8")) as Record<
      string,
      unknown
    >;
    if (
      typeof raw.u !== "string" ||
      typeof raw.o !== "string" ||
      typeof raw.r !== "string" ||
      typeof raw.e !== "number"
    ) {
      return null;
    }
    return { userId: raw.u, origin: raw.o, returnTo: raw.r, expiresAt: raw.e };
  } catch {
    return null;
  }
}

/** Google's consent screen for these scopes, coming back with a refresh token. */
export function consentUrl(google: GoogleSettings, state: string) {
  const params = new URLSearchParams({
    client_id: google.clientId,
    redirect_uri: google.redirectUrl,
    response_type: "code",
    scope: SCOPES.join(" "),
    access_type: "offline",
    // Asked every time, so Google always hands out a refresh token, even on reconnecting.
    prompt: "consent",
    include_granted_scopes: "false",
    state,
  });
  return `${AUTHORIZE_URL}?${params}`;
}

export type GrantedTokens = {
  accessToken: string;
  refreshToken: string;
  expiresAt: Date;
  /** Google's id for the account, and its address. */
  accountId: string;
  email: string | null;
  /** Whether the person left the calendar box ticked on the consent screen. */
  calendarGranted: boolean;
};

type TokenResponse = {
  access_token?: string;
  refresh_token?: string;
  expires_in?: number;
  scope?: string;
  id_token?: string;
  error?: string;
  error_description?: string;
};

async function tokenRequest(config: Config, params: Record<string, string>) {
  const res = await fetch(`${config.googleOauthUrl}/token`, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams(params),
    signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
  });
  const body = (await res.json().catch(() => ({}))) as TokenResponse;
  if (!res.ok || !body.access_token) {
    throw new GoogleError(
      res.status,
      body.error ?? "token_failed",
      body.error_description ?? body.error ?? `token request failed (${res.status})`,
    );
  }
  return body;
}

/** Trades the code Google sent back for the person's tokens. */
export async function exchangeCode(
  config: Config,
  google: GoogleSettings,
  code: string,
  now = Date.now(),
): Promise<GrantedTokens> {
  const body = await tokenRequest(config, {
    code,
    client_id: google.clientId,
    client_secret: google.clientSecret,
    redirect_uri: google.redirectUrl,
    grant_type: "authorization_code",
  });
  if (!body.refresh_token) throw new GoogleError(400, "no_refresh_token", "no refresh token");
  // Straight from Google's token endpoint over TLS, so its signature needn't be checked.
  const claims = body.id_token ? decodeJwtPayload(body.id_token) : null;
  const accountId = typeof claims?.sub === "string" ? claims.sub : null;
  if (!accountId) throw new GoogleError(400, "no_account", "no account id");
  return {
    accessToken: body.access_token ?? "",
    refreshToken: body.refresh_token,
    expiresAt: new Date(now + (body.expires_in ?? 3600) * 1000),
    accountId,
    email: typeof claims?.email === "string" ? claims.email : null,
    calendarGranted: (body.scope ?? "").split(" ").includes(GOOGLE_CALENDAR_SCOPE),
  };
}

/** A fresh access token from the stored refresh token. */
export async function refreshAccessToken(
  config: Config,
  google: GoogleSettings,
  refreshToken: string,
  now = Date.now(),
) {
  const body = await tokenRequest(config, {
    refresh_token: refreshToken,
    client_id: google.clientId,
    client_secret: google.clientSecret,
    grant_type: "refresh_token",
  });
  return {
    accessToken: body.access_token ?? "",
    expiresAt: new Date(now + (body.expires_in ?? 3600) * 1000),
  };
}

/** Takes the install's access back from the person's account. Best effort. */
export async function revokeToken(config: Config, token: string) {
  try {
    await fetch(`${config.googleOauthUrl}/revoke`, {
      method: "POST",
      headers: { "content-type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({ token }),
      signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
    });
  } catch (error) {
    console.error("google: could not revoke a token", error);
  }
}

function decodeJwtPayload(token: string): Record<string, unknown> | null {
  try {
    return JSON.parse(Buffer.from(token.split(".")[1] ?? "", "base64url").toString("utf8"));
  } catch {
    return null;
  }
}

// --- Calendar API -----------------------------------------------------------------------

export type GoogleEventInput = {
  summary: string;
  description: string;
  location: string;
  start: { dateTime: string; timeZone: string };
  end: { dateTime: string; timeZone: string };
  attendees?: { email: string; displayName?: string }[];
};

export type SendUpdates = "all" | "none";

export function createGoogleCalendarApi(config: Config) {
  const base = `${config.googleApiUrl}/calendar/v3`;

  const call = async <T>(
    token: string,
    method: string,
    path: string,
    { body, query }: { body?: unknown; query?: Record<string, string> } = {},
  ): Promise<T> => {
    const url = `${base}${path}${query ? `?${new URLSearchParams(query)}` : ""}`;
    const res = await fetch(url, {
      method,
      headers: {
        authorization: `Bearer ${token}`,
        ...(body === undefined ? {} : { "content-type": "application/json" }),
      },
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
    });
    if (res.status === 204) return undefined as T;
    const text = await res.text();
    const json = text ? (JSON.parse(text) as Record<string, unknown>) : {};
    if (!res.ok) {
      const error = json.error as
        | { message?: string; errors?: { reason?: string }[]; status?: string }
        | undefined;
      throw new GoogleError(
        res.status,
        error?.errors?.[0]?.reason ?? error?.status ?? "request_failed",
        error?.message ?? `Google Calendar answered ${res.status}`,
      );
    }
    return json as T;
  };

  const calendarPath = (calendarId: string) => `/calendars/${encodeURIComponent(calendarId)}`;
  const eventPath = (calendarId: string, eventId: string) =>
    `${calendarPath(calendarId)}/events/${encodeURIComponent(eventId)}`;

  type EventResult = { id: string; hangoutLink?: string };

  return {
    async createCalendar(token: string, input: { summary: string; timeZone: string }) {
      const created = await call<{ id: string }>(token, "POST", "/calendars", { body: input });
      return created.id;
    },

    async updateCalendar(
      token: string,
      calendarId: string,
      input: { summary: string; timeZone: string },
    ) {
      await call(token, "PATCH", calendarPath(calendarId), { body: input });
    },

    async deleteCalendar(token: string, calendarId: string) {
      await call(token, "DELETE", calendarPath(calendarId));
    },

    async getEvent(token: string, calendarId: string, eventId: string) {
      return call<EventResult>(token, "GET", eventPath(calendarId, eventId));
    },

    /** A new event; with `meeting`, Google adds a Meet video call to it. */
    async insertEvent(
      token: string,
      calendarId: string,
      event: GoogleEventInput,
      { meeting, sendUpdates }: { meeting: boolean; sendUpdates: SendUpdates },
    ) {
      return call<EventResult>(token, "POST", `${calendarPath(calendarId)}/events`, {
        body: meeting ? { ...event, conferenceData: meetingRequest() } : event,
        query: { sendUpdates, ...(meeting ? { conferenceDataVersion: "1" } : {}) },
      });
    },

    /** Rewrites the event's fields; a call it already has stays. */
    async patchEvent(
      token: string,
      calendarId: string,
      eventId: string,
      event: GoogleEventInput,
      { meeting, sendUpdates }: { meeting: boolean; sendUpdates: SendUpdates },
    ) {
      return call<EventResult>(token, "PATCH", eventPath(calendarId, eventId), {
        body: meeting ? { ...event, conferenceData: meetingRequest() } : event,
        query: { sendUpdates, ...(meeting ? { conferenceDataVersion: "1" } : {}) },
      });
    },

    async deleteEvent(
      token: string,
      calendarId: string,
      eventId: string,
      sendUpdates: SendUpdates,
    ) {
      await call(token, "DELETE", eventPath(calendarId, eventId), { query: { sendUpdates } });
    },
  };
}

export type GoogleCalendarApi = ReturnType<typeof createGoogleCalendarApi>;

function meetingRequest() {
  return {
    createRequest: {
      requestId: randomBytes(12).toString("hex"),
      conferenceSolutionKey: { type: "hangoutsMeet" },
    },
  };
}
