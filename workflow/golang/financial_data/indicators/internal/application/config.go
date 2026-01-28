package application

import (
	"fmt"
	"strings"
	"time"

	"github.com/caarlos0/env/v10"
)

// Config holds all configuration for the indicators job
type Config struct {
	Database    DatabaseConfig
	Job         JobConfig
	Sources     SourceConfig
	HealthCheck HealthCheckConfig
	Filters     FilterConfig
}

// DatabaseConfig holds PostgreSQL connection settings
type DatabaseConfig struct {
	Host     string `env:"POSTGRES_HOST" envDefault:"localhost"`
	Port     int    `env:"POSTGRES_PORT" envDefault:"5432"`
	Database string `env:"POSTGRES_DB" envDefault:"neotool_db"`
	User     string `env:"POSTGRES_USER" envDefault:"neotool"`
	Password string `env:"POSTGRES_PASSWORD,required"`
	PoolMin  int    `env:"POSTGRES_POOL_MIN" envDefault:"2"`
	PoolMax  int    `env:"POSTGRES_POOL_MAX" envDefault:"10"`
	SSLMode  string `env:"POSTGRES_SSL_MODE" envDefault:"prefer"`
}

// ConnectionString returns a PostgreSQL connection string
func (c DatabaseConfig) ConnectionString() string {
	return fmt.Sprintf(
		"postgres://%s:%s@%s:%d/%s?pool_min_conns=%d&pool_max_conns=%d&sslmode=%s",
		c.User, c.Password, c.Host, c.Port, c.Database, c.PoolMin, c.PoolMax, c.SSLMode,
	)
}

// JobConfig holds worker pool configuration
type JobConfig struct {
	MaxWorkers            int `env:"INDICATORS_MAX_WORKERS" envDefault:"5"`
	QueueBufferMultiplier int `env:"INDICATORS_QUEUE_BUFFER_MULTIPLIER" envDefault:"2"`
	BackfillMonths        int `env:"INDICATORS_BACKFILL_MONTHS" envDefault:"120"`
	BackfillYears         int `env:"INDICATORS_BACKFILL_YEARS" envDefault:"10"`
	IncrementalMonths     int `env:"INDICATORS_INCREMENTAL_MONTHS" envDefault:"6"`
	IncrementalYears      int `env:"INDICATORS_INCREMENTAL_YEARS" envDefault:"2"`
	IncrementalDays       int `env:"INDICATORS_INCREMENTAL_DAYS" envDefault:"30"`
}

// SourceConfig holds external source settings
type SourceConfig struct {
	SIDRATimeoutSeconds       int           `env:"SIDRA_TIMEOUT_SECONDS" envDefault:"60"`
	SIDRATimeout              time.Duration `envDefault:"60s"`
	SGSTimeoutSeconds         int           `env:"SGS_TIMEOUT_SECONDS" envDefault:"60"`
	SGSTimeout                time.Duration `envDefault:"60s"`
	SGSMinIntervalMS          int           `env:"SGS_MIN_INTERVAL_MS" envDefault:"0"`
	SGSMinInterval            time.Duration `envDefault:"0s"`
	COMEXSTATTimeoutSeconds   int           `env:"COMEXSTAT_TIMEOUT_SECONDS" envDefault:"60"`
	COMEXSTATTimeout          time.Duration `envDefault:"60s"`
	COMEXSTATUserAgent        string        `env:"COMEXSTAT_USER_AGENT" envDefault:"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"`
	COMEXSTATReferer          string        `env:"COMEXSTAT_REFERER" envDefault:"https://comexstat.mdic.gov.br/pt/home"`
	COMEXSTATOrigin           string        `env:"COMEXSTAT_ORIGIN" envDefault:"https://comexstat.mdic.gov.br"`
	COMEXSTATAccept           string        `env:"COMEXSTAT_ACCEPT" envDefault:"application/json, text/plain, */*"`
	COMEXSTATMinIntervalMS    int           `env:"COMEXSTAT_MIN_INTERVAL_MS" envDefault:"1000"`
	COMEXSTATMinInterval      time.Duration `envDefault:"1s"`
	COMEXSTATMaxRetries       int           `env:"COMEXSTAT_MAX_RETRIES" envDefault:"2"`
	COMEXSTATRetryWaitSeconds int           `env:"COMEXSTAT_RETRY_WAIT_SECONDS" envDefault:"10"`
	COMEXSTATRetryWait        time.Duration `envDefault:"10s"`
	IPEADATATimeoutSeconds    int           `env:"IPEADATA_TIMEOUT_SECONDS" envDefault:"60"`
	IPEADATATimeout           time.Duration `envDefault:"60s"`
	IPEADATABaseURL           string        `env:"IPEADATA_BASE_URL" envDefault:"http://www.ipeadata.gov.br/api/odata4"`
}

// HealthCheckConfig holds health check server settings
type HealthCheckConfig struct {
	Enabled bool `env:"HEALTH_CHECK_ENABLED" envDefault:"true"`
	Port    int  `env:"HEALTH_CHECK_PORT" envDefault:"8080"`
}

// FilterConfig allows limiting execution to a specific indicator/variation
type FilterConfig struct {
	IndicatorLabel string `env:"INDICATOR_FILTER"`
	VariationLabel string `env:"VARIATION_FILTER"`
}

// LoadConfig loads configuration from environment variables
func LoadConfig() (*Config, error) {
	cfg := &Config{}
	if err := env.Parse(cfg); err != nil {
		return nil, fmt.Errorf("failed to parse config: %w", err)
	}

	cfg.Sources.SIDRATimeout = time.Duration(cfg.Sources.SIDRATimeoutSeconds) * time.Second
	cfg.Sources.SGSTimeout = time.Duration(cfg.Sources.SGSTimeoutSeconds) * time.Second
	cfg.Sources.SGSMinInterval = time.Duration(cfg.Sources.SGSMinIntervalMS) * time.Millisecond
	cfg.Sources.COMEXSTATTimeout = time.Duration(cfg.Sources.COMEXSTATTimeoutSeconds) * time.Second
	cfg.Sources.COMEXSTATMinInterval = time.Duration(cfg.Sources.COMEXSTATMinIntervalMS) * time.Millisecond
	cfg.Sources.COMEXSTATRetryWait = time.Duration(cfg.Sources.COMEXSTATRetryWaitSeconds) * time.Second
	cfg.Sources.IPEADATATimeout = time.Duration(cfg.Sources.IPEADATATimeoutSeconds) * time.Second

	if err := cfg.Validate(); err != nil {
		return nil, err
	}

	return cfg, nil
}

// Validate validates the configuration
func (c *Config) Validate() error {
	var errors []string

	if c.Database.Password == "" {
		errors = append(errors, "POSTGRES_PASSWORD is required")
	}
	if c.Job.MaxWorkers < 1 {
		errors = append(errors, "INDICATORS_MAX_WORKERS must be at least 1")
	}
	if c.Job.QueueBufferMultiplier < 1 {
		errors = append(errors, "INDICATORS_QUEUE_BUFFER_MULTIPLIER must be at least 1")
	}
	if c.Job.BackfillMonths < 1 {
		errors = append(errors, "INDICATORS_BACKFILL_MONTHS must be at least 1")
	}
	if c.Job.BackfillYears < 1 {
		errors = append(errors, "INDICATORS_BACKFILL_YEARS must be at least 1")
	}
	if c.Job.IncrementalMonths < 1 {
		errors = append(errors, "INDICATORS_INCREMENTAL_MONTHS must be at least 1")
	}
	if c.Job.IncrementalYears < 1 {
		errors = append(errors, "INDICATORS_INCREMENTAL_YEARS must be at least 1")
	}
	if c.Job.IncrementalDays < 1 {
		errors = append(errors, "INDICATORS_INCREMENTAL_DAYS must be at least 1")
	}
	if c.Sources.SIDRATimeoutSeconds < 1 {
		errors = append(errors, "SIDRA_TIMEOUT_SECONDS must be at least 1")
	}
	if c.Sources.SGSTimeoutSeconds < 1 {
		errors = append(errors, "SGS_TIMEOUT_SECONDS must be at least 1")
	}
	if c.Sources.COMEXSTATTimeoutSeconds < 1 {
		errors = append(errors, "COMEXSTAT_TIMEOUT_SECONDS must be at least 1")
	}
	if c.Sources.SGSMinIntervalMS < 0 {
		errors = append(errors, "SGS_MIN_INTERVAL_MS must be >= 0")
	}
	if c.Sources.COMEXSTATMinIntervalMS < 0 {
		errors = append(errors, "COMEXSTAT_MIN_INTERVAL_MS must be >= 0")
	}
	if c.Sources.COMEXSTATMaxRetries < 0 {
		errors = append(errors, "COMEXSTAT_MAX_RETRIES must be >= 0")
	}
	if c.Sources.COMEXSTATRetryWaitSeconds < 0 {
		errors = append(errors, "COMEXSTAT_RETRY_WAIT_SECONDS must be >= 0")
	}
	if c.Sources.IPEADATATimeoutSeconds < 1 {
		errors = append(errors, "IPEADATA_TIMEOUT_SECONDS must be at least 1")
	}

	if len(errors) > 0 {
		return fmt.Errorf("configuration validation failed: %s", strings.Join(errors, "; "))
	}

	return nil
}
