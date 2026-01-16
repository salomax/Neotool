package graphql

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"time"
)

// Client is a GraphQL client for making requests to Apollo Router
type Client struct {
	httpClient *http.Client
	endpoint   string
	token      string
	logger     *slog.Logger
}

// Request represents a GraphQL request
type Request struct {
	Query         string                 `json:"query"`
	Variables     map[string]interface{} `json:"variables,omitempty"`
	OperationName string                 `json:"operationName,omitempty"`
}

// Response represents a GraphQL response
type Response struct {
	Data   json.RawMessage        `json:"data"`
	Errors []Error                `json:"errors,omitempty"`
}

// Error represents a GraphQL error
type Error struct {
	Message    string                 `json:"message"`
	Path       []interface{}          `json:"path,omitempty"`
	Extensions map[string]interface{} `json:"extensions,omitempty"`
}

// NewClient creates a new GraphQL client
func NewClient(endpoint string, token string, timeout time.Duration, logger *slog.Logger) *Client {
	return &Client{
		httpClient: &http.Client{
			Timeout: timeout,
		},
		endpoint: endpoint,
		token:    token,
		logger:   logger,
	}
}

// Execute executes a GraphQL request and returns the response
// If token is provided, it will be used instead of the client's stored token
func (c *Client) Execute(ctx context.Context, req *Request) (*Response, error) {
	return c.ExecuteWithToken(ctx, req, "")
}

// ExecuteWithToken executes a GraphQL request with an optional token
// If token is empty, uses the client's stored token
func (c *Client) ExecuteWithToken(ctx context.Context, req *Request, token string) (*Response, error) {
	// Use provided token if available, otherwise fall back to stored token
	authToken := token
	if authToken == "" {
		authToken = c.token
	}

	// Marshal request to JSON
	reqBody, err := json.Marshal(req)
	if err != nil {
		return nil, fmt.Errorf("failed to marshal request: %w", err)
	}

	c.logger.Debug("Executing GraphQL request",
		"endpoint", c.endpoint,
		"operation", req.OperationName,
		"query_length", len(req.Query))

	// Create HTTP request
	httpReq, err := http.NewRequestWithContext(ctx, http.MethodPost, c.endpoint, bytes.NewReader(reqBody))
	if err != nil {
		return nil, fmt.Errorf("failed to create request: %w", err)
	}

	// Set headers
	httpReq.Header.Set("Content-Type", "application/json")
	if authToken != "" {
		httpReq.Header.Set("Authorization", "Bearer "+authToken)
	}

	// Execute request
	resp, err := c.httpClient.Do(httpReq)
	if err != nil {
		c.logger.Warn("GraphQL request failed",
			"endpoint", c.endpoint,
			"error", err)
		return nil, fmt.Errorf("request failed: %w", err)
	}
	defer resp.Body.Close()

	// Read response body
	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("failed to read response: %w", err)
	}

	// Check HTTP status code
	if resp.StatusCode != http.StatusOK {
		c.logger.Warn("GraphQL request returned non-200 status",
			"endpoint", c.endpoint,
			"status", resp.StatusCode,
			"body_preview", string(body))
		return nil, fmt.Errorf("HTTP %d: %s", resp.StatusCode, string(body))
	}

	// Parse GraphQL response
	var graphqlResp Response
	if err := json.Unmarshal(body, &graphqlResp); err != nil {
		c.logger.Warn("Failed to parse GraphQL response",
			"endpoint", c.endpoint,
			"error", err,
			"body_preview", string(body))
		return nil, fmt.Errorf("failed to parse response: %w", err)
	}

	// Check for GraphQL errors
	if len(graphqlResp.Errors) > 0 {
		c.logger.Warn("GraphQL response contains errors",
			"endpoint", c.endpoint,
			"error_count", len(graphqlResp.Errors),
			"errors", graphqlResp.Errors)
		return &graphqlResp, fmt.Errorf("GraphQL errors: %v", graphqlResp.Errors)
	}

	c.logger.Debug("GraphQL request succeeded",
		"endpoint", c.endpoint,
		"operation", req.OperationName)

	return &graphqlResp, nil
}


