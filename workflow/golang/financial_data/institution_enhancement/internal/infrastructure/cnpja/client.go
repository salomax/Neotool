package cnpja

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"time"

	"github.com/salomax/invistus/workflow/go/financial_data/institution_enhancement/internal/domain"
)

// Client is a CNPJá API client with rate limiting
type Client struct {
	baseURL     string
	timeout     time.Duration
	rateLimiter *RateLimiter
	httpClient  *http.Client
	logger      *slog.Logger
}

// NewClient creates a new CNPJá API client
func NewClient(baseURL string, timeoutSeconds int, rateLimitSeconds int, logger *slog.Logger) *Client {
	return &Client{
		baseURL:     baseURL,
		timeout:     time.Duration(timeoutSeconds) * time.Second,
		rateLimiter: NewRateLimiter(rateLimitSeconds),
		httpClient: &http.Client{
			Timeout: time.Duration(timeoutSeconds) * time.Second,
		},
		logger: logger,
	}
}

// Query queries the CNPJá API for company information
func (c *Client) Query(ctx context.Context, cnpj string) (*domain.CNPJAQueryResult, error) {
	// Enforce rate limiting
	if err := c.rateLimiter.Wait(ctx); err != nil {
		return nil, fmt.Errorf("rate limiter canceled: %w", err)
	}

	// Build URL
	url := fmt.Sprintf("%s/office/%s", c.baseURL, cnpj)

	c.logger.Info("Querying CNPJá API", "cnpj", cnpj, "url", url)

	// Create request
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return nil, fmt.Errorf("failed to create request: %w", err)
	}

	// Set headers
	req.Header.Set("User-Agent", "Invistus Institution Enhancement Consumer")
	req.Header.Set("Accept", "application/json")

	// Execute request
	resp, err := c.httpClient.Do(req)
	if err != nil {
		c.logger.Error("CNPJá API request failed", "cnpj", cnpj, "error", err)
		return &domain.CNPJAQueryResult{Found: false, Error: err, Data: nil}, nil
	}
	defer resp.Body.Close()

	// Handle 404 - institution not found
	if resp.StatusCode == http.StatusNotFound {
		c.logger.Info("Institution not found in CNPJá", "cnpj", cnpj)
		return &domain.CNPJAQueryResult{Found: false, Error: nil, Data: nil}, nil
	}

	// Handle non-200 status codes
	if resp.StatusCode != http.StatusOK {
		bodyBytes, _ := io.ReadAll(resp.Body)
		bodyPreview := string(bodyBytes)
		if len(bodyPreview) > 200 {
			bodyPreview = bodyPreview[:200]
		}

		err := fmt.Errorf("HTTP %d: %s", resp.StatusCode, bodyPreview)
		c.logger.Error("CNPJá API returned error", "cnpj", cnpj, "status", resp.StatusCode, "body", bodyPreview)
		return &domain.CNPJAQueryResult{Found: false, Error: err, Data: nil}, nil
	}

	// Parse response
	var cnpjaResp domain.CNPJAResponse
	if err := json.NewDecoder(resp.Body).Decode(&cnpjaResp); err != nil {
		c.logger.Error("Failed to decode CNPJá response", "cnpj", cnpj, "error", err)
		return nil, fmt.Errorf("failed to decode response: %w", err)
	}

	c.logger.Info("Successfully retrieved CNPJá data",
		"cnpj", cnpj,
		"legal_name", cnpjaResp.Company.Name,
		"has_alias", cnpjaResp.Alias != nil)

	return &domain.CNPJAQueryResult{Found: true, Error: nil, Data: &cnpjaResp}, nil
}
