import { describe, expect, it } from "vitest";
import { NextRequest } from "next/server";
import { proxy } from "@/proxy";
import { AUTH_HINT_COOKIE_NAME } from "@/shared/config/auth-route-policy";

function makeRequest(path: string, cookieHeader?: string): NextRequest {
  const headers = cookieHeader ? { cookie: cookieHeader } : undefined;
  return new NextRequest(new URL(`https://example.com${path}`), { headers });
}

describe("proxy", () => {
  it("redirects protected route to /signin when auth hint cookie is absent", () => {
    const request = makeRequest("/settings");
    const response = proxy(request);
    const location = response.headers.get("location");

    expect(response.status).toBe(307);
    expect(location).toBeTruthy();

    const redirectUrl = new URL(location!);
    expect(redirectUrl.pathname).toBe("/signin");
    expect(redirectUrl.searchParams.get("next")).toBe("/settings");
  });

  it("preserves query string in next when redirecting to signin", () => {
    const request = makeRequest("/settings?tab=roles&sort=desc");
    const response = proxy(request);
    const location = response.headers.get("location");

    expect(response.status).toBe(307);
    expect(location).toBeTruthy();

    const redirectUrl = new URL(location!);
    expect(redirectUrl.pathname).toBe("/signin");
    expect(redirectUrl.searchParams.get("next")).toBe(
      "/settings?tab=roles&sort=desc"
    );
  });

  it("does not redirect protected route when auth hint cookie is present", () => {
    const request = makeRequest("/settings", `${AUTH_HINT_COOKIE_NAME}=1`);
    const response = proxy(request);

    expect(response.status).toBe(200);
    expect(response.headers.get("x-middleware-next")).toBe("1");
    expect(response.headers.get("location")).toBeNull();
  });
});
