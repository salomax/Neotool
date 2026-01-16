package processor

import (
	"testing"

	"github.com/salomax/invistus/workflow/go/financial_data/institution_enhancement/internal/domain"
	"github.com/stretchr/testify/assert"
)

func TestExtractDomainFromEmails(t *testing.T) {
	tests := []struct {
		name     string
		emails   []domain.EmailField
		expected *string
	}{
		{
			name: "Valid non-blacklisted domain",
			emails: []domain.EmailField{
				{Address: "contact@company.com", Domain: "company.com"},
			},
			expected: stringPtr("company.com"),
		},
		{
			name: "Blacklisted domain (gmail)",
			emails: []domain.EmailField{
				{Address: "user@gmail.com", Domain: "gmail.com"},
			},
			expected: nil,
		},
		{
			name: "Multiple emails, first blacklisted",
			emails: []domain.EmailField{
				{Address: "user@hotmail.com", Domain: "hotmail.com"},
				{Address: "contact@company.com", Domain: "company.com"},
			},
			expected: stringPtr("company.com"),
		},
		{
			name:     "Empty emails list",
			emails:   []domain.EmailField{},
			expected: nil,
		},
		{
			name: "All blacklisted",
			emails: []domain.EmailField{
				{Address: "user@gmail.com", Domain: "gmail.com"},
				{Address: "user@yahoo.com", Domain: "yahoo.com"},
			},
			expected: nil,
		},
		{
			name: "Case insensitive blacklist check",
			emails: []domain.EmailField{
				{Address: "user@GMAIL.COM", Domain: "GMAIL.COM"},
			},
			expected: nil,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := ExtractDomainFromEmails(tt.emails)

			if tt.expected == nil {
				assert.Nil(t, result)
			} else {
				assert.NotNil(t, result)
				assert.Equal(t, *tt.expected, *result)
			}
		})
	}
}

func stringPtr(s string) *string {
	return &s
}
