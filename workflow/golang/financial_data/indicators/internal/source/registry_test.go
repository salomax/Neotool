package source

import (
	"net/url"
	"strings"
	"testing"
	"time"

	"github.com/salomax/invistus/workflow/go/financial_data/indicators/internal/domain"
)

func TestRegistryResolve_SIDRAWindowSelection(t *testing.T) {
	registry := NewRegistry(120, 10, 6, 2, 30)

	variation := domain.IndicatorVariation{
		IndicatorLabel:     "IPCA",
		Label:              "MONTHLY_CHANGE",
		IndicatorFrequency: "monthly",
	}

	oldRef := time.Now().UTC().AddDate(0, -12, 0)
	req, err := registry.Resolve(variation, &oldRef)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if !strings.Contains(req.URL, "last%20120") {
		t.Fatalf("expected backfill window in URL, got: %s", req.URL)
	}

	recentRef := time.Now().UTC().AddDate(0, -1, 0)
	req, err = registry.Resolve(variation, &recentRef)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if !strings.Contains(req.URL, "last%206") {
		t.Fatalf("expected incremental window in URL, got: %s", req.URL)
	}
}

func TestRegistryResolve_SGSStartDate(t *testing.T) {
	registry := NewRegistry(120, 10, 6, 2, 30)

	variation := domain.IndicatorVariation{
		IndicatorLabel:     "SELIC",
		Label:              "EFFECTIVE_RATE",
		IndicatorFrequency: "daily",
	}

	recentRef := time.Now().UTC().AddDate(0, 0, -5)
	req, err := registry.Resolve(variation, &recentRef)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	parsed, err := url.Parse(req.URL)
	if err != nil {
		t.Fatalf("invalid URL: %v", err)
	}
	qs := parsed.Query()
	start := qs.Get("dataInicial")
	if start == "" {
		t.Fatalf("expected dataInicial in URL")
	}
	startDate, err := time.Parse("02/01/2006", start)
	if err != nil {
		t.Fatalf("invalid dataInicial: %v", err)
	}
	if startDate.Before(recentRef.AddDate(0, 0, 1).Add(-24 * time.Hour)) {
		t.Fatalf("expected start >= last_reference_date + 1, got %s", startDate.Format("2006-01-02"))
	}
}
