package application

import (
	"fmt"
	"strings"

	"github.com/caarlos0/env/v10"
)

// Config holds all configuration for the b3-cotahist job, loaded from environment variables.
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
	// ChunkSize is the number of rows per BEGIN/COMMIT transaction.
	// Smaller chunks reduce deadlock scope; 1000 is a good balance.
	ChunkSize int `env:"B3_CHUNK_SIZE" envDefault:"1000"`

	// BackfillYears is the number of past years to load on the first run.
	BackfillYears int `env:"B3_BACKFILL_YEARS" envDefault:"10"`

	// GapThresholdDays: when the outstanding data window is this many days or fewer,
	// only daily files are fetched (no annual/monthly ZIPs).
	GapThresholdDays int `env:"B3_GAP_THRESHOLD_DAYS" envDefault:"3"`
}

type B3Config struct {
	// BaseURL is the root URL for COTAHIST file downloads.
	// Default: http://bvmf.bmfbovespa.com.br/InstDados/SerHist
	BaseURL string `env:"B3_BASE_URL" envDefault:"http://bvmf.bmfbovespa.com.br/InstDados/SerHist"`

	// TimeoutSeconds is the per-request HTTP timeout.
	// Annual files can be 30–150 MB, so a generous timeout is needed.
	TimeoutSeconds int `env:"B3_TIMEOUT_SECONDS" envDefault:"300"`

	// DownloadIntervalSeconds is the minimum gap between consecutive download starts.
	// B3's CDN (Cloudflare) returns 520 errors when requests arrive too close together.
	// 10–15 s is sufficient; set to 0 to disable rate limiting.
	DownloadIntervalSeconds int `env:"B3_DOWNLOAD_INTERVAL_SECONDS" envDefault:"10"`
}

type HealthCheckConfig struct {
	Enabled bool `env:"HEALTH_CHECK_ENABLED" envDefault:"true"`
	Port    int  `env:"HEALTH_CHECK_PORT"    envDefault:"8080"`
}

type LoggingConfig struct {
	LogConfig bool `env:"B3_LOG_CONFIG" envDefault:"false"`
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
	if c.Job.ChunkSize < 100 || c.Job.ChunkSize > 5000 {
		errs = append(errs, "B3_CHUNK_SIZE must be between 100 and 5000")
	}
	if c.Job.BackfillYears < 1 || c.Job.BackfillYears > 30 {
		errs = append(errs, "B3_BACKFILL_YEARS must be between 1 and 30")
	}
	if c.Job.GapThresholdDays < 0 || c.Job.GapThresholdDays > 30 {
		errs = append(errs, "B3_GAP_THRESHOLD_DAYS must be between 0 and 30")
	}
	if c.B3.TimeoutSeconds < 30 {
		errs = append(errs, "B3_TIMEOUT_SECONDS must be >= 30")
	}
	if c.B3.DownloadIntervalSeconds < 0 || c.B3.DownloadIntervalSeconds > 300 {
		errs = append(errs, "B3_DOWNLOAD_INTERVAL_SECONDS must be between 0 and 300")
	}
	if c.B3.BaseURL == "" {
		errs = append(errs, "B3_BASE_URL must not be empty")
	}

	if len(errs) > 0 {
		return fmt.Errorf("config validation failed: %s", strings.Join(errs, "; "))
	}
	return nil
}
