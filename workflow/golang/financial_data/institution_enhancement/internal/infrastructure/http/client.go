package http

import (
	"context"
	"crypto/tls"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"time"

	"github.com/salomax/invistus/workflow/go/financial_data/institution_enhancement/internal/shared"
)

// Client is an HTTP client with retry logic
type Client struct {
	httpClient  *http.Client
	retryPolicy shared.RetryPolicy
	logger      *slog.Logger
}

// ClientOption configures the HTTP client
type ClientOption func(*clientConfig)

type clientConfig struct {
	insecureSkipVerify bool
}

// WithInsecureSkipVerify configures the client to skip TLS certificate verification
// WARNING: This should only be used for website reachability checks where we only
// care if the site responds, not about secure communication.
// DO NOT use this for fetching sensitive data or making API calls with credentials.
func WithInsecureSkipVerify(skip bool) ClientOption {
	return func(c *clientConfig) {
		c.insecureSkipVerify = skip
	}
}

// NewClient creates a new HTTP client with retry capability
func NewClient(timeout time.Duration, retryPolicy shared.RetryPolicy, logger *slog.Logger, opts ...ClientOption) *Client {
	// Default config with secure settings
	cfg := &clientConfig{
		insecureSkipVerify: false, // Secure by default
	}

	// Apply options
	for _, opt := range opts {
		opt(cfg)
	}

	// Configure TLS based on options
	tlsConfig := &tls.Config{
		InsecureSkipVerify: cfg.insecureSkipVerify,
		MinVersion:         tls.VersionTLS12, // Enforce minimum TLS 1.2
	}

	// Log security warning if InsecureSkipVerify is enabled
	if cfg.insecureSkipVerify {
		logger.Warn("HTTP client configured with InsecureSkipVerify=true - use only for reachability checks")
	}

	transport := &http.Transport{
		TLSClientConfig: tlsConfig,
		// Connection pooling settings
		MaxIdleConns:        100,
		MaxIdleConnsPerHost: 10,
		IdleConnTimeout:     90 * time.Second,
		// Timeouts for various stages
		TLSHandshakeTimeout:   10 * time.Second,
		ResponseHeaderTimeout: 10 * time.Second,
		ExpectContinueTimeout: 1 * time.Second,
	}

	return &Client{
		httpClient: &http.Client{
			Timeout:   timeout,
			Transport: transport,
			// Don't follow redirects automatically - we want to detect 3xx as "reachable"
			CheckRedirect: func(req *http.Request, via []*http.Request) error {
				return http.ErrUseLastResponse
			},
		},
		retryPolicy: retryPolicy,
		logger:      logger,
	}
}

// Get performs an HTTP GET request with retry logic
func (c *Client) Get(ctx context.Context, url string) (*http.Response, error) {
	var resp *http.Response
	var err error

	operation := func() error {
		req, reqErr := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
		if reqErr != nil {
			return reqErr
		}

		// Set User-Agent to mimic browser (bypass simple bot detection)
		req.Header.Set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
		req.Header.Set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
		req.Header.Set("Accept-Language", "en-US,en;q=0.9,pt-BR;q=0.8,pt;q=0.7")
		req.Header.Set("Accept-Encoding", "gzip, deflate, br")
		req.Header.Set("Connection", "keep-alive")

		resp, err = c.httpClient.Do(req)
		if err != nil {
			c.logger.Warn("HTTP request failed", "url", url, "error", err)
			return err
		}

		// Check for server errors (5xx) - these are retryable
		if resp.StatusCode >= 500 {
			resp.Body.Close()
			return fmt.Errorf("HTTP %d: server error", resp.StatusCode)
		}

		return nil
	}

	// Execute with retry
	err = shared.Retry(ctx, c.retryPolicy, operation)
	if err != nil {
		return nil, err
	}

	return resp, nil
}

// IsReachable checks if a URL is reachable (returns 2xx or 3xx status code)
func (c *Client) IsReachable(ctx context.Context, url string) (bool, error) {
	c.logger.Info("Validating website reachability", "url", url)

	resp, err := c.Get(ctx, url)
	if err != nil {
		c.logger.Warn("Website validation failed", "url", url, "error", err)
		return false, err
	}
	defer resp.Body.Close()

	// Drain and close body to reuse connection
	_, _ = io.Copy(io.Discard, resp.Body)

	// Consider 2xx, 3xx, and 403 (Forbidden) as reachable
	// 403 indicates the website exists and is responding, but blocking due to security checks
	// (e.g., Cloudflare, bot protection, etc.)
	reachable := (resp.StatusCode >= 200 && resp.StatusCode < 400) || resp.StatusCode == 403

	c.logger.Info("Website validation complete",
		"url", url,
		"status_code", resp.StatusCode,
		"reachable", reachable)

	return reachable, nil
}

// FetchHTML fetches HTML content from a URL
func (c *Client) FetchHTML(ctx context.Context, url string) (string, error) {
	c.logger.Info("Fetching HTML content", "url", url)

	resp, err := c.Get(ctx, url)
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()

	// Read body
	bodyBytes, err := io.ReadAll(resp.Body)
	if err != nil {
		return "", fmt.Errorf("failed to read response body: %w", err)
	}

	return string(bodyBytes), nil
}
