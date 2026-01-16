package config

import (
	"os"
	"path/filepath"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestLoadConfig(t *testing.T) {
	// Create temp config file
	tmpDir := t.TempDir()
	configPath := filepath.Join(tmpDir, "dlq-mappings.yaml")

	validConfig := `version: "1.0"
defaults:
  retry_strategy:
    max_retries: 3
    backoff:
      - retry_count: 0
        delay: 1h
      - retry_count: 1
        delay: 6h
      - retry_count: 2
        delay: 24h
  error_categorization:
    transient_patterns: ["timeout", "503"]
    permanent_patterns: ["validation", "404"]

dlq_mappings:
  - name: test-service
    enabled: true
    dlq_topic: test.dlq
    original_topic: test.events
    consumer_group: dlq-retry-consumer-test
    description: "Test service"
`

	err := os.WriteFile(configPath, []byte(validConfig), 0644)
	require.NoError(t, err)

	// Set environment variable
	os.Setenv("DLQ_CONFIG_PATH", configPath)
	os.Setenv("KAFKA_BROKERS", "kafka:9092")
	defer os.Unsetenv("DLQ_CONFIG_PATH")
	defer os.Unsetenv("KAFKA_BROKERS")

	// Load config
	config, err := LoadConfig()
	require.NoError(t, err)
	require.NotNil(t, config)

	// Assertions
	assert.Equal(t, "1.0", config.Version)
	assert.Equal(t, 3, config.Defaults.RetryStrategy.MaxRetries)
	assert.Len(t, config.Defaults.RetryStrategy.Backoff, 3)
	assert.Equal(t, 1*time.Hour, config.Defaults.RetryStrategy.Backoff[0].Delay)
	assert.Equal(t, "kafka:9092", config.Kafka.Brokers)

	assert.Len(t, config.Mappings, 1)
	assert.Equal(t, "test-service", config.Mappings[0].Name)
	assert.True(t, config.Mappings[0].Enabled)
	assert.Equal(t, "test.dlq", config.Mappings[0].DLQTopic)
}

func TestLoadConfig_InvalidYAML(t *testing.T) {
	tmpDir := t.TempDir()
	configPath := filepath.Join(tmpDir, "invalid.yaml")

	invalidConfig := `invalid yaml content: [[[`
	err := os.WriteFile(configPath, []byte(invalidConfig), 0644)
	require.NoError(t, err)

	os.Setenv("DLQ_CONFIG_PATH", configPath)
	defer os.Unsetenv("DLQ_CONFIG_PATH")

	_, err = LoadConfig()
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "failed to parse YAML config")
}

func TestRetryStrategyValidation(t *testing.T) {
	tests := []struct {
		name      string
		strategy  RetryStrategyConfig
		wantError bool
		errorMsg  string
	}{
		{
			name: "valid strategy",
			strategy: RetryStrategyConfig{
				MaxRetries: 2,
				Backoff: []BackoffConfig{
					{RetryCount: 0, Delay: 1 * time.Hour},
					{RetryCount: 1, Delay: 6 * time.Hour},
				},
			},
			wantError: false,
		},
		{
			name: "max retries too high",
			strategy: RetryStrategyConfig{
				MaxRetries: 15,
				Backoff:    []BackoffConfig{},
			},
			wantError: true,
			errorMsg:  "must be between 0 and 10",
		},
		{
			name: "backoff array length mismatch",
			strategy: RetryStrategyConfig{
				MaxRetries: 3,
				Backoff: []BackoffConfig{
					{RetryCount: 0, Delay: 1 * time.Hour},
				},
			},
			wantError: true,
			errorMsg:  "must have exactly 3 entries",
		},
		{
			name: "invalid retry count sequence",
			strategy: RetryStrategyConfig{
				MaxRetries: 2,
				Backoff: []BackoffConfig{
					{RetryCount: 0, Delay: 1 * time.Hour},
					{RetryCount: 2, Delay: 6 * time.Hour}, // Should be 1
				},
			},
			wantError: true,
			errorMsg:  "must be 1",
		},
		{
			name: "negative delay",
			strategy: RetryStrategyConfig{
				MaxRetries: 1,
				Backoff: []BackoffConfig{
					{RetryCount: 0, Delay: -1 * time.Hour},
				},
			},
			wantError: true,
			errorMsg:  "must be positive",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			err := tt.strategy.Validate()
			if tt.wantError {
				assert.Error(t, err)
				assert.Contains(t, err.Error(), tt.errorMsg)
			} else {
				assert.NoError(t, err)
			}
		})
	}
}

func TestDLQMappingValidation(t *testing.T) {
	tests := []struct {
		name      string
		mapping   DLQMapping
		wantError bool
		errorMsg  string
	}{
		{
			name: "valid mapping",
			mapping: DLQMapping{
				Name:          "test",
				Enabled:       true,
				DLQTopic:      "test.dlq",
				OriginalTopic: "test.events",
				ConsumerGroup: "dlq-consumer-test",
			},
			wantError: false,
		},
		{
			name: "missing name",
			mapping: DLQMapping{
				DLQTopic:      "test.dlq",
				OriginalTopic: "test.events",
				ConsumerGroup: "dlq-consumer-test",
			},
			wantError: true,
			errorMsg:  "name is required",
		},
		{
			name: "missing dlq_topic",
			mapping: DLQMapping{
				Name:          "test",
				OriginalTopic: "test.events",
				ConsumerGroup: "dlq-consumer-test",
			},
			wantError: true,
			errorMsg:  "dlq_topic is required",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			err := tt.mapping.Validate()
			if tt.wantError {
				assert.Error(t, err)
				assert.Contains(t, err.Error(), tt.errorMsg)
			} else {
				assert.NoError(t, err)
			}
		})
	}
}

func TestGetRetryStrategy(t *testing.T) {
	defaults := &DefaultConfig{
		RetryStrategy: RetryStrategyConfig{
			MaxRetries: 3,
			Backoff: []BackoffConfig{
				{RetryCount: 0, Delay: 1 * time.Hour},
				{RetryCount: 1, Delay: 6 * time.Hour},
				{RetryCount: 2, Delay: 24 * time.Hour},
			},
		},
	}

	t.Run("uses custom strategy", func(t *testing.T) {
		custom := &RetryStrategyConfig{
			MaxRetries: 2,
			Backoff: []BackoffConfig{
				{RetryCount: 0, Delay: 30 * time.Minute},
				{RetryCount: 1, Delay: 2 * time.Hour},
			},
		}

		mapping := DLQMapping{
			Name:          "test",
			RetryStrategy: custom,
		}

		strategy := mapping.GetRetryStrategy(defaults)
		assert.Equal(t, 2, strategy.MaxRetries)
		assert.Equal(t, 30*time.Minute, strategy.Backoff[0].Delay)
	})

	t.Run("uses default strategy", func(t *testing.T) {
		mapping := DLQMapping{
			Name:          "test",
			RetryStrategy: nil,
		}

		strategy := mapping.GetRetryStrategy(defaults)
		assert.Equal(t, 3, strategy.MaxRetries)
		assert.Equal(t, 1*time.Hour, strategy.Backoff[0].Delay)
	})
}

func TestGetBackoffDelay(t *testing.T) {
	strategy := RetryStrategyConfig{
		MaxRetries: 3,
		Backoff: []BackoffConfig{
			{RetryCount: 0, Delay: 1 * time.Hour},
			{RetryCount: 1, Delay: 6 * time.Hour},
			{RetryCount: 2, Delay: 24 * time.Hour},
		},
	}

	tests := []struct {
		name         string
		retryCount   int
		wantDelay    time.Duration
		wantError    bool
	}{
		{"retry 0", 0, 1 * time.Hour, false},
		{"retry 1", 1, 6 * time.Hour, false},
		{"retry 2", 2, 24 * time.Hour, false},
		{"invalid negative", -1, 0, true},
		{"invalid too high", 3, 0, true},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			delay, err := strategy.GetBackoffDelay(tt.retryCount)
			if tt.wantError {
				assert.Error(t, err)
			} else {
				assert.NoError(t, err)
				assert.Equal(t, tt.wantDelay, delay)
			}
		})
	}
}

func TestConfigValidation_DuplicateNames(t *testing.T) {
	config := &Config{
		Version: "1.0",
		Defaults: DefaultConfig{
			RetryStrategy: RetryStrategyConfig{
				MaxRetries: 1,
				Backoff: []BackoffConfig{
					{RetryCount: 0, Delay: 1 * time.Hour},
				},
			},
			ErrorCategorization: ErrorCategorizationConfig{
				TransientPatterns: []string{"timeout"},
				PermanentPatterns: []string{"validation"},
			},
		},
		Mappings: []DLQMapping{
			{
				Name:          "test",
				Enabled:       true,
				DLQTopic:      "test1.dlq",
				OriginalTopic: "test1.events",
				ConsumerGroup: "group1",
			},
			{
				Name:          "test", // Duplicate!
				Enabled:       true,
				DLQTopic:      "test2.dlq",
				OriginalTopic: "test2.events",
				ConsumerGroup: "group2",
			},
		},
	}

	err := config.Validate()
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "duplicate DLQ name: test")
}
