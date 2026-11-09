import { describe, expect, it } from "vitest";
import { createBusiness, createTestApp, OWNER, setUpOwner } from "./helpers.ts";

const APP_URL = "http://localhost:3000";

describe("phone app sign-in", () => {
  it("gives the app a token it sends as Bearer, with no cookie and no origin", async () => {
    const app = await createTestApp();
    const owner = await setUpOwner(app);
    await createBusiness(owner);

    const signIn = await app.client().post("/api/mobile/sign-in", {
      email: OWNER.email,
      password: OWNER.password,
    });
    expect(signIn.status).toBe(200);
    expect(signIn.body.user).toMatchObject({ email: OWNER.email, role: "owner" });
    const token = signIn.body.token as string;
    expect(token).toBeTruthy();

    // Like a phone: only the token, no cookie and no browser origin.
    const call = (method: string, path: string, body?: unknown) =>
      app.app.request(`${APP_URL}${path}`, {
        method,
        headers: {
          authorization: `Bearer ${token}`,
          ...(body ? { "content-type": "application/json" } : {}),
        },
        body: body ? JSON.stringify(body) : undefined,
      });
    const me = await call("GET", "/api/me");
    expect(me.status).toBe(200);
    expect(((await me.json()) as { user: { email: string } }).user.email).toBe(OWNER.email);

    const list = await call("GET", "/api/businesses");
    expect(list.status).toBe(200);
    const created = await call("POST", "/api/businesses", {
      name: "Sede Norte",
      slug: "sede-norte",
      timezone: "America/Bogota",
      currency: "COP",
      locale: "es",
    });
    expect(created.status).toBe(201);

    const out = await call("POST", "/api/mobile/sign-out");
    expect(out.status).toBe(200);
    expect((await call("GET", "/api/me")).status).toBe(401);
  });

  it("refuses a wrong password without saying which part was wrong", async () => {
    const app = await createTestApp();
    await setUpOwner(app);
    const wrong = await app.client().post("/api/mobile/sign-in", {
      email: OWNER.email,
      password: "not-the-password",
    });
    expect(wrong.status).toBe(401);
    expect(wrong.body.error.code).toBe("invalid_credentials");
    const nobody = await app.client().post("/api/mobile/sign-in", {
      email: "nobody@example.com",
      password: OWNER.password,
    });
    expect(nobody.body.error.code).toBe("invalid_credentials");
  });

  it("doesn't accept a made-up token", async () => {
    const app = await createTestApp();
    await setUpOwner(app);
    const res = await app.app.request(`${APP_URL}/api/me`, {
      headers: { authorization: "Bearer made-up" },
    });
    expect(res.status).toBe(401);
  });
});
