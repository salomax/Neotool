package application

import (
	"fmt"
	"strings"

	"github.com/caarlos0/env/v10"
)

type Config struct {
	Database    DatabaseConfig
	Job         JobConfig
	B3          B3Config
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
	// Workers: concurrent company tasks (each makes one B3 API call)
	Workers     int `env:"B3_CE_WORKERS"      envDefault:"5"`
	QueueBuffer int `env:"B3_CE_QUEUE_BUFFER" envDefault:"2"`

	// PageSize for fetching the B3 company list
	CompanyPageSize int `env:"B3_CE_COMPANY_PAGE_SIZE" envDefault:"100"`
}

type B3Config struct {
	BaseURL         string `env:"B3_CE_BASE_URL"          envDefault:"https://sistemaswebb3-listados.b3.com.br"`
	TimeoutSeconds  int    `env:"B3_CE_TIMEOUT_SECONDS"   envDefault:"30"`
	// Minimum delay between API requests per worker (milliseconds)
	RequestDelayMs  int    `env:"B3_CE_REQUEST_DELAY_MS"  envDefault:"200"`
}

type HealthCheckConfig struct {
	Enabled bool `env:"HEALTH_CHECK_ENABLED" envDefault:"true"`
	Port    int  `env:"HEALTH_CHECK_PORT"    envDefault:"8080"`
}

type LoggingConfig struct {
	LogConfig bool `env:"B3_CE_LOG_CONFIG" envDefault:"false"`
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
	if c.Job.Workers < 1 || c.Job.Workers > 20 {
		errs = append(errs, "B3_CE_WORKERS must be between 1 and 20")
	}
	if c.Job.QueueBuffer < 1 {
		errs = append(errs, "B3_CE_QUEUE_BUFFER must be >= 1")
	}
	if c.Job.CompanyPageSize < 10 || c.Job.CompanyPageSize > 500 {
		errs = append(errs, "B3_CE_COMPANY_PAGE_SIZE must be between 10 and 500")
	}
	if c.B3.TimeoutSeconds < 5 {
		errs = append(errs, "B3_CE_TIMEOUT_SECONDS must be >= 5")
	}
	if c.B3.RequestDelayMs < 0 {
		errs = append(errs, "B3_CE_REQUEST_DELAY_MS must be >= 0")
	}

	if len(errs) > 0 {
		return fmt.Errorf("config validation failed: %s", strings.Join(errs, "; "))
	}
	return nil
}
