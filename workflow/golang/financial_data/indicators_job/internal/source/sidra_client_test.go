package source

import (
	"testing"
	"time"
)

func TestParseSIDRAPeriod_Month(t *testing.T) {
	d, err := parseSIDRAPeriod("202512")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	expected := time.Date(2025, time.December, 1, 0, 0, 0, 0, time.UTC)
	if !d.Equal(expected) {
		t.Fatalf("expected %v, got %v", expected, d)
	}
}

func TestParseSIDRAPeriod_Year(t *testing.T) {
	d, err := parseSIDRAPeriod("2023")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	expected := time.Date(2023, time.January, 1, 0, 0, 0, 0, time.UTC)
	if !d.Equal(expected) {
		t.Fatalf("expected %v, got %v", expected, d)
	}
}
