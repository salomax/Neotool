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
  
  // Check if this is a connection error and convert to user-friendly translated message
  // Only treat as connection error if the message actually indicates a connection issue
  if (isConnectionError(cleaned)) {
    return getConnectionErrorMessage(cleaned);
  }
  
  // Return cleaned message or default (with translation if available)
  return cleaned || getTranslatedError('errors.default', defaultMessage);
}

