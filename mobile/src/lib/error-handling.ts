import type { TFunction } from 'i18next';

export type AppErrorCategory = 'network' | 'auth' | 'business-validation' | 'unknown';

export type AppFieldError = 'name' | 'email' | 'password' | 'confirmPassword';

type GraphQLErrorEntry = {
  message?: string;
  extensions?: {
    code?: string;
    parameters?: Record<string, unknown>;
  };
};

export type AppErrorDescriptor = {
  category: AppErrorCategory;
  code?: string;
  messageKey: string;
  fallbackMessage: string;
  fieldErrors?: Partial<Record<AppFieldError, string>>;
};

const AUTH_ERROR_CODES = new Set([
  'AUTH_AUTHENTICATION_REQUIRED',
  'AUTH_AUTHORIZATION_DENIED',
  'AUTH_INSUFFICIENT_PERMISSIONS',
  'AUTH_INVALID_CREDENTIALS',
  'AUTH_INVALID_OR_EXPIRED_ACCESS_TOKEN',
  'AUTH_RESOURCE_ACCESS_DENIED',
  'AUTH_SESSION_EXPIRED',
  'AUTH_TOKEN_EXPIRED',
  'AUTH_TOKEN_INVALID',
  'UNAUTHENTICATED',
]);

const AUTH_ERROR_MESSAGE_PATTERNS = [
  'auth_session_expired',
  'auth_invalid_credentials',
  'auth_invalid_or_expired_access_token',
  'unauthenticated',
  'authentication required',
  'invalid authentication token',
  'session expired',
];

const BUSINESS_VALIDATION_FIELD_MAP: Partial<
  Record<string, Partial<Record<AppFieldError, string>>>
> = {
  USER_EMAIL_ALREADY_EXISTS: { email: 'errors.codes.USER_EMAIL_ALREADY_EXISTS' },
  USER_EMAIL_INVALID: { email: 'errors.codes.USER_EMAIL_INVALID' },
  USER_EMAIL_REQUIRED: { email: 'errors.codes.USER_EMAIL_REQUIRED' },
  USER_PASSWORD_REQUIRED: { password: 'errors.codes.USER_PASSWORD_REQUIRED' },
  USER_PASSWORD_TOO_SHORT: { password: 'errors.codes.USER_PASSWORD_TOO_SHORT' },
  USER_PASSWORD_TOO_WEAK: { password: 'errors.codes.USER_PASSWORD_TOO_WEAK' },
};

function appendGraphQLErrors(target: GraphQLErrorEntry[], errors: unknown): void {
  if (!Array.isArray(errors)) {
    return;
  }

  for (const error of errors) {
    if (error && typeof error === 'object') {
      target.push(error as GraphQLErrorEntry);
    }
  }
}

function collectGraphQLErrors(error: unknown): GraphQLErrorEntry[] {
  if (!error || typeof error !== 'object') {
    return [];
  }

  const errRecord = error as Record<string, unknown>;
  const collected: GraphQLErrorEntry[] = [];

  appendGraphQLErrors(collected, errRecord.graphQLErrors);
  appendGraphQLErrors(collected, errRecord.errors);
  appendGraphQLErrors(collected, (errRecord.response as { errors?: unknown } | undefined)?.errors);
  appendGraphQLErrors(
    collected,
    (errRecord.networkError as { result?: { errors?: unknown } } | undefined)?.result?.errors
  );

  if (errRecord.cause && errRecord.cause !== error) {
    appendGraphQLErrors(collected, collectGraphQLErrors(errRecord.cause));
  }

  return collected;
}

function extractCodeFromErrors(errors: GraphQLErrorEntry[]): string | undefined {
  for (const error of errors) {
    const code = error.extensions?.code;
    if (typeof code === 'string' && code.trim().length > 0) {
      return code;
    }
  }

  return undefined;
}

function isConnectionError(message: string | null | undefined): boolean {
  if (!message) {
    return false;
  }

  const lowerMessage = message.toLowerCase();

  return [
    'failed to fetch',
    'network request failed',
    'network error',
    'timeout',
    'timed out',
    'http_request_failed',
    'subrequest_http_error',
    'fetch failed',
    'load failed',
    'connection',
    'offline',
  ].some((pattern) => lowerMessage.includes(pattern));
}

export function extractErrorCode(error: unknown): string | undefined {
  return extractCodeFromErrors(collectGraphQLErrors(error));
}

export function classifyAppError(error: unknown): AppErrorDescriptor {
  const graphQLErrors = collectGraphQLErrors(error);
  const errorCode = extractCodeFromErrors(graphQLErrors);
  const errorMessage = error instanceof Error ? error.message : '';
  const normalizedMessage = errorMessage.trim().toLowerCase();

  if (errorCode && AUTH_ERROR_CODES.has(errorCode)) {
    return {
      category: 'auth',
      code: errorCode,
      messageKey: `errors.codes.${errorCode}`,
      fallbackMessage: 'Authentication failed.',
    };
  }

  if (AUTH_ERROR_MESSAGE_PATTERNS.some((pattern) => normalizedMessage.includes(pattern))) {
    return {
      category: 'auth',
      code: errorCode,
      messageKey: errorCode ? `errors.codes.${errorCode}` : 'errors.codes.AUTH_SESSION_EXPIRED',
      fallbackMessage: 'Authentication failed.',
    };
  }

  if (errorCode && BUSINESS_VALIDATION_FIELD_MAP[errorCode]) {
    return {
      category: 'business-validation',
      code: errorCode,
      messageKey: `errors.codes.${errorCode}`,
      fallbackMessage: 'Validation failed.',
      fieldErrors: BUSINESS_VALIDATION_FIELD_MAP[errorCode],
    };
  }

  if (errorCode === 'GRAPHQL_VALIDATION_FAILED') {
    return {
      category: 'business-validation',
      code: errorCode,
      messageKey: `errors.codes.${errorCode}`,
      fallbackMessage: 'We could not validate this request.',
    };
  }

  if (isConnectionError(errorMessage) || errorCode === 'HTTP_REQUEST_FAILED') {
    return {
      category: 'network',
      code: errorCode,
      messageKey: 'errors.network',
      fallbackMessage: 'Network error. Please check your connection.',
    };
  }

  return {
    category: 'unknown',
    code: errorCode,
    messageKey: errorCode ? `errors.codes.${errorCode}` : 'errors.generic',
    fallbackMessage: 'Something went wrong. Please try again.',
  };
}

export function translateAppError(
  error: unknown,
  t: TFunction
): AppErrorDescriptor & { message: string } {
  const descriptor = classifyAppError(error);

  return {
    ...descriptor,
    message: t(descriptor.messageKey, descriptor.fallbackMessage),
  };
}

export function isRetryableAppError(error: unknown): boolean {
  const descriptor = classifyAppError(error);
  return descriptor.category === 'network';
}
