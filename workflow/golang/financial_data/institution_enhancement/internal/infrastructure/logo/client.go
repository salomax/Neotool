package logo

import (
	"context"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"time"
)

// Client downloads logos from logo.dev API
type Client struct {
	httpClient *http.Client
	token      string
	baseURL    string
	logger     *slog.Logger
}

// NewClient creates a new logo downloader client
func NewClient(token string, timeout time.Duration, logger *slog.Logger) *Client {
	return &Client{
		httpClient: &http.Client{
			Timeout: timeout,
		},
		token:   token,
		baseURL: "https://img.logo.dev",
		logger:  logger,
	}
}

// DownloadLogo downloads a logo for the given domain
// Returns the logo image data as bytes, or nil if download fails
// Errors are logged but not returned (graceful degradation)
func (c *Client) DownloadLogo(ctx context.Context, domain string) ([]byte, error) {
	if domain == "" {
		c.logger.Warn("Empty domain provided for logo download")
		return nil, fmt.Errorf("domain is required")
	}

	if c.token == "" {
		c.logger.Warn("Logo.dev token not configured, skipping logo download", "domain", domain)
		return nil, fmt.Errorf("logo.dev token not configured")
	}

	// Construct URL: https://img.logo.dev/{domain}?token={token}&retina=true
	url := fmt.Sprintf("%s/%s?token=%s&retina=true", c.baseURL, domain, c.token)

	c.logger.Info("Downloading logo from logo.dev", "domain", domain, "url", url)

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		c.logger.Warn("Failed to create logo download request", "domain", domain, "error", err)
		return nil, fmt.Errorf("failed to create request: %w", err)
	}

	// Set headers
	req.Header.Set("User-Agent", "Invistus Institution Enhancement/1.0")
	req.Header.Set("Accept", "image/*")

	resp, err := c.httpClient.Do(req)
	if err != nil {
		c.logger.Warn("Logo download request failed", "domain", domain, "error", err)
		return nil, fmt.Errorf("request failed: %w", err)
	}
	defer resp.Body.Close()

	// Check status code
	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		bodyPreview := string(body)
		if len(bodyPreview) > 200 {
			bodyPreview = bodyPreview[:200]
		}
		c.logger.Warn("Logo download returned non-200 status",
			"domain", domain,
			"status", resp.StatusCode,
			"body_preview", bodyPreview)
		return nil, fmt.Errorf("HTTP %d: %s", resp.StatusCode, bodyPreview)
	}

	// Read logo data
	logoData, err := io.ReadAll(resp.Body)
	if err != nil {
		c.logger.Warn("Failed to read logo response body", "domain", domain, "error", err)
		return nil, fmt.Errorf("failed to read response: %w", err)
	}

	if len(logoData) == 0 {
		c.logger.Warn("Logo download returned empty response", "domain", domain)
		return nil, fmt.Errorf("empty logo response")
	}

	c.logger.Info("Successfully downloaded logo",
		"domain", domain,
		"size_bytes", len(logoData),
		"content_type", resp.Header.Get("Content-Type"))

	return logoData, nil
}


