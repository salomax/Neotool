package shared

import (
	"context"
	"errors"
	"fmt"
	"net"
	"syscall"
	"testing"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
)

func TestIsRetryableError_ContextErrors(t *testing.T) {
	tests := []struct {
		name      string
		err       error
		retryable bool
	}{
		{
			name:      "context canceled",
			err:       context.Canceled,
			retryable: false,
		},
		{
			name:      "context deadline exceeded",
			err:       context.DeadlineExceeded,
			retryable: false,
		},
		{
			name:      "wrapped context canceled",
			err:       fmt.Errorf("operation failed: %w", context.Canceled),
			retryable: false,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := IsRetryableError(tt.err)
			if got != tt.retryable {
				t.Errorf("IsRetryableError() = %v, want %v", got, tt.retryable)
			}
		})
	}
}

func TestIsRetryableError_HTTPErrors(t *testing.T) {
	tests := []struct {
		name      string
		err       error
		retryable bool
	}{
		{
			name:      "HTTP 429 Too Many Requests",
			err:       errors.New("HTTP 429: Rate limit exceeded"),
			retryable: true,
		},
		{
			name:      "HTTP 500 Internal Server Error",
			err:       errors.New("HTTP 500: Server error"),
			retryable: true,
		},
		{
			name:      "HTTP 502 Bad Gateway",
			err:       errors.New("HTTP 502: Bad gateway"),
			retryable: true,
		},
		{
			name:      "HTTP 503 Service Unavailable",
			err:       errors.New("HTTP 503: Service unavailable"),
			retryable: true,
		},
		{
			name:      "HTTP 504 Gateway Timeout",
			err:       errors.New("HTTP 504: Gateway timeout"),
			retryable: true,
		},
		{
			name:      "HTTP 400 Bad Request",
			err:       errors.New("HTTP 400: Bad request"),
			retryable: false,
		},
		{
			name:      "HTTP 404 Not Found",
			err:       errors.New("HTTP 404: Not found"),
			retryable: false,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := IsRetryableError(tt.err)
			if got != tt.retryable {
				t.Errorf("IsRetryableError() = %v, want %v", got, tt.retryable)
			}
		})
	}
}

func TestIsRetryableError_NetworkErrors(t *testing.T) {
	tests := []struct {
		name      string
		err       error
		retryable bool
	}{
		{
			name:      "connection refused",
			err:       &net.OpError{Op: "dial", Err: syscall.ECONNREFUSED},
			retryable: true,
		},
		{
			name:      "connection reset",
			err:       &net.OpError{Op: "read", Err: syscall.ECONNRESET},
			retryable: true,
		},
		{
			name:      "timeout",
			err:       &net.OpError{Op: "read", Err: syscall.ETIMEDOUT},
			retryable: true,
		},
		{
			name:      "network unreachable",
			err:       &net.OpError{Op: "dial", Err: syscall.ENETUNREACH},
			retryable: true,
		},
		{
			name:      "error message with timeout",
			err:       errors.New("operation timeout"),
			retryable: true,
		},
		{
			name:      "error message with connection refused",
			err:       errors.New("connection refused by server"),
			retryable: true,
		},
		{
			name:      "EOF error",
			err:       errors.New("unexpected EOF"),
			retryable: true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := IsRetryableError(tt.err)
			if got != tt.retryable {
				t.Errorf("IsRetryableError() = %v, want %v", got, tt.retryable)
			}
		})
	}
}

func TestIsRetryableError_PostgreSQLErrors(t *testing.T) {
	tests := []struct {
		name      string
		err       error
		retryable bool
	}{
		{
			name:      "pgx.ErrNoRows",
			err:       pgx.ErrNoRows,
			retryable: false,
		},
		{
			name: "connection exception (08xxx)",
			err: &pgconn.PgError{
				Code:    "08006", // connection_failure
				Message: "Connection failed",
			},
			retryable: true,
		},
		{
			name: "transaction rollback (40xxx)",
			err: &pgconn.PgError{
				Code:    "40001", // serialization_failure
				Message: "Could not serialize access",
			},
			retryable: true,
		},
		{
			name: "insufficient resources (53xxx)",
			err: &pgconn.PgError{
				Code:    "53300", // too_many_connections
				Message: "Too many connections",
			},
			retryable: true,
		},
		{
			name: "operator intervention (57xxx)",
			err: &pgconn.PgError{
				Code:    "57P01", // admin_shutdown
				Message: "Server shutting down",
			},
			retryable: true,
		},
		{
			name: "system error (58xxx)",
			err: &pgconn.PgError{
				Code:    "58000", // system_error
				Message: "System error",
			},
			retryable: true,
		},
		{
			name: "integrity constraint violation (23xxx)",
			err: &pgconn.PgError{
				Code:    "23505", // unique_violation
				Message: "Duplicate key",
			},
			retryable: false,
		},
		{
			name: "data exception (22xxx)",
			err: &pgconn.PgError{
				Code:    "22P02", // invalid_text_representation
				Message: "Invalid input",
			},
			retryable: false,
		},
		{
			name:      "pool exhausted",
			err:       errors.New("connection pool exhausted"),
			retryable: true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := IsRetryableError(tt.err)
			if got != tt.retryable {
				t.Errorf("IsRetryableError() = %v, want %v", got, tt.retryable)
			}
		})
	}
}

func TestWrapNonRetryableError(t *testing.T) {
	originalErr := errors.New("original error")
	wrappedErr := WrapNonRetryableError(originalErr)

	if wrappedErr == nil {
		t.Fatal("WrapNonRetryableError() returned nil")
	}

	if !IsExplicitlyNonRetryable(wrappedErr) {
		t.Error("wrapped error should be explicitly non-retryable")
	}

	// Should still contain original error
	if !errors.Is(wrappedErr, originalErr) {
		t.Error("wrapped error should contain original error")
	}
}

func TestWrapNonRetryableError_Nil(t *testing.T) {
	wrappedErr := WrapNonRetryableError(nil)
	if wrappedErr != nil {
		t.Errorf("WrapNonRetryableError(nil) = %v, want nil", wrappedErr)
	}
}

func TestIsExplicitlyNonRetryable(t *testing.T) {
	tests := []struct {
		name      string
		err       error
		expectNon bool
	}{
		{
			name:      "nil error",
			err:       nil,
			expectNon: false,
		},
		{
			name:      "regular error",
			err:       errors.New("regular error"),
			expectNon: false,
		},
		{
			name:      "wrapped non-retryable",
			err:       WrapNonRetryableError(errors.New("test")),
			expectNon: true,
		},
		{
			name:      "error with (non-retryable) in message",
			err:       errors.New("this is (non-retryable) error"),
			expectNon: true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := IsExplicitlyNonRetryable(tt.err)
			if got != tt.expectNon {
				t.Errorf("IsExplicitlyNonRetryable() = %v, want %v", got, tt.expectNon)
			}
		})
	}
}
