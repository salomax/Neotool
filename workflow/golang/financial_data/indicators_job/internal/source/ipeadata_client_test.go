package source

import (
	"math"
	"strconv"
	"testing"
	"time"
)

func TestIpeadataComputeFromIndexYTD(t *testing.T) {
	client := NewIpeadataClient(5*time.Second, "http://example.com")

	rows := []struct {
		SeriesCode string `json:"SERCODIGO"`
		Date       string `json:"VALDATA"`
		Value      any    `json:"VALVALOR"`
	}{
		{SeriesCode: "IGP12_IGPM12", Date: "2024-12-01T00:00:00-03:00", Value: 1197.562},
		{SeriesCode: "IGP12_IGPM12", Date: "2025-11-01T00:00:00-03:00", Value: 1185.175},
		{SeriesCode: "IGP12_IGPM12", Date: "2025-12-01T00:00:00-03:00", Value: 1184.998},
	}

	start := time.Date(2025, time.January, 1, 0, 0, 0, 0, time.UTC)
	end := time.Date(2025, time.December, 31, 0, 0, 0, 0, time.UTC)
	values, err := client.computeFromIndex("var-igp-ytd", "http://example.com", "IGP12_IGPM12", rows, &start, &end, "YTD")
	if err != nil {
		t.Fatalf("computeFromIndex(YTD) failed: %v", err)
	}
	if len(values) != 2 {
		t.Fatalf("expected 2 values, got %d", len(values))
	}

	dec := values[len(values)-1]
	if dec.AdjustedValue == nil {
		t.Fatalf("expected adjusted value for YTD")
	}
	got, err := parseFloat(*dec.AdjustedValue)
	if err != nil {
		t.Fatalf("parse value failed: %v", err)
	}
	expected := (1184.998/1197.562 - 1) * 100
	if !almostEqual(got, expected, 0.02) {
		t.Fatalf("unexpected YTD value: got %.4f, expected %.4f", got, expected)
	}
	raw, err := parseFloat(dec.Value)
	if err != nil {
		t.Fatalf("parse raw value failed: %v", err)
	}
	if !almostEqual(raw, 1184.998, 0.001) {
		t.Fatalf("unexpected raw value: got %.4f, expected %.4f", raw, 1184.998)
	}
}

func TestIpeadataComputeFromIndexRolling12M(t *testing.T) {
	client := NewIpeadataClient(5*time.Second, "http://example.com")

	rows := []struct {
		SeriesCode string `json:"SERCODIGO"`
		Date       string `json:"VALDATA"`
		Value      any    `json:"VALVALOR"`
	}{
		{SeriesCode: "IGP12_IGPM12", Date: "2024-11-01T00:00:00-03:00", Value: 1186.000},
		{SeriesCode: "IGP12_IGPM12", Date: "2024-12-01T00:00:00-03:00", Value: 1197.562},
		{SeriesCode: "IGP12_IGPM12", Date: "2025-11-01T00:00:00-03:00", Value: 1185.175},
		{SeriesCode: "IGP12_IGPM12", Date: "2025-12-01T00:00:00-03:00", Value: 1184.998},
	}

	start := time.Date(2025, time.November, 1, 0, 0, 0, 0, time.UTC)
	end := time.Date(2025, time.December, 31, 0, 0, 0, 0, time.UTC)
	values, err := client.computeFromIndex("var-igp-12m", "http://example.com", "IGP12_IGPM12", rows, &start, &end, "ROLLING_12M")
	if err != nil {
		t.Fatalf("computeFromIndex(ROLLING_12M) failed: %v", err)
	}
	if len(values) != 2 {
		t.Fatalf("expected 2 values, got %d", len(values))
	}

	nov := values[0]
	if nov.AdjustedValue == nil {
		t.Fatalf("expected adjusted value for ROLLING_12M")
	}
	novVal, err := parseFloat(*nov.AdjustedValue)
	if err != nil {
		t.Fatalf("parse value failed: %v", err)
	}
	expectedNov := (1185.175/1186.000 - 1) * 100
	if !almostEqual(novVal, expectedNov, 0.02) {
		t.Fatalf("unexpected rolling 12m Nov value: got %.4f, expected %.4f", novVal, expectedNov)
	}
	raw, err := parseFloat(nov.Value)
	if err != nil {
		t.Fatalf("parse raw value failed: %v", err)
	}
	if !almostEqual(raw, 1185.175, 0.001) {
		t.Fatalf("unexpected raw value: got %.4f, expected %.4f", raw, 1185.175)
	}
}

func parseFloat(val string) (float64, error) {
	return strconv.ParseFloat(val, 64)
}

func almostEqual(a, b, tolerance float64) bool {
	return math.Abs(a-b) <= tolerance
}
