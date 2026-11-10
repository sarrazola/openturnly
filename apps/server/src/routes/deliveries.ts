import { addDays, isDateString, zonedTimeToInstant } from "@openturnly/core";
import { and, desc, eq, gte, lt, type SQL, sql } from "drizzle-orm";
import { Hono } from "hono";
import { z } from "zod";
import {
  appointments,
  automationDeliveries,
  automations,
  businesses,
  customers,
  DELIVERY_STATUSES,
} from "../db/schema.ts";
import { type AppEnv, apiError, type Deps, requireOwner } from "../lib/http.ts";

/** Sends per page. */
const PAGE = 25;
const CHANNELS = ["webhook", "whatsapp", "email", "sms"] as const;

const query = z.object({
  from: z.string().refine(isDateString),
  to: z.string().refine(isDateString),
  /** The viewer's time zone, for whole days; UTC without one. */
  timezone: z.string().min(1).max(64).optional(),
  businessId: z.string().min(1).optional(),
  channel: z.enum(CHANNELS).optional(),
  status: z.enum(DELIVERY_STATUSES).optional(),
  automationId: z.string().min(1).optional(),
  /** Which page, from 1. */
  page: z.coerce.number().int().min(1).max(10_000).default(1),
});

/**
 * Every send of every business's flows, for whoever runs the install: when, which business,
 * flow and step, for whom, how it ended, what the provider charged and what it counted for
 * on the install's own accounts. Newest first, 25 to a page, with the period's totals.
 */
export function deliveryRoutes({ db }: Deps) {
  return new Hono<AppEnv>().get("/", async (c) => {
    requireOwner(c);
    const parsed = query.safeParse(c.req.query());
    if (!parsed.success) throw apiError(400, "invalid_range");
    const { from, to, businessId, channel, status, automationId, page } = parsed.data;
    let timezone = parsed.data.timezone ?? "UTC";
    try {
      new Intl.DateTimeFormat("en", { timeZone: timezone });
    } catch {
      timezone = "UTC";
    }
    const dayStart = (date: string) => new Date(zonedTimeToInstant(date, 0, timezone));
    const where: SQL[] = [
      gte(automationDeliveries.createdAt, dayStart(from)),
      lt(automationDeliveries.createdAt, dayStart(addDays(to, 1))),
    ];
    if (businessId) where.push(eq(automations.businessId, businessId));
    if (channel) where.push(eq(automationDeliveries.channel, channel));
    if (status) where.push(eq(automationDeliveries.status, status));
    if (automationId) where.push(eq(automationDeliveries.automationId, automationId));

    // The period's totals, before paging: what was counted on the install's accounts.
    const totals = await db
      .select({
        sends: sql<number>`count(*)::int`,
        failed: sql<number>`count(*) filter (where ${automationDeliveries.status} = 'failed')::int`,
        currency: automationDeliveries.chargeCurrency,
        charged: sql<string>`coalesce(sum(${automationDeliveries.charge}), 0)::text`,
      })
      .from(automationDeliveries)
      .innerJoin(automations, eq(automations.id, automationDeliveries.automationId))
      .where(and(...where))
      .groupBy(automationDeliveries.chargeCurrency);

    const rows = await db
      .select({
        delivery: automationDeliveries,
        flow: { name: automations.name, actions: automations.actions },
        business: { id: businesses.id, name: businesses.name, timezone: businesses.timezone },
        appointment: { code: appointments.code },
        customer: { firstName: customers.firstName, lastName: customers.lastName },
      })
      .from(automationDeliveries)
      .innerJoin(automations, eq(automations.id, automationDeliveries.automationId))
      .innerJoin(businesses, eq(businesses.id, automations.businessId))
      .leftJoin(appointments, eq(appointments.id, automationDeliveries.appointmentId))
      .leftJoin(customers, eq(customers.id, appointments.customerId))
      .where(and(...where))
      .orderBy(desc(automationDeliveries.createdAt), desc(automationDeliveries.id))
      .limit(PAGE)
      .offset((page - 1) * PAGE);

    return c.json({
      deliveries: rows.map(({ delivery, flow, business, appointment, customer }) => {
        const step = flow.actions.find((action) => action.id === delivery.actionId);
        return {
          ...delivery,
          channel: delivery.channel ?? step?.type ?? null,
          automationName: flow.name,
          business,
          appointment: appointment?.code ? appointment : null,
          customer: customer?.firstName ? customer : null,
        };
      }),
      page,
      pageSize: PAGE,
      totals: {
        sends: totals.reduce((sum, row) => sum + row.sends, 0),
        failed: totals.reduce((sum, row) => sum + row.failed, 0),
        charged: totals
          .filter((row) => row.currency && Number(row.charged) > 0)
          .map((row) => ({ currency: row.currency as string, amount: row.charged })),
      },
    });
  });
}
