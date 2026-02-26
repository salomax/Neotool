import type { UnleashEvaluationContext } from "@/shared/types/unleash";

export const AUTH_SESSION_COOKIE_NAME = "nt_auth_session";
export const AUTH_SESSION_PERSISTENT_MAX_AGE_SECONDS = 60 * 60 * 24 * 30;

type AuthSessionRole = { name: string };

export type AuthSessionUser = {
  id?: string | null;
  tenantId?: string | null;
  role?: string | null;
  roles?: AuthSessionRole[] | null;
  plan?: string | null;
};

export type AuthSessionContext = Pick<
  UnleashEvaluationContext,
  "userId" | "tenantId" | "role" | "plan"
>;

export function buildAuthSessionContext(
  user: AuthSessionUser | null | undefined
): AuthSessionContext | null {
  if (!user?.id) {
    return null;
  }

  const firstRole = Array.isArray(user.roles) && user.roles.length > 0
    ? user.roles[0]?.name
    : undefined;

  return {
    userId: user.id,
    ...(user.tenantId ? { tenantId: user.tenantId } : {}),
    ...(user.role || firstRole ? { role: user.role || firstRole } : {}),
    ...(user.plan ? { plan: user.plan } : {}),
  };
}

export function buildUnleashEvaluationContext(
  sessionContext: AuthSessionContext | null | undefined
): UnleashEvaluationContext {
  const environment = process.env.NEXT_PUBLIC_ENV || "production";

  if (!sessionContext) {
    return { environment };
  }

  return {
    ...sessionContext,
    environment,
  };
}

export function encodeAuthSessionContext(context: AuthSessionContext): string {
  return encodeURIComponent(JSON.stringify(context));
}

export function decodeAuthSessionContext(
  rawValue: string | null | undefined
): AuthSessionContext | null {
  if (!rawValue) {
    return null;
  }

  try {
    const decoded = decodeURIComponent(rawValue);
    const parsed = JSON.parse(decoded);

    if (!parsed || typeof parsed !== "object" || typeof parsed.userId !== "string") {
      return null;
    }

    return {
      userId: parsed.userId,
      ...(typeof parsed.tenantId === "string" ? { tenantId: parsed.tenantId } : {}),
      ...(typeof parsed.role === "string" ? { role: parsed.role } : {}),
      ...(typeof parsed.plan === "string" ? { plan: parsed.plan } : {}),
    };
  } catch {
    return null;
  }
}
