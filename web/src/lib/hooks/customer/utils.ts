/**
 * Utility functions for customer-related operations
 */

/**
 * Extracts error message from Apollo Client or generic errors
 * @param error - The error object from Apollo Client or generic error
 * @param defaultMessage - Default message if error extraction fails
 * @returns Extracted error message
 */
export function extractErrorMessage(
  error: unknown,
  defaultMessage: string = 'An error occurred'
): string {
  if (!error) return defaultMessage;

  // Handle Error instances
  if (error instanceof Error) {
    return error.message || defaultMessage;
  }

  // Handle Apollo Client errors
  if (typeof error === 'object' && error !== null) {
    const err = error as any;
    
    // Check for GraphQL errors array
    if (err.graphQLErrors && Array.isArray(err.graphQLErrors) && err.graphQLErrors.length > 0) {
      return err.graphQLErrors[0].message || defaultMessage;
    }
    
    // Check for network error
    if (err.networkError) {
      return err.networkError.message || err.networkError.error?.message || defaultMessage;
    }
    
    // Check for message property
    if (err.message) {
      return err.message;
    }
  }

  // Fallback to string conversion
  return String(error) || defaultMessage;
}

