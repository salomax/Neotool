package assets

import (
	"context"
	"encoding/json"
	"fmt"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/salomax/invistus/workflow/golang/financial_data/institution_enhancement/internal/infrastructure/graphql"
	"github.com/salomax/invistus/workflow/golang/shared/auth"
)

// TestAssetUploadFlow tests the full asset upload flow with mocked services
func TestAssetUploadFlow(t *testing.T) {
	// Mock security service for token issuance
	tokenCallCount := 0
	securityServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/oauth/token" {
			t.Errorf("Expected /oauth/token, got %s", r.URL.Path)
		}
		tokenCallCount++

		// Parse request
		var req struct {
			GrantType    string `json:"grant_type"`
			ClientID     string `json:"client_id"`
			ClientSecret string `json:"client_secret"`
			Audience     string `json:"audience"`
		}
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			t.Errorf("Failed to parse request: %v", err)
			w.WriteHeader(http.StatusBadRequest)
			return
		}

		// Validate request
		if req.GrantType != "client_credentials" {
			t.Errorf("Expected grant_type=client_credentials, got %s", req.GrantType)
		}
		if req.Audience != "assets" {
			t.Errorf("Expected audience=assets, got %s", req.Audience)
		}

		// Return token response
		resp := map[string]interface{}{
			"access_token": fmt.Sprintf("test-token-%d", tokenCallCount),
			"token_type":   "Bearer",
			"expires_in":   3600,
		}
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(resp)
	}))
	defer securityServer.Close()

	// Mock S3/MinIO storage for file upload
	storageCallCount := 0
	storageServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPut {
			t.Errorf("Expected PUT, got %s", r.Method)
		}
		storageCallCount++
		w.WriteHeader(http.StatusOK)
	}))
	defer storageServer.Close()

	// Storage URL to be returned in GraphQL response
	storageURL := storageServer.URL + "/upload/test-asset-id-123"

	// Mock GraphQL server (Apollo Router)
	graphqlCallCount := 0
	graphqlServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		graphqlCallCount++

		// Verify Authorization header
		authHeader := r.Header.Get("Authorization")
		if authHeader == "" || authHeader[:7] != "Bearer " {
			t.Errorf("Expected Authorization Bearer header, got: %s", authHeader)
			w.WriteHeader(http.StatusUnauthorized)
			return
		}

		// Parse GraphQL request
		var req struct {
			Query     string                 `json:"query"`
			Variables map[string]interface{} `json:"variables"`
		}
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			t.Errorf("Failed to parse GraphQL request: %v", err)
			w.WriteHeader(http.StatusBadRequest)
			return
		}

		// Handle different mutations
		var response map[string]interface{}
		if contains(req.Query, "CreateAssetUpload") {
			// createAssetUpload response
			input := req.Variables["input"].(map[string]interface{})
			response = map[string]interface{}{
				"data": map[string]interface{}{
					"createAssetUpload": map[string]interface{}{
						"id":             "test-asset-id-123",
						"uploadUrl":       storageURL,
						"uploadExpiresAt": "2026-01-08T21:32:37.939Z",
						"status":          "PENDING",
						"storageKey":      fmt.Sprintf("%s/%s", input["namespace"], "test-asset-id-123"),
					},
				},
			}
		} else if contains(req.Query, "ConfirmAssetUpload") {
			// confirmAssetUpload response
			response = map[string]interface{}{
				"data": map[string]interface{}{
					"confirmAssetUpload": map[string]interface{}{
						"id":         "test-asset-id-123",
						"storageKey": "institution-logos/test-asset-id-123",
						"status":     "READY",
						"publicUrl":   "https://cdn.example.com/institution-logos/test-asset-id-123",
					},
				},
			}
		} else {
			t.Errorf("Unexpected GraphQL query: %s", req.Query)
			w.WriteHeader(http.StatusBadRequest)
			return
		}

		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(response)
	}))
	defer graphqlServer.Close()

	logger := slog.New(slog.NewTextHandler(nil, &slog.HandlerOptions{Level: slog.LevelError}))

	// Create token client
	tokenClient := auth.NewTokenClient(
		securityServer.URL,
		"test-service",
		"test-secret",
		"assets",
		5*time.Second,
		logger,
	)

	// Create GraphQL client
	graphqlClient := graphql.NewClient(
		graphqlServer.URL,
		"", // Token provided per-request
		5*time.Second,
		logger,
	)

	// Create assets client
	assetsClient := NewClient(
		graphqlClient,
		tokenClient,
		5*time.Second,
		"institution-logos",
		logger,
	)

	// Test data
	logoData := []byte("fake-logo-data")
	filename := "example.com.png"
	mimeType := "image/png"

	ctx := context.Background()

	// Execute upload flow
	assetID, err := assetsClient.UploadLogo(ctx, logoData, filename, mimeType)
	if err != nil {
		t.Fatalf("UploadLogo failed: %v", err)
	}

	// Verify results
	expectedAssetID := "test-asset-id-123"
	if assetID != expectedAssetID {
		t.Errorf("Expected asset ID '%s', got '%s'", expectedAssetID, assetID)
	}

	// Verify service calls
	// Token should be fetched (may be cached for second call)
	if tokenCallCount < 1 {
		t.Errorf("Expected at least 1 token request, got %d", tokenCallCount)
	}

	// GraphQL should be called twice: createAssetUpload and confirmAssetUpload
	if graphqlCallCount != 2 {
		t.Errorf("Expected 2 GraphQL calls, got %d", graphqlCallCount)
	}

	// Storage upload should be called once
	if storageCallCount != 1 {
		t.Errorf("Expected 1 storage upload, got %d", storageCallCount)
	}
}

// Helper function to check if string contains substring
func contains(s, substr string) bool {
	for i := 0; i <= len(s)-len(substr); i++ {
		if s[i:i+len(substr)] == substr {
			return true
		}
	}
	return false
}
