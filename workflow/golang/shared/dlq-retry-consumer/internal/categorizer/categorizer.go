package categorizer

import (
	"strings"

	"github.com/salomax/invistus/workflow/golang/shared/dlq-retry-consumer/internal/config"
)

// ErrorCategory represents the type of error
type ErrorCategory string

const (
	// Transient errors are temporary and can be retried
	Transient ErrorCategory = "transient"

	// Permanent errors are not retryable and require manual intervention
	Permanent ErrorCategory = "permanent"
)

// Categorizer determines if an error is transient or permanent
type Categorizer struct {
	config *config.ErrorCategorizationConfig
}

// NewCategorizer creates a new error categorizer
func NewCategorizer(cfg *config.ErrorCategorizationConfig) *Categorizer {
	return &Categorizer{
		config: cfg,
	}
}

// Categorize determines if an error is transient or permanent
func (c *Categorizer) Categorize(err error) ErrorCategory {
	if err == nil {
		return Permanent // No error should not happen, but safe default
	}

	errStr := strings.ToLower(err.Error())

	// Check transient patterns first (more specific)
	for _, pattern := range c.config.TransientPatterns {
		if strings.Contains(errStr, strings.ToLower(pattern)) {
			return Transient
		}
	}

	// Check permanent patterns
	for _, pattern := range c.config.PermanentPatterns {
		if strings.Contains(errStr, strings.ToLower(pattern)) {
			return Permanent
		}
	}

	// Default to permanent (safer - requires manual review)
	return Permanent
}

// CategorizeString categorizes an error from its string representation
func (c *Categorizer) CategorizeString(errMsg string) ErrorCategory {
	if errMsg == "" {
		return Permanent
	}

	errStr := strings.ToLower(errMsg)

	// Check transient patterns first
	for _, pattern := range c.config.TransientPatterns {
		if strings.Contains(errStr, strings.ToLower(pattern)) {
			return Transient
		}
	}

	// Check permanent patterns
	for _, pattern := range c.config.PermanentPatterns {
		if strings.Contains(errStr, strings.ToLower(pattern)) {
			return Permanent
		}
	}

	return Permanent
}

// IsTransient checks if an error is transient
func (c *Categorizer) IsTransient(err error) bool {
	return c.Categorize(err) == Transient
}

// IsPermanent checks if an error is permanent
func (c *Categorizer) IsPermanent(err error) bool {
	return c.Categorize(err) == Permanent
}

// GetErrorType extracts a simple error type from error message
// Examples: "timeout", "http_404", "validation_error"
func GetErrorType(err error) string {
	if err == nil {
		return "unknown"
	}

	errStr := strings.ToLower(err.Error())

	// Common patterns
	patterns := map[string]string{
		"timeout":                "timeout",
		"deadline exceeded":      "timeout",
		"connection refused":     "connection_error",
		"connection reset":       "connection_error",
		"rate limit":             "rate_limit",
		"429":                    "rate_limit",
		"503":                    "service_unavailable",
		"502":                    "bad_gateway",
		"504":                    "gateway_timeout",
		"validation":             "validation_error",
		"invalid":                "validation_error",
		"malformed":              "malformed_data",
		"404":                    "not_found",
		"401":                    "unauthorized",
		"403":                    "forbidden",
		"database pool":          "database_pool_exhausted",
		"pool exhausted":         "pool_exhausted",
	}

	for pattern, errorType := range patterns {
		if strings.Contains(errStr, pattern) {
			return errorType
		}
	}

	return "unknown"
}
