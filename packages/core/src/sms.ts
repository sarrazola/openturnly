import type { EmailTemplateName } from "./email.ts";
import type { WhatsappLanguage } from "./whatsapp.ts";

/** The same moments as the ready-made emails, in a few words each. */
export const SMS_TEMPLATE_NAMES = [
  "appointment_confirmation",
  "appointment_reminder",
  "appointment_rescheduled",
  "appointment_cancelled",
] as const satisfies readonly EmailTemplateName[];
export type SmsTemplateName = (typeof SMS_TEMPLATE_NAMES)[number];

export type SmsTemplateDefinition = {
  name: SmsTemplateName;
  body: Record<WhatsappLanguage, string>;
};

const WHEN = "{{appointment.date_text}}, {{appointment.time_text}}";
const CODE = { es: "Código: {{appointment.code}}", en: "Code: {{appointment.code}}" };

/**
 * Short enough for one message with an ordinary name and date: a text message is charged
 * by the part, and the customer only needs the when and the code. The code goes on a line of
 * its own: a time can end in a period ("10:00 a. m.").
 */
export const SMS_TEMPLATES: readonly SmsTemplateDefinition[] = [
  {
    name: "appointment_confirmation",
    body: {
      es: `Hola {{customer.first_name}}, tu cita en {{business.name}} quedó agendada: ${WHEN}\n${CODE.es}`,
      en: `Hi {{customer.first_name}}, your appointment at {{business.name}} is booked: ${WHEN}\n${CODE.en}`,
    },
  },
  {
    name: "appointment_reminder",
    body: {
      es: `Hola {{customer.first_name}}, te recordamos tu cita en {{business.name}}: ${WHEN}\n${CODE.es}`,
      en: `Hi {{customer.first_name}}, a reminder of your appointment at {{business.name}}: ${WHEN}\n${CODE.en}`,
    },
  },
  {
    name: "appointment_rescheduled",
    body: {
      es: `Hola {{customer.first_name}}, tu cita en {{business.name}} cambió a ${WHEN}\n${CODE.es}`,
      en: `Hi {{customer.first_name}}, your appointment at {{business.name}} moved to ${WHEN}\n${CODE.en}`,
    },
  },
  {
    name: "appointment_cancelled",
    body: {
      es: `Hola {{customer.first_name}}, tu cita en {{business.name}} del ${WHEN} fue cancelada.\n${CODE.es}`,
      en: `Hi {{customer.first_name}}, your appointment at {{business.name}} on ${WHEN} was cancelled.\n${CODE.en}`,
    },
  },
];

export function smsTemplate(name: SmsTemplateName): SmsTemplateDefinition | undefined {
  return SMS_TEMPLATES.find((template) => template.name === name);
}

/** The characters a plain text message carries, each counted once. */
const GSM_BASIC =
  "@£$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà";
/** Also carried, but each one counts as two. */
const GSM_EXTENDED = "^{}\\[~]|€\f";
const GSM = new Set([...GSM_BASIC, ...GSM_EXTENDED]);

/**
 * Letters a plain text message can't carry, swapped for close ones it can. One of them
 * anywhere would send the whole message in the wider alphabet, at less than half the
 * characters per part: "Código" alone would double what a short message costs.
 */
const PLAIN: Record<string, string> = {
  á: "a",
  í: "i",
  ó: "o",
  ú: "u",
  Á: "A",
  Í: "I",
  Ó: "O",
  Ú: "U",
  â: "a",
  ê: "e",
  î: "i",
  ô: "o",
  û: "u",
  ã: "a",
  õ: "o",
  ç: "c",
  ë: "e",
  ï: "i",
  "‘": "'",
  "’": "'",
  "“": '"',
  "”": '"',
  "–": "-",
  "—": "-",
  "…": "...",
  " ": " ",
  " ": " ",
};

/** The text as it goes out: what has a close plain letter gets it; anything else stays. */
export function smsText(text: string): string {
  return [...text].map((char) => (GSM.has(char) ? char : (PLAIN[char] ?? char))).join("");
}

/** How many parts a message is sent in, and so charged for, and its alphabet. */
export function smsSegments(text: string): {
  segments: number;
  characters: number;
  unicode: boolean;
} {
  const chars = [...text];
  const unicode = chars.some((char) => !GSM.has(char));
  if (unicode) {
    // Counted in UTF-16 units: an emoji takes two.
    const units = text.length;
    return { segments: units <= 70 ? 1 : Math.ceil(units / 67), characters: units, unicode };
  }
  const units = chars.reduce((total, char) => total + (GSM_EXTENDED.includes(char) ? 2 : 1), 0);
  return { segments: units <= 160 ? 1 : Math.ceil(units / 153), characters: units, unicode };
}
