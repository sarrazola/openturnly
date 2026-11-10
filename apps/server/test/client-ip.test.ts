import { Hono } from "hono";
import { describe, expect, it } from "vitest";
import {
  CLIENT_IP_HEADER,
  isInternalAddress,
  networkKey,
  withClientIp,
} from "../src/lib/client-ip.ts";

async function ipSeenByAuth(trustProxy: boolean, headers: Record<string, string>) {
  const app = new Hono().all("*", (c) =>
    c.text(withClientIp(c, trustProxy).headers.get(CLIENT_IP_HEADER) ?? "none"),
  );
  const res = await app.request("/", { headers });
  return res.text();
}

describe("client IP for rate limiting", () => {
  it("drops a client IP header sent by the client", async () => {
    expect(await ipSeenByAuth(false, { [CLIENT_IP_HEADER]: "6.6.6.6" })).toBe("none");
  });

  it("ignores X-Forwarded-For unless a proxy is trusted", async () => {
    expect(await ipSeenByAuth(false, { "x-forwarded-for": "6.6.6.6" })).toBe("none");
  });

  it("uses the entry appended by the trusted proxy", async () => {
    const ip = await ipSeenByAuth(true, { "x-forwarded-for": "6.6.6.6, 203.0.113.7" });
    expect(ip).toBe("203.0.113.7");
  });
});

describe("internal addresses", () => {
  it("see an IPv4 address inside IPv6 however it's written", () => {
    // Loopback, the cloud metadata address and a home network, each spelled three ways.
    for (const address of [
      "127.0.0.1",
      "::ffff:127.0.0.1",
      "::ffff:7f00:1",
      "0:0:0:0:0:ffff:7f00:1",
      "::FFFF:7F00:1",
      "169.254.169.254",
      "::ffff:a9fe:a9fe",
      "192.168.1.1",
      "::ffff:c0a8:101",
      "::ffff:a00:1",
      "::1",
    ]) {
      expect(isInternalAddress(address), address).toBe(true);
    }
    for (const address of ["8.8.8.8", "::ffff:808:808", "::ffff:8.8.8.8", "2001:4860::8888"]) {
      expect(isInternalAddress(address), address).toBe(false);
    }
  });

  it("are what a URL resolves to when it writes the address in brackets", () => {
    const host = new URL("http://[::ffff:169.254.169.254]/").hostname.slice(1, -1);
    expect(host).toBe("::ffff:a9fe:a9fe");
    expect(isInternalAddress(host)).toBe(true);
  });

  it("limit a mapped address as the IPv4 network it is", () => {
    expect(networkKey("::ffff:cb00:7107")).toBe("203.0.113.7");
    expect(networkKey("::ffff:7f00:1")).toBeNull();
  });
});
