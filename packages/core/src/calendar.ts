/**
 * An appointment as an event in the customer's own calendar: the links that open Google's
 * and Outlook's "new event" page already filled in, and the iCalendar file (RFC 5545) that
 * Apple's calendar and the others open as an event ready to save.
 */

export type CalendarEvent = {
  /** The same for every copy of one appointment, so adding it again updates the event. */
  uid: string;
  title: string;
  description: string;
  location: string;
  startsAt: Date | string;
  endsAt: Date | string;
};

const TEXT = {
  es: {
    title: (service: string, business: string) => `${service} en ${business}`,
    professional: "Profesional",
    code: "Código de reserva",
    phone: "Teléfono",
  },
  en: {
    title: (service: string, business: string) => `${service} at ${business}`,
    professional: "Professional",
    code: "Booking code",
    phone: "Phone",
  },
} as const;

/** What the customer's calendar shows for one appointment, in the business's language. */
export function appointmentEvent(input: {
  id: string;
  code: string;
  businessName: string;
  businessPhone?: string | null;
  serviceName: string;
  resourceName: string | null;
  location: { name: string | null; address: string | null } | null;
  startsAt: Date | string;
  endsAt: Date | string;
  language: string;
}): CalendarEvent {
  const text = TEXT[input.language === "en" ? "en" : "es"];
  // A location named like the business adds nothing next to its address.
  const place = input.location?.name === input.businessName ? null : input.location?.name;
  return {
    uid: `${input.id}@openturnly`,
    title: text.title(input.serviceName, input.businessName),
    description: [
      input.resourceName ? `${text.professional}: ${input.resourceName}` : null,
      `${text.code}: ${input.code}`,
      input.businessPhone ? `${text.phone}: ${input.businessPhone}` : null,
    ]
      .filter(Boolean)
      .join("\n"),
    location: [place, input.location?.address].filter(Boolean).join(", "),
    startsAt: input.startsAt,
    endsAt: input.endsAt,
  };
}

/** "20261109T150000Z": a moment in UTC, the way calendars write it. */
function utcStamp(value: Date | string) {
  return new Date(value)
    .toISOString()
    .replace(/[-:]/g, "")
    .replace(/\.\d{3}/, "");
}

function query(params: Record<string, string>) {
  return Object.entries(params)
    .map(([name, value]) => `${encodeURIComponent(name)}=${encodeURIComponent(value)}`)
    .join("&");
}

/** Google Calendar's "new event" page with everything filled in. */
export function googleCalendarUrl(event: CalendarEvent) {
  return `https://calendar.google.com/calendar/render?${query({
    action: "TEMPLATE",
    text: event.title,
    dates: `${utcStamp(event.startsAt)}/${utcStamp(event.endsAt)}`,
    details: event.description,
    location: event.location,
  })}`;
}

/** Outlook's "new event" page (outlook.com and Hotmail accounts) with everything filled in. */
export function outlookCalendarUrl(event: CalendarEvent) {
  return `https://outlook.live.com/calendar/0/action/compose?${query({
    path: "/calendar/action/compose",
    rru: "addevent",
    subject: event.title,
    startdt: new Date(event.startsAt).toISOString(),
    enddt: new Date(event.endsAt).toISOString(),
    body: event.description,
    location: event.location,
  })}`;
}

/** A text value as iCalendar wants it: backslashes, commas, semicolons and line breaks escaped. */
function icsText(value: string) {
  return value
    .replace(/\\/g, "\\\\")
    .replace(/;/g, "\\;")
    .replace(/,/g, "\\,")
    .replace(/\r?\n/g, "\\n");
}

/** How many bytes a character takes in UTF-8. */
export function utf8Length(char: string) {
  const code = char.codePointAt(0) ?? 0;
  return code < 0x80 ? 1 : code < 0x800 ? 2 : code < 0x10000 ? 3 : 4;
}

/** Lines longer than 75 bytes continue on the next one after a space, never mid-character. */
function fold(line: string) {
  const parts: string[] = [];
  let current = "";
  let size = 0;
  for (const char of line) {
    const bytes = utf8Length(char);
    // The first line takes 75 bytes; the ones after it start with a space.
    if (size + bytes > (parts.length === 0 ? 75 : 74)) {
      parts.push(current);
      current = "";
      size = 0;
    }
    current += char;
    size += bytes;
  }
  parts.push(current);
  return parts.join("\r\n ");
}

/** The event as an iCalendar file: what Apple's calendar, Thunderbird and others open. */
export function icsFile(event: CalendarEvent, now: Date = new Date()) {
  const lines = [
    "BEGIN:VCALENDAR",
    "VERSION:2.0",
    "PRODID:-//OpenTurnly//Booking//EN",
    "CALSCALE:GREGORIAN",
    "METHOD:PUBLISH",
    "BEGIN:VEVENT",
    `UID:${event.uid}`,
    `DTSTAMP:${utcStamp(now)}`,
    `DTSTART:${utcStamp(event.startsAt)}`,
    `DTEND:${utcStamp(event.endsAt)}`,
    `SUMMARY:${icsText(event.title)}`,
    ...(event.description ? [`DESCRIPTION:${icsText(event.description)}`] : []),
    ...(event.location ? [`LOCATION:${icsText(event.location)}`] : []),
    "STATUS:CONFIRMED",
    "END:VEVENT",
    "END:VCALENDAR",
  ];
  return `${lines.map(fold).join("\r\n")}\r\n`;
}
