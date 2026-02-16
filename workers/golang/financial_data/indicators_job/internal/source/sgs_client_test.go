package source

import "testing"

func TestNormalizeDecimal(t *testing.T) {
	cases := map[string]string{
		"1,23": "1.23",
		"4.56": "4.56",
		"7":    "7",
	}

	for input, expected := range cases {
		if got := normalizeDecimal(input); got != expected {
			t.Fatalf("expected %s for %s, got %s", expected, input, got)
		}
	}
}
