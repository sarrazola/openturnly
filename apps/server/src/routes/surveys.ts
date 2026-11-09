import { isDateString, surveyAnswerSchema, surveySettingsSchema } from "@openturnly/core";
import { eq } from "drizzle-orm";
import { Hono } from "hono";
import { z } from "zod";
import { type Business, businesses } from "../db/schema.ts";
import { type AppEnv, apiError, type BusinessEnv, type Deps, parseBody } from "../lib/http.ts";
import { imageUrls } from "../lib/images.ts";
import { answerSurvey, findSentSurvey, surveyResults, surveyState } from "../lib/surveys.ts";

const periodQuery = z
  .object({
    from: z.string().refine(isDateString),
    to: z.string().refine(isDateString),
    locationId: z.string().min(1).optional(),
    resourceId: z.string().min(1).optional(),
  })
  .refine((query) => query.from <= query.to);

/** Longest period one listing covers: a bit over a year, like reports. */
const MAX_DAYS = 400;

const settingsOf = (business: Business) => ({
  enabled: business.surveyEnabled,
  validHours: business.surveyValidHours,
});

/** The business's surveys for its admins: the setting, and the answers over a period. */
export function surveyRoutes({ db }: Deps) {
  return new Hono<BusinessEnv>()
    .get("/settings", (c) => c.json(settingsOf(c.get("business"))))
    .patch("/settings", async (c) => {
      const input = await parseBody(c, surveySettingsSchema);
      const [business] = await db
        .update(businesses)
        .set({
          ...(input.enabled === undefined ? {} : { surveyEnabled: input.enabled }),
          ...(input.validHours === undefined ? {} : { surveyValidHours: input.validHours }),
          updatedAt: new Date(),
        })
        .where(eq(businesses.id, c.get("business").id))
        .returning();
      return c.json(settingsOf(business as Business));
    })
    .get("/", async (c) => {
      const result = periodQuery.safeParse(c.req.query());
      if (!result.success) throw apiError(400, "invalid_range");
      const { from, to, locationId, resourceId } = result.data;
      if (Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`) > MAX_DAYS * 86_400_000) {
        throw apiError(400, "invalid_range");
      }
      return c.json(
        await surveyResults(db, c.get("business"), { from, to }, { locationId, resourceId }),
      );
    });
}

/**
 * The page a survey link opens (/e/<code>): what the visit was and, while it's open, the
 * question. It shows only the business, the service, the professional and the time.
 */
export function surveyPublicRoutes({ db }: Deps) {
  const find = async (code: string) => {
    const found = await findSentSurvey(db, code);
    if (!found) throw apiError(404, "survey_not_found");
    return found;
  };

  return new Hono<AppEnv>()
    .get("/:code", async (c) => {
      const { survey, appointment, business } = await find(c.req.param("code"));
      const { logoUrl } = await imageUrls(db, business.id, business.slug);
      return c.json({
        state: surveyState(survey, Date.now()),
        business: {
          name: business.name,
          slug: business.slug,
          locale: business.locale,
          timezone: business.timezone,
          brandColor: business.brandColor,
          logoUrl,
        },
        visit: {
          startsAt: appointment.startsAt,
          service: appointment.serviceName,
          professional: appointment.resourceName,
        },
        rating: survey.rating,
      });
    })
    .post("/:code", async (c) => {
      const { survey } = await find(c.req.param("code"));
      const answer = await parseBody(c, surveyAnswerSchema);
      const state = await answerSurvey(db, survey, answer, Date.now());
      if (state === "answered") throw apiError(409, "survey_answered");
      if (state === "closed") throw apiError(410, "survey_closed");
      return c.json({ ok: true });
    });
}
