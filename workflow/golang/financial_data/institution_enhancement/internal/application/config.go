package application

import (
	"fmt"
	"strings"

	"github.com/caarlos0/env/v10"
)

// Config holds all application configuration
type Config struct {
	Database    DatabaseConfig    `envPrefix:"POSTGRES_"`
	Kafka       KafkaConfig       `envPrefix:"KAFKA_"`
	CNPJA       CNPJAConfig       `envPrefix:"CNPJA_"`
	Serper      SerperConfig      `envPrefix:"SERPER_"`
	WebsitesCSV WebsitesCSVConfig `envPrefix:"WEBSITES_CSV_"`
	HealthCheck HealthCheckConfig `envPrefix:"HEALTH_CHECK_"`
	Retry       RetryConfig       `envPrefix:"RETRY_"`
	WorkerPool  WorkerPoolConfig  `envPrefix:"WORKER_POOL_"`
	LogoDev     LogoDevConfig     `envPrefix:"LOGO_DEV_"`
	Security    SecurityConfig    `envPrefix:"SECURITY_"`
	Assets      AssetsConfig      `envPrefix:"ASSETS_"`
}

// DatabaseConfig holds PostgreSQL configuration
type DatabaseConfig struct {
	Host     string `env:"HOST" envDefault:"localhost"`
	Port     int    `env:"PORT" envDefault:"6432"`
	Database string `env:"DB" envDefault:"neotool_db"`
	User     string `env:"USER" envDefault:"neotool"`
	Password string `env:"PASSWORD,required"`
	PoolMin  int    `env:"POOL_MIN" envDefault:"2"`
	PoolMax  int    `env:"POOL_MAX" envDefault:"10"`
}

// ConnectionString returns PostgreSQL connection string
func (c DatabaseConfig) ConnectionString() string {
	return fmt.Sprintf("host=%s port=%d dbname=%s user=%s password=%s sslmode=disable pool_min_conns=%d pool_max_conns=%d",
		c.Host, c.Port, c.Database, c.User, c.Password, c.PoolMin, c.PoolMax)
}

// KafkaConfig holds Kafka configuration
type KafkaConfig struct {
	Brokers       string `env:"BROKERS" envDefault:"localhost:9092"`
	Topic         string `env:"TOPIC" envDefault:"financial-data.institution.upserted"`
	ConsumerGroup string `env:"CONSUMER_GROUP" envDefault:"institution-enhancement-consumer-go"`
	DLQTopic      string `env:"DLQ_TOPIC" envDefault:"financial-data.institution.upserted.enhancement.dlq"`
}

// BrokerList returns Kafka brokers as a slice
func (c KafkaConfig) BrokerList() []string {
	return strings.Split(c.Brokers, ",")
}

// CNPJAConfig holds CNPJá API configuration
type CNPJAConfig struct {
	BaseURL        string `env:"BASE_URL" envDefault:"https://open.cnpja.com"`
	Timeout        int    `env:"TIMEOUT_SECONDS" envDefault:"30"`
	RateLimitDelay int    `env:"RATE_LIMIT_SECONDS" envDefault:"15"`
}

// SerperConfig holds Serper API configuration
type SerperConfig struct {
	APIKey  string `env:"API_KEY"`
	Enabled bool   `env:"ENABLED" envDefault:"false"`
	Timeout int    `env:"TIMEOUT_SECONDS" envDefault:"10"`
}

// WebsitesCSVConfig holds websites CSV file configuration
type WebsitesCSVConfig struct {
	FilePath string `env:"PATH" envDefault:"internal/data/websites.csv"`
	Enabled  bool   `env:"ENABLED" envDefault:"true"`
}

// HealthCheckConfig holds health check server configuration
type HealthCheckConfig struct {
	Enabled bool `env:"ENABLED" envDefault:"true"`
	Port    int  `env:"PORT" envDefault:"8080"`
}

// RetryConfig holds retry policy configuration
type RetryConfig struct {
	MaxRetries        int     `env:"MAX_RETRIES" envDefault:"3"`
	InitialDelayMs    int     `env:"INITIAL_DELAY_MS" envDefault:"1000"`
	MaxDelayMs        int     `env:"MAX_DELAY_MS" envDefault:"30000"`
	BackoffMultiplier float64 `env:"BACKOFF_MULTIPLIER" envDefault:"2.0"`
	Jitter            bool    `env:"JITTER" envDefault:"true"`
	DLQMaxRetries     int     `env:"DLQ_MAX_RETRIES" envDefault:"3"`
}

// WorkerPoolConfig holds worker pool configuration
type WorkerPoolConfig struct {
	Size int `env:"SIZE" envDefault:"5"`
}

// LogoDevConfig holds logo.dev API configuration
type LogoDevConfig struct {
	Token   string `env:"TOKEN"` // From Vault
	Timeout int    `env:"TIMEOUT_SECONDS" envDefault:"30"`
}

// SecurityConfig holds security service configuration for service-to-service authentication
type SecurityConfig struct {
	Host         string `env:"HOST" envDefault:"localhost:8080"`
	ServiceID    string `env:"SERVICE_ID"`    // From Vault in k8s, manual in local
	ServiceSecret string `env:"SERVICE_SECRET"` // From Vault in k8s, manual in local
	Timeout      int    `env:"TIMEOUT_SECONDS" envDefault:"10"`
}

// AssetsConfig holds assets service configuration
type AssetsConfig struct {
	ApolloRouterURL string `env:"APOLLO_ROUTER_URL" envDefault:"http://router:4000/graphql"`
	Namespace       string `env:"NAMESPACE" envDefault:"institution-logos"`
	UploadTimeout   int    `env:"UPLOAD_TIMEOUT_SECONDS" envDefault:"60"`
	GraphQLTimeout  int    `env:"GRAPHQL_TIMEOUT_SECONDS" envDefault:"30"`
}

// LoadConfig loads configuration from environment variables
func LoadConfig() (*Config, error) {
	cfg := &Config{}
	if err := env.Parse(cfg); err != nil {
		return nil, fmt.Errorf("failed to parse config: %w", err)
	}
	return cfg, nil
}
