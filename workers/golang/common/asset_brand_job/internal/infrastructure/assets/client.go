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

	"github.com/salomax/invistus/workers/golang/financial_data/asset-brand/internal/infrastructure/graphql"
	"github.com/salomax/invistus/workflow/golang/shared/auth"
)

type Client struct {
	graphqlClient *graphql.Client
	tokenClient   *auth.TokenClient
	httpClient    *http.Client
	namespace     string
	logger        *slog.Logger
}

type Asset struct {
	ID         string `json:"id"`
	UploadURL  string `json:"uploadUrl"`
	StorageKey string `json:"storageKey"`
	Status     string `json:"status"`
	PublicURL  string `json:"publicUrl"`
}

func NewClient(
	graphqlClient *graphql.Client,
	tokenClient *auth.TokenClient,
	httpTimeout time.Duration,
	namespace string,
	logger *slog.Logger,
) *Client {
	return &Client{
		graphqlClient: graphqlClient,
		tokenClient:   tokenClient,
		httpClient:    &http.Client{Timeout: httpTimeout},
		namespace:     namespace,
		logger:        logger,
	}
}

func (c *Client) UploadLogo(ctx context.Context, logoData []byte, filename string, mimeType string) (string, error) {
	if len(logoData) == 0 {
		return "", fmt.Errorf("logo data is empty")
	}

	asset, err := c.createAssetUpload(ctx, filename, mimeType, int64(len(logoData)))
	if err != nil {
		return "", fmt.Errorf("create asset upload: %w", err)
	}
	if err := c.uploadToPresignedURL(ctx, asset.UploadURL, logoData, mimeType); err != nil {
		return "", fmt.Errorf("upload to presigned URL: %w", err)
	}
	confirmedAsset, err := c.confirmAssetUpload(ctx, asset.ID)
	if err != nil {
		return "", fmt.Errorf("confirm asset upload: %w", err)
	}
	return confirmedAsset.ID, nil
}

func (c *Client) createAssetUpload(ctx context.Context, filename string, mimeType string, sizeBytes int64) (*Asset, error) {
	token, err := c.tokenClient.GetToken(ctx)
	if err != nil {
		return nil, fmt.Errorf("get service token: %w", err)
	}

	req := &graphql.Request{
		Query: `
			mutation CreateAssetUpload($input: CreateAssetUploadInput!) {
				createAssetUpload(input: $input) {
					id
					uploadUrl
					storageKey
					status
				}
			}
		`,
		Variables: map[string]interface{}{
			"input": map[string]interface{}{
				"namespace": c.namespace,
				"filename":  filename,
				"mimeType":  mimeType,
				"sizeBytes": sizeBytes,
			},
		},
		OperationName: "CreateAssetUpload",
	}

	resp, err := c.graphqlClient.ExecuteWithToken(ctx, req, token)
	if err != nil {
		return nil, err
	}

	var result struct {
		CreateAssetUpload Asset `json:"createAssetUpload"`
	}
	if err := json.Unmarshal(resp.Data, &result); err != nil {
		return nil, fmt.Errorf("decode createAssetUpload response: %w", err)
	}
	if result.CreateAssetUpload.ID == "" || result.CreateAssetUpload.UploadURL == "" {
		return nil, fmt.Errorf("createAssetUpload returned incomplete payload")
	}
	return &result.CreateAssetUpload, nil
}

func (c *Client) uploadToPresignedURL(ctx context.Context, uploadURL string, logoData []byte, mimeType string) error {
	req, err := http.NewRequestWithContext(ctx, http.MethodPut, uploadURL, bytes.NewReader(logoData))
	if err != nil {
		return fmt.Errorf("create request: %w", err)
	}
	req.Header.Set("Content-Type", mimeType)
	req.ContentLength = int64(len(logoData))

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return fmt.Errorf("execute request: %w", err)
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

func (c *Client) confirmAssetUpload(ctx context.Context, assetID string) (*Asset, error) {
	token, err := c.tokenClient.GetToken(ctx)
	if err != nil {
		return nil, fmt.Errorf("get service token: %w", err)
	}

	req := &graphql.Request{
		Query: `
			mutation ConfirmAssetUpload($input: ConfirmAssetUploadInput!) {
				confirmAssetUpload(input: $input) {
					id
					storageKey
					status
					publicUrl
				}
			}
		`,
		Variables: map[string]interface{}{
			"input": map[string]interface{}{
				"assetId": assetID,
			},
		},
		OperationName: "ConfirmAssetUpload",
	}

	resp, err := c.graphqlClient.ExecuteWithToken(ctx, req, token)
	if err != nil {
		return nil, err
	}

	var result struct {
		ConfirmAssetUpload Asset `json:"confirmAssetUpload"`
	}
	if err := json.Unmarshal(resp.Data, &result); err != nil {
		return nil, fmt.Errorf("decode confirmAssetUpload response: %w", err)
	}
	if result.ConfirmAssetUpload.ID == "" {
		return nil, fmt.Errorf("confirmAssetUpload returned incomplete payload")
	}
	return &result.ConfirmAssetUpload, nil
}
