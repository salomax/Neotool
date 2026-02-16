package categorizer

import (
	"errors"
	"testing"

	"github.com/salomax/invistus/workflow/golang/shared/dlq-retry-consumer/internal/config"
	"github.com/stretchr/testify/assert"
)

func TestCategorizer_Categorize(t *testing.T) {
	cfg := &config.ErrorCategorizationConfig{
		TransientPatterns: []string{
			"timeout",
			"deadline exceeded",
			"503",
			"connection refused",
		},
		PermanentPatterns: []string{
			"validation",
			"invalid",
			"404",
		},
	}

	categorizer := NewCategorizer(cfg)

	tests := []struct {
		name     string
		err      error
		expected ErrorCategory
	}{
		// Transient errors
		{
			name:     "timeout error",
			err:      errors.New("context deadline exceeded"),
			expected: Transient,
		},
		{
			name:     "connection error",
			err:      errors.New("connection refused"),
			expected: Transient,
		},
		{
			name:     "503 error",
			err:      errors.New("HTTP 503 Service Unavailable"),
			expected: Transient,
		},
		{
			name:     "uppercase timeout",
			err:      errors.New("TIMEOUT occurred"),
			expected: Transient,
		},

		// Permanent errors
		{
			name:     "validation error",
			err:      errors.New("validation failed: invalid CNPJ"),
			expected: Permanent,
		},
		{
			name:     "404 error",
			err:      errors.New("HTTP 404 Not Found"),
			expected: Permanent,
		},
		{
			name:     "invalid format",
			err:      errors.New("invalid email format"),
			expected: Permanent,
		},

		// Edge cases
		{
			name:     "nil error",
			err:      nil,
			expected: Permanent, // Safe default
		},
		{
			name:     "unknown error",
			err:      errors.New("some unknown error"),
			expected: Permanent, // Safe default
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := categorizer.Categorize(tt.err)
			assert.Equal(t, tt.expected, result)
		})
	}
}

func TestCategorizer_CategorizeString(t *testing.T) {
	cfg := &config.ErrorCategorizationConfig{
		TransientPatterns: []string{"timeout", "503"},
		PermanentPatterns: []string{"validation", "404"},
	}

	categorizer := NewCategorizer(cfg)

	tests := []struct {
		name     string
		errMsg   string
		expected ErrorCategory
	}{
		{
			name:     "transient from string",
			errMsg:   "request timeout",
			expected: Transient,
		},
		{
			name:     "permanent from string",
			errMsg:   "validation error",
			expected: Permanent,
		},
		{
			name:     "empty string",
			errMsg:   "",
			expected: Permanent,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := categorizer.CategorizeString(tt.errMsg)
			assert.Equal(t, tt.expected, result)
		})
	}
}

func TestCategorizer_IsTransient(t *testing.T) {
	cfg := &config.ErrorCategorizationConfig{
		TransientPatterns: []string{"timeout"},
		PermanentPatterns: []string{"validation"},
	}

	categorizer := NewCategorizer(cfg)

	assert.True(t, categorizer.IsTransient(errors.New("timeout error")))
	assert.False(t, categorizer.IsTransient(errors.New("validation error")))
}

func TestCategorizer_IsPermanent(t *testing.T) {
	cfg := &config.ErrorCategorizationConfig{
		TransientPatterns: []string{"timeout"},
		PermanentPatterns: []string{"validation"},
	}

	categorizer := NewCategorizer(cfg)

	assert.True(t, categorizer.IsPermanent(errors.New("validation error")))
	assert.False(t, categorizer.IsPermanent(errors.New("timeout error")))
}

func TestGetErrorType(t *testing.T) {
	tests := []struct {
		name         string
		err          error
		expectedType string
	}{
		{
			name:         "timeout",
			err:          errors.New("context deadline exceeded"),
			expectedType: "timeout",
		},
		{
			name:         "connection refused",
			err:          errors.New("connection refused"),
			expectedType: "connection_error",
		},
		{
			name:         "rate limit",
			err:          errors.New("rate limit exceeded"),
			expectedType: "rate_limit",
		},
		{
			name:         "429 status",
			err:          errors.New("HTTP 429 Too Many Requests"),
			expectedType: "rate_limit",
		},
		{
			name:         "503 status",
			err:          errors.New("HTTP 503 Service Unavailable"),
			expectedType: "service_unavailable",
		},
		{
			name:         "validation error",
			err:          errors.New("validation failed"),
			expectedType: "validation_error",
		},
		{
			name:         "404 status",
			err:          errors.New("HTTP 404 Not Found"),
			expectedType: "not_found",
		},
		{
			name:         "database pool",
			err:          errors.New("database pool exhausted"),
			expectedType: "database_pool_exhausted",
		},
		{
			name:         "nil error",
			err:          nil,
			expectedType: "unknown",
		},
		{
			name:         "unknown error",
			err:          errors.New("some weird error"),
			expectedType: "unknown",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := GetErrorType(tt.err)
			assert.Equal(t, tt.expectedType, result)
		})
	}
}

func TestCategorizer_PatternPriority(t *testing.T) {
	// Test that transient patterns are checked before permanent patterns
	cfg := &config.ErrorCategorizationConfig{
		TransientPatterns: []string{"validation timeout"}, // More specific
		PermanentPatterns: []string{"validation"},         // Less specific
	}

	categorizer := NewCategorizer(cfg)

	// Should match transient because it's checked first
	result := categorizer.Categorize(errors.New("validation timeout occurred"))
	assert.Equal(t, Transient, result)

	// Should match permanent (doesn't contain "timeout")
	result = categorizer.Categorize(errors.New("validation failed"))
	assert.Equal(t, Permanent, result)
}

func TestCategorizer_CaseInsensitive(t *testing.T) {
	cfg := &config.ErrorCategorizationConfig{
		TransientPatterns: []string{"timeout"},
		PermanentPatterns: []string{"validation"},
	}

	categorizer := NewCategorizer(cfg)

	tests := []struct {
		name string
		err  error
	}{
		{"lowercase", errors.New("timeout")},
		{"uppercase", errors.New("TIMEOUT")},
		{"mixed case", errors.New("TiMeOuT")},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := categorizer.Categorize(tt.err)
			assert.Equal(t, Transient, result)
		})
	}
}
