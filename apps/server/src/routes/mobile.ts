import { and, eq } from "drizzle-orm";
import { Hono } from "hono";
import { z } from "zod";
import { devices } from "../db/schema.ts";
import { CLIENT_IP_HEADER, clientIp } from "../lib/client-ip.ts";
import { type AppEnv, apiError, type Deps, parseBody, requireUser } from "../lib/http.ts";
import { DEVICE_ENVIRONMENTS, DEVICE_PLATFORMS } from "../lib/push.ts";

const deviceSchema = z.object({
  token: z.string().trim().min(1).max(4096),
  platform: z.enum(DEVICE_PLATFORMS),
  environment: z.enum(DEVICE_ENVIRONMENTS).default("production"),
});

const forgetDeviceSchema = z.object({ token: z.string().trim().min(1).max(4096) });

/**
 * Sign-in for the phone apps. A browser keeps its session in a cookie; an app can't share
 * the browser's, so it gets the session's token and sends it with every call as
 * "Authorization: Bearer". It's an ordinary session underneath: the same password rules,
 * the same limits on wrong attempts, and "sign out everywhere" ends it too.
 */
export function mobileRoutes({ db, auth, config, notifications }: Deps) {
  /** Hands a request to the sign-in pages as the browser would call them, limits included. */
  const forward = (c: Parameters<typeof clientIp>[0], path: string, body?: string) => {
    const headers = new Headers(c.req.raw.headers);
    // The address comes from the socket (or the trusted proxy), never from the client.
    headers.delete(CLIENT_IP_HEADER);
    const ip = clientIp(c, config.trustProxy);
    if (ip) headers.set(CLIENT_IP_HEADER, ip);
    // A cookie from elsewhere never mixes with the app's own credential.
    headers.delete("cookie");
    headers.delete("content-length");
    headers.set("content-type", "application/json");
    // The sign-in pages check where a request comes from; the app is this install's own.
    headers.set("origin", config.appUrl);
    return auth.handler(
      new Request(new URL(path, config.appUrl), {
        method: "POST",
        headers,
        body: body ?? "{}",
      }),
    );
  };

  return (
    new Hono<AppEnv>()
      .post("/sign-in", async (c) => {
        const input = (await c.req.json().catch(() => null)) as {
          email?: unknown;
          password?: unknown;
        } | null;
        if (typeof input?.email !== "string" || typeof input.password !== "string") {
          throw apiError(400, "validation_failed");
        }
        const res = await forward(
          c,
          "/api/auth/sign-in/email",
          JSON.stringify({ email: input.email.trim(), password: input.password }),
        );
        const token = res.headers.get("set-auth-token");
        if (res.status === 429) throw apiError(429, "too_many_attempts");
        if (!res.ok || !token) throw apiError(401, "invalid_credentials");
        const { user } = (await res.json()) as {
          user: { id: string; name: string; email: string; role?: string };
        };
        return c.json({
          token,
          user: { id: user.id, name: user.name, email: user.email, role: user.role ?? "member" },
        });
      })

      /** What this install offers the apps: whether it sends push notifications at all. */
      .get("/settings", (c) => {
        requireUser(c);
        return c.json({ push: { enabled: notifications.pushEnabled } });
      })

      /**
       * Registers the phone for push notifications under the current session: signing out,
       * or the session ending, stops them. A token seen before moves to this person.
       */
      .post("/devices", async (c) => {
        const user = requireUser(c);
        const input = await parseBody(c, deviceSchema);
        const current = await auth.api.getSession({ headers: c.req.raw.headers });
        if (!current) throw apiError(401, "unauthorized");
        const values = {
          userId: user.id,
          sessionId: current.session.id,
          platform: input.platform,
          token: input.token,
          environment: input.environment,
        };
        await db
          .insert(devices)
          .values(values)
          .onConflictDoUpdate({ target: devices.token, set: { ...values, updatedAt: new Date() } });
        return c.json({ ok: true });
      })

      /** Stops push notifications to this phone, as when they're switched off in the app. */
      .delete("/devices", async (c) => {
        const user = requireUser(c);
        const input = await parseBody(c, forgetDeviceSchema);
        await db
          .delete(devices)
          .where(and(eq(devices.token, input.token), eq(devices.userId, user.id)));
        return c.json({ ok: true });
      })

      /** Ends the app's session, like signing out in a browser. */
      .post("/sign-out", async (c) => {
        requireUser(c);
        await forward(c, "/api/auth/sign-out");
        return c.json({ ok: true });
      })
  );
}
