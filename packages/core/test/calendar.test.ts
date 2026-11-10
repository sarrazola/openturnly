import { describe, expect, it } from "vitest";
import {
  appointmentEvent,
  googleCalendarUrl,
  icsFile,
  outlookCalendarUrl,
  utf8Length,
} from "../src/calendar.ts";

/** A link's address and its query, decoded. */
function parse(link: string) {
  const [address = "", search = ""] = link.split("?");
  const params = Object.fromEntries(
    search.split("&").map((pair) => pair.split("=").map(decodeURIComponent) as [string, string]),
  );
  return { address, params };
}

const event = appointmentEvent({
  id: "a1b2c3",
  code: "K7Q4XM",
  businessName: "Barbería Central",
  businessPhone: "+57 300 123 4567",
  serviceName: "Corte clásico",
  resourceName: "Camila",
  location: { name: "Sede Norte", address: "Calle 10 # 20-30, Bogotá" },
  startsAt: "2026-11-12T15:00:00.000Z",
  endsAt: "2026-11-12T15:45:00.000Z",
  language: "es",
});

describe("calendar events", () => {
  it("describe the appointment in the business's language", () => {
    expect(event).toEqual({
      uid: "a1b2c3@openturnly",
      title: "Corte clásico en Barbería Central",
      description: "Profesional: Camila\nCódigo de reserva: K7Q4XM\nTeléfono: +57 300 123 4567",
      location: "Sede Norte, Calle 10 # 20-30, Bogotá",
      startsAt: "2026-11-12T15:00:00.000Z",
      endsAt: "2026-11-12T15:45:00.000Z",
    });
    const english = appointmentEvent({
      id: "x",
      code: "K7Q4XM",
      businessName: "Central",
      serviceName: "Haircut",
      resourceName: null,
      // Named like the business: only the address goes in.
      location: { name: "Central", address: "10 Main St" },
      startsAt: "2026-11-12T15:00:00.000Z",
      endsAt: "2026-11-12T15:30:00.000Z",
      language: "en",
    });
    expect(english).toMatchObject({
      title: "Haircut at Central",
      description: "Booking code: K7Q4XM",
      location: "10 Main St",
    });
  });

  it("open Google's and Outlook's new event page filled in", () => {
    const google = parse(googleCalendarUrl(event));
    expect(google.address).toBe("https://calendar.google.com/calendar/render");
    expect(google.params).toEqual({
      action: "TEMPLATE",
      text: "Corte clásico en Barbería Central",
      dates: "20261112T150000Z/20261112T154500Z",
      details: event.description,
      location: event.location,
    });
    const outlook = parse(outlookCalendarUrl(event));
    expect(outlook.address).toBe("https://outlook.live.com/calendar/0/action/compose");
    expect(outlook.params).toMatchObject({
      rru: "addevent",
      subject: "Corte clásico en Barbería Central",
      startdt: "2026-11-12T15:00:00.000Z",
      enddt: "2026-11-12T15:45:00.000Z",
      location: event.location,
    });
  });

  it("write an iCalendar file other calendars open as one event", () => {
    const file = icsFile(event, new Date("2026-11-09T12:00:00Z"));
    expect(file.endsWith("\r\n")).toBe(true);
    const lines = file.split("\r\n");
    expect(lines.slice(0, 6)).toEqual([
      "BEGIN:VCALENDAR",
      "VERSION:2.0",
      "PRODID:-//OpenTurnly//Booking//EN",
      "CALSCALE:GREGORIAN",
      "METHOD:PUBLISH",
      "BEGIN:VEVENT",
    ]);
    // Unfolded, with commas, semicolons and line breaks escaped.
    const unfolded = file.replace(/\r\n /g, "");
    expect(unfolded).toContain("UID:a1b2c3@openturnly\r\n");
    expect(unfolded).toContain("DTSTAMP:20261109T120000Z\r\n");
    expect(unfolded).toContain("DTSTART:20261112T150000Z\r\nDTEND:20261112T154500Z\r\n");
    expect(unfolded).toContain("SUMMARY:Corte clásico en Barbería Central\r\n");
    expect(unfolded).toContain(
      "DESCRIPTION:Profesional: Camila\\nCódigo de reserva: K7Q4XM\\nTeléfono: +57 300 123 4567\r\n",
    );
    expect(unfolded).toContain("LOCATION:Sede Norte\\, Calle 10 # 20-30\\, Bogotá\r\n");
    expect(file.trimEnd().split("\r\n").at(-1)).toBe("END:VCALENDAR");
  });

  it("fold long lines at 75 bytes without splitting a character", () => {
    const long = icsFile({ ...event, title: "Ñ".repeat(60), description: "", location: "" });
    for (const line of long.split("\r\n")) {
      const bytes = [...line].reduce((sum, char) => sum + utf8Length(char), 0);
      expect(bytes).toBeLessThanOrEqual(75);
    }
    expect(long.replace(/\r\n /g, "")).toContain(`SUMMARY:${"Ñ".repeat(60)}\r\n`);
    expect(long).not.toContain("DESCRIPTION");
    expect(long).not.toContain("LOCATION");
  });
});
