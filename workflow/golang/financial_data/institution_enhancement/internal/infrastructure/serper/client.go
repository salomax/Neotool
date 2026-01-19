package serper

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"regexp"
	"strings"
	"time"
)

// Client is a Serper API client for searching websites
type Client struct {
	apiKey     string
	timeout    time.Duration
	httpClient *http.Client
	logger     *slog.Logger
}

// NewClient creates a new Serper API client
func NewClient(apiKey string, timeoutSeconds int, logger *slog.Logger) *Client {
	return &Client{
		apiKey:  apiKey,
		timeout: time.Duration(timeoutSeconds) * time.Second,
		httpClient: &http.Client{
			Timeout: time.Duration(timeoutSeconds) * time.Second,
		},
		logger: logger,
	}
}

// SearchRequest represents a Serper search request
type SearchRequest struct {
	Q  string `json:"q"`
	GL string `json:"gl,omitempty"` // Country (e.g., "br" for Brazil)
	HL string `json:"hl,omitempty"` // Language (e.g., "pt" for Portuguese)
}

// SearchResult represents a single search result
type SearchResult struct {
	Title   string `json:"title"`
	Link    string `json:"link"`
	Snippet string `json:"snippet"`
}

// SearchResponse represents a Serper search response
type SearchResponse struct {
	Organic []SearchResult `json:"organic"`
}

// SearchWebsite searches for an institution's website using Serper API
// Returns the most likely website URL or empty string if not found
func (c *Client) SearchWebsite(ctx context.Context, institutionName string, cnpj string) (string, error) {
	c.logger.Info("Searching for website using Serper",
		"institution_name", institutionName,
		"cnpj", cnpj)

	// Build search query
	query := fmt.Sprintf("%s site oficial", institutionName)
	if cnpj != "" {
		query = fmt.Sprintf("%s %s site oficial", institutionName, cnpj)
	}

	// Create request
	reqBody := SearchRequest{
		Q:  query,
		GL: "br", // Search in Brazil
		HL: "pt", // Portuguese language
	}

	bodyBytes, err := json.Marshal(reqBody)
	if err != nil {
		return "", fmt.Errorf("failed to marshal request: %w", err)
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, "https://google.serper.dev/search", bytes.NewBuffer(bodyBytes))
	if err != nil {
		return "", fmt.Errorf("failed to create request: %w", err)
	}

	// Set headers
	req.Header.Set("X-API-KEY", c.apiKey)
	req.Header.Set("Content-Type", "application/json")

	// Execute request
	resp, err := c.httpClient.Do(req)
	if err != nil {
		c.logger.Error("Serper API request failed", "error", err)
		return "", fmt.Errorf("failed to execute request: %w", err)
	}
	defer resp.Body.Close()

	// Check status code
	if resp.StatusCode != http.StatusOK {
		bodyBytes, _ := io.ReadAll(resp.Body)
		c.logger.Error("Serper API returned error",
			"status", resp.StatusCode,
			"body", string(bodyBytes))
		return "", fmt.Errorf("HTTP %d: %s", resp.StatusCode, string(bodyBytes))
	}

	// Parse response
	var searchResp SearchResponse
	if err := json.NewDecoder(resp.Body).Decode(&searchResp); err != nil {
		return "", fmt.Errorf("failed to decode response: %w", err)
	}

	// Extract website from results
	website := c.extractWebsiteFromResults(searchResp.Organic, institutionName)

	if website != "" {
		c.logger.Info("Website found using Serper",
			"institution_name", institutionName,
			"website", website)
	} else {
		c.logger.Warn("No website found using Serper",
			"institution_name", institutionName,
			"results_count", len(searchResp.Organic))
	}

	return website, nil
}

// extractWebsiteFromResults extracts the most likely website from search results
func (c *Client) extractWebsiteFromResults(results []SearchResult, institutionName string) string {
	if len(results) == 0 {
		return ""
	}

	// Patterns to exclude (social media, general portals, etc.)
	excludePatterns := []string{
		"facebook.com",
		"instagram.com",
		"linkedin.com",
		"twitter.com",
		"x.com",
		"youtube.com",
		"wikipedia.org",
		"gov.br",
		"bcb.gov.br",
		"receita.fazenda.gov.br",
		"reclameaqui.com.br",
	}

	// Try to find official website in the first few results
	for i, result := range results {
		if i >= 5 { // Only check first 5 results
			break
		}

		// Skip excluded domains
		shouldExclude := false
		for _, pattern := range excludePatterns {
			if strings.Contains(strings.ToLower(result.Link), pattern) {
				shouldExclude = true
				break
			}
		}
		if shouldExclude {
			c.logger.Info("Skipping excluded domain",
				"link", result.Link,
				"position", i+1)
			continue
		}

		// Extract domain from link
		domain := extractDomain(result.Link)
		if domain == "" {
			continue
		}

		// Check if title or snippet mentions "oficial" or institution name
		titleLower := strings.ToLower(result.Title)
		snippetLower := strings.ToLower(result.Snippet)
		nameLower := strings.ToLower(institutionName)

		// Score the result based on relevance indicators
		score := 0
		if strings.Contains(titleLower, nameLower) {
			score += 3
		}
		if strings.Contains(titleLower, "oficial") || strings.Contains(titleLower, "official") {
			score += 2
		}
		if strings.Contains(snippetLower, nameLower) {
			score += 1
		}
		if strings.Contains(snippetLower, "oficial") || strings.Contains(snippetLower, "official") {
			score += 1
		}

		c.logger.Info("Evaluating search result",
			"position", i+1,
			"link", result.Link,
			"title", result.Title,
			"score", score)

		// If first result or has good score, use it
		if i == 0 || score >= 3 {
			return normalizeURL(domain)
		}
	}

	// Fallback: use first non-excluded result
	for _, result := range results {
		domain := extractDomain(result.Link)
		if domain != "" {
			// Skip excluded domains
			shouldExclude := false
			for _, pattern := range excludePatterns {
				if strings.Contains(strings.ToLower(result.Link), pattern) {
					shouldExclude = true
					break
				}
			}
			if !shouldExclude {
				c.logger.Info("Using fallback result", "link", result.Link)
				return normalizeURL(domain)
			}
		}
	}

	return ""
}

// extractDomain extracts the domain from a URL
func extractDomain(url string) string {
	// Remove protocol
	re := regexp.MustCompile(`^https?://`)
	url = re.ReplaceAllString(url, "")

	// Remove path and query
	if idx := strings.Index(url, "/"); idx != -1 {
		url = url[:idx]
	}
	if idx := strings.Index(url, "?"); idx != -1 {
		url = url[:idx]
	}

	return url
}

// normalizeURL ensures URL has https:// protocol
func normalizeURL(url string) string {
	url = strings.TrimSpace(url)
	if url == "" {
		return url
	}

	if !strings.HasPrefix(url, "http://") && !strings.HasPrefix(url, "https://") {
		return "https://" + url
	}

	return url
}
