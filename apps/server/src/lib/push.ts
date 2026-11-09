/**
 * Push notifications to the phone apps: the delivery side.
 *
 * The server keeps the devices and decides who hears about what (see notifications.ts);
 * a provider only delivers. An install picks one with PUSH_PROVIDER:
 *
 * - `none` (the default): nothing is sent, nothing is configured, nobody is billed.
 * - `webhook`: each message is POSTed as JSON to PUSH_WEBHOOK_URL, to be routed from there
 *   to whatever delivers it.
 *
 * A deployment that embeds the server can also hand createApp a provider of its own.
 *
 * Push credentials belong to whoever publishes the app: Apple and Google only deliver to an
 * app signed by the account that holds them. A server can reach the phones of the app built
 * against the same credentials, and no other.
 */

import type { Config } from "../config.ts";

export const DEVICE_PLATFORMS = ["ios", "android"] as const;
export type DevicePlatform = (typeof DEVICE_PLATFORMS)[number];

/** Which Apple gateway a token belongs to: development builds use the sandbox. */
export const DEVICE_ENVIRONMENTS = ["production", "sandbox"] as const;
export type DeviceEnvironment = (typeof DEVICE_ENVIRONMENTS)[number];

export type PushDevice = {
  token: string;
  platform: DevicePlatform;
  environment: DeviceEnvironment;
};

/** One notification for one person, to every phone they're signed in on. */
export type PushMessage = {
  /** The person it's for: their user id on this install. */
  userId: string;
  title: string;
  body: string;
  /** The person's unread notifications, for the number on the app's icon. */
  badge: number;
  /** Where tapping it leads: strings only, as every platform carries them. */
  data: Record<string, string>;
  devices: PushDevice[];
};

export type PushResult = {
  /** Tokens the provider says will never work again; the server forgets them. */
  invalidTokens?: string[];
};

export type PushProvider = {
  name: string;
  send(message: PushMessage): Promise<PushResult | undefined>;
};

export type PushSettings =
  | { provider: "none" }
  | { provider: "webhook"; url: string; secret: string | null };

export function loadPushSettings(env: NodeJS.ProcessEnv): PushSettings {
  const provider = env.PUSH_PROVIDER || "none";
  if (provider === "none") return { provider };
  if (provider === "webhook") {
    const url = env.PUSH_WEBHOOK_URL;
    if (!url) throw new Error("PUSH_PROVIDER=webhook needs PUSH_WEBHOOK_URL");
    return { provider, url, secret: env.PUSH_WEBHOOK_SECRET || null };
  }
  throw new Error(`PUSH_PROVIDER must be "none" or "webhook", not "${provider}"`);
}

/** The provider the install's settings describe, or null when push is off. */
export function pushProviderFromConfig(config: Pick<Config, "push">): PushProvider | null {
  const settings = config.push;
  if (settings.provider === "webhook") return webhookPushProvider(settings.url, settings.secret);
  return null;
}

/**
 * POSTs every message as JSON. The body carries the devices with their tokens, so the
 * receiving end is trusted with them: an address the install's operator controls. The
 * response may list `invalidTokens` to forget.
 */
export function webhookPushProvider(url: string, secret: string | null): PushProvider {
  return {
    name: "webhook",
    async send(message) {
      const res = await fetch(url, {
        method: "POST",
        headers: {
          "content-type": "application/json",
          ...(secret ? { authorization: `Bearer ${secret}` } : {}),
        },
        body: JSON.stringify(message),
        signal: AbortSignal.timeout(8_000),
      });
      if (!res.ok) throw new Error(`push webhook answered ${res.status}`);
      const answer = (await res.json().catch(() => null)) as { invalidTokens?: unknown } | null;
      const invalid = answer?.invalidTokens;
      return Array.isArray(invalid)
        ? { invalidTokens: invalid.filter((t): t is string => typeof t === "string") }
        : undefined;
    },
  };
}
