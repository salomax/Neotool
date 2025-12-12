/**
 * Centralized error logging utility
 * 
 * This utility should only be used for technical/unexpected errors, not business-logic errors.
 * 
 * Business-logic errors (invalid credentials, validation failures, etc.) should be handled
 * in the UI (toasts, inline messages) without logging.
 * 
 * Technical errors (network failures, GraphQL connection errors, unexpected exceptions)
 * should be logged using this utility.
 */

const isDev = process.env.NODE_ENV === 'development';
const isDebugEnabled = isDev || process.env.NEXT_PUBLIC_ENABLE_DEBUG_LOGS === 'true';

/**
 * Logs technical errors
 * 
 * In development: logs to console.error for debugging
 * In production: sends to error tracker (to be defined)
 * 
 * @param message - Error message or context
 * @param error - Optional error object or exception
 * 
 * @example
 * logger.error("Network request failed", networkError);
 * logger.error("GraphQL connection error", graphQLError);
 */
export const logger = {
  error: (message: string, error?: unknown) => {
    if (isDev) {
      console.error(message, error);
    } else {
      // TODO: Send to error tracker (to be defined)
      // This will be implemented when the error tracking service is chosen
      // Example implementations:
      // - Sentry: Sentry.captureException(error, { extra: { message } });
      // - LogRocket: LogRocket.captureException(error);
      // - Datadog: datadogLogs.logger.error(message, { error });
      // - Custom: errorTracker.captureException(error, { context: { message } });
    }
  },
  debug: (...args: unknown[]) => {
    if (isDebugEnabled) {
      // eslint-disable-next-line no-console
      console.debug(...args);
    }
  },
};
