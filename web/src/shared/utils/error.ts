/**
 * Shared error handling utilities
 */

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
 * Extracts and cleans error message from Apollo Client or generic errors.
 * This is a shared utility that should be used throughout the application
 * to ensure consistent error message handling.
 * 
 * @param error - The error object from Apollo Client or generic error
 * @param defaultMessage - Default message if error extraction fails
 * @returns Extracted and cleaned error message
 */
export function extractErrorMessage(
  error: unknown,
  defaultMessage: string = 'An error occurred'
): string {
  if (!error) return defaultMessage;

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
  
  // Return cleaned message or default
  return cleaned || defaultMessage;
}

