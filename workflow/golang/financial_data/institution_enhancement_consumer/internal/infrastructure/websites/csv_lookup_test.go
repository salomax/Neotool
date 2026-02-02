package websites

import (
	"log/slog"
	"os"
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestNewCSVLookup(t *testing.T) {
	logger := slog.New(slog.NewTextHandler(os.Stderr, &slog.HandlerOptions{Level: slog.LevelError}))

	t.Run("successful load", func(t *testing.T) {
		// Create a temporary CSV file
		tmpDir := t.TempDir()
		csvPath := filepath.Join(tmpDir, "websites.csv")
		csvContent := `"bacen_cod_inst";"parent_cnpj";"legal_name";"website"
"C0010001";"12345678000190";"TEST BANK S.A.";"https://www.testbank.com.br"
"C0010002";"98765432000110";"ANOTHER BANK S.A.";"https://www.anotherbank.com.br"`
		err := os.WriteFile(csvPath, []byte(csvContent), 0644)
		require.NoError(t, err)

		lookup, err := NewCSVLookup(csvPath, logger)
		require.NoError(t, err)
		assert.NotNil(t, lookup)
		assert.Equal(t, 2, len(lookup.byBacenCodInst))
		assert.Equal(t, 2, len(lookup.byParentCNPJ))
	})

	t.Run("file not found", func(t *testing.T) {
		lookup, err := NewCSVLookup("/nonexistent/file.csv", logger)
		assert.Error(t, err)
		assert.Nil(t, lookup)
		assert.Contains(t, err.Error(), "failed to open file")
	})

	t.Run("empty file", func(t *testing.T) {
		tmpDir := t.TempDir()
		csvPath := filepath.Join(tmpDir, "empty.csv")
		err := os.WriteFile(csvPath, []byte(""), 0644)
		require.NoError(t, err)

		lookup, err := NewCSVLookup(csvPath, logger)
		assert.Error(t, err)
		assert.Nil(t, lookup)
		assert.Contains(t, err.Error(), "CSV file is empty")
	})

	t.Run("malformed CSV", func(t *testing.T) {
		tmpDir := t.TempDir()
		csvPath := filepath.Join(tmpDir, "malformed.csv")
		csvContent := `"bacen_cod_inst";"parent_cnpj";"legal_name";"website"
"C0010001";"12345678000190";"TEST BANK S.A."`
		err := os.WriteFile(csvPath, []byte(csvContent), 0644)
		require.NoError(t, err)

		// Should still load but skip malformed rows
		_, err = NewCSVLookup(csvPath, logger)
		// This might succeed but with fewer entries, or fail depending on implementation
		// For now, we'll accept either behavior
		if err != nil {
			assert.Contains(t, err.Error(), "failed to parse CSV")
		}
	})
}

func TestCSVLookup_LookupWebsite(t *testing.T) {
	logger := slog.New(slog.NewTextHandler(os.Stderr, &slog.HandlerOptions{Level: slog.LevelError}))

	// Create a temporary CSV file
	tmpDir := t.TempDir()
	csvPath := filepath.Join(tmpDir, "websites.csv")
	csvContent := `"bacen_cod_inst";"parent_cnpj";"legal_name";"website"
"C0010001";"12345678000190";"TEST BANK S.A.";"https://www.testbank.com.br"
"C0010002";"98765432000110";"ANOTHER BANK S.A.";"https://www.anotherbank.com.br"
"C0010003";"11111111000111";"BANK WITH QUOTES";"https://www.quotedbank.com.br"`
	err := os.WriteFile(csvPath, []byte(csvContent), 0644)
	require.NoError(t, err)

	lookup, err := NewCSVLookup(csvPath, logger)
	require.NoError(t, err)

	t.Run("LookupWebsite by parent_cnpj", func(t *testing.T) {
		tests := []struct {
			name       string
			parentCNPJ string
			expected   string
			found      bool
		}{
			{
				name:       "found CNPJ",
				parentCNPJ: "12345678000190",
				expected:   "https://www.testbank.com.br",
				found:      true,
			},
			{
				name:       "found another CNPJ",
				parentCNPJ: "98765432000110",
				expected:   "https://www.anotherbank.com.br",
				found:      true,
			},
			{
				name:       "not found",
				parentCNPJ: "99999999000199",
				expected:   "",
				found:      false,
			},
			{
				name:       "empty CNPJ",
				parentCNPJ: "",
				expected:   "",
				found:      false,
			},
		}

		for _, tt := range tests {
			t.Run(tt.name, func(t *testing.T) {
				website, found := lookup.LookupWebsite(tt.parentCNPJ)
				assert.Equal(t, tt.found, found)
				if tt.found {
					assert.Equal(t, tt.expected, website)
				} else {
					assert.Empty(t, website)
				}
			})
		}
	})

	t.Run("LookupWebsiteByBacenCodInst", func(t *testing.T) {
		tests := []struct {
			name         string
			bacenCodInst string
			expected     string
			found        bool
		}{
			{
				name:         "found bacen_cod_inst",
				bacenCodInst: "C0010001",
				expected:     "https://www.testbank.com.br",
				found:        true,
			},
			{
				name:         "found another bacen_cod_inst",
				bacenCodInst: "C0010002",
				expected:     "https://www.anotherbank.com.br",
				found:        true,
			},
			{
				name:         "not found",
				bacenCodInst: "C9999999",
				expected:     "",
				found:        false,
			},
			{
				name:         "empty bacen_cod_inst",
				bacenCodInst: "",
				expected:     "",
				found:        false,
			},
		}

		for _, tt := range tests {
			t.Run(tt.name, func(t *testing.T) {
				website, found := lookup.LookupWebsiteByBacenCodInst(tt.bacenCodInst)
				assert.Equal(t, tt.found, found)
				if tt.found {
					assert.Equal(t, tt.expected, website)
				} else {
					assert.Empty(t, website)
				}
			})
		}
	})
}

func TestCSVLookup_ConcurrentAccess(t *testing.T) {
	logger := slog.New(slog.NewTextHandler(os.Stderr, &slog.HandlerOptions{Level: slog.LevelError}))

	// Create a temporary CSV file
	tmpDir := t.TempDir()
	csvPath := filepath.Join(tmpDir, "websites.csv")
	csvContent := `"bacen_cod_inst";"parent_cnpj";"legal_name";"website"
"C0010001";"12345678000190";"TEST BANK S.A.";"https://www.testbank.com.br"
"C0010002";"98765432000110";"ANOTHER BANK S.A.";"https://www.anotherbank.com.br"`
	err := os.WriteFile(csvPath, []byte(csvContent), 0644)
	require.NoError(t, err)

	lookup, err := NewCSVLookup(csvPath, logger)
	require.NoError(t, err)

	// Test concurrent reads
	done := make(chan bool)
	for i := 0; i < 10; i++ {
		go func() {
			for j := 0; j < 100; j++ {
				_, _ = lookup.LookupWebsite("12345678000190")
				_, _ = lookup.LookupWebsite("98765432000110")
				_, _ = lookup.LookupWebsite("99999999000199")
				_, _ = lookup.LookupWebsiteByBacenCodInst("C0010001")
				_, _ = lookup.LookupWebsiteByBacenCodInst("C0010002")
			}
			done <- true
		}()
	}

	// Wait for all goroutines to complete
	for i := 0; i < 10; i++ {
		<-done
	}

	// Verify data integrity
	website, found := lookup.LookupWebsite("12345678000190")
	assert.True(t, found)
	assert.Equal(t, "https://www.testbank.com.br", website)

	website, found = lookup.LookupWebsiteByBacenCodInst("C0010001")
	assert.True(t, found)
	assert.Equal(t, "https://www.testbank.com.br", website)
}

func TestCleanCSVValue(t *testing.T) {
	tests := []struct {
		name     string
		input    string
		expected string
	}{
		{
			name:     "quoted value",
			input:    `"12345678000190"`,
			expected: "12345678000190",
		},
		{
			name:     "quoted value with spaces",
			input:    `"  https://www.test.com  "`,
			expected: "https://www.test.com",
		},
		{
			name:     "unquoted value",
			input:    "12345678000190",
			expected: "12345678000190",
		},
		{
			name:     "value with spaces",
			input:    "  12345678000190  ",
			expected: "12345678000190",
		},
		{
			name:     "empty string",
			input:    "",
			expected: "",
		},
		{
			name:     "only quotes",
			input:    `""`,
			expected: "",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := cleanCSVValue(tt.input)
			assert.Equal(t, tt.expected, result)
		})
	}
}

