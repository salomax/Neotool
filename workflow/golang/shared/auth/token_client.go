package auth

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"sync"
	"time"
)

// TokenClient handles OAuth2 client credentials flow for service-to-service authentication
type TokenClient struct {
	securityURL  string
	clientID     string
	clientSecret string
	audience     string
	httpClient   *http.Client
	cache        *tokenCache
	mu           sync.RWMutex
	logger       *slog.Logger
}

// tokenCache holds a cached token with expiration time
type tokenCache struct {
	token     string
	expiresAt time.Time
}

// TokenRequest represents an OAuth2 token request
type TokenRequest struct {
	GrantType    string `json:"grant_type"`
	ClientID     string `json:"client_id"`
	ClientSecret string `json:"client_secret"`
	Audience     string `json:"audience,omitempty"`
}

// TokenResponse represents an OAuth2 token response
type TokenResponse struct {
	AccessToken string `json:"access_token"`
	TokenType   string `json:"token_type"`
	ExpiresIn   int    `json:"expires_in"` // in seconds
}

// NewTokenClient creates a new service token client
func NewTokenClient(
	securityURL string,
	clientID string,
	clientSecret string,
	audience string,
	timeout time.Duration,
	logger *slog.Logger,
) *TokenClient {
	return &TokenClient{
		securityURL:  securityURL,
		clientID:     clientID,
		clientSecret: clientSecret,
		audience:     audience,
		httpClient: &http.Client{
			Timeout: timeout,
		},
		cache:  nil,
		logger: logger,
	}
}

// GetToken returns a valid service token, fetching a new one if cached token is expired
func (c *TokenClient) GetToken(ctx context.Context) (string, error) {
	// Check cache first
	c.mu.RLock()
	cached := c.cache
	c.mu.RUnlock()

	// Use cached token if valid (with 60 second buffer)
	if cached != nil && time.Now().Before(cached.expiresAt.Add(-60*time.Second)) {
		c.logger.Debug("Returning cached token", "expires_at", cached.expiresAt)
		return cached.token, nil
	}

	// Fetch new token
	c.logger.Debug("Fetching new token from security service", "audience", c.audience)
	token, expiresIn, err := c.fetchTokenFromSecurityService(ctx)
	if err != nil {
		return "", fmt.Errorf("failed to fetch token: %w", err)
	}

	// Cache token
	c.mu.Lock()
	c.cache = &tokenCache{
		token:     token,
		expiresAt: time.Now().Add(time.Duration(expiresIn) * time.Second),
	}
	c.mu.Unlock()

	c.logger.Debug("Token cached successfully", "expires_in", expiresIn, "expires_at", c.cache.expiresAt)
	return token, nil
}

// fetchTokenFromSecurityService fetches a new token from the security service
func (c *TokenClient) fetchTokenFromSecurityService(ctx context.Context) (string, int, error) {
	if c.clientID == "" {
		return "", 0, fmt.Errorf("client ID not configured")
	}
	if c.clientSecret == "" {
		return "", 0, fmt.Errorf("client secret not configured")
	}

	// Build request
	reqBody := TokenRequest{
		GrantType:    "client_credentials",
		ClientID:     c.clientID,
		ClientSecret: c.clientSecret,
		Audience:     c.audience,
	}

	jsonBody, err := json.Marshal(reqBody)
	if err != nil {
		return "", 0, fmt.Errorf("failed to marshal request: %w", err)
	}

	// Create HTTP request
	url := fmt.Sprintf("%s/oauth/token", c.securityURL)
	httpReq, err := http.NewRequestWithContext(ctx, http.MethodPost, url, bytes.NewReader(jsonBody))
	if err != nil {
		return "", 0, fmt.Errorf("failed to create request: %w", err)
	}

	httpReq.Header.Set("Content-Type", "application/json")

	// Execute request
	resp, err := c.httpClient.Do(httpReq)
	if err != nil {
		c.logger.Warn("Token request failed",
			"url", url,
			"error", err)
		return "", 0, fmt.Errorf("request failed: %w", err)
	}
	defer resp.Body.Close()

	// Read response body
	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return "", 0, fmt.Errorf("failed to read response: %w", err)
	}

	// Check HTTP status code
	if resp.StatusCode != http.StatusOK {
		bodyPreview := string(body)
		if len(bodyPreview) > 200 {
			bodyPreview = bodyPreview[:200]
		}
		c.logger.Warn("Token request returned non-200 status",
			"status", resp.StatusCode,
			"body_preview", bodyPreview)
		return "", 0, fmt.Errorf("HTTP %d: %s", resp.StatusCode, bodyPreview)
	}

	// Parse response
	var tokenResp TokenResponse
	if err := json.Unmarshal(body, &tokenResp); err != nil {
		c.logger.Warn("Failed to parse token response",
			"error", err,
			"body_preview", string(body))
		return "", 0, fmt.Errorf("failed to parse response: %w", err)
	}

	if tokenResp.AccessToken == "" {
		return "", 0, fmt.Errorf("empty access token in response")
	}

	expiresIn := tokenResp.ExpiresIn
	if expiresIn <= 0 {
		expiresIn = 3600 // Default to 1 hour if not specified
	}

	c.logger.Info("Token obtained successfully",
		"audience", c.audience,
		"expires_in", expiresIn)

	return tokenResp.AccessToken, expiresIn, nil
}

// ClearCache clears the token cache (useful for testing or credential rotation)
func (c *TokenClient) ClearCache() {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.cache = nil
	c.logger.Debug("Token cache cleared")
}
