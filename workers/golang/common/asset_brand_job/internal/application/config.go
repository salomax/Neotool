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
	Security    SecurityConfig
	Assets      AssetsConfig
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
	BatchLimit              int     `env:"ASSET_BRAND_BATCH_LIMIT"              envDefault:"0"`
	ThrottleMS              int     `env:"ASSET_BRAND_THROTTLE_MS"              envDefault:"250"`
	AutoApproveThreshold    float64 `env:"ASSET_BRAND_AUTO_APPROVE_THRESHOLD"   envDefault:"0.93"`
	RunAssetBrandHydrator   bool    `env:"RUN_ASSET_BRAND_HYDRATOR"             envDefault:"true"`
	RunIssuerLogoHydrator   bool    `env:"RUN_ISSUER_LOGO_HYDRATOR"             envDefault:"true"`
}

type LogoDevConfig struct {
	BaseURL        string `env:"LOGO_DEV_BASE_URL"        envDefault:"https://api.logo.dev"`
	ImageBaseURL   string `env:"LOGO_DEV_IMAGE_BASE_URL"  envDefault:"https://img.logo.dev"`
	Token          string `env:"LOGO_DEV_TOKEN,required"`
	APIKey         string `env:"LOGO_DEV_API_KEY,required"`
	TimeoutSeconds int    `env:"LOGO_DEV_TIMEOUT_SECONDS" envDefault:"15"`
}

type SecurityConfig struct {
	ServiceURL     string `env:"SECURITY_SERVICE_URL"     envDefault:"http://localhost:8080"`
	ServiceID      string `env:"SECURITY_SERVICE_ID"`
	ServiceSecret  string `env:"SECURITY_SERVICE_SECRET"`
	TimeoutSeconds int    `env:"SECURITY_TIMEOUT_SECONDS" envDefault:"10"`
}

type AssetsConfig struct {
	ApolloRouterURL string `env:"ASSETS_APOLLO_ROUTER_URL"      envDefault:"http://localhost:4000/graphql"`
	Namespace       string `env:"ASSETS_NAMESPACE"              envDefault:"institution-logos"`
	GraphQLTimeout  int    `env:"ASSETS_GRAPHQL_TIMEOUT_SECONDS" envDefault:"30"`
	UploadTimeout   int    `env:"ASSETS_UPLOAD_TIMEOUT_SECONDS"  envDefault:"60"`
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
	if c.Job.BatchLimit < 0 {
		errs = append(errs, "ASSET_BRAND_BATCH_LIMIT must be >= 0 (0 means unlimited)")
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
	if c.LogoDev.ImageBaseURL == "" {
		errs = append(errs, "LOGO_DEV_IMAGE_BASE_URL must not be empty")
	}
	if c.LogoDev.Token == "" {
		errs = append(errs, "LOGO_DEV_TOKEN is required")
	}
	if c.LogoDev.APIKey == "" {
		errs = append(errs, "LOGO_DEV_API_KEY is required")
	}
	if c.LogoDev.TimeoutSeconds < 5 {
		errs = append(errs, "LOGO_DEV_TIMEOUT_SECONDS must be >= 5")
	}
	if c.Security.TimeoutSeconds < 1 {
		errs = append(errs, "SECURITY_TIMEOUT_SECONDS must be >= 1")
	}
	if c.Assets.ApolloRouterURL == "" {
		errs = append(errs, "ASSETS_APOLLO_ROUTER_URL must not be empty")
	}
	if c.Assets.Namespace == "" {
		errs = append(errs, "ASSETS_NAMESPACE must not be empty")
	}
	if c.Assets.GraphQLTimeout < 1 {
		errs = append(errs, "ASSETS_GRAPHQL_TIMEOUT_SECONDS must be >= 1")
	}
	if c.Assets.UploadTimeout < 1 {
		errs = append(errs, "ASSETS_UPLOAD_TIMEOUT_SECONDS must be >= 1")
	}

	if len(errs) > 0 {
		return fmt.Errorf("config validation failed: %s", strings.Join(errs, "; "))
	}
	return nil
}
