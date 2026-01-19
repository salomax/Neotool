package shared

import (
	"context"
	"errors"
	"net"
	"syscall"
	"testing"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/stretchr/testify/assert"
)

func TestIsRetryableError_ContextErrors(t *testing.T) {
	tests := []struct {
		name     string
		err      error
		expected bool
	}{
		{
			name:     "nil error",
			err:      nil,
			expected: false,
		},
		{
			name:     "context canceled",
			err:      context.Canceled,
			expected: false,
		},
		{
			name:     "context deadline exceeded",
			err:      context.DeadlineExceeded,
			expected: false,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := IsRetryableError(tt.err)
			assert.Equal(t, tt.expected, result)
		})
	}
}

func TestIsRetryableError_HTTPErrors(t *testing.T) {
	tests := []struct {
		name     string
		err      error
		expected bool
	}{
		{
			name:     "HTTP 429 - Too Many Requests",
			err:      errors.New("HTTP 429: rate limit exceeded"),
			expected: true,
		},
		{
			name:     "HTTP 500 - Internal Server Error",
			err:      errors.New("HTTP 500: internal server error"),
			expected: true,
		},
		{
			name:     "HTTP 502 - Bad Gateway",
			err:      errors.New("HTTP 502: bad gateway"),
			expected: true,
		},
		{
			name:     "HTTP 503 - Service Unavailable",
			err:      errors.New("HTTP 503: service unavailable"),
			expected: true,
		},
		{
			name:     "HTTP 504 - Gateway Timeout",
			err:      errors.New("HTTP 504: gateway timeout"),
			expected: true,
		},
		{
			name:     "HTTP 400 - Bad Request",
			err:      errors.New("HTTP 400: bad request"),
			expected: false,
		},
		{
			name:     "HTTP 401 - Unauthorized",
			err:      errors.New("HTTP 401: unauthorized"),
			expected: false,
		},
		{
			name:     "HTTP 404 - Not Found",
			err:      errors.New("HTTP 404: not found"),
			expected: false,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := IsRetryableError(tt.err)
			assert.Equal(t, tt.expected, result)
		})
	}
}

func TestIsRetryableError_NetworkErrors(t *testing.T) {
	tests := []struct {
		name     string
		err      error
		expected bool
	}{
		{
			name:     "connection refused",
			err:      &net.OpError{Op: "dial", Err: syscall.ECONNREFUSED},
			expected: true,
		},
		{
			name:     "connection reset",
			err:      &net.OpError{Op: "read", Err: syscall.ECONNRESET},
			expected: true,
		},
		{
			name:     "connection timeout",
			err:      &net.OpError{Op: "dial", Err: syscall.ETIMEDOUT},
			expected: true,
		},
		{
			name:     "DNS error",
			err:      &net.DNSError{Err: "no such host", Name: "example.com"},
			expected: true,
		},
		{
			name:     "timeout error from net package",
			err:      &timeoutError{},
			expected: true,
		},
		{
			name:     "temporary error from net package",
			err:      &temporaryError{},
			expected: true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := IsRetryableError(tt.err)
			assert.Equal(t, tt.expected, result)
		})
	}
}

func TestIsRetryableError_NetworkErrorStrings(t *testing.T) {
	tests := []struct {
		name     string
		err      error
		expected bool
	}{
		{
			name:     "connection refused string",
			err:      errors.New("dial tcp: connection refused"),
			expected: true,
		},
		{
			name:     "connection reset string",
			err:      errors.New("read tcp: connection reset by peer"),
			expected: true,
		},
		{
			name:     "timeout string",
			err:      errors.New("dial tcp: i/o timeout"),
			expected: true,
		},
		{
			name:     "no such host string",
			err:      errors.New("dial tcp: lookup example.com: no such host"),
			expected: true,
		},
		{
			name:     "network unreachable string",
			err:      errors.New("dial tcp: network is unreachable"),
			expected: true,
		},
		{
			name:     "EOF error",
			err:      errors.New("unexpected EOF"),
			expected: true,
		},
		{
			name:     "broken pipe",
			err:      errors.New("write tcp: broken pipe"),
			expected: true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := IsRetryableError(tt.err)
			assert.Equal(t, tt.expected, result)
		})
	}
}

func TestIsRetryableError_DatabaseErrors(t *testing.T) {
	tests := []struct {
		name     string
		err      error
		expected bool
	}{
		{
			name:     "pgx.ErrNoRows - not retryable",
			err:      pgx.ErrNoRows,
			expected: false,
		},
		{
			name:     "connection exception (08xxx)",
			err:      &pgconn.PgError{Code: "08006"},
			expected: true,
		},
		{
			name:     "transaction rollback - deadlock (40P01)",
			err:      &pgconn.PgError{Code: "40P01"},
			expected: true,
		},
		{
			name:     "transaction rollback - serialization failure (40001)",
			err:      &pgconn.PgError{Code: "40001"},
			expected: true,
		},
		{
			name:     "insufficient resources (53xxx)",
			err:      &pgconn.PgError{Code: "53000"},
			expected: true,
		},
		{
			name:     "operator intervention (57xxx)",
			err:      &pgconn.PgError{Code: "57014"},
			expected: true,
		},
		{
			name:     "system error (58xxx)",
			err:      &pgconn.PgError{Code: "58000"},
			expected: true,
		},
		{
			name:     "unique violation - not retryable (23505)",
			err:      &pgconn.PgError{Code: "23505"},
			expected: false,
		},
		{
			name:     "foreign key violation - not retryable (23503)",
			err:      &pgconn.PgError{Code: "23503"},
			expected: false,
		},
		{
			name:     "data exception - not retryable (22xxx)",
			err:      &pgconn.PgError{Code: "22001"},
			expected: false,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := IsRetryableError(tt.err)
			assert.Equal(t, tt.expected, result)
		})
	}
}

func TestIsRetryableError_DatabaseErrorStrings(t *testing.T) {
	tests := []struct {
		name     string
		err      error
		expected bool
	}{
		{
			name:     "pool closed",
			err:      errors.New("pool is closed"),
			expected: true,
		},
		{
			name:     "too many connections",
			err:      errors.New("too many connections"),
			expected: true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := IsRetryableError(tt.err)
			assert.Equal(t, tt.expected, result)
		})
	}
}

func TestHTTPStatusCodeFromError(t *testing.T) {
	tests := []struct {
		name     string
		err      error
		expected int
	}{
		{
			name:     "nil error",
			err:      nil,
			expected: 0,
		},
		{
			name:     "HTTP 400",
			err:      errors.New("HTTP 400: bad request"),
			expected: 400,
		},
		{
			name:     "HTTP 401",
			err:      errors.New("HTTP 401: unauthorized"),
			expected: 401,
		},
		{
			name:     "HTTP 404",
			err:      errors.New("HTTP 404: not found"),
			expected: 404,
		},
		{
			name:     "HTTP 429",
			err:      errors.New("HTTP 429: too many requests"),
			expected: 429,
		},
		{
			name:     "HTTP 500",
			err:      errors.New("HTTP 500: internal server error"),
			expected: 500,
		},
		{
			name:     "HTTP 503",
			err:      errors.New("HTTP 503: service unavailable"),
			expected: 503,
		},
		{
			name:     "no status code",
			err:      errors.New("connection failed"),
			expected: 0,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := HTTPStatusCodeFromError(tt.err)
			assert.Equal(t, tt.expected, result)
		})
	}
}

// Mock error types for testing net.Error interface
type timeoutError struct{}

func (e *timeoutError) Error() string   { return "timeout" }
func (e *timeoutError) Timeout() bool   { return true }
func (e *timeoutError) Temporary() bool { return false }

type temporaryError struct{}

func (e *temporaryError) Error() string   { return "temporary" }
func (e *temporaryError) Timeout() bool   { return false }
func (e *temporaryError) Temporary() bool { return true }
