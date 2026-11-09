/**
 * Surveys: one short question after an appointment, 1 to 5 stars and a comment.
 *
 * A business turns them on and sends the link from a flow that runs when an appointment
 * ends, with {{appointment.survey_link}} in its message (or the ready-made survey email or
 * text). The link is made when the step is queued and starts counting its hours when the
 * message goes out. A customer gets at most one every SURVEY_RESEND_DAYS, however often
 * they come, and each link takes one answer.
 */

import { addDays, zonedTimeToInstant } from "@openturnly/core";
import { and, asc, desc, eq, gt, gte, isNotNull, isNull, lt, ne, type SQL } from "drizzle-orm";
import type { Db } from "../db/client.ts";
import {
  appointments,
  type Business,
  businesses,
  customers,
  locations,
  type Survey,
  surveys,
} from "../db/schema.ts";
import { randomCode } from "./codes.ts";

/** The placeholder that puts the link in a message. */
export const SURVEY_FIELD = "appointment.survey_link";
/** At most one survey per customer in this many days. */
export const SURVEY_RESEND_DAYS = 30;
/** Characters in a link's code: 32^8, about a trillion, so links can't be guessed. */
const CODE_LENGTH = 8;
const HOUR_MS = 3_600_000;
const DAY_MS = 24 * HOUR_MS;

/** Whether a flow step sends the survey link, in its own words or a ready-made text. */
export function usesSurveyLink(action: { type: string; template?: unknown }): boolean {
  if (
    (action.type === "email" || action.type === "sms") &&
    action.template === "appointment_survey"
  ) {
    return true;
  }
  return JSON.stringify(action).includes(SURVEY_FIELD);
}

export function surveyUrl(appUrl: string, code: string) {
  return `${appUrl}/e/${code}`;
}

/** The appointment's survey code, made the first time a step is about to send it. */
export async function surveyCodeFor(db: Db, businessId: string, appointmentId: string) {
  const existing = async () => {
    const [row] = await db
      .select({ code: surveys.code })
      .from(surveys)
      .where(eq(surveys.appointmentId, appointmentId));
    return row?.code;
  };
  for (;;) {
    const found = await existing();
    if (found) return found;
    const [made] = await db
      .insert(surveys)
      .values({ code: randomCode(CODE_LENGTH), businessId, appointmentId })
      .onConflictDoNothing()
      .returning({ code: surveys.code });
    if (made) return made.code;
    // The code was taken, or another step made this appointment's survey meanwhile.
  }
}

/**
 * Why a survey step can't go out now, or null when it can: the survey was turned off (or
 * never prepared), or the customer got another one recently. A link already sent (by
 * another step, or a retry) can always go again.
 */
export async function surveyHold(
  db: Db,
  business: Pick<Business, "surveyEnabled">,
  appointmentId: string,
  now: number,
): Promise<"survey_off" | "survey_recent" | null> {
  if (!business.surveyEnabled) return "survey_off";
  const [row] = await db
    .select({ survey: surveys, customerId: appointments.customerId })
    .from(surveys)
    .innerJoin(appointments, eq(appointments.id, surveys.appointmentId))
    .where(eq(surveys.appointmentId, appointmentId));
  if (!row) return "survey_off";
  if (row.survey.sentAt) return null;
  const [recent] = await db
    .select({ id: surveys.id })
    .from(surveys)
    .innerJoin(appointments, eq(appointments.id, surveys.appointmentId))
    .where(
      and(
        eq(appointments.customerId, row.customerId),
        ne(surveys.id, row.survey.id),
        gt(surveys.sentAt, new Date(now - SURVEY_RESEND_DAYS * DAY_MS)),
      ),
    )
    .limit(1);
  return recent ? "survey_recent" : null;
}

/** Starts the link's hours once its message went out (the first time only). */
export async function markSurveySent(
  db: Db,
  business: Pick<Business, "surveyValidHours">,
  appointmentId: string,
  now: number,
) {
  await db
    .update(surveys)
    .set({
      sentAt: new Date(now),
      expiresAt: new Date(now + business.surveyValidHours * HOUR_MS),
    })
    .where(and(eq(surveys.appointmentId, appointmentId), isNull(surveys.sentAt)));
}

export type SurveyState = "open" | "answered" | "closed";

export function surveyState(survey: Survey, now: number): SurveyState {
  if (survey.answeredAt) return "answered";
  if (!survey.expiresAt || survey.expiresAt.getTime() <= now) return "closed";
  return "open";
}

/** A sent survey by its link's code, with what the page shows about the visit. */
export async function findSentSurvey(db: Db, code: string) {
  const [row] = await db
    .select({ survey: surveys, appointment: appointments, business: businesses })
    .from(surveys)
    .innerJoin(appointments, eq(appointments.id, surveys.appointmentId))
    .innerJoin(businesses, eq(businesses.id, surveys.businessId))
    .where(and(eq(surveys.code, code.trim().toUpperCase()), isNotNull(surveys.sentAt)));
  return row ?? null;
}

/** Saves the answer if the link is still open; otherwise says why not. */
export async function answerSurvey(
  db: Db,
  survey: Survey,
  answer: { rating: number; comment?: string | null },
  now: number,
): Promise<SurveyState> {
  const state = surveyState(survey, now);
  if (state !== "open") return state;
  const [saved] = await db
    .update(surveys)
    .set({ rating: answer.rating, comment: answer.comment ?? null, answeredAt: new Date(now) })
    .where(
      and(
        eq(surveys.id, survey.id),
        isNull(surveys.answeredAt),
        gt(surveys.expiresAt, new Date(now)),
      ),
    )
    .returning({ id: surveys.id });
  // Someone answered the same link a moment before.
  return saved ? "open" : "answered";
}

/**
 * The surveys sent for appointments in a period (the business's days, both included):
 * how many went out, how many were answered, the average and each star's count, and the
 * answers, newest first.
 */
export async function surveyResults(
  db: Db,
  business: Business,
  period: { from: string; to: string },
  filters: { resourceId?: string; locationId?: string } = {},
) {
  const start = new Date(zonedTimeToInstant(period.from, 0, business.timezone));
  const end = new Date(zonedTimeToInstant(addDays(period.to, 1), 0, business.timezone));
  const where: SQL[] = [
    eq(surveys.businessId, business.id),
    isNotNull(surveys.sentAt),
    gte(appointments.startsAt, start),
    lt(appointments.startsAt, end),
  ];
  if (filters.resourceId) where.push(eq(appointments.resourceId, filters.resourceId));
  if (filters.locationId) where.push(eq(appointments.locationId, filters.locationId));
  const rows = await db
    .select({
      id: surveys.id,
      rating: surveys.rating,
      comment: surveys.comment,
      answeredAt: surveys.answeredAt,
      appointment: {
        id: appointments.id,
        code: appointments.code,
        startsAt: appointments.startsAt,
        serviceName: appointments.serviceName,
        resourceName: appointments.resourceName,
        locationName: locations.name,
      },
      customer: {
        id: customers.id,
        firstName: customers.firstName,
        lastName: customers.lastName,
      },
    })
    .from(surveys)
    .innerJoin(appointments, eq(appointments.id, surveys.appointmentId))
    .innerJoin(customers, eq(customers.id, appointments.customerId))
    .leftJoin(locations, eq(locations.id, appointments.locationId))
    .where(and(...where))
    .orderBy(desc(surveys.answeredAt), asc(surveys.id));

  const answered = rows.filter((row) => row.rating !== null);
  const stars = [1, 2, 3, 4, 5].map((star) => answered.filter((row) => row.rating === star).length);
  const total = answered.reduce((sum, row) => sum + (row.rating ?? 0), 0);
  return {
    summary: {
      sent: rows.length,
      answered: answered.length,
      /** One decimal, or null with no answers yet. */
      average: answered.length ? Math.round((total / answered.length) * 10) / 10 : null,
      /** How many gave 1, 2, 3, 4 and 5 stars. */
      stars,
    },
    responses: answered.map((row) => ({
      id: row.id,
      rating: row.rating as number,
      comment: row.comment,
      answeredAt: row.answeredAt,
      appointment: row.appointment,
      customer: row.customer,
    })),
  };
}
