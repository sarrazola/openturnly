import { randomBytes } from "node:crypto";
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { type BookingGuardSettings, DEFAULT_GUARD_SETTINGS } from "./lib/booking-guard.ts";
import { loadPushSettings, type PushSettings } from "./lib/push.ts";

/** The WhatsApp number every business of the install uses until it connects its own. */
export type WhatsappDefaults = {
  phoneNumberId: string;
  wabaId: string | null;
  accessToken: string;
  appSecret: string | null;
  verifyToken: string | null;
};

/** The email account every business of the install uses until it connects its own. */
export type EmailDefaults = {
  host: string;
  port: number;
  username: string;
  password: string;
  fromEmail: string;
  /** A last line for emails sent on this account. */
  footer: string | null;
};

/** The Twilio account every business of the install texts on until it connects its own. */
export type SmsDefaults = {
  accountSid: string;
  apiKeySid: string;
  apiKeySecret: string;
  /** A number with its country code, or a messaging service (MG…). */
  sender: string;
};

/** The install's Google Cloud OAuth client, for people to connect their own Google Calendar. */
export type GoogleSettings = {
  clientId: string;
  clientSecret: string;
  /**
   * Where Google sends people back after they allow access. Registered in the OAuth client
   * exactly as written. By default this install's own address; an install reached under
   * several hostnames registers one of them and passes the rest through it (see
   * lib/google-calendar.ts, `returnOrigin`).
   */
  redirectUrl: string;
};

export type Config = {
  isProduction: boolean;
  port: number;
  /** Public URL without trailing slash, e.g. https://reservas.example.com */
  appUrl: string;
  dataDir: string;
  /** PostgreSQL connection URL. Without it, development uses embedded PGlite in dataDir. */
  databaseUrl: string | undefined;
  secret: string;
  /** Built web app served by the server. Missing in development (Vite serves it). */
  webDistDir: string;
  trustedOrigins: string[];
  /** Behind a reverse proxy: read the client IP from X-Forwarded-For. */
  trustProxy: boolean;
  /** Anti-bot settings for the public booking page. */
  bookingGuard: BookingGuardSettings;
  /**
   * Whether automations may POST to addresses inside the server's network (another
   * container, localhost). Off in production unless WEBHOOKS_ALLOW_PRIVATE=true.
   */
  allowPrivateWebhooks: boolean;
  /** Set with WHATSAPP_PHONE_NUMBER_ID and WHATSAPP_ACCESS_TOKEN; null without them. */
  whatsapp: WhatsappDefaults | null;
  /** Set with SMTP_HOST, SMTP_USER, SMTP_PASSWORD and EMAIL_FROM; null without them. */
  email: EmailDefaults | null;
  /**
   * Set with TWILIO_ACCOUNT_SID, TWILIO_API_KEY_SID, TWILIO_API_KEY_SECRET and
   * TWILIO_FROM_NUMBER; null without them.
   */
  sms: SmsDefaults | null;
  /** Where Twilio's API lives. */
  twilioApiUrl: string;
  /** Where WhatsApp's API lives, with its version. */
  whatsappApiUrl: string;
  /** Where Mercado Pago's API lives. */
  mercadopagoApiUrl: string;
  /** Set with GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET; null without them. */
  google: GoogleSettings | null;
  /** Where Google issues and revokes tokens. */
  googleOauthUrl: string;
  /** Where Google's APIs live (Calendar). */
  googleApiUrl: string;
  /** How notifications reach the phone apps: PUSH_PROVIDER, "none" unless set. */
  push: PushSettings;
  /**
   * Sent with every message and handed back in its delivery updates, so several installs
   * sharing one number can tell theirs apart.
   */
  whatsappCallbackTag: string | null;
};

const DEFAULT_DATA_DIR = fileURLToPath(new URL("../../../data", import.meta.url));
const WEB_DIST_DIR = fileURLToPath(new URL("../../web/dist", import.meta.url));
const VITE_DEV_ORIGIN = "http://localhost:5173";
const WHATSAPP_API_URL = "https://graph.facebook.com/v25.0";
const MERCADOPAGO_API_URL = "https://api.mercadopago.com";
const TWILIO_API_URL = "https://api.twilio.com";
const GOOGLE_OAUTH_URL = "https://oauth2.googleapis.com";
const GOOGLE_API_URL = "https://www.googleapis.com";

export function loadConfig(env: NodeJS.ProcessEnv = process.env): Config {
  const isProduction = env.NODE_ENV === "production";
  const port = Number(env.PORT ?? 3000);
  const appUrl = (env.APP_URL || `http://localhost:${port}`).replace(/\/+$/, "");
  const dataDir = path.resolve(env.DATA_DIR || DEFAULT_DATA_DIR);
  mkdirSync(dataDir, { recursive: true });
  const databaseUrl = env.DATABASE_URL || undefined;
  if (isProduction && !databaseUrl) {
    throw new Error(
      "DATABASE_URL is required, e.g. postgres://openturnly:password@db:5432/openturnly",
    );
  }

  return {
    isProduction,
    port,
    appUrl,
    dataDir,
    databaseUrl,
    secret: env.SECRET || readOrCreateSecret(dataDir),
    webDistDir: WEB_DIST_DIR,
    trustedOrigins: isProduction ? [appUrl] : [appUrl, VITE_DEV_ORIGIN],
    trustProxy: env.TRUST_PROXY === "true",
    bookingGuard: DEFAULT_GUARD_SETTINGS,
    allowPrivateWebhooks: env.WEBHOOKS_ALLOW_PRIVATE
      ? env.WEBHOOKS_ALLOW_PRIVATE === "true"
      : !isProduction,
    whatsapp: loadWhatsappDefaults(env),
    email: loadEmailDefaults(env),
    sms: loadSmsDefaults(env),
    twilioApiUrl: (env.TWILIO_API_URL || TWILIO_API_URL).replace(/\/+$/, ""),
    whatsappApiUrl: (env.WHATSAPP_API_URL || WHATSAPP_API_URL).replace(/\/+$/, ""),
    whatsappCallbackTag: env.WHATSAPP_CALLBACK_TAG || null,
    mercadopagoApiUrl: (env.MERCADOPAGO_API_URL || MERCADOPAGO_API_URL).replace(/\/+$/, ""),
    google: loadGoogleSettings(env, appUrl),
    googleOauthUrl: (env.GOOGLE_OAUTH_URL || GOOGLE_OAUTH_URL).replace(/\/+$/, ""),
    googleApiUrl: (env.GOOGLE_API_URL || GOOGLE_API_URL).replace(/\/+$/, ""),
    push: loadPushSettings(env),
  };
}

function loadGoogleSettings(env: NodeJS.ProcessEnv, appUrl: string): GoogleSettings | null {
  const { GOOGLE_CLIENT_ID: clientId, GOOGLE_CLIENT_SECRET: clientSecret } = env;
  if (!clientId || !clientSecret) return null;
  return {
    clientId,
    clientSecret,
    redirectUrl: env.GOOGLE_REDIRECT_URL || `${appUrl}/api/integrations/google/callback`,
  };
}

function loadEmailDefaults(env: NodeJS.ProcessEnv): EmailDefaults | null {
  const { SMTP_HOST: host, SMTP_USER: username, SMTP_PASSWORD: password, EMAIL_FROM } = env;
  if (!host || !username || !password || !EMAIL_FROM) return null;
  return {
    host,
    port: Number(env.SMTP_PORT || 587),
    username,
    password,
    fromEmail: EMAIL_FROM,
    footer: env.EMAIL_FOOTER || null,
  };
}

function loadSmsDefaults(env: NodeJS.ProcessEnv): SmsDefaults | null {
  const {
    TWILIO_ACCOUNT_SID: accountSid,
    TWILIO_API_KEY_SID: apiKeySid,
    TWILIO_API_KEY_SECRET: apiKeySecret,
    TWILIO_FROM_NUMBER: sender,
  } = env;
  if (!accountSid || !apiKeySid || !apiKeySecret || !sender) return null;
  return { accountSid, apiKeySid, apiKeySecret, sender: sender.replace(/[\s().-]/g, "") };
}

function loadWhatsappDefaults(env: NodeJS.ProcessEnv): WhatsappDefaults | null {
  const phoneNumberId = env.WHATSAPP_PHONE_NUMBER_ID;
  const accessToken = env.WHATSAPP_ACCESS_TOKEN;
  if (!phoneNumberId || !accessToken) return null;
  return {
    phoneNumberId,
    accessToken,
    wabaId: env.WHATSAPP_WABA_ID || null,
    appSecret: env.WHATSAPP_APP_SECRET || null,
    verifyToken: env.WHATSAPP_VERIFY_TOKEN || null,
  };
}

/** Lets `docker compose up` work without asking the user to invent a secret. */
function readOrCreateSecret(dataDir: string): string {
  const file = path.join(dataDir, "secret");
  if (existsSync(file)) return readFileSync(file, "utf8").trim();
  const secret = randomBytes(32).toString("hex");
  writeFileSync(file, secret, { mode: 0o600 });
  return secret;
}
