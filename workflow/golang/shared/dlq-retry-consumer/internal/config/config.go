package config

import (
	"fmt"
	"os"
	"time"

	"github.com/caarlos0/env/v10"
	"gopkg.in/yaml.v3"
)

// Config represents the complete DLQ retry consumer configuration
type Config struct {
	Version  string         `yaml:"version"`
	Defaults DefaultConfig  `yaml:"defaults"`
	Mappings []DLQMapping   `yaml:"dlq_mappings"`
	Kafka    KafkaConfig    `yaml:"-"` // Not in YAML, comes from env
}

// DefaultConfig holds default retry strategies and error categorization
type DefaultConfig struct {
	RetryStrategy       RetryStrategyConfig       `yaml:"retry_strategy"`
	ErrorCategorization ErrorCategorizationConfig `yaml:"error_categorization"`
}

// DLQMapping represents a single DLQ configuration
type DLQMapping struct {
	Name                string                     `yaml:"name"`
	Enabled             bool                       `yaml:"enabled"`
	DLQTopic            string                     `yaml:"dlq_topic"`
	OriginalTopic       string                     `yaml:"original_topic"`
	ConsumerGroup       string                     `yaml:"consumer_group"`
	Description         string                     `yaml:"description"`
	RetryStrategy       *RetryStrategyConfig       `yaml:"retry_strategy,omitempty"`
	ErrorCategorization *ErrorCategorizationConfig `yaml:"error_categorization,omitempty"`
}

// RetryStrategyConfig defines how retries are handled
type RetryStrategyConfig struct {
	MaxRetries int             `yaml:"max_retries"`
	Backoff    []BackoffConfig `yaml:"backoff"`
}

// BackoffConfig defines delay for a specific retry count
type BackoffConfig struct {
	RetryCount int           `yaml:"retry_count"`
	Delay      time.Duration `yaml:"delay"`
}

// ErrorCategorizationConfig defines patterns for error classification
type ErrorCategorizationConfig struct {
	TransientPatterns []string `yaml:"transient_patterns"`
	PermanentPatterns []string `yaml:"permanent_patterns"`
}

// KafkaConfig holds Kafka connection settings from environment
type KafkaConfig struct {
	Brokers string `env:"KAFKA_BROKERS" envDefault:"localhost:9092"`
}

// LoadConfig loads configuration from YAML file and environment variables
func LoadConfig() (*Config, error) {
	// Get config path from environment or use default
	configPath := os.Getenv("DLQ_CONFIG_PATH")
	if configPath == "" {
		configPath = "config/dlq-mappings.yaml"
	}

	// Read YAML file
	data, err := os.ReadFile(configPath)
	if err != nil {
		return nil, fmt.Errorf("failed to read config file %s: %w", configPath, err)
	}

	// Parse YAML
	var config Config
	if err := yaml.Unmarshal(data, &config); err != nil {
		return nil, fmt.Errorf("failed to parse YAML config: %w", err)
	}

	// Load Kafka config from environment
	if err := env.Parse(&config.Kafka); err != nil {
		return nil, fmt.Errorf("failed to parse Kafka environment variables: %w", err)
	}

	// Validate configuration
	if err := config.Validate(); err != nil {
		return nil, fmt.Errorf("configuration validation failed: %w", err)
	}

	return &config, nil
}

// Validate checks if configuration is valid
func (c *Config) Validate() error {
	// Check version
	if c.Version != "1.0" {
		return fmt.Errorf("unsupported config version: %s (expected 1.0)", c.Version)
	}

	// Validate defaults
	if err := c.Defaults.Validate(); err != nil {
		return fmt.Errorf("default config validation failed: %w", err)
	}

	// Validate each DLQ mapping
	names := make(map[string]bool)
	for i, mapping := range c.Mappings {
		if err := mapping.Validate(); err != nil {
			return fmt.Errorf("DLQ mapping %d (%s) validation failed: %w", i, mapping.Name, err)
		}

		// Check for duplicate names
		if names[mapping.Name] {
			return fmt.Errorf("duplicate DLQ name: %s", mapping.Name)
		}
		names[mapping.Name] = true
	}

	if len(c.Mappings) == 0 {
		return fmt.Errorf("no DLQ mappings configured")
	}

	return nil
}

// Validate checks if default config is valid
func (dc *DefaultConfig) Validate() error {
	if err := dc.RetryStrategy.Validate(); err != nil {
		return fmt.Errorf("retry strategy validation failed: %w", err)
	}

	if err := dc.ErrorCategorization.Validate(); err != nil {
		return fmt.Errorf("error categorization validation failed: %w", err)
	}

	return nil
}

// Validate checks if DLQ mapping is valid
func (dm *DLQMapping) Validate() error {
	if dm.Name == "" {
		return fmt.Errorf("name is required")
	}

	if dm.DLQTopic == "" {
		return fmt.Errorf("dlq_topic is required")
	}

	if dm.OriginalTopic == "" {
		return fmt.Errorf("original_topic is required")
	}

	if dm.ConsumerGroup == "" {
		return fmt.Errorf("consumer_group is required")
	}

	// Validate custom retry strategy if provided
	if dm.RetryStrategy != nil {
		if err := dm.RetryStrategy.Validate(); err != nil {
			return fmt.Errorf("custom retry strategy validation failed: %w", err)
		}
	}

	// Validate custom error categorization if provided
	if dm.ErrorCategorization != nil {
		if err := dm.ErrorCategorization.Validate(); err != nil {
			return fmt.Errorf("custom error categorization validation failed: %w", err)
		}
	}

	return nil
}

// Validate checks if retry strategy is valid
func (rs *RetryStrategyConfig) Validate() error {
	if rs.MaxRetries < 0 || rs.MaxRetries > 10 {
		return fmt.Errorf("max_retries must be between 0 and 10, got %d", rs.MaxRetries)
	}

	if len(rs.Backoff) != rs.MaxRetries {
		return fmt.Errorf("backoff array must have exactly %d entries (max_retries), got %d", rs.MaxRetries, len(rs.Backoff))
	}

	// Validate each backoff entry
	for i, backoff := range rs.Backoff {
		if backoff.RetryCount != i {
			return fmt.Errorf("backoff[%d].retry_count must be %d, got %d", i, i, backoff.RetryCount)
		}

		if backoff.Delay <= 0 {
			return fmt.Errorf("backoff[%d].delay must be positive, got %s", i, backoff.Delay)
		}
	}

	return nil
}

// Validate checks if error categorization is valid
func (ec *ErrorCategorizationConfig) Validate() error {
	if len(ec.TransientPatterns) == 0 {
		return fmt.Errorf("transient_patterns cannot be empty")
	}

	if len(ec.PermanentPatterns) == 0 {
		return fmt.Errorf("permanent_patterns cannot be empty")
	}

	return nil
}

// GetRetryStrategy returns the retry strategy for a DLQ mapping (custom or default)
func (dm *DLQMapping) GetRetryStrategy(defaults *DefaultConfig) *RetryStrategyConfig {
	if dm.RetryStrategy != nil {
		return dm.RetryStrategy
	}
	return &defaults.RetryStrategy
}

// GetErrorCategorization returns the error categorization for a DLQ mapping (custom or default)
func (dm *DLQMapping) GetErrorCategorization(defaults *DefaultConfig) *ErrorCategorizationConfig {
	if dm.ErrorCategorization != nil {
		return dm.ErrorCategorization
	}
	return &defaults.ErrorCategorization
}

// GetBackoffDelay returns the delay for a specific retry count
func (rs *RetryStrategyConfig) GetBackoffDelay(retryCount int) (time.Duration, error) {
	if retryCount < 0 || retryCount >= len(rs.Backoff) {
		return 0, fmt.Errorf("invalid retry_count %d (max: %d)", retryCount, len(rs.Backoff)-1)
	}
	return rs.Backoff[retryCount].Delay, nil
}
