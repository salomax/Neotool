import "server-only";

import { cookies } from "next/headers";
import {
  AUTH_SESSION_COOKIE_NAME,
  decodeAuthSessionContext,
  type AuthSessionContext,
} from "./shared";

export async function getAuthSessionContextFromCookies(): Promise<AuthSessionContext | null> {
  const cookieStore = await cookies();
  const rawSessionCookie = cookieStore.get(AUTH_SESSION_COOKIE_NAME)?.value;
  return decodeAuthSessionContext(rawSessionCookie);
}
