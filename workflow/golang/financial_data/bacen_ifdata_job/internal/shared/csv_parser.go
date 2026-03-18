package shared

import (
	"encoding/csv"
	"fmt"
	"io"
	"strings"
)

// ParseCSV parses CSV data from a string
func ParseCSV(data string) ([][]string, error) {
	reader := csv.NewReader(strings.NewReader(data))
	reader.Comma = ','
	reader.LazyQuotes = true
	reader.TrimLeadingSpace = true

	records, err := reader.ReadAll()
	if err != nil {
		return nil, fmt.Errorf("failed to parse CSV: %w", err)
	}

	return records, nil
}

// ParseCSVWithHeader parses CSV and separates header from data rows
func ParseCSVWithHeader(data string) ([]string, [][]string, error) {
	records, err := ParseCSV(data)
	if err != nil {
		return nil, nil, err
	}

	if len(records) == 0 {
		return nil, nil, nil
	}

	header := records[0]
	rows := records[1:]

	return header, rows, nil
}

// CSVStreamParser provides streaming CSV parsing for large datasets
type CSVStreamParser struct {
	reader *csv.Reader
}

// NewCSVStreamParser creates a new streaming CSV parser
func NewCSVStreamParser(r io.Reader) *CSVStreamParser {
	reader := csv.NewReader(r)
	reader.Comma = ','
	reader.LazyQuotes = true
	reader.TrimLeadingSpace = true

	return &CSVStreamParser{
		reader: reader,
	}
}

// ReadRecord reads the next CSV record
func (p *CSVStreamParser) ReadRecord() ([]string, error) {
	return p.reader.Read()
}

// ReadAll reads all remaining records
func (p *CSVStreamParser) ReadAll() ([][]string, error) {
	return p.reader.ReadAll()
}
