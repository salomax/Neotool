package processor

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestHasWWWPrefix(t *testing.T) {
	tests := []struct {
		name     string
		url      string
		expected bool
	}{
		{"URL with www", "https://www.example.com", true},
		{"URL without www", "https://example.com", false},
		{"URL with www, no protocol", "www.example.com", true},
		{"URL without www, no protocol", "example.com", false},
		{"HTTP with www", "http://www.example.com", true},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := hasWWWPrefix(tt.url)
			assert.Equal(t, tt.expected, result)
		})
	}
}

func TestAddWWWPrefix(t *testing.T) {
	tests := []struct {
		name     string
		url      string
		expected string
	}{
		{"HTTPS URL", "https://example.com", "https://www.example.com"},
		{"HTTP URL", "http://example.com", "http://www.example.com"},
		{"No protocol", "example.com", "https://www.example.com"},
		{"With path", "https://example.com/path", "https://www.example.com/path"},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := addWWWPrefix(tt.url)
			assert.Equal(t, tt.expected, result)
		})
	}
}

func TestRemoveWWWPrefix(t *testing.T) {
	tests := []struct {
		name     string
		url      string
		expected string
	}{
		{"HTTPS URL with www", "https://www.example.com", "https://example.com"},
		{"HTTP URL with www", "http://www.example.com", "http://example.com"},
		{"No www", "https://example.com", "https://example.com"},
		{"With path", "https://www.example.com/path", "https://example.com/path"},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := removeWWWPrefix(tt.url)
			assert.Equal(t, tt.expected, result)
		})
	}
}
