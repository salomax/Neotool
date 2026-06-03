/**
 * Shared error handling utilities
 */

import i18n from '@/shared/i18n/config';

/**
 * Removes technical error prefixes from error messages.
 * Apollo Client and other GraphQL clients often prefix errors with technical information
 * like "Exception while fetching data (operationName) :" which should not be shown to users.
 * 
 * @param message - The error message to clean
 * @returns The cleaned error message without technical prefixes
 * 
 * @example
 * cleanErrorMessage("Exception while fetching data (/signIn) : Invalid email or password")
 * // Returns: "Invalid email or password"
 */
export function cleanErrorMessage(message: string | null | undefined): string {
  if (!message) return '';
  
  // Remove "Exception while fetching data" prefix and variations
  // Matches patterns like:
  // - "Exception while fetching data (/signIn) : message"
  // - "Exception while fetching data (/signIn): message"
  // - "Exception while fetching data: message"
  const cleaned = message.replace(
    /^Exception\s+while\s+fetching\s+data\s*(\([^)]+\))?\s*:\s*/i,
    ''
  ).trim();
  
  return cleaned || message;
}

/**
 * Gets a translated error message from i18n, with fallback to English if i18n is not initialized.
 * 
 * @param key - The translation key (e.g., "errors.connectionError")
 * @param fallback - Fallback message if translation is not available
 * @returns Translated error message
 */
function getTranslatedError(key: string, fallback: string): string {
  try {
    if (i18n.isInitialized) {
      const translated = i18n.t(key, { ns: 'common' });
      // If translation returns the key itself, it means the key wasn't found
      return translated !== key ? translated : fallback;
    }
  } catch (error) {
    // If i18n is not initialized or fails, use fallback
  }
  return fallback;
}

type GraphQLErrorEntry = {
  message?: string;
  extensions?: {
    code?: string;
    parameters?: Record<string, unknown>;
  };
};

function isServerErrorLike(
  error: unknown
): error is { bodyText: string; message?: string; statusCode?: number } {
  return (
    typeof error === 'object' &&
    error !== null &&
    'bodyText' in error &&
    typeof (error as { bodyText?: unknown }).bodyText === 'string'
  );
}

function parseGraphQLErrorsFromBodyText(bodyText: string | undefined): GraphQLErrorEntry[] {
  if (!bodyText) return [];

  try {
    const parsed = JSON.parse(bodyText) as { errors?: unknown };
    if (!Array.isArray(parsed.errors)) return [];
    return parsed.errors.filter(
      (entry): entry is GraphQLErrorEntry => !!entry && typeof entry === 'object'
    );
  } catch {
    return [];
  }
}

function appendGraphQLErrors(target: GraphQLErrorEntry[], errors: unknown): void {
  if (!Array.isArray(errors)) return;

  for (const error of errors) {
    if (error && typeof error === 'object') {
      target.push(error as GraphQLErrorEntry);
    }
  }
}

/**
 * Collects GraphQL errors from Apollo Client 3/4 error shapes, including
 * ServerError.bodyText payloads returned on non-200 HTTP responses.
 */
function collectGraphQLErrors(error: unknown): GraphQLErrorEntry[] {
  if (!error || typeof error !== 'object') return [];

  const err = error as Record<string, unknown>;
  const collected: GraphQLErrorEntry[] = [];

  appendGraphQLErrors(collected, err.graphQLErrors);
  appendGraphQLErrors(collected, err.errors);
  appendGraphQLErrors(collected, (err.response as { errors?: unknown } | undefined)?.errors);
  appendGraphQLErrors(
    collected,
    (err.networkError as { result?: { errors?: unknown } } | undefined)?.result?.errors
  );

  if (isServerErrorLike(err)) {
    appendGraphQLErrors(collected, parseGraphQLErrorsFromBodyText(err.bodyText));
  }

  const networkError = err.networkError;
  if (networkError && typeof networkError === 'object') {
    const networkErr = networkError as Record<string, unknown>;
    appendGraphQLErrors(collected, networkErr.graphQLErrors);
    appendGraphQLErrors(
      collected,
      (networkErr.result as { errors?: unknown } | undefined)?.errors
    );
    if (isServerErrorLike(networkErr)) {
      appendGraphQLErrors(collected, parseGraphQLErrorsFromBodyText(networkErr.bodyText));
    }
  }

  if (err.cause && err.cause !== error) {
    appendGraphQLErrors(collected, collectGraphQLErrors(err.cause));
  }

  return collected;
}

function extractCodeFromErrorArray(errors: unknown): string | undefined {
  if (!Array.isArray(errors)) return undefined;

  for (const error of errors) {
    const code = (error as GraphQLErrorEntry)?.extensions?.code;
    if (typeof code === 'string' && code.trim().length > 0) {
      return code;
    }
  }

  return undefined;
}

function extractParametersFromErrorArray(errors: unknown): Record<string, unknown> | undefined {
  if (!Array.isArray(errors)) return undefined;

  for (const error of errors) {
    const parameters = (error as GraphQLErrorEntry)?.extensions?.parameters;
    if (parameters && typeof parameters === 'object' && !Array.isArray(parameters)) {
      return parameters as Record<string, unknown>;
    }
  }

  return undefined;
}

function hasGraphQLErrorShape(error: unknown): boolean {
  return collectGraphQLErrors(error).length > 0;
}

function isGenericHttpStatusMessage(message: string | null | undefined): boolean {
  if (!message) return false;
  return /^Response not successful: Received status code \d{3}$/i.test(message.trim());
}

function isTechnicalGraphQLErrorMessage(message: string | null | undefined): boolean {
  if (!message) return false;

  return (
    /^Cannot query field /i.test(message) ||
    /^Field ".+" of type /i.test(message) ||
    /^Unknown (argument|type|field)/i.test(message)
  );
}

function translateByErrorCode(
  code: string,
  parameters: Record<string, unknown> | undefined,
  fallback: string
): string | undefined {
  const normalizedCode = normalizeErrorCode(code);
  const translationKey = `errors.${normalizedCode}`;

  try {
    if (i18n.isInitialized) {
      const translated = i18n.t(translationKey, { ns: 'common', ...(parameters ?? {}) });
      if (translated !== translationKey) {
        return translated;
      }
    }
  } catch {
    // Ignore translation failures and fall back below.
  }

  return fallback;
}

const LEGACY_CODE_ALIASES: Record<string, string> = {
  INVALID_CREDENTIALS: 'AUTH_INVALID_CREDENTIALS',
};

function normalizeErrorCode(code: string): string {
  return LEGACY_CODE_ALIASES[code] ?? code;
}

/**
 * Extracts GraphQL error code from Apollo/GraphQL error objects.
 *
 * @param error - The error object from Apollo Client or generic error
 * @returns GraphQL error code (e.g. "INVALID_CREDENTIALS"), if present
 */
export function extractErrorCode(error: unknown): string | undefined {
  const graphqlErrors = collectGraphQLErrors(error);
  return extractCodeFromErrorArray(graphqlErrors);
}

export function extractErrorParameters(error: unknown): Record<string, unknown> | undefined {
  const graphqlErrors = collectGraphQLErrors(error);
  return extractParametersFromErrorArray(graphqlErrors);
}

/**
 * Detects if an error message indicates a connection/network issue.
 * 
 * @param message - The error message to check
 * @returns True if the message indicates a connection error
 */
function isConnectionError(message: string | null | undefined): boolean {
  if (!message) return false;
  
  const lowerMessage = message.toLowerCase();
  
  // Common connection error patterns
  const connectionErrorPatterns = [
    'tcp connect error',
    'http fetch failed',
    'network error',
    'connection refused',
    'connection timeout',
    'connection reset',
    'failed to fetch',
    'network request failed',
    'fetch failed',
    'unable to connect',
    'connection closed',
    'socket error',
    'econnrefused',
    'etimedout',
    'econnreset',
    'service unavailable',
    'gateway timeout',
  ];
  
  return connectionErrorPatterns.some(pattern => lowerMessage.includes(pattern));
}

/**
 * Converts technical connection error messages to user-friendly translated messages.
 * 
 * @param message - The technical error message
 * @returns A user-friendly translated error message
 */
function getConnectionErrorMessage(message: string | null | undefined): string {
  if (!message) {
    return getTranslatedError(
      'errors.connectionError',
      'We were unable to connect to the server. Please check your internet connection or wait a few moments.'
    );
  }
  
  // Check for specific error types to provide more context
  const lowerMessage = message.toLowerCase();
  
  if (lowerMessage.includes('timeout') || lowerMessage.includes('timed out')) {
    return getTranslatedError(
      'errors.connectionTimeout',
      'The request took too long to complete. Please check your connection and try again.'
    );
  }
  
  if (lowerMessage.includes('service unavailable') || lowerMessage.includes('503')) {
    return getTranslatedError(
      'errors.serviceUnavailable',
      'The service is temporarily unavailable. Please try again in a few moments.'
    );
  }
  
  if (lowerMessage.includes('gateway timeout') || lowerMessage.includes('504')) {
    return getTranslatedError(
      'errors.gatewayTimeout',
      'The server is taking too long to respond. Please try again later.'
    );
  }
  
  // Default connection error message
  return getTranslatedError(
    'errors.connectionError',
    'We were unable to connect to the server. Please check your internet connection or wait a few moments.'
  );
}

/**
 * Extracts and cleans error message from Apollo Client or generic errors.
 * This is a shared utility that should be used throughout the application
 * to ensure consistent error message handling.
 * 
 * Automatically detects connection errors and converts them to user-friendly translated messages.
 * 
 * @param error - The error object from Apollo Client or generic error
 * @param defaultMessage - Default message if error extraction fails (will be translated if i18n is available)
 * @returns Extracted and cleaned error message
 * 
 * @example
 * // Connection error
 * extractErrorMessage({ networkError: { message: "HTTP fetch failed: tcp connect error" }})
 * // Returns: Translated connection error message
 * 
 * // Regular error
 * extractErrorMessage({ graphQLErrors: [{ message: "Invalid email or password" }] })
 * // Returns: "Invalid email or password"
 */
export function extractErrorMessage(
  error: unknown,
  defaultMessage: string = 'An error occurred'
): string {
  if (!error) {
    // If custom default message is provided, use it directly (don't translate)
    // Translation should happen at the call site if needed
    return defaultMessage;
  }

  const defaultTranslated = getTranslatedError('errors.default', defaultMessage);
  const graphqlErrors = collectGraphQLErrors(error);
  const errorCode = extractErrorCode(error);
  if (errorCode) {
    const translated = translateByErrorCode(errorCode, extractErrorParameters(error), defaultTranslated);
    if (translated !== defaultTranslated) {
      return translated;
    }

    if (typeof error === 'object' && error !== null) {
      const err = error as Record<string, unknown>;
      const rawMessage =
        graphqlErrors[0]?.message ||
        (err.networkError as { message?: string } | undefined)?.message ||
        (err.networkError as { error?: { message?: string } } | undefined)?.error?.message ||
        (typeof err.message === 'string' ? err.message : undefined);

      const cleaned = cleanErrorMessage(rawMessage);
      if (isConnectionError(cleaned)) {
        return getConnectionErrorMessage(cleaned);
      }

      if (cleaned && !isTechnicalGraphQLErrorMessage(cleaned)) {
        return cleaned;
      }
    }

    return defaultTranslated;
  }

  if (graphqlErrors.length > 0) {
    const firstMessage = graphqlErrors[0]?.message;
    const cleaned = cleanErrorMessage(firstMessage);

    if (cleaned && !isTechnicalGraphQLErrorMessage(cleaned) && !isConnectionError(cleaned)) {
      return cleaned;
    }

    return getTranslatedError(
      'errors.GRAPHQL_VALIDATION_FAILED',
      getTranslatedError('errors.HTTP_REQUEST_FAILED', defaultTranslated)
    );
  }

  let rawMessage: string | undefined;

  // Handle Error instances
  if (error instanceof Error) {
    rawMessage = error.message;
  }
  // Handle Apollo Client errors
  else if (typeof error === 'object' && error !== null) {
    const err = error as any;
    
    // Check for GraphQL errors array (preferred source)
    if (err.graphQLErrors && Array.isArray(err.graphQLErrors) && err.graphQLErrors.length > 0) {
      rawMessage = err.graphQLErrors[0].message;
    }
    // Check for network error
    else if (err.networkError) {
      rawMessage = err.networkError.message || err.networkError.error?.message;
    }
    // Check for message property
    else if (err.message) {
      rawMessage = err.message;
    }
  }
  // Fallback to string conversion
  else {
    rawMessage = String(error);
  }

  // Clean the message to remove technical prefixes
  const cleaned = cleanErrorMessage(rawMessage);

  if (isGenericHttpStatusMessage(cleaned)) {
    return getTranslatedError('errors.HTTP_REQUEST_FAILED', defaultTranslated);
  }

  // Check if this is a connection error and convert to user-friendly translated message
  // Only treat as connection error if the message actually indicates a connection issue
  if (isConnectionError(cleaned)) {
    return getConnectionErrorMessage(cleaned);
  }

  if (isTechnicalGraphQLErrorMessage(cleaned)) {
    return getTranslatedError(
      'errors.GRAPHQL_VALIDATION_FAILED',
      getTranslatedError('errors.HTTP_REQUEST_FAILED', defaultTranslated)
    );
  }

  if (hasGraphQLErrorShape(error)) {
    return defaultTranslated;
  }

  // Return cleaned message or default (with translation if available)
  return cleaned || defaultTranslated;
}
