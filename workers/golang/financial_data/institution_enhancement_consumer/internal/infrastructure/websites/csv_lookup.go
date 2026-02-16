package websites

import (
	"encoding/csv"
	"fmt"
	"log/slog"
	"os"
	"strings"
	"sync"
)

// CSVLookup provides fast in-memory lookup of websites by bacen_cod_inst or parent_cnpj from a CSV file
type CSVLookup struct {
	byBacenCodInst map[string]string // bacen_cod_inst -> Website mapping
	byParentCNPJ   map[string]string // parent_cnpj -> Website mapping
	mu             sync.RWMutex      // Protects data maps for concurrent reads
	logger         *slog.Logger
}

// NewCSVLookup creates a new CSV lookup service by loading the CSV file
// If the file cannot be loaded, returns an error (caller should handle gracefully)
func NewCSVLookup(filePath string, logger *slog.Logger) (*CSVLookup, error) {
	lookup := &CSVLookup{
		byBacenCodInst: make(map[string]string),
		byParentCNPJ:   make(map[string]string),
		logger:         logger,
	}

	if err := lookup.loadCSV(filePath); err != nil {
		return nil, fmt.Errorf("failed to load CSV file %s: %w", filePath, err)
	}

	logger.Info("CSV lookup service initialized",
		"file_path", filePath,
		"entries_by_bacen_cod_inst", len(lookup.byBacenCodInst),
		"entries_by_parent_cnpj", len(lookup.byParentCNPJ))

	return lookup, nil
}

// LookupWebsite looks up a website by parent_cnpj (14-digit CNPJ)
// Returns the website URL and true if found, empty string and false otherwise
func (c *CSVLookup) LookupWebsite(parentCNPJ string) (string, bool) {
	if parentCNPJ == "" {
		return "", false
	}

	c.mu.RLock()
	defer c.mu.RUnlock()

	website, found := c.byParentCNPJ[parentCNPJ]
	return website, found
}

// LookupWebsiteByBacenCodInst looks up a website by bacen_cod_inst
// Returns the website URL and true if found, empty string and false otherwise
func (c *CSVLookup) LookupWebsiteByBacenCodInst(bacenCodInst string) (string, bool) {
	if bacenCodInst == "" {
		return "", false
	}

	c.mu.RLock()
	defer c.mu.RUnlock()

	website, found := c.byBacenCodInst[bacenCodInst]
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

	// Parse records into maps
	c.mu.Lock()
	defer c.mu.Unlock()

	for i, record := range records {
		// Skip empty lines
		if len(record) == 0 || (len(record) == 1 && strings.TrimSpace(record[0]) == "") {
			continue
		}

		// Expect at least 4 columns: bacen_cod_inst, parent_cnpj, legal_name, website
		if len(record) < 4 {
			c.logger.Warn("Skipping malformed CSV row",
				"row", i+2, // +2 because we skipped header and 0-indexed
				"columns", len(record),
				"expected", 4)
			continue
		}

		// Extract and clean values (remove quotes and whitespace)
		bacenCodInst := cleanCSVValue(record[0])
		parentCNPJ := cleanCSVValue(record[1])
		website := cleanCSVValue(record[3])

		if website == "" {
			c.logger.Warn("Skipping row with empty website",
				"row", i+2,
				"bacen_cod_inst", bacenCodInst,
				"parent_cnpj", parentCNPJ)
			continue
		}

		// Store by bacen_cod_inst if provided
		if bacenCodInst != "" {
			c.byBacenCodInst[bacenCodInst] = website
		}

		// Store by parent_cnpj if provided
		if parentCNPJ != "" {
			c.byParentCNPJ[parentCNPJ] = website
		}
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

