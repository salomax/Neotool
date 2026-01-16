package websites

import (
	"encoding/csv"
	"fmt"
	"log/slog"
	"os"
	"strings"
	"sync"
)

// CSVLookup provides fast in-memory lookup of websites by CNPJ from a CSV file
type CSVLookup struct {
	data  map[string]string // CNPJ -> Website mapping
	mu    sync.RWMutex     // Protects data map for concurrent reads
	logger *slog.Logger
}

// NewCSVLookup creates a new CSV lookup service by loading the CSV file
// If the file cannot be loaded, returns an error (caller should handle gracefully)
func NewCSVLookup(filePath string, logger *slog.Logger) (*CSVLookup, error) {
	lookup := &CSVLookup{
		data:   make(map[string]string),
		logger: logger,
	}

	if err := lookup.loadCSV(filePath); err != nil {
		return nil, fmt.Errorf("failed to load CSV file %s: %w", filePath, err)
	}

	logger.Info("CSV lookup service initialized",
		"file_path", filePath,
		"entries_count", len(lookup.data))

	return lookup, nil
}

// LookupWebsite looks up a website by CNPJ
// Returns the website URL and true if found, empty string and false otherwise
func (c *CSVLookup) LookupWebsite(cnpj string) (string, bool) {
	if cnpj == "" {
		return "", false
	}

	c.mu.RLock()
	defer c.mu.RUnlock()

	website, found := c.data[cnpj]
	return website, found
}

// loadCSV loads and parses the CSV file into memory
func (c *CSVLookup) loadCSV(filePath string) error {
	file, err := os.Open(filePath)
	if err != nil {
		return fmt.Errorf("failed to open file: %w", err)
	}
	defer file.Close()

	// Create CSV reader with semicolon delimiter
	reader := csv.NewReader(file)
	reader.Comma = ';'
	reader.TrimLeadingSpace = true

	// Read all records
	records, err := reader.ReadAll()
	if err != nil {
		return fmt.Errorf("failed to parse CSV: %w", err)
	}

	if len(records) == 0 {
		return fmt.Errorf("CSV file is empty")
	}

	// Skip header row (first row)
	records = records[1:]

	// Parse records into map
	c.mu.Lock()
	defer c.mu.Unlock()

	for i, record := range records {
		// Skip empty lines
		if len(record) == 0 || (len(record) == 1 && strings.TrimSpace(record[0]) == "") {
			continue
		}

		// Expect at least 3 columns: parent_cnpj, legal_name, website
		if len(record) < 3 {
			c.logger.Warn("Skipping malformed CSV row",
				"row", i+2, // +2 because we skipped header and 0-indexed
				"columns", len(record),
				"expected", 3)
			continue
		}

		// Extract and clean CNPJ (remove quotes and whitespace)
		cnpj := cleanCSVValue(record[0])
		if cnpj == "" {
			c.logger.Warn("Skipping row with empty CNPJ",
				"row", i+2)
			continue
		}

		// Extract and clean website (remove quotes and whitespace)
		website := cleanCSVValue(record[2])
		if website == "" {
			c.logger.Warn("Skipping row with empty website",
				"row", i+2,
				"cnpj", cnpj)
			continue
		}

		// Store in map
		c.data[cnpj] = website
	}

	return nil
}

// cleanCSVValue removes quotes and trims whitespace from CSV values
func cleanCSVValue(value string) string {
	value = strings.TrimSpace(value)
	// Remove surrounding quotes if present
	if len(value) >= 2 && value[0] == '"' && value[len(value)-1] == '"' {
		value = value[1 : len(value)-1]
	}
	return strings.TrimSpace(value)
}

