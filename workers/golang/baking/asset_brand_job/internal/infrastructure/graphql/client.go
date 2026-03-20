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

type Client struct {
	httpClient *http.Client
	endpoint   string
	logger     *slog.Logger
}

type Request struct {
	Query         string                 `json:"query"`
	Variables     map[string]interface{} `json:"variables,omitempty"`
	OperationName string                 `json:"operationName,omitempty"`
}

type Response struct {
	Data   json.RawMessage `json:"data"`
	Errors []Error         `json:"errors,omitempty"`
}

type Error struct {
	Message string `json:"message"`
}

func NewClient(endpoint string, timeout time.Duration, logger *slog.Logger) *Client {
	return &Client{
		httpClient: &http.Client{Timeout: timeout},
		endpoint:   endpoint,
		logger:     logger,
	}
}

func (c *Client) ExecuteWithToken(ctx context.Context, req *Request, token string) (*Response, error) {
	payload, err := json.Marshal(req)
	if err != nil {
		return nil, fmt.Errorf("marshal request: %w", err)
	}

	httpReq, err := http.NewRequestWithContext(ctx, http.MethodPost, c.endpoint, bytes.NewReader(payload))
	if err != nil {
		return nil, fmt.Errorf("create request: %w", err)
	}
	httpReq.Header.Set("Content-Type", "application/json")
	if token != "" {
		httpReq.Header.Set("Authorization", "Bearer "+token)
	}

	resp, err := c.httpClient.Do(httpReq)
	if err != nil {
		return nil, fmt.Errorf("execute request: %w", err)
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("read response: %w", err)
	}
	if resp.StatusCode != http.StatusOK {
		bodyPreview := string(body)
		if len(bodyPreview) > 200 {
			bodyPreview = bodyPreview[:200]
		}
		c.logger.Warn("graphql request returned non-200 status", "status", resp.StatusCode, "body_preview", bodyPreview)
		return nil, fmt.Errorf("graphql HTTP %d: %s", resp.StatusCode, bodyPreview)
	}

	var graphqlResp Response
	if err := json.Unmarshal(body, &graphqlResp); err != nil {
		return nil, fmt.Errorf("decode response: %w", err)
	}
	if len(graphqlResp.Errors) > 0 {
		return &graphqlResp, fmt.Errorf("graphql errors: %+v", graphqlResp.Errors)
	}

	return &graphqlResp, nil
}
