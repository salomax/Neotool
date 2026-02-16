package shared

import (
	"context"
	"errors"
	"fmt"
	"net"
	"strings"
	"syscall"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
)

// IsRetryableError determines if an error should trigger a retry
func IsRetryableError(err error) bool {
	if err == nil {
		return false
	}

	// Context errors are not retryable
	if errors.Is(err, context.Canceled) || errors.Is(err, context.DeadlineExceeded) {
		return false
	}

	// Check for HTTP-related retryable errors by examining error message
	if isHTTPRetryable(err) {
		return true
	}

	// Check for network errors
	if isNetworkError(err) {
		return true
	}

	// Check for PostgreSQL errors
	if isPgRetryable(err) {
		return true
	}

	// Default to not retryable
	return false
}

// isHTTPRetryable checks if the error is a retryable HTTP error
func isHTTPRetryable(err error) bool {
	errMsg := err.Error()

	// Check for specific HTTP status codes in error message
	retryableStatuses := []string{
		"429",  // Too Many Requests
		"500",  // Internal Server Error
		"502",  // Bad Gateway
		"503",  // Service Unavailable
		"504",  // Gateway Timeout
	}

	for _, status := range retryableStatuses {
		if strings.Contains(errMsg, status) {
			return true
		}
	}

	return false
}

// isNetworkError checks if the error is a network-related error
func isNetworkError(err error) bool {
	var netErr net.Error
	if errors.As(err, &netErr) {
		return true
	}

	// Check for specific network errors
	var opErr *net.OpError
	if errors.As(err, &opErr) {
		return true
	}

	// Check for syscall errors
	var syscallErr syscall.Errno
	if errors.As(err, &syscallErr) {
		switch syscallErr {
		case syscall.ECONNREFUSED, syscall.ECONNRESET, syscall.ETIMEDOUT,
			syscall.EPIPE, syscall.ENETUNREACH, syscall.EHOSTUNREACH:
			return true
		}
	}

	// Check error message for common network error patterns
	errMsg := strings.ToLower(err.Error())
	networkPatterns := []string{
		"connection refused",
		"connection reset",
		"timeout",
		"broken pipe",
		"network unreachable",
		"host unreachable",
		"eof",
		"i/o timeout",
	}

	for _, pattern := range networkPatterns {
		if strings.Contains(errMsg, pattern) {
			return true
		}
	}

	return false
}

// isPgRetryable checks if the error is a retryable PostgreSQL error
func isPgRetryable(err error) bool {
	// Check for specific pgx errors
	if errors.Is(err, pgx.ErrNoRows) {
		return false // Not found is not retryable
	}

	// Check for pgconn errors with specific error codes
	var pgErr *pgconn.PgError
	if errors.As(err, &pgErr) {
		// PostgreSQL error codes (SQLSTATE)
		// https://www.postgresql.org/docs/current/errcodes-appendix.html

		code := pgErr.Code

		// Class 08 — Connection Exception
		if strings.HasPrefix(code, "08") {
			return true
		}

		// Class 40 — Transaction Rollback
		if strings.HasPrefix(code, "40") {
			return true
		}

		// Class 53 — Insufficient Resources
		if strings.HasPrefix(code, "53") {
			return true
		}

		// Class 57 — Operator Intervention
		if strings.HasPrefix(code, "57") {
			return true
		}

		// Class 58 — System Error
		if strings.HasPrefix(code, "58") {
			return true
		}

		// Non-retryable error classes
		// Class 23 — Integrity Constraint Violation
		// Class 22 — Data Exception
		return false
	}

	// Check for pool exhaustion
	errMsg := err.Error()
	if strings.Contains(errMsg, "pool") && strings.Contains(errMsg, "exhausted") {
		return true
	}

	return false
}

// WrapNonRetryableError wraps an error to explicitly mark it as non-retryable
func WrapNonRetryableError(err error) error {
	if err == nil {
		return nil
	}
	return fmt.Errorf("(non-retryable) %w", err)
}

// IsExplicitlyNonRetryable checks if an error has been explicitly marked as non-retryable
func IsExplicitlyNonRetryable(err error) bool {
	if err == nil {
		return false
	}
	return strings.Contains(err.Error(), "(non-retryable)")
}
