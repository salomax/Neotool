package application

import (
	"fmt"
	"strings"

	"github.com/caarlos0/env/v10"
)

type Config struct {
	Database    DatabaseConfig
	Job         JobConfig
	CVM         CVMConfig
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
	// Catalog: chunk-level parallelism — N workers upsert chunks concurrently
	CatalogWorkers     int `env:"CVM_CATALOG_WORKERS"      envDefault:"4"`
	CatalogQueueBuffer int `env:"CVM_CATALOG_QUEUE_BUFFER" envDefault:"2"`
	CatalogChunkSize   int `env:"CVM_CATALOG_CHUNK_SIZE"   envDefault:"500"`

	// NAV: month-level parallelism — N workers each process one month file concurrently.
	// Keep low (default 2) to bound disk usage: each active task holds one temp ZIP file.
	NAVWorkers     int `env:"CVM_NAV_WORKERS"      envDefault:"2"`
	NAVQueueBuffer int `env:"CVM_NAV_QUEUE_BUFFER" envDefault:"2"`
	// Rows accumulated before each pgx.Batch commit within a month task
	NAVChunkSize int `env:"CVM_NAV_CHUNK_SIZE" envDefault:"500"`

	// Backfill: max years of NAV history loaded on first run (no last_data_date)
	NAVBackfillYears int `env:"CVM_NAV_BACKFILL_YEARS" envDefault:"5"`
}

type CVMConfig struct {
	BaseURL        string `env:"CVM_BASE_URL"        envDefault:"https://dados.cvm.gov.br"`
	TimeoutSeconds int    `env:"CVM_TIMEOUT_SECONDS" envDefault:"120"`
}

type HealthCheckConfig struct {
	Enabled bool `env:"HEALTH_CHECK_ENABLED" envDefault:"true"`
	Port    int  `env:"HEALTH_CHECK_PORT"    envDefault:"8080"`
}

type LoggingConfig struct {
	LogConfig bool `env:"CVM_LOG_CONFIG" envDefault:"false"`
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
	if c.Job.CatalogWorkers < 1 {
		errs = append(errs, "CVM_CATALOG_WORKERS must be >= 1")
	}
	if c.Job.CatalogQueueBuffer < 1 {
		errs = append(errs, "CVM_CATALOG_QUEUE_BUFFER must be >= 1")
	}
	if c.Job.CatalogChunkSize < 100 || c.Job.CatalogChunkSize > 2000 {
		errs = append(errs, "CVM_CATALOG_CHUNK_SIZE must be between 100 and 2000")
	}
	if c.Job.NAVWorkers < 1 || c.Job.NAVWorkers > 8 {
		errs = append(errs, "CVM_NAV_WORKERS must be between 1 and 8")
	}
	if c.Job.NAVQueueBuffer < 1 {
		errs = append(errs, "CVM_NAV_QUEUE_BUFFER must be >= 1")
	}
	if c.Job.NAVChunkSize < 100 || c.Job.NAVChunkSize > 2000 {
		errs = append(errs, "CVM_NAV_CHUNK_SIZE must be between 100 and 2000")
	}
	if c.Job.NAVBackfillYears < 1 || c.Job.NAVBackfillYears > 10 {
		errs = append(errs, "CVM_NAV_BACKFILL_YEARS must be between 1 and 10")
	}
	if c.CVM.TimeoutSeconds < 10 {
		errs = append(errs, "CVM_TIMEOUT_SECONDS must be >= 10")
	}

	if len(errs) > 0 {
		return fmt.Errorf("config validation failed: %s", strings.Join(errs, "; "))
	}
	return nil
}
