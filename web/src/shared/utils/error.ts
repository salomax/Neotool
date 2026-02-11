/**
 * Shared error handling utilities with error code translation support
 */

import i18n from '@/shared/i18n/config';

/**
 * Type definition for GraphQL error from Apollo Client
 */
interface GraphQLErrorExtensions {
  code?: string;
  parameters?: Record<string, any>;
  service?: string;
}

interface GraphQLErrorType {
  message: string;
  extensions?: GraphQLErrorExtensions;
}

/**
 * Extracts error code from GraphQL error extensions.
 *
 * @param error - The error object
 * @returns The error code if found, null otherwise
 */
export function extractErrorCode(error: unknown): string | null {
  if (typeof error === 'object' && error !== null) {
    const err = error as any;

    // Check GraphQL errors array for error code in extensions
    if (err.graphQLErrors && Array.isArray(err.graphQLErrors) && err.graphQLErrors.length > 0) {
      const graphQLError = err.graphQLErrors[0] as GraphQLErrorType;
      if (graphQLError.extensions?.code) {
        return graphQLError.extensions.code;
      }
    }

    // Check for direct extensions property
    if (err.extensions?.code) {
      return err.extensions.code;
    }
  }

  return null;
}

/**
 * Extracts parameters from GraphQL error extensions for message interpolation.
 *
 * @param error - The error object
 * @returns The parameters object if found, null otherwise
 */
export function extractErrorParameters(error: unknown): Record<string, any> | null {
  if (typeof error === 'object' && error !== null) {
    const err = error as any;

    // Check GraphQL errors array for parameters in extensions
    if (err.graphQLErrors && Array.isArray(err.graphQLErrors) && err.graphQLErrors.length > 0) {
      const graphQLError = err.graphQLErrors[0] as GraphQLErrorType;
      if (graphQLError.extensions?.parameters) {
        return graphQLError.extensions.parameters;
      }
    }

    // Check for direct extensions property
    if (err.extensions?.parameters) {
      return err.extensions.parameters;
    }
  }

  return null;
}

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
 * @param key - The translation key (e.g., "errors.connectionError" or just "ACCOUNT_NAME_REQUIRED")
 * @param fallback - Fallback message if translation is not available
 * @param parameters - Optional parameters for message interpolation
 * @returns Translated error message
 */
function getTranslatedError(
  key: string,
  fallback: string,
  parameters?: Record<string, any>
): string {
  try {
    if (i18n.isInitialized) {
      // Normalize the key - if it doesn't start with 'errors.', add it
      const normalizedKey = key.startsWith('errors.') ? key : `errors.${key}`;

      const translated = i18n.t(normalizedKey, {
        ns: 'common',
        ...parameters, // Spread parameters for interpolation
      });

      // If translation returns the key itself, it means the key wasn't found
      return translated !== normalizedKey ? translated : fallback;
    }
  } catch (error) {
    // If i18n is not initialized or fails, use fallback
  }
  return fallback;
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
 * Extracts raw message from error object without translation.
 *
 * @param error - The error object
 * @returns The raw error message
 */
function extractRawMessage(error: unknown): string | undefined {
  // Handle Error instances
  if (error instanceof Error) {
    return error.message;
  }

  // Handle Apollo Client errors
  if (typeof error === 'object' && error !== null) {
    const err = error as any;

    // Check for GraphQL errors array (preferred source)
    if (err.graphQLErrors && Array.isArray(err.graphQLErrors) && err.graphQLErrors.length > 0) {
      return err.graphQLErrors[0].message;
    }

    // Check for network error
    if (err.networkError) {
      return err.networkError.message || err.networkError.error?.message;
    }

    // Check for message property
    if (err.message) {
      return err.message;
    }
  }

  // Fallback to string conversion
  return String(error);
}

/**
 * Extracts and translates error message from Apollo Client or generic errors.
 * This is the primary utility for error message handling in the application.
 *
 * **Best Practice Flow:**
 * 1. Extracts error code from GraphQL error extensions
 * 2. Translates error code using i18n (e.g., ACCOUNT_NAME_REQUIRED -> "Account name is required...")
 * 3. Falls back to cleaned error message if no error code found
 * 4. Detects and translates connection errors
 * 5. Returns default message as last resort
 *
 * @param error - The error object from Apollo Client or generic error
 * @param defaultMessage - Default message if error extraction fails
 * @returns Translated error message in the user's language
 *
 * @example
 * // Error with code
 * extractErrorMessage({
 *   graphQLErrors: [{
 *     message: "Account name is required",
 *     extensions: { code: "ACCOUNT_NAME_REQUIRED" }
 *   }]
 * })
 * // Returns: Translated message for ACCOUNT_NAME_REQUIRED
 *
 * // Connection error
 * extractErrorMessage({ networkError: { message: "HTTP fetch failed: tcp connect error" }})
 * // Returns: Translated connection error message
 *
 * // Regular error without code
 * extractErrorMessage({ graphQLErrors: [{ message: "Invalid email or password" }] })
 * // Returns: "Invalid email or password" (cleaned)
 */
export function extractErrorMessage(
  error: unknown,
  defaultMessage: string = 'An error occurred'
): string {
  if (!error) {
    return defaultMessage;
  }

  // Step 1: Try to extract error code
  const errorCode = extractErrorCode(error);
  if (errorCode) {
    // Extract parameters for interpolation
    const parameters = extractErrorParameters(error);

    // Extract raw message as fallback
    const rawMessage = extractRawMessage(error);
    const fallback = rawMessage ? cleanErrorMessage(rawMessage) : defaultMessage;

    // Translate using error code
    return getTranslatedError(errorCode, fallback, parameters ?? undefined);
  }

  // Step 2: No error code found, extract raw message
  const rawMessage = extractRawMessage(error);
  const cleaned = cleanErrorMessage(rawMessage);

  // Step 3: Check if this is a connection error
  if (isConnectionError(cleaned)) {
    return getConnectionErrorMessage(cleaned);
  }

  // Step 4: Return cleaned message or default
  return cleaned || getTranslatedError('errors.default', defaultMessage);
}
