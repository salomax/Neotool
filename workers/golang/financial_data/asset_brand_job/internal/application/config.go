package application

import (
	"fmt"
	"strings"

	"github.com/caarlos0/env/v10"
)

type Config struct {
	Database    DatabaseConfig
	Job         JobConfig
	LogoDev     LogoDevConfig
	HealthCheck HealthCheckConfig
	Logging     LoggingConfig
}

type DatabaseConfig struct {
	Host     string `env:"POSTGRES_HOST"     envDefault:"localhost"`
	Port     int    `env:"POSTGRES_PORT"     envDefault:"5432"`
	Database string `env:"POSTGRES_DB"       envDefault:"neotool_db"`
	User     string `env:"POSTGRES_USER"     envDefault:"neotool"`
	Password string `env:"POSTGRES_PASSWORD,required"`
	PoolMin  int    `env:"POSTGRES_POOL_MIN" envDefault:"2"`
	PoolMax  int    `env:"POSTGRES_POOL_MAX" envDefault:"10"`
	SSLMode  string `env:"POSTGRES_SSL_MODE" envDefault:"prefer"`
}

func (c DatabaseConfig) ConnectionString() string {
	return fmt.Sprintf(
		"postgres://%s:%s@%s:%d/%s?pool_min_conns=%d&pool_max_conns=%d&sslmode=%s",
		c.User, c.Password, c.Host, c.Port, c.Database, c.PoolMin, c.PoolMax, c.SSLMode,
	)
}

type JobConfig struct {
	BatchLimit           int     `env:"ASSET_BRAND_BATCH_LIMIT"            envDefault:"250"`
	ThrottleMS           int     `env:"ASSET_BRAND_THROTTLE_MS"            envDefault:"250"`
	AutoApproveThreshold float64 `env:"ASSET_BRAND_AUTO_APPROVE_THRESHOLD" envDefault:"0.93"`
}

type LogoDevConfig struct {
	BaseURL        string `env:"LOGO_DEV_BASE_URL"         envDefault:"https://api.logo.dev"`
	Token          string `env:"LOGO_DEV_TOKEN,required"`
	TimeoutSeconds int    `env:"LOGO_DEV_TIMEOUT_SECONDS"  envDefault:"15"`
}

type HealthCheckConfig struct {
	Enabled bool `env:"HEALTH_CHECK_ENABLED" envDefault:"true"`
	Port    int  `env:"HEALTH_CHECK_PORT"    envDefault:"8080"`
}

type LoggingConfig struct {
	LogConfig bool `env:"ASSET_BRAND_LOG_CONFIG" envDefault:"false"`
}

func LoadConfig() (*Config, error) {
	cfg := &Config{}
	if err := env.Parse(cfg); err != nil {
		return nil, fmt.Errorf("parsing config: %w", err)
	}
	return cfg, cfg.Validate()
}

func (c *Config) Validate() error {
	var errs []string

	if c.Database.Password == "" {
		errs = append(errs, "POSTGRES_PASSWORD is required")
	}
	if c.Job.BatchLimit < 1 || c.Job.BatchLimit > 5000 {
		errs = append(errs, "ASSET_BRAND_BATCH_LIMIT must be between 1 and 5000")
	}
	if c.Job.ThrottleMS < 0 || c.Job.ThrottleMS > 60000 {
		errs = append(errs, "ASSET_BRAND_THROTTLE_MS must be between 0 and 60000")
	}
	if c.Job.AutoApproveThreshold < 0 || c.Job.AutoApproveThreshold > 1 {
		errs = append(errs, "ASSET_BRAND_AUTO_APPROVE_THRESHOLD must be between 0 and 1")
	}
	if c.LogoDev.BaseURL == "" {
		errs = append(errs, "LOGO_DEV_BASE_URL must not be empty")
	}
	if c.LogoDev.Token == "" {
		errs = append(errs, "LOGO_DEV_TOKEN is required")
	}
	if c.LogoDev.TimeoutSeconds < 5 {
		errs = append(errs, "LOGO_DEV_TIMEOUT_SECONDS must be >= 5")
	}

	if len(errs) > 0 {
		return fmt.Errorf("config validation failed: %s", strings.Join(errs, "; "))
	}
	return nil
}
