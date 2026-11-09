import { describe, expect, it } from "vitest";
import { SMS_TEMPLATES, smsSegments, smsText } from "../src/sms.ts";

describe("smsText", () => {
  it("swaps the letters a plain text can't carry, and keeps the ones it can", () => {
    expect(smsText("Código: sábado, miércoles, mañana, pingüino")).toBe(
      "Codigo: sabado, miércoles, mañana, pingüino",
    );
    expect(smsText("“Hola” — ¿qué tal…?")).toBe('"Hola" - ¿qué tal...?');
  });

  it("leaves what has no close plain letter", () => {
    expect(smsText("Te esperamos 💈")).toBe("Te esperamos 💈");
  });
});

describe("smsSegments", () => {
  it("fits 160 plain characters in one part, and 153 per part after that", () => {
    expect(smsSegments("a".repeat(160))).toEqual({ segments: 1, characters: 160, unicode: false });
    expect(smsSegments("a".repeat(161)).segments).toBe(2);
    expect(smsSegments("a".repeat(306)).segments).toBe(2);
    expect(smsSegments("a".repeat(307)).segments).toBe(3);
  });

  it("counts the extended characters twice", () => {
    expect(smsSegments(`${"a".repeat(158)}€`)).toMatchObject({ segments: 1, characters: 160 });
    expect(smsSegments(`${"a".repeat(159)}€`).segments).toBe(2);
  });

  it("drops to 70 per part when anything needs the wider alphabet", () => {
    expect(smsSegments(`${"a".repeat(68)}💈`)).toEqual({
      segments: 1,
      characters: 70,
      unicode: true,
    });
    expect(smsSegments(`${"a".repeat(69)}💈`).segments).toBe(2);
  });

  it("keeps the ready-made texts in one part once filled with ordinary data", () => {
    const values: Record<string, string> = {
      "customer.first_name": "Alejandra",
      "business.name": "Barbería Central del Norte",
      "appointment.date_text": "miércoles 18 de noviembre",
      "appointment.time_text": "10:30 a. m.",
      "appointment.code": "7K2M9P",
    };
    for (const template of SMS_TEMPLATES) {
      for (const text of Object.values(template.body)) {
        const filled = text.replace(/\{\{([\w.]+)\}\}/g, (_, field: string) => values[field] ?? "");
        const parts = smsSegments(smsText(filled));
        expect(parts.unicode, filled).toBe(false);
        expect(parts.segments, filled).toBe(1);
      }
    }
  });
});
