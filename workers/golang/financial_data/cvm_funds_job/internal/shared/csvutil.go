package shared

import "strings"

// ColumnIndex builds a column-name → position map from a CSV header row.
// Column names are trimmed of surrounding whitespace.
func ColumnIndex(header []string) map[string]int {
	m := make(map[string]int, len(header))
	for i, col := range header {
		m[strings.TrimSpace(col)] = i
	}
	return m
}

// GetField returns the trimmed value at column name in a CSV row,
// or an empty string if the column is absent or the row is too short.
func GetField(row []string, cols map[string]int, name string) string {
	if idx, ok := cols[name]; ok && idx < len(row) {
		return strings.TrimSpace(row[idx])
	}
	return ""
}
