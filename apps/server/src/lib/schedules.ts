import {
  addDays,
  type BookingRules,
  instantToZoned,
  type ResourceSchedule,
} from "@openturnly/core";
import { and, eq, gt, gte, inArray, lt, lte, ne } from "drizzle-orm";
import type { Db } from "../db/client.ts";
import {
  appointments,
  BUSY_STATUSES,
  type Business,
  closures,
  holidayOverrides,
  locationServices,
  resourceLocations,
  resourceServices,
  resources,
  weeklyHours,
} from "../db/schema.ts";
import { closedHolidayDates } from "./holidays.ts";

/** Either the database or an open transaction: both run the same queries. */
export type Queryable = Db | Parameters<Parameters<Db["transaction"]>[0]>[0];

export function bookingRules(business: Business): BookingRules {
  return {
    timeZone: business.timezone,
    slotIntervalMin: business.slotIntervalMin,
    minNoticeMin: business.minNoticeMin,
    maxAdvanceDays: business.maxAdvanceDays,
    dailyLimits: true,
  };
}

/**
 * Builds the schedules the availability engine needs for one service at one location
 * between two dates. With `resourceId` null it includes everyone at the location offering
 * the service ("anyone"). A location without professionals gets one schedule
 * (`resourceId: null`) on its own hours. Professionals are busy with their appointments
 * at every location, so nobody is booked in two places at once.
 */
export async function loadSchedules(
  db: Queryable,
  business: Business,
  locationId: string,
  serviceId: string,
  resourceId: string | null,
  fromDate: string,
  toDate: string,
  /** An appointment being moved: its own time doesn't count as busy. */
  ignoreAppointmentId?: string,
  /**
   * Only these professionals (`null`: nobody in particular, for a location without them).
   * Online services are limited to who can host their call.
   */
  onlyResources?: ReadonlySet<string | null>,
): Promise<ResourceSchedule[]> {
  const [offeredHere] = await db
    .select({ id: locationServices.serviceId })
    .from(locationServices)
    .where(
      and(eq(locationServices.locationId, locationId), eq(locationServices.serviceId, serviceId)),
    );
  if (!offeredHere) return [];

  const team = await db
    .select({ id: resources.id, maxDailyAppointments: resources.maxDailyAppointments })
    .from(resources)
    .innerJoin(resourceLocations, eq(resourceLocations.resourceId, resources.id))
    .where(
      and(
        eq(resources.businessId, business.id),
        eq(resources.active, true),
        eq(resourceLocations.locationId, locationId),
      ),
    );

  let candidates: (string | null)[];
  if (team.length === 0) {
    candidates = resourceId === null ? [null] : [];
  } else {
    const offering = (
      await db
        .select({ id: resourceServices.resourceId })
        .from(resourceServices)
        .where(
          and(
            eq(resourceServices.serviceId, serviceId),
            inArray(
              resourceServices.resourceId,
              team.map((resource) => resource.id),
            ),
          ),
        )
    ).map((row) => row.id);
    candidates = resourceId === null ? offering : offering.filter((id) => id === resourceId);
  }
  if (onlyResources) candidates = candidates.filter((id) => onlyResources.has(id));
  if (candidates.length === 0) return [];

  const hours = await db
    .select()
    .from(weeklyHours)
    .where(and(eq(weeklyHours.businessId, business.id), eq(weeklyHours.locationId, locationId)));
  const locationHours = hours.filter((range) => range.resourceId === null);

  const closedRows = (
    await db
      .select({
        date: closures.date,
        endDate: closures.endDate,
        locationId: closures.locationId,
        resourceId: closures.resourceId,
      })
      .from(closures)
      .where(
        and(
          eq(closures.businessId, business.id),
          gte(closures.endDate, fromDate),
          lte(closures.date, toDate),
        ),
      )
  )
    // Another location's closure doesn't close this one.
    .filter(
      (row) => row.resourceId !== null || row.locationId === null || row.locationId === locationId,
    )
    .flatMap((row) => {
      // One entry per closed day inside the requested window.
      const days: { date: string; resourceId: string | null }[] = [];
      const last = row.endDate < toDate ? row.endDate : toDate;
      for (
        let day = row.date < fromDate ? fromDate : row.date;
        day <= last;
        day = addDays(day, 1)
      ) {
        days.push({ date: day, resourceId: row.resourceId });
      }
      return days;
    });

  const overrides = await db
    .select({ date: holidayOverrides.date })
    .from(holidayOverrides)
    .where(
      and(
        eq(holidayOverrides.businessId, business.id),
        gte(holidayOverrides.date, fromDate),
        lte(holidayOverrides.date, toDate),
      ),
    );
  const holidays = closedHolidayDates(
    business,
    new Set(overrides.map((row) => row.date)),
    fromDate,
    toDate,
  );

  // Wide window on purpose: time zones can shift a date by up to a day either way.
  const windowStart = new Date(`${addDays(fromDate, -1)}T00:00:00Z`);
  const windowEnd = new Date(`${addDays(toDate, 2)}T00:00:00Z`);
  const people = candidates.filter((candidate): candidate is string => candidate !== null);
  const booked = await db
    .select({
      locationId: appointments.locationId,
      resourceId: appointments.resourceId,
      start: appointments.startsAt,
      end: appointments.endsAt,
      before: appointments.bufferBeforeMin,
      after: appointments.bufferAfterMin,
    })
    .from(appointments)
    .where(
      and(
        eq(appointments.businessId, business.id),
        inArray(appointments.status, BUSY_STATUSES),
        lt(appointments.startsAt, windowEnd),
        gt(appointments.endsAt, windowStart),
        ignoreAppointmentId ? ne(appointments.id, ignoreAppointmentId) : undefined,
        people.length === candidates.length
          ? inArray(appointments.resourceId, people)
          : eq(appointments.locationId, locationId),
      ),
    );

  return candidates.map((candidate) => {
    const own = hours.filter((range) => candidate !== null && range.resourceId === candidate);
    // Without professionals the location takes one appointment at a time.
    const theirs = booked.filter((row) =>
      candidate === null ? row.locationId === locationId : row.resourceId === candidate,
    );
    const dailyCounts = new Map<string, number>();
    for (const row of theirs) {
      const day = instantToZoned(row.start.getTime(), business.timezone).date;
      dailyCounts.set(day, (dailyCounts.get(day) ?? 0) + 1);
    }
    const ownLimit = team.find((member) => member.id === candidate)?.maxDailyAppointments;
    return {
      resourceId: candidate,
      hours: own.length > 0 ? own : locationHours,
      closedDates: new Set([
        ...holidays,
        ...closedRows
          .filter((row) => row.resourceId === null || row.resourceId === candidate)
          .map((row) => row.date),
      ]),
      // Each appointment keeps its own margins free too.
      busy: theirs.map((row) => ({
        start: row.start.getTime() - row.before * 60_000,
        end: row.end.getTime() + row.after * 60_000,
      })),
      dailyLimit: ownLimit ?? business.maxDailyAppointments,
      dailyCounts,
    };
  });
}

/** For "anyone available", spread the work: the professional with the fewest bookings that day. */
export function pickLeastBusy(
  candidates: (string | null)[],
  schedules: ResourceSchedule[],
  slotStart: number,
): string | null {
  const dayStart = slotStart - 12 * 60 * 60_000;
  const dayEnd = slotStart + 12 * 60 * 60_000;
  let best: string | null = candidates[0] ?? null;
  let bestLoad = Number.POSITIVE_INFINITY;
  for (const candidate of candidates) {
    const schedule = schedules.find((s) => s.resourceId === candidate);
    const load =
      schedule?.busy.filter((busy) => busy.start >= dayStart && busy.start < dayEnd).length ?? 0;
    if (load < bestLoad) {
      best = candidate;
      bestLoad = load;
    }
  }
  return best;
}
