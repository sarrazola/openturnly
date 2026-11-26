import { describe, expect, it } from "vitest";
import {
  availabilityBetween,
  type BookingRules,
  hoursProblem,
  type ResourceSchedule,
  slotsForDate,
  visibleSlots,
} from "../src/availability.ts";
import { zonedTimeToInstant } from "../src/time.ts";

const TZ = "America/Bogota";
const rules: BookingRules = {
  timeZone: TZ,
  slotIntervalMin: 30,
  minNoticeMin: 0,
  maxAdvanceDays: 60,
};
// Monday 2026-10-19, 07:00 in Bogotá.
const NOW = zonedTimeToInstant("2026-10-19", 7 * 60, TZ);
const SATURDAY = "2026-10-24";

function schedule(overrides: Partial<ResourceSchedule> = {}): ResourceSchedule {
  return {
    resourceId: null,
    hours: [{ weekday: 6, start: "09:00", end: "12:00" }],
    closedDates: new Set(),
    busy: [],
    ...overrides,
  };
}

const at = (time: string, date = SATURDAY) => {
  const [hours, minutes] = time.split(":").map(Number) as [number, number];
  return zonedTimeToInstant(date, hours * 60 + minutes, TZ);
};
const times = (date: string, duration: number, schedules: ResourceSchedule[], r = rules) =>
  slotsForDate(date, duration, schedules, r, NOW).map((slot) => slot.time);

describe("slotsForDate", () => {
  it("offers start times where the whole service fits", () => {
    expect(times(SATURDAY, 60, [schedule()])).toEqual([
      "09:00",
      "09:30",
      "10:00",
      "10:30",
      "11:00",
    ]);
  });

  it("supports split shifts", () => {
    const split = schedule({
      hours: [
        { weekday: 6, start: "09:00", end: "10:00" },
        { weekday: 6, start: "14:00", end: "15:00" },
      ],
    });
    expect(times(SATURDAY, 30, [split])).toEqual(["09:00", "09:30", "14:00", "14:30"]);
  });

  it("skips times that overlap existing appointments", () => {
    const busy = schedule({ busy: [{ start: at("10:00"), end: at("11:00") }] });
    expect(times(SATURDAY, 60, [busy])).toEqual(["09:00", "11:00"]);
  });

  it("returns nothing on closed dates and days without hours", () => {
    expect(times(SATURDAY, 30, [schedule({ closedDates: new Set([SATURDAY]) })])).toEqual([]);
    expect(times("2026-10-25", 30, [schedule()])).toEqual([]);
  });

  it("respects minimum notice and the booking window", () => {
    const today = schedule({ hours: [{ weekday: 1, start: "07:00", end: "10:00" }] });
    expect(times("2026-10-19", 60, [today], { ...rules, minNoticeMin: 120 })).toEqual(["09:00"]);
    expect(times(SATURDAY, 30, [schedule()], { ...rules, maxAdvanceDays: 3 })).toEqual([]);
    expect(times("2026-10-17", 30, [schedule()])).toEqual([]); // in the past
  });

  it("merges professionals and tells which ones are free", () => {
    const ana = schedule({ resourceId: "ana" });
    const leo = schedule({
      resourceId: "leo",
      busy: [{ start: at("09:00"), end: at("10:00") }],
    });
    const slots = slotsForDate(SATURDAY, 60, [ana, leo], rules, NOW);
    expect(slots[0]).toMatchObject({ time: "09:00", resourceIds: ["ana"] });
    expect(slots.find((slot) => slot.time === "10:00")?.resourceIds).toEqual(["ana", "leo"]);
  });
});

describe("availabilityBetween", () => {
  it("lists only dates that have free times", () => {
    const result = availabilityBetween("2026-10-19", "2026-11-01", 60, [schedule()], rules, NOW);
    expect(Object.keys(result)).toEqual(["2026-10-24", "2026-10-31"]);
  });
});

describe("hoursProblem", () => {
  it("finds inverted and overlapping ranges", () => {
    expect(hoursProblem([{ weekday: 1, start: "10:00", end: "09:00" }])).toBe("end_before_start");
    expect(
      hoursProblem([
        { weekday: 1, start: "09:00", end: "12:00" },
        { weekday: 1, start: "11:00", end: "13:00" },
      ]),
    ).toBe("overlap");
    expect(
      hoursProblem([
        { weekday: 1, start: "09:00", end: "12:00" },
        { weekday: 2, start: "11:00", end: "13:00" },
      ]),
    ).toBeNull();
  });
});

describe("margins, daily limits and hidden times", () => {
  it("keeps the professional free during the margins of both appointments", () => {
    // 09:30–10:00 booked with 15 minutes after it.
    const busy = [{ start: at("09:30"), end: at("10:15") }];
    expect(times(SATURDAY, 30, [schedule({ busy })])).toEqual(["09:00", "10:30", "11:00", "11:30"]);
    // The new one needs 30 minutes free before it, too: 10:30 would start 15 minutes after
    // the other one's margin ends.
    const slots = slotsForDate(
      SATURDAY,
      { durationMin: 30, bufferBeforeMin: 30 },
      [schedule({ busy })],
      rules,
      NOW,
    ).map((slot) => slot.time);
    expect(slots).toEqual(["09:00", "11:00", "11:30"]);
    // Margins don't have to fit inside the opening hours.
    expect(
      slotsForDate(SATURDAY, { durationMin: 30, bufferAfterMin: 60 }, [schedule()], rules, NOW).map(
        (slot) => slot.time,
      ),
    ).toContain("11:30");
  });

  it("stops at the daily limit for customers, not for the business's people", () => {
    const full = schedule({ dailyLimit: 2, dailyCounts: new Map([[SATURDAY, 2]]) });
    expect(times(SATURDAY, 60, [full])).toEqual([]);
    expect(times(SATURDAY, 60, [full], { ...rules, dailyLimits: false })).not.toEqual([]);
    expect(
      times(SATURDAY, 60, [schedule({ dailyLimit: 3, dailyCounts: full.dailyCounts })]),
    ).not.toEqual([]);
  });

  it("hides the same times on every load and never a whole day", () => {
    const all = slotsForDate(SATURDAY, 30, [schedule()], rules, NOW);
    const shown = visibleSlots(all, SATURDAY, 50, "business-1");
    expect(shown.length).toBeLessThan(all.length);
    expect(visibleSlots(all, SATURDAY, 50, "business-1")).toEqual(shown);
    expect(visibleSlots(all, SATURDAY, 0, "business-1")).toEqual(all);
    expect(visibleSlots(all, SATURDAY, 90, "business-1").length).toBeGreaterThanOrEqual(1);
    expect(visibleSlots(all.slice(0, 1), SATURDAY, 90, "business-1")).toHaveLength(1);
  });
});
