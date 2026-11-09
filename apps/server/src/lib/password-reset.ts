/**
 * Forgotten passwords.
 *
 * With an email account (the install's own or the one in its settings), anyone signs in
 * again on their own: the sign-in page's "Forgot your password?" emails a link that sets a
 * new password, once, within the hour, and signs them out everywhere else. Without one, the
 * person who runs the server sets a temporary password from the command line
 * (src/reset-password.ts), and an admin can still set a new password for anyone on their
 * team from the panel.
 */

import { randomInt } from "node:crypto";
import { eq } from "drizzle-orm";
import type { Auth } from "../auth.ts";
import type { Config } from "../config.ts";
import type { Db } from "../db/client.ts";
import { users } from "../db/schema.ts";
import { resetPassword } from "./accounts.ts";
import { createEmail } from "./email.ts";

/** How long a link to set a new password works. */
export const RESET_LINK_MINUTES = 60;

/**
 * What asking for a link answers, word for word what Better Auth answers, whether or not the
 * address has an account and whether or not its limit was reached.
 */
export const RESET_REQUESTED = {
  status: true,
  message: "If this email exists in our system, check your email for the reset link",
};

type Language = "es" | "en";

/** The email's language: the one the page asked in, Spanish or English. */
export function resetLanguage(request?: Request | null): Language {
  const asked = request?.headers.get("accept-language")?.trim().toLowerCase() ?? "";
  return asked.startsWith("es") ? "es" : "en";
}

/** The email with the link. Plain text: nothing in it is markup. */
export function resetEmail(language: Language, name: string, address: string, link: string) {
  const hello = name.trim();
  if (language === "es") {
    return {
      subject: "Crea una contraseña nueva",
      body: [
        hello ? `Hola, ${hello}:` : "Hola:",
        `Alguien pidió una contraseña nueva para tu cuenta de OpenTurnly (${address}). Para crearla, abre este enlace:`,
        link,
        "El enlace sirve una sola vez durante 1 hora. Si no fuiste tú, ignora este correo: tu contraseña sigue igual.",
      ].join("\n\n"),
    };
  }
  return {
    subject: "Set a new password",
    body: [
      hello ? `Hi ${hello},` : "Hi,",
      `Someone asked for a new password for your OpenTurnly account (${address}). To set it, open this link:`,
      link,
      "The link works once, for 1 hour. If it wasn't you, ignore this email: your password stays the same.",
    ].join("\n\n"),
  };
}

/**
 * What Better Auth calls when someone asks for the link. It sends in the background, so the
 * answer takes the same time whether or not the address has an account; a problem sending
 * is logged and never shown.
 */
export function passwordResetSender({ db, config }: { db: Db; config: Config }) {
  const email = createEmail({ db, config });
  const deliver = async (
    user: { name: string; email: string },
    token: string,
    language: Language,
  ) => {
    const sender = await email.sender();
    if (!sender) {
      console.warn("password reset: this install has no email account to send the link");
      return;
    }
    const link = `${config.appUrl}/reset-password?token=${encodeURIComponent(token)}`;
    const message = resetEmail(language, user.name, user.email, link);
    const sent = await email.send(sender, { to: user.email, fromName: "OpenTurnly", ...message });
    if (!sent.ok) console.warn("password reset: the email was not sent:", sent.error);
  };

  return async (
    { user, token }: { user: { name: string; email: string }; token: string },
    request?: Request,
  ) => {
    const language = resetLanguage(request);
    void deliver(user, token, language).catch((error) =>
      console.error("password reset: could not send the link", error),
    );
  };
}

/** Easy to read out and to type: no 0/O, 1/l/I. Four groups of four. */
const ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789";

export function temporaryPassword() {
  const group = () =>
    Array.from({ length: 4 }, () => ALPHABET[randomInt(ALPHABET.length)]).join("");
  return [group(), group(), group(), group()].join("-");
}

/**
 * Gives an account a temporary password and signs it out everywhere: for the server's
 * operator, when the person can't get the email. Returns the password to hand over.
 */
export async function resetForgottenPassword(auth: Auth, db: Db, address: string) {
  const [user] = await db
    .select({ id: users.id })
    .from(users)
    .where(eq(users.email, address.trim().toLowerCase()));
  if (!user) return null;
  const password = temporaryPassword();
  await resetPassword(auth, db, user.id, password);
  return password;
}
