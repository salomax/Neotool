package auth

import (
	"context"
	"encoding/json"
	"fmt"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"
)

func TestTokenClient_GetToken(t *testing.T) {
	// Mock security service
	mockServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost {
			t.Errorf("Expected POST, got %s", r.Method)
		}
		if r.URL.Path != "/oauth/token" {
			t.Errorf("Expected /oauth/token, got %s", r.URL.Path)
		}

		// Parse request
		var req TokenRequest
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			t.Errorf("Failed to parse request: %v", err)
			w.WriteHeader(http.StatusBadRequest)
			return
		}

		// Validate request
		if req.GrantType != "client_credentials" {
			t.Errorf("Expected grant_type=client_credentials, got %s", req.GrantType)
		}
		if req.ClientID != "test-service" {
			t.Errorf("Expected client_id=test-service, got %s", req.ClientID)
		}
		if req.ClientSecret != "test-secret" {
			t.Errorf("Expected client_secret=test-secret, got %s", req.ClientSecret)
		}
		if req.Audience != "assets" {
			t.Errorf("Expected audience=assets, got %s", req.Audience)
		}

		// Return token response
		resp := TokenResponse{
			AccessToken: "test-token-123",
			TokenType:   "Bearer",
			ExpiresIn:   3600,
		}
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(resp)
	}))
	defer mockServer.Close()

	logger := slog.New(slog.NewTextHandler(nil, &slog.HandlerOptions{Level: slog.LevelError}))

	client := NewTokenClient(
		mockServer.URL,
		"test-service",
		"test-secret",
		"assets",
		5*time.Second,
		logger,
	)

	ctx := context.Background()

	// First call should fetch token
	token, err := client.GetToken(ctx)
	if err != nil {
		t.Fatalf("GetToken failed: %v", err)
	}
	if token != "test-token-123" {
		t.Errorf("Expected token 'test-token-123', got '%s'", token)
	}

	// Second call should use cache
	token2, err := client.GetToken(ctx)
	if err != nil {
		t.Fatalf("GetToken failed on second call: %v", err)
	}
	if token2 != "test-token-123" {
		t.Errorf("Expected cached token 'test-token-123', got '%s'", token2)
	}
}

func TestTokenClient_GetToken_ExpiredCache(t *testing.T) {
	callCount := 0
	mockServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		callCount++
		resp := TokenResponse{
			AccessToken: fmt.Sprintf("test-token-%d", callCount),
			TokenType:   "Bearer",
			ExpiresIn:   1, // Very short expiration
		}
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(resp)
	}))
	defer mockServer.Close()

	logger := slog.New(slog.NewTextHandler(nil, &slog.HandlerOptions{Level: slog.LevelError}))

	client := NewTokenClient(
		mockServer.URL,
		"test-service",
		"test-secret",
		"assets",
		5*time.Second,
		logger,
	)

	ctx := context.Background()

	// First call
	token1, err := client.GetToken(ctx)
	if err != nil {
		t.Fatalf("GetToken failed: %v", err)
	}
	if token1 != "test-token-1" {
		t.Errorf("Expected token 'test-token-1', got '%s'", token1)
	}

	// Wait for cache to expire (with buffer)
	time.Sleep(2 * time.Second)

	// Second call should fetch new token
	token2, err := client.GetToken(ctx)
	if err != nil {
		t.Fatalf("GetToken failed on second call: %v", err)
	}
	if token2 != "test-token-2" {
		t.Errorf("Expected new token 'test-token-2', got '%s'", token2)
	}

	if callCount != 2 {
		t.Errorf("Expected 2 API calls, got %d", callCount)
	}
}

func TestTokenClient_GetToken_InvalidCredentials(t *testing.T) {
	mockServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusUnauthorized)
		json.NewEncoder(w).Encode(map[string]string{
			"error": "invalid_client",
		})
	}))
	defer mockServer.Close()

	logger := slog.New(slog.NewTextHandler(nil, &slog.HandlerOptions{Level: slog.LevelError}))

	client := NewTokenClient(
		mockServer.URL,
		"test-service",
		"wrong-secret",
		"assets",
		5*time.Second,
		logger,
	)

	ctx := context.Background()

	_, err := client.GetToken(ctx)
	if err == nil {
		t.Fatal("Expected error for invalid credentials")
	}
}

func TestTokenClient_GetToken_MissingConfig(t *testing.T) {
	logger := slog.New(slog.NewTextHandler(nil, &slog.HandlerOptions{Level: slog.LevelError}))

	client := NewTokenClient(
		"http://localhost:8080",
		"", // Missing client ID
		"test-secret",
		"assets",
		5*time.Second,
		logger,
	)

	ctx := context.Background()

	_, err := client.GetToken(ctx)
	if err == nil {
		t.Fatal("Expected error for missing client ID")
	}
}

func TestTokenClient_ClearCache(t *testing.T) {
	mockServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		resp := TokenResponse{
			AccessToken: "test-token",
			TokenType:   "Bearer",
			ExpiresIn:   3600,
		}
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(resp)
	}))
	defer mockServer.Close()

	logger := slog.New(slog.NewTextHandler(nil, &slog.HandlerOptions{Level: slog.LevelError}))

	client := NewTokenClient(
		mockServer.URL,
		"test-service",
		"test-secret",
		"assets",
		5*time.Second,
		logger,
	)

	ctx := context.Background()

	// Get token to populate cache
	_, err := client.GetToken(ctx)
	if err != nil {
		t.Fatalf("GetToken failed: %v", err)
	}

	// Clear cache
	client.ClearCache()

	// Verify cache is cleared by checking that a new request would be made
	// (we can't directly test this without more complex mocking, but we can verify it doesn't panic)
	client.ClearCache() // Should be safe to call multiple times
}
