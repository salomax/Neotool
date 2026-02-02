package shared

import (
	"context"
	"errors"
	"net"
	"net/http"
	"strings"
	"syscall"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
)

// IsRetryableError determines if an error should be retried
func IsRetryableError(err error) bool {
	if err == nil {
		return false
	}

	// Context errors are not retryable
	if errors.Is(err, context.Canceled) || errors.Is(err, context.DeadlineExceeded) {
		return false
	}

	// Check for retryable HTTP errors
	if isRetryableHTTPError(err) {
		return true
	}

	// Check for network/connection errors (retryable)
	if isNetworkError(err) {
		return true
	}

	// Check for PostgreSQL errors
	if isRetryableDatabaseError(err) {
		return true
	}

	// Default: non-retryable
	return false
}

// isRetryableHTTPError checks if an HTTP error is retryable
func isRetryableHTTPError(err error) bool {
	// Check if error contains HTTP status code
	errStr := err.Error()

	// Retryable HTTP status codes with "HTTP " prefix to avoid false matches
	// (e.g., PostgreSQL error code "23503" should not match "503")
	retryablePatterns := []string{
		"HTTP 429", "429 ",      // Too Many Requests (rate limit)
		"HTTP 500", "500 ",      // Internal Server Error
		"HTTP 502", "502 ",      // Bad Gateway
		"HTTP 503", "503 ",      // Service Unavailable
		"HTTP 504", "504 ",      // Gateway Timeout
	}

	for _, pattern := range retryablePatterns {
		if strings.Contains(errStr, pattern) {
			return true
		}
	}

	return false
}

// isNetworkError checks if an error is a network/connection error
func isNetworkError(err error) bool {
	// Check for specific network error types
	var netErr net.Error
	if errors.As(err, &netErr) {
		// Timeout errors are retryable
		if netErr.Timeout() {
			return true
		}
		// Temporary errors are retryable
		if netErr.Temporary() {
			return true
		}
	}

	// Check for DNS errors
	var dnsErr *net.DNSError
	if errors.As(err, &dnsErr) {
		return true
	}

	// Check for connection refused/reset errors
	var opErr *net.OpError
	if errors.As(err, &opErr) {
		if opErr.Err == syscall.ECONNREFUSED ||
			opErr.Err == syscall.ECONNRESET ||
			opErr.Err == syscall.ETIMEDOUT {
			return true
		}
	}

	// Check for common connection error strings
	errStr := strings.ToLower(err.Error())
	connectionErrors := []string{
		"connection refused",
		"connection reset",
		"connection timeout",
		"timeout",
		"broken pipe",
		"no such host",
		"network is unreachable",
		"i/o timeout",
		"eof",
	}

	for _, connErr := range connectionErrors {
		if strings.Contains(errStr, connErr) {
			return true
		}
	}

	return false
}

// isRetryableDatabaseError checks if a PostgreSQL error is retryable
func isRetryableDatabaseError(err error) bool {
	// Check for pgx specific errors
	if errors.Is(err, pgx.ErrNoRows) {
		return false // Not found is not retryable
	}

	// Check for pgconn errors (PostgreSQL protocol errors)
	var pgErr *pgconn.PgError
	if errors.As(err, &pgErr) {
		// Check PostgreSQL error code classes
		// https://www.postgresql.org/docs/current/errcodes-appendix.html

		// Class 08 — Connection Exception (retryable)
		if strings.HasPrefix(pgErr.Code, "08") {
			return true
		}

		// Class 40 — Transaction Rollback (retryable - deadlock, serialization failure)
		if strings.HasPrefix(pgErr.Code, "40") {
			return true
		}

		// Class 53 — Insufficient Resources (retryable)
		if strings.HasPrefix(pgErr.Code, "53") {
			return true
		}

		// Class 57 — Operator Intervention (retryable)
		if strings.HasPrefix(pgErr.Code, "57") {
			return true
		}

		// Class 58 — System Error (retryable)
		if strings.HasPrefix(pgErr.Code, "58") {
			return true
		}

		// Class 23 — Integrity Constraint Violation (NOT retryable)
		if strings.HasPrefix(pgErr.Code, "23") {
			return false
		}

		// Class 22 — Data Exception (NOT retryable)
		if strings.HasPrefix(pgErr.Code, "22") {
			return false
		}
	}

	// Check for pool exhaustion errors
	errStr := strings.ToLower(err.Error())
	if strings.Contains(errStr, "pool") && strings.Contains(errStr, "closed") {
		return true
	}
	if strings.Contains(errStr, "too many connections") {
		return true
	}

	return false
}

// HTTPStatusCodeFromError extracts HTTP status code from error string
func HTTPStatusCodeFromError(err error) int {
	if err == nil {
		return 0
	}

	errStr := err.Error()

	// Common HTTP status codes
	statusCodes := map[string]int{
		"400": http.StatusBadRequest,
		"401": http.StatusUnauthorized,
		"403": http.StatusForbidden,
		"404": http.StatusNotFound,
		"429": http.StatusTooManyRequests,
		"500": http.StatusInternalServerError,
		"502": http.StatusBadGateway,
		"503": http.StatusServiceUnavailable,
		"504": http.StatusGatewayTimeout,
	}

	for codeStr, code := range statusCodes {
		if strings.Contains(errStr, codeStr) {
			return code
		}
	}

	return 0
}
