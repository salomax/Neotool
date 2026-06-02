package http

import (
	"context"
	"io"
	"log/slog"
	nethttp "net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/salomax/invistus/workers/golang/financial_data/asset-brand/internal/domain"
)

func brandfetchTestLogger() *slog.Logger {
	return slog.New(slog.NewJSONHandler(io.Discard, nil))
}

func TestBrandfetchClient_LookupBrand_FallsBackToLogoType(t *testing.T) {
	var requestedPaths []string
	server := httptest.NewServer(nethttp.HandlerFunc(func(w nethttp.ResponseWriter, r *nethttp.Request) {
		requestedPaths = append(requestedPaths, r.URL.RequestURI())
		switch r.URL.Path {
		case "/isin/BRPETRACNPR6/w/400/h/400/":
			w.WriteHeader(nethttp.StatusNotFound)
		case "/isin/BRPETRACNPR6/w/400/h/400/logo":
			w.Header().Set("Content-Type", "image/png")
			w.WriteHeader(nethttp.StatusOK)
			_, _ = w.Write([]byte("png"))
		default:
			w.WriteHeader(nethttp.StatusNotFound)
		}
	}))
	defer server.Close()

	client := NewBrandfetchClient(server.URL, "client-123", 5, brandfetchTestLogger())
	result, err := client.LookupBrand(context.Background(), domain.PendingAsset{
		Ticker:    "PETR4",
		AssetType: "EQUITY",
		IsinCode:  "BRPETRACNPR6",
	})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if result == nil {
		t.Fatal("expected result, got nil")
	}
	if result.QueryUsed != "BRPETRACNPR6" {
		t.Fatalf("unexpected query used: %q", result.QueryUsed)
	}
	if !strings.Contains(result.LogoURL, "/isin/BRPETRACNPR6/w/400/h/400/logo?c=client-123") {
		t.Fatalf("unexpected logo url: %q", result.LogoURL)
	}
	if result.MimeType != "image/png" {
		t.Fatalf("unexpected mime type: %q", result.MimeType)
	}
	if string(result.LogoData) != "png" {
		t.Fatalf("unexpected logo data: %q", string(result.LogoData))
	}
	if !strings.Contains(result.RawPayload, `"resolvedType":"logo"`) {
		t.Fatalf("expected resolvedType=logo in raw payload, got %q", result.RawPayload)
	}
	if len(requestedPaths) != 2 {
		t.Fatalf("expected 2 attempts, got %d", len(requestedPaths))
	}
}

func TestBrandfetchClient_LookupBrand_ReturnsNilWhenAllAttemptsMiss(t *testing.T) {
	server := httptest.NewServer(nethttp.HandlerFunc(func(w nethttp.ResponseWriter, r *nethttp.Request) {
		w.WriteHeader(nethttp.StatusNotFound)
	}))
	defer server.Close()

	client := NewBrandfetchClient(server.URL, "client-123", 5, brandfetchTestLogger())
	result, err := client.LookupBrand(context.Background(), domain.PendingAsset{
		Ticker:    "PETR4",
		AssetType: "EQUITY",
		IsinCode:  "BRPETRACNPR6",
	})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if result != nil {
		t.Fatalf("expected nil result, got %+v", result)
	}
}
