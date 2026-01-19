package assets

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"time"

	"github.com/salomax/invistus/workflow/go/financial_data/institution_enhancement/internal/infrastructure/graphql"
	"github.com/salomax/invistus/workflow/go/shared/auth"
)

// Client handles asset uploads to the assets service via GraphQL
type Client struct {
	graphqlClient *graphql.Client
	tokenClient   *auth.TokenClient
	httpClient    *http.Client
	namespace     string
	logger        *slog.Logger
}

// Asset represents an asset from the GraphQL response
type Asset struct {
	ID             string `json:"id"`
	StorageKey     string `json:"storageKey"`
	UploadURL      string `json:"uploadUrl"`
	UploadExpiresAt string `json:"uploadExpiresAt"` // ISO 8601 DateTime string
	Status         string `json:"status"`
	PublicURL      string `json:"publicUrl"`
}

// NewClient creates a new asset uploader client
func NewClient(graphqlClient *graphql.Client, tokenClient *auth.TokenClient, httpTimeout time.Duration, namespace string, logger *slog.Logger) *Client {
	return &Client{
		graphqlClient: graphqlClient,
		tokenClient:   tokenClient,
		httpClient: &http.Client{
			Timeout: httpTimeout,
		},
		namespace: namespace,
		logger:    logger,
	}
}

// UploadLogo uploads a logo to the assets service and returns the asset ID
// Steps:
// 1. Call createAssetUpload mutation
// 2. Upload logo binary to presigned URL
// 3. Call confirmAssetUpload mutation
// 4. Return asset ID (UUID string)
func (c *Client) UploadLogo(ctx context.Context, logoData []byte, filename string, mimeType string) (string, error) {
	if len(logoData) == 0 {
		return "", fmt.Errorf("logo data is empty")
	}

	// Step 1: Create asset upload
	asset, err := c.createAssetUpload(ctx, filename, mimeType, int64(len(logoData)))
	if err != nil {
		c.logger.Warn("Failed to create asset upload",
			"filename", filename,
			"error", err)
		return "", fmt.Errorf("failed to create asset upload: %w", err)
	}

	c.logger.Info("Created asset upload",
		"asset_id", asset.ID,
		"upload_url", asset.UploadURL,
		"storage_key", asset.StorageKey)

	// Step 2: Upload logo binary to presigned URL
	if err := c.uploadToPresignedURL(ctx, asset.UploadURL, logoData, mimeType); err != nil {
		c.logger.Warn("Failed to upload logo to presigned URL",
			"asset_id", asset.ID,
			"error", err)
		return "", fmt.Errorf("failed to upload to presigned URL: %w", err)
	}

	c.logger.Info("Uploaded logo to presigned URL", "asset_id", asset.ID)

	// Step 3: Confirm asset upload
	confirmedAsset, err := c.confirmAssetUpload(ctx, asset.ID)
	if err != nil {
		c.logger.Warn("Failed to confirm asset upload",
			"asset_id", asset.ID,
			"error", err)
		return "", fmt.Errorf("failed to confirm asset upload: %w", err)
	}

	c.logger.Info("Confirmed asset upload",
		"asset_id", confirmedAsset.ID,
		"storage_key", confirmedAsset.StorageKey,
		"public_url", confirmedAsset.PublicURL)

	return confirmedAsset.ID, nil
}

// createAssetUpload calls the createAssetUpload GraphQL mutation
func (c *Client) createAssetUpload(ctx context.Context, filename string, mimeType string, sizeBytes int64) (*Asset, error) {
	// Get service token
	token, err := c.tokenClient.GetToken(ctx)
	if err != nil {
		return nil, fmt.Errorf("failed to get service token: %w", err)
	}

	query := `
		mutation CreateAssetUpload($input: CreateAssetUploadInput!) {
			createAssetUpload(input: $input) {
				id
				uploadUrl
				uploadExpiresAt
				status
				storageKey
			}
		}
	`

	variables := map[string]interface{}{
		"input": map[string]interface{}{
			"namespace": c.namespace,
			"filename":  filename,
			"mimeType":  mimeType,
			"sizeBytes": sizeBytes,
		},
	}

	req := &graphql.Request{
		Query:         query,
		Variables:     variables,
		OperationName: "CreateAssetUpload",
	}

	resp, err := c.graphqlClient.ExecuteWithToken(ctx, req, token)
	if err != nil {
		return nil, err
	}

	// Parse response data
	var result struct {
		CreateAssetUpload Asset `json:"createAssetUpload"`
	}

	if err := json.Unmarshal(resp.Data, &result); err != nil {
		return nil, fmt.Errorf("failed to unmarshal response: %w", err)
	}

	return &result.CreateAssetUpload, nil
}

// uploadToPresignedURL uploads binary data to a presigned URL
func (c *Client) uploadToPresignedURL(ctx context.Context, uploadURL string, data []byte, mimeType string) error {
	req, err := http.NewRequestWithContext(ctx, http.MethodPut, uploadURL, bytes.NewReader(data))
	if err != nil {
		return fmt.Errorf("failed to create request: %w", err)
	}

	req.Header.Set("Content-Type", mimeType)
	req.ContentLength = int64(len(data))

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return fmt.Errorf("request failed: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		body, _ := io.ReadAll(resp.Body)
		bodyPreview := string(body)
		if len(bodyPreview) > 200 {
			bodyPreview = bodyPreview[:200]
		}
		return fmt.Errorf("HTTP %d: %s", resp.StatusCode, bodyPreview)
	}

	return nil
}

// confirmAssetUpload calls the confirmAssetUpload GraphQL mutation
func (c *Client) confirmAssetUpload(ctx context.Context, assetID string) (*Asset, error) {
	// Get service token
	token, err := c.tokenClient.GetToken(ctx)
	if err != nil {
		return nil, fmt.Errorf("failed to get service token: %w", err)
	}

	query := `
		mutation ConfirmAssetUpload($input: ConfirmAssetUploadInput!) {
			confirmAssetUpload(input: $input) {
				id
				storageKey
				status
				publicUrl
			}
		}
	`

	variables := map[string]interface{}{
		"input": map[string]interface{}{
			"assetId": assetID,
		},
	}

	req := &graphql.Request{
		Query:         query,
		Variables:     variables,
		OperationName: "ConfirmAssetUpload",
	}

	resp, err := c.graphqlClient.ExecuteWithToken(ctx, req, token)
	if err != nil {
		return nil, err
	}

	// Parse response data
	var result struct {
		ConfirmAssetUpload Asset `json:"confirmAssetUpload"`
	}

	if err := json.Unmarshal(resp.Data, &result); err != nil {
		return nil, fmt.Errorf("failed to unmarshal response: %w", err)
	}

	return &result.ConfirmAssetUpload, nil
}

