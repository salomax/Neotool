package source

import (
	"net/url"
	"strings"
	"testing"
	"time"

	"github.com/salomax/invistus/workflow/golang/financial_data/indicators/internal/domain"
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
		Label:              "TARGET_RATE",
		IndicatorFrequency: "monthly",
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

func TestRegistryResolve_TRUsesMonthlySeries7811(t *testing.T) {
	registry := NewRegistry(120, 10, 6, 2, 30)
	cases := []struct {
		variationLabel string
		calcType       string
	}{
		{variationLabel: "MONTHLY_CHANGE", calcType: ""},
		{variationLabel: "YTD", calcType: "YTD"},
		{variationLabel: "ROLLING_12M", calcType: "ROLLING_12M"},
	}

	for _, tc := range cases {
		tc := tc
		t.Run(tc.variationLabel, func(t *testing.T) {
			variation := domain.IndicatorVariation{
				IndicatorLabel:     "TR",
				Label:              tc.variationLabel,
				IndicatorFrequency: "monthly",
			}

			req, err := registry.Resolve(variation, nil)
			if err != nil {
				t.Fatalf("unexpected error: %v", err)
			}
			if req.SeriesID != 7811 {
				t.Fatalf("expected TR to use SGS series 7811, got %d", req.SeriesID)
			}
			if tc.calcType != "" && req.CalcType != tc.calcType {
				t.Fatalf("expected calc type %s, got %s", tc.calcType, req.CalcType)
			}
		})
	}
}
