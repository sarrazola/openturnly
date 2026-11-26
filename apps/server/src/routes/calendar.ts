import { calendarTargetSchema } from "@openturnly/core";
import { and, asc, eq, inArray } from "drizzle-orm";
import { Hono } from "hono";
import { businesses, resources } from "../db/schema.ts";
import {
  consentUrl,
  exchangeCode,
  type GrantedTokens,
  readState,
  revokeToken,
  signState,
} from "../lib/google-calendar.ts";
import { type AppEnv, apiError, type Deps, parseBody, requireUser } from "../lib/http.ts";

const DEFAULT_RETURN = "/admin/account";

/** A path on this install to come back to; anything else goes to the account page. */
function safeReturn(path: string | undefined) {
  if (!path?.startsWith("/") || path.startsWith("//") || path.includes("\\")) {
    return DEFAULT_RETURN;
  }
  return path.slice(0, 300);
}

/** The person's connected calendar: what it is, and which businesses go to it. */
export function calendarAccountRoutes({ db, calendar }: Deps) {
  const overview = async (userId: string) => {
    const view = await calendar.overview(userId);
    const ids = view.businesses.map((place) => place.businessId);
    const names =
      ids.length > 0
        ? await db
            .select({ id: businesses.id, name: businesses.name })
            .from(businesses)
            .where(inArray(businesses.id, ids))
        : [];
    // Owners and admins choose whose appointments they get.
    const managed = view.businesses
      .filter((place) => place.role !== "professional")
      .map((place) => place.businessId);
    const team =
      managed.length > 0
        ? await db
            .select({ id: resources.id, name: resources.name, businessId: resources.businessId })
            .from(resources)
            .where(and(inArray(resources.businessId, managed), eq(resources.active, true)))
            .orderBy(asc(resources.position), asc(resources.name))
        : [];
    return {
      available: calendar.available,
      connection: view.connection,
      businesses: view.businesses
        .map((place) => ({
          id: place.businessId,
          name: names.find((row) => row.id === place.businessId)?.name ?? "",
          role: place.role,
          enabled: place.enabled,
          resourceIds: place.resourceIds,
          failing: place.failing,
          professionals:
            place.role === "professional"
              ? []
              : team
                  .filter((member) => member.businessId === place.businessId)
                  .map(({ id, name }) => ({ id, name })),
        }))
        .sort((a, b) => a.name.localeCompare(b.name)),
    };
  };

  return (
    new Hono<AppEnv>()
      .get("/", async (c) => c.json(await overview(requireUser(c).id)))

      /** One business on or off, or whose appointments go there. */
      .patch("/", async (c) => {
        const user = requireUser(c);
        const input = await parseBody(c, calendarTargetSchema);
        const result = await calendar.setTarget(user.id, input);
        if (result === "not_connected") throw apiError(409, "calendar_not_connected");
        if (result === "business_not_found") throw apiError(404, "business_not_found");
        return c.json(await overview(user.id));
      })

      /** Disconnects; `?calendars=keep` leaves the business calendars in the person's account. */
      .delete("/", async (c) => {
        const user = requireUser(c);
        await calendar.disconnect(user.id, {
          deleteCalendars: c.req.query("calendars") !== "keep",
        });
        return c.json(await overview(user.id));
      })
  );
}

/**
 * Google's consent round trip. `/start` sends the signed-in person to Google; Google sends
 * them back to `/callback` (the OAuth client's redirect address), which stores the grant and
 * returns them to the page they came from with `?calendar=<outcome>`.
 */
export function googleConsentRoutes({ config, calendar }: Deps) {
  return new Hono<AppEnv>()
    .get("/start", (c) => {
      const user = requireUser(c);
      const google = config.google;
      if (!google) throw apiError(404, "calendar_not_available");
      const state = signState(config.secret, {
        userId: user.id,
        origin: config.appUrl,
        returnTo: safeReturn(c.req.query("returnTo")),
      });
      // No login hint: the Google account is often not the address used here.
      return c.redirect(consentUrl(google, state));
    })

    .get("/callback", async (c) => {
      const google = config.google;
      if (!google) throw apiError(404, "calendar_not_available");
      const state = readState(config.secret, c.req.query("state") ?? "");
      const back = (outcome: string) => {
        const path = state?.returnTo ?? DEFAULT_RETURN;
        return c.redirect(`${path}${path.includes("?") ? "&" : "?"}calendar=${outcome}`);
      };
      if (!state || state.origin !== config.appUrl) return back("expired");
      // Only the person who started it, signed in here, can finish it.
      const user = c.get("user");
      if (!user || user.id !== state.userId) return back("signed_out");
      if (c.req.query("error")) return back("denied");
      const code = c.req.query("code");
      if (!code) return back("failed");

      let tokens: GrantedTokens;
      try {
        tokens = await exchangeCode(config, google, code);
      } catch (error) {
        console.error("google: could not finish connecting", error);
        return back("failed");
      }
      // Google's consent screen lets people untick the calendar: without it there's nothing to do.
      if (!tokens.calendarGranted) {
        await revokeToken(config, tokens.refreshToken);
        return back("calendar_not_allowed");
      }
      await calendar.connect(user.id, tokens);
      return back("connected");
    });
}
