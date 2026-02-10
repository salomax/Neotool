package application

import (
	"fmt"
	"strings"
	"time"

	"github.com/caarlos0/env/v10"
	"github.com/invistus/bacen-ifdata/internal/domain"
)

// Config holds all configuration for the application
type Config struct {
	Database    DatabaseConfig
	BACEN       BACENConfig
	Kafka       KafkaConfig
	Retry       RetryConfig
	Extraction  ExtractionConfig
	Promotion   PromotionConfig
	HealthCheck HealthCheckConfig
}

// DatabaseConfig holds PostgreSQL configuration
type DatabaseConfig struct {
	Host     string `env:"POSTGRES_HOST" envDefault:"localhost"`
	Port     int    `env:"POSTGRES_PORT" envDefault:"6432"`
	Database string `env:"POSTGRES_DB" envDefault:"neotool_db"`
	User     string `env:"POSTGRES_USER" envDefault:"neotool"`
	Password string `env:"POSTGRES_PASSWORD,required"`
	PoolMin  int    `env:"POSTGRES_POOL_MIN" envDefault:"2"`
	PoolMax  int    `env:"POSTGRES_POOL_MAX" envDefault:"10"`
	SSLMode  string `env:"POSTGRES_SSL_MODE" envDefault:"prefer"` // disable, allow, prefer, require, verify-ca, verify-full
}

// ConnectionString returns the PostgreSQL connection string with TLS configuration
func (c DatabaseConfig) ConnectionString() string {
	return fmt.Sprintf(
		"postgres://%s:%s@%s:%d/%s?pool_min_conns=%d&pool_max_conns=%d&sslmode=%s",
		c.User, c.Password, c.Host, c.Port, c.Database, c.PoolMin, c.PoolMax, c.SSLMode,
	)
}

// BACENConfig holds BACEN API configuration
type BACENConfig struct {
	BaseURL              string        `env:"BACEN_BASE_URL" envDefault:"https://olinda.bcb.gov.br/olinda/servico/IFDATA/versao/v1/odata/"`
	TimeoutSeconds       int           `env:"BACEN_TIMEOUT_SECONDS" envDefault:"60"`
	Timeout              time.Duration `envDefault:"60s"`
	MaxRequestsPerSecond float64       `env:"BACEN_MAX_REQUESTS_PER_SECOND" envDefault:"2.0"`
	BurstSize            int           `env:"BACEN_BURST_SIZE" envDefault:"5"`
}

// KafkaConfig holds Kafka configuration
type KafkaConfig struct {
	Brokers         string `env:"KAFKA_BROKERS" envDefault:"localhost:9092"`
	Topic           string `env:"KAFKA_TOPIC" envDefault:"financial-data.institution.upserted"`
	CompressionType string `env:"KAFKA_COMPRESSION_TYPE" envDefault:"gzip"`
}

// BrokerList returns Kafka brokers as a slice
func (c KafkaConfig) BrokerList() []string {
	return strings.Split(c.Brokers, ",")
}

// RetryConfig holds retry policy configuration
type RetryConfig struct {
	MaxRetries         int     `env:"RETRY_MAX_RETRIES" envDefault:"3"`
	InitialDelayMS     int     `env:"RETRY_INITIAL_DELAY_MS" envDefault:"1000"`
	MaxDelayMS         int     `env:"RETRY_MAX_DELAY_MS" envDefault:"30000"`
	BackoffMultiplier  float64 `env:"RETRY_BACKOFF_MULTIPLIER" envDefault:"2.0"`
	Jitter             bool    `env:"RETRY_JITTER" envDefault:"true"`
	InitialDelay       time.Duration
	MaxDelay           time.Duration
}

// ExtractionConfig holds extraction-specific configuration
type ExtractionConfig struct {
	MaxWorkers          int `env:"EXTRACTION_MAX_WORKERS" envDefault:"5"`
	MaxIterations       int `env:"EXTRACTION_MAX_ITERATIONS" envDefault:"1000"`
	PageSize            int `env:"EXTRACTION_PAGE_SIZE" envDefault:"20000"`
	MaxTaskRetries      int `env:"EXTRACTION_MAX_TASK_RETRIES" envDefault:"3"`
	TaskPollInterval    int `env:"EXTRACTION_TASK_POLL_INTERVAL_MS" envDefault:"1000"`
	TaskPollIntervalDur time.Duration
}

// PromotionConfig holds promotion-specific configuration
type PromotionConfig struct {
	MaxWorkers           int           `env:"PROMOTION_MAX_WORKERS" envDefault:"3"`
	ChunkSize            int           `env:"PROMOTION_CHUNK_SIZE" envDefault:"5000"`
	WaitPollIntervalMs   int           `env:"PROMOTION_WAIT_POLL_INTERVAL_MS" envDefault:"5000"`
	WaitPollIntervalDur  time.Duration // set in LoadConfig from WaitPollIntervalMs
}

// HealthCheckConfig holds health check server configuration
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

	// Convert milliseconds to durations
	cfg.Retry.InitialDelay = time.Duration(cfg.Retry.InitialDelayMS) * time.Millisecond
	cfg.Retry.MaxDelay = time.Duration(cfg.Retry.MaxDelayMS) * time.Millisecond
	cfg.BACEN.Timeout = time.Duration(cfg.BACEN.TimeoutSeconds) * time.Second
	cfg.Extraction.TaskPollIntervalDur = time.Duration(cfg.Extraction.TaskPollInterval) * time.Millisecond
	cfg.Promotion.WaitPollIntervalDur = time.Duration(cfg.Promotion.WaitPollIntervalMs) * time.Millisecond

	return cfg, nil
}

// Validate validates the configuration
func (c *Config) Validate() error {
	if c.Database.Password == "" {
		return fmt.Errorf("POSTGRES_PASSWORD is required")
	}

	if c.Extraction.MaxWorkers < 1 {
		return fmt.Errorf("EXTRACTION_MAX_WORKERS must be at least 1")
	}

	if c.Extraction.PageSize < 1 {
		return fmt.Errorf("EXTRACTION_PAGE_SIZE must be at least 1")
	}

	if c.Promotion.MaxWorkers < 1 {
		return fmt.Errorf("PROMOTION_MAX_WORKERS must be at least 1")
	}

	if c.Promotion.ChunkSize < 100 {
		return fmt.Errorf("PROMOTION_CHUNK_SIZE must be at least 100")
	}

	if c.Retry.MaxRetries < 0 {
		return fmt.Errorf("RETRY_MAX_RETRIES must be non-negative")
	}

	return nil
}

// ToDomainExtractionConfig converts to domain.ExtractionConfig
func (c *ExtractionConfig) ToDomain() *domain.ExtractionConfig {
	return &domain.ExtractionConfig{
		MaxWorkers:          c.MaxWorkers,
		MaxIterations:       c.MaxIterations,
		PageSize:            c.PageSize,
		MaxTaskRetries:      c.MaxTaskRetries,
		TaskPollIntervalDur: int64(c.TaskPollInterval),
	}
}
