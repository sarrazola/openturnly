import { Hono } from "hono";
import type { AppEnv, Deps } from "../lib/http.ts";

/**
 * Where Twilio reports on the texts sent from here: each one carries its own address, signed
 * for its delivery. What the report says isn't trusted; Twilio is asked about the message.
 */
export function smsWebhookRoutes({ automations, sms }: Deps) {
  return new Hono<AppEnv>().post("/status/:deliveryId", async (c) => {
    const deliveryId = c.req.param("deliveryId");
    if (!sms.validStatusToken(deliveryId, c.req.query("token"))) {
      return c.json({ error: { code: "forbidden" } }, 403);
    }
    await automations.recordSmsReport(deliveryId);
    // An empty answer: Twilio would otherwise read the body as instructions.
    return c.body(null, 204);
  });
}
