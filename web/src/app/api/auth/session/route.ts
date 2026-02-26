import { NextRequest, NextResponse } from "next/server";
import {
  AUTH_SESSION_COOKIE_NAME,
  AUTH_SESSION_PERSISTENT_MAX_AGE_SECONDS,
  buildAuthSessionContext,
  encodeAuthSessionContext,
  type AuthSessionUser,
} from "@/shared/utils/auth-session/shared";

function buildClearCookieResponse() {
  const response = NextResponse.json({ ok: true });
  response.cookies.set({
    name: AUTH_SESSION_COOKIE_NAME,
    value: "",
    path: "/",
    maxAge: 0,
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "lax",
  });
  return response;
}

export async function POST(request: NextRequest) {
  try {
    const body = (await request.json()) as { user?: AuthSessionUser | null; rememberMe?: boolean };
    const sessionContext = buildAuthSessionContext(body?.user);

    if (!sessionContext) {
      return buildClearCookieResponse();
    }

    const response = NextResponse.json({ ok: true });
    response.cookies.set({
      name: AUTH_SESSION_COOKIE_NAME,
      value: encodeAuthSessionContext(sessionContext),
      path: "/",
      httpOnly: true,
      secure: process.env.NODE_ENV === "production",
      sameSite: "lax",
      ...(body?.rememberMe ? { maxAge: AUTH_SESSION_PERSISTENT_MAX_AGE_SECONDS } : {}),
    });

    return response;
  } catch {
    return NextResponse.json(
      {
        ok: false,
        error: "Invalid session payload",
      },
      { status: 400 }
    );
  }
}

export async function DELETE() {
  return buildClearCookieResponse();
}
