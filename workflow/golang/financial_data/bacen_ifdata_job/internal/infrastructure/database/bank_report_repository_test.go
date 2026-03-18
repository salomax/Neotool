package database

import (
	"testing"
)

func TestGetNullableStringFromJSON(t *testing.T) {
	tests := []struct {
		name     string
		data     map[string]interface{}
		key      string
		expected *string
	}{
		{
			name:     "string value",
			data:     map[string]interface{}{"Grupo": "test"},
			key:      "Grupo",
			expected: stringPtr("test"),
		},
		{
			name:     "null value",
			data:     map[string]interface{}{"Grupo": nil},
			key:      "Grupo",
			expected: nil,
		},
		{
			name:     "missing key",
			data:     map[string]interface{}{},
			key:      "Grupo",
			expected: nil,
		},
		{
			name:     "empty string",
			data:     map[string]interface{}{"Grupo": ""},
			key:      "Grupo",
			expected: nil,
		},
		{
			name:     "whitespace only",
			data:     map[string]interface{}{"Grupo": "   "},
			key:      "Grupo",
			expected: nil,
		},
		{
			name:     "null string",
			data:     map[string]interface{}{"Grupo": "null"},
			key:      "Grupo",
			expected: nil,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := getNullableStringFromJSON(tt.data, tt.key)
			if tt.expected == nil {
				if got != nil {
					t.Errorf("getNullableStringFromJSON() = %v, want nil", *got)
				}
			} else {
				if got == nil {
					t.Errorf("getNullableStringFromJSON() = nil, want %v", *tt.expected)
				} else if *got != *tt.expected {
					t.Errorf("getNullableStringFromJSON() = %v, want %v", *got, *tt.expected)
				}
			}
		})
	}
}

func TestGetNullableFloat64FromJSON(t *testing.T) {
	tests := []struct {
		name     string
		data     map[string]interface{}
		key      string
		expected *float64
	}{
		{
			name:     "float64 value",
			data:     map[string]interface{}{"Saldo": 123.45},
			key:      "Saldo",
			expected: floatPtr(123.45),
		},
		{
			name:     "null value",
			data:     map[string]interface{}{"Saldo": nil},
			key:      "Saldo",
			expected: nil,
		},
		{
			name:     "missing key",
			data:     map[string]interface{}{},
			key:      "Saldo",
			expected: nil,
		},
		{
			name:     "zero value",
			data:     map[string]interface{}{"Saldo": 0.0},
			key:      "Saldo",
			expected: floatPtr(0.0),
		},
		{
			name:     "negative value",
			data:     map[string]interface{}{"Saldo": -123.45},
			key:      "Saldo",
			expected: floatPtr(-123.45),
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := getNullableFloat64FromJSON(tt.data, tt.key)
			if tt.expected == nil {
				if got != nil {
					t.Errorf("getNullableFloat64FromJSON() = %v, want nil", *got)
				}
			} else {
				if got == nil {
					t.Errorf("getNullableFloat64FromJSON() = nil, want %v", *tt.expected)
				} else {
					epsilon := 0.0001
					diff := *got - *tt.expected
					if diff < 0 {
						diff = -diff
					}
					if diff > epsilon {
						t.Errorf("getNullableFloat64FromJSON() = %v, want %v (diff: %v)", *got, *tt.expected, diff)
					}
				}
			}
		})
	}
}

func TestTruncateSaldo(t *testing.T) {
	tests := []struct {
		name     string
		input    float64
		expected float64
	}{
		{
			name:     "truncate 0.0520972846637658 to 0.0520",
			input:    0.0520972846637658,
			expected: 0.0520,
		},
		{
			name:     "truncate 0.276996948262527 to 0.2769",
			input:    0.276996948262527,
			expected: 0.2769,
		},
		{
			name:     "truncate 123.456789 to 123.4567",
			input:    123.456789,
			expected: 123.4567,
		},
		{
			name:     "exactly 4 decimal places",
			input:    123.4567,
			expected: 123.4567,
		},
		{
			name:     "less than 4 decimal places",
			input:    123.45,
			expected: 123.45,
		},
		{
			name:     "zero",
			input:    0.0,
			expected: 0.0,
		},
		{
			name:     "negative number",
			input:    -123.456789,
			expected: -123.4567,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := truncateSaldo(tt.input)
			epsilon := 0.0001
			diff := got - tt.expected
			if diff < 0 {
				diff = -diff
			}
			if diff > epsilon {
				t.Errorf("truncateSaldo() = %v, want %v (diff: %v)", got, tt.expected, diff)
			}
		})
	}
}


func TestTruncateString(t *testing.T) {
	tests := []struct {
		name    string
		input   string
		maxLen  int
		want    string
	}{
		{
			name:   "string shorter than max",
			input:  "short",
			maxLen: 10,
			want:   "short",
		},
		{
			name:   "string equal to max",
			input:  "1234567890",
			maxLen: 10,
			want:   "1234567890",
		},
		{
			name:   "string longer than max",
			input:  "this is a very long string",
			maxLen: 10,
			want:   "this is a ...",
		},
		{
			name:   "empty string",
			input:  "",
			maxLen: 10,
			want:   "",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := truncateString(tt.input, tt.maxLen)
			if got != tt.want {
				t.Errorf("truncateString() = %q, want %q", got, tt.want)
			}
		})
	}
}

// Helper functions
func floatPtr(f float64) *float64 {
	return &f
}

func stringPtr(s string) *string {
	return &s
}

