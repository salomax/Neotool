package application

import (
	"fmt"
	"strings"
	"time"

	"github.com/caarlos0/env/v10"
	"github.com/invistus/ratings/internal/shared"
)

// Config holds all configuration for the ratings ingestion service
type Config struct {
	Database    DatabaseConfig
	Fitch       FitchConfig
	Retry       RetryConfig
	HealthCheck HealthCheckConfig
}

// DatabaseConfig holds PostgreSQL connection settings
type DatabaseConfig struct {
	Host     string `env:"POSTGRES_HOST" envDefault:"localhost"`
	Port     int    `env:"POSTGRES_PORT" envDefault:"5432"`
	Database string `env:"POSTGRES_DB" envDefault:"neotool_db"`
	User     string `env:"POSTGRES_USER" envDefault:"neotool"`
	Password string `env:"POSTGRES_PASSWORD,required"`
	PoolMin  int    `env:"POSTGRES_POOL_MIN" envDefault:"2"`
	PoolMax  int    `env:"POSTGRES_POOL_MAX" envDefault:"5"`
	SSLMode  string `env:"POSTGRES_SSL_MODE" envDefault:"prefer"`
}

// ConnectionString returns a PostgreSQL connection string
func (c DatabaseConfig) ConnectionString() string {
	return fmt.Sprintf(
		"postgres://%s:%s@%s:%d/%s?pool_min_conns=%d&pool_max_conns=%d&sslmode=%s",
		c.User,
		c.Password,
		c.Host,
		c.Port,
		c.Database,
		c.PoolMin,
		c.PoolMax,
		c.SSLMode,
	)
}

// FitchConfig holds Fitch API settings
type FitchConfig struct {
	APIURL          string `env:"FITCH_API_URL" envDefault:"https://api.fitchratings.com/"`
	TimeoutSeconds  int    `env:"FITCH_TIMEOUT_SECONDS" envDefault:"60"`
	ThrottleSeconds int    `env:"FITCH_THROTTLE_SECONDS" envDefault:"15"`

	// Computed fields
	Timeout  time.Duration
	Throttle time.Duration
}

// RetryConfig holds retry policy settings
type RetryConfig struct {
	MaxRetries        int `env:"RETRY_MAX_RETRIES" envDefault:"3"`
	InitialDelayMS    int `env:"RETRY_INITIAL_DELAY_MS" envDefault:"1000"`
	MaxDelayMS        int `env:"RETRY_MAX_DELAY_MS" envDefault:"30000"`
	BackoffMultiplier float64 `env:"RETRY_BACKOFF_MULTIPLIER" envDefault:"2.0"`
	Jitter            bool    `env:"RETRY_JITTER" envDefault:"true"`

	// Computed fields
	InitialDelay time.Duration
	MaxDelay     time.Duration
}

// ToRetryPolicy converts the config to a shared.RetryPolicy
func (c RetryConfig) ToRetryPolicy() shared.RetryPolicy {
	return shared.RetryPolicy{
		MaxRetries:        c.MaxRetries,
		InitialDelay:      c.InitialDelay,
		MaxDelay:          c.MaxDelay,
		BackoffMultiplier: c.BackoffMultiplier,
		Jitter:            c.Jitter,
	}
}

// HealthCheckConfig holds health check server settings
type HealthCheckConfig struct {
	Enabled bool `env:"HEALTH_CHECK_ENABLED" envDefault:"true"`
	Port    int  `env:"HEALTH_CHECK_PORT" envDefault:"8080"`
}

// LoadConfig loads configuration from environment variables
func LoadConfig() (*Config, error) {
	cfg := &Config{}
	if err := env.Parse(cfg); err != nil {
		return nil, fmt.Errorf("failed to parse config: %w", err)
	}

	// Convert durations
	cfg.Fitch.Timeout = time.Duration(cfg.Fitch.TimeoutSeconds) * time.Second
	cfg.Fitch.Throttle = time.Duration(cfg.Fitch.ThrottleSeconds) * time.Second
	cfg.Retry.InitialDelay = time.Duration(cfg.Retry.InitialDelayMS) * time.Millisecond
	cfg.Retry.MaxDelay = time.Duration(cfg.Retry.MaxDelayMS) * time.Millisecond

	// Validate
	if err := cfg.Validate(); err != nil {
		return nil, err
	}

	return cfg, nil
}

// Validate validates the configuration
func (c *Config) Validate() error {
	var errors []string

	// Database validation
	if c.Database.Host == "" {
		errors = append(errors, "POSTGRES_HOST is required")
	}
	if c.Database.Database == "" {
		errors = append(errors, "POSTGRES_DB is required")
	}
	if c.Database.User == "" {
		errors = append(errors, "POSTGRES_USER is required")
	}
	if c.Database.PoolMin < 1 {
		errors = append(errors, "POSTGRES_POOL_MIN must be at least 1")
	}
	if c.Database.PoolMax < c.Database.PoolMin {
		errors = append(errors, "POSTGRES_POOL_MAX must be >= POSTGRES_POOL_MIN")
	}

	// Fitch validation
	if c.Fitch.APIURL == "" {
		errors = append(errors, "FITCH_API_URL is required")
	}
	if c.Fitch.TimeoutSeconds < 1 {
		errors = append(errors, "FITCH_TIMEOUT_SECONDS must be at least 1")
	}
	if c.Fitch.ThrottleSeconds < 1 {
		errors = append(errors, "FITCH_THROTTLE_SECONDS must be at least 1")
	}

	// Retry validation
	if c.Retry.MaxRetries < 0 {
		errors = append(errors, "RETRY_MAX_RETRIES must be non-negative")
	}
	if c.Retry.InitialDelayMS < 100 {
		errors = append(errors, "RETRY_INITIAL_DELAY_MS must be at least 100")
	}
	if c.Retry.BackoffMultiplier < 1.0 {
		errors = append(errors, "RETRY_BACKOFF_MULTIPLIER must be at least 1.0")
	}

	if len(errors) > 0 {
		return fmt.Errorf("configuration validation failed: %s", strings.Join(errors, "; "))
	}

	return nil
}
