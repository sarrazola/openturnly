import { like } from "drizzle-orm";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { verifications } from "../src/db/schema.ts";
import { resetForgottenPassword } from "../src/lib/password-reset.ts";
import { createTestApp, OWNER, setUpOwner } from "./helpers.ts";

const mail = vi.hoisted(() => ({ sent: [] as Record<string, unknown>[] }));

// Stands in for the mail server: what matters here is what's handed to it.
vi.mock("nodemailer", () => ({
  default: {
    createTransport: () => ({
      verify: async () => true,
      sendMail: async (message: Record<string, unknown>) => {
        mail.sent.push(message);
        return { response: "250 OK" };
      },
      close: () => {},
    }),
  },
}));

const ACCOUNT = {
  host: "smtp.example.com",
  port: 465,
  username: "install",
  password: "install-password",
  fromEmail: "no-reply@example.com",
  footer: null,
};

beforeEach(() => {
  mail.sent.length = 0;
});
afterEach(() => vi.restoreAllMocks());

/** The link's token, from the last email sent. */
async function linkToken() {
  await vi.waitFor(() => expect(mail.sent.length).toBeGreaterThan(0));
  const text = String(mail.sent.at(-1)?.text);
  const match = /http:\/\/localhost:3000\/reset-password\?token=([\w-]+)/.exec(text);
  expect(match).not.toBeNull();
  return match?.[1] as string;
}

async function settle() {
  await new Promise((resolve) => setTimeout(resolve, 50));
}

describe("forgotten passwords, with email", () => {
  it("email a link that sets a new password once and signs out everywhere", async () => {
    const app = await createTestApp({ email: ACCOUNT });
    const owner = await setUpOwner(app);
    expect((await app.client().get("/api/setup")).body).toEqual({
      needsSetup: false,
      passwordReset: "email",
    });

    const asked = await app
      .client()
      .post(
        "/api/auth/request-password-reset",
        { email: OWNER.email },
        { "accept-language": "es-CO,es;q=0.9" },
      );
    expect(asked.status).toBe(200);
    expect(asked.body.status).toBe(true);
    const token = await linkToken();
    expect(mail.sent[0]).toMatchObject({ to: OWNER.email, subject: "Crea una contraseña nueva" });
    expect(String(mail.sent[0]?.text)).toContain("sirve una sola vez durante 1 hora");

    const reset = await app
      .client()
      .post("/api/auth/reset-password", { token, newPassword: "a-brand-new-password" });
    expect(reset.status).toBe(200);
    // Signed out everywhere: the old session no longer works.
    expect((await owner.get("/api/me")).status).toBe(401);
    const old = await app
      .client()
      .post("/api/auth/sign-in/email", { email: OWNER.email, password: OWNER.password });
    expect(old.status).toBe(401);
    const fresh = await app
      .client()
      .post("/api/auth/sign-in/email", { email: OWNER.email, password: "a-brand-new-password" });
    expect(fresh.status).toBe(200);

    // Once only.
    const again = await app
      .client()
      .post("/api/auth/reset-password", { token, newPassword: "yet-another-password" });
    expect(again.status).toBe(400);
  });

  it("writes in English unless the page asked in Spanish", async () => {
    const app = await createTestApp({ email: ACCOUNT });
    await setUpOwner(app);
    await app
      .client()
      .post(
        "/api/auth/request-password-reset",
        { email: OWNER.email },
        { "accept-language": "en-US" },
      );
    await linkToken();
    expect(mail.sent[0]?.subject).toBe("Set a new password");
  });

  it("answers the same for an address without an account, and sends nothing", async () => {
    const app = await createTestApp({ email: ACCOUNT });
    await setUpOwner(app);
    const known = await app
      .client()
      .post("/api/auth/request-password-reset", { email: OWNER.email });
    const unknown = await app
      .client()
      .post("/api/auth/request-password-reset", { email: "nobody@example.com" });
    expect(unknown.status).toBe(known.status);
    expect(unknown.body).toEqual(known.body);
    await settle();
    expect(mail.sent.map((m) => m.to)).toEqual([OWNER.email]);
  });

  it("sends at most three links an hour to one address, without saying so", async () => {
    const app = await createTestApp({ email: ACCOUNT });
    await setUpOwner(app);
    const answers = [];
    for (let i = 0; i < 4; i++) {
      answers.push(
        await app.client().post("/api/auth/request-password-reset", { email: OWNER.email }),
      );
    }
    expect(answers.map((a) => a.status)).toEqual([200, 200, 200, 200]);
    expect(answers[3]?.body).toEqual(answers[0]?.body);
    await settle();
    expect(mail.sent).toHaveLength(3);
  });

  it("refuses a link that has expired", async () => {
    const app = await createTestApp({ email: ACCOUNT });
    await setUpOwner(app);
    await app.client().post("/api/auth/request-password-reset", { email: OWNER.email });
    const token = await linkToken();
    await app.db
      .update(verifications)
      .set({ expiresAt: new Date(Date.now() - 1000) })
      .where(like(verifications.identifier, "reset-password:%"));
    const late = await app
      .client()
      .post("/api/auth/reset-password", { token, newPassword: "a-brand-new-password" });
    expect(late.status).toBe(400);
  });
});

describe("forgotten passwords, without email", () => {
  it("send nothing, and the sign-in page says to ask whoever runs the server", async () => {
    const app = await createTestApp({ email: null });
    await setUpOwner(app);
    expect((await app.client().get("/api/setup")).body.passwordReset).toBe("operator");
    const warn = vi.spyOn(console, "warn").mockImplementation(() => {});
    const asked = await app
      .client()
      .post("/api/auth/request-password-reset", { email: OWNER.email });
    expect(asked.status).toBe(200);
    await settle();
    expect(mail.sent).toHaveLength(0);
    expect(warn).toHaveBeenCalled();
  });

  it("let the server's operator set a temporary password", async () => {
    const app = await createTestApp({ email: null });
    const owner = await setUpOwner(app);
    expect(await resetForgottenPassword(app.auth, app.db, "nobody@example.com")).toBeNull();
    const temporary = await resetForgottenPassword(
      app.auth,
      app.db,
      ` ${OWNER.email.toUpperCase()} `,
    );
    expect(temporary).toMatch(/^[a-z2-9]{4}(-[a-z2-9]{4}){3}$/);
    expect((await owner.get("/api/me")).status).toBe(401);
    const signIn = await app
      .client()
      .post("/api/auth/sign-in/email", { email: OWNER.email, password: temporary });
    expect(signIn.status).toBe(200);
  });
});
