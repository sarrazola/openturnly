import { Hono } from "hono";
import { CLIENT_IP_HEADER, clientIp } from "../lib/client-ip.ts";
import { type AppEnv, apiError, type Deps, requireUser } from "../lib/http.ts";

/**
 * Sign-in for the phone apps. A browser keeps its session in a cookie; an app can't share
 * the browser's, so it gets the session's token and sends it with every call as
 * "Authorization: Bearer". It's an ordinary session underneath: the same password rules,
 * the same limits on wrong attempts, and "sign out everywhere" ends it too.
 */
export function mobileRoutes({ auth, config }: Deps) {
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

      /** Ends the app's session, like signing out in a browser. */
      .post("/sign-out", async (c) => {
        requireUser(c);
        await forward(c, "/api/auth/sign-out");
        return c.json({ ok: true });
      })
  );
}
