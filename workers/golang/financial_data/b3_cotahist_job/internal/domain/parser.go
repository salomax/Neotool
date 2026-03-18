// Package domain contains the COTAHIST fixed-width record parser.
//
// B3 COTAHIST format reference (layout SeriesHistoricas_Layout):
// Each data record (TIPREG="01") is exactly 245 bytes (including the trailing
// space / CRLF-stripped to 244 chars in practice). Fields are positional —
// no delimiters, no quoting.
//
// All price fields store integers with 2 implicit decimal places:
//
//	"0000000003417" → 3417 / 100 → 34.17
//
// VOLTOT (financial volume) stores integers with 2 implicit decimal places:
//
//	"000000001074616400" → 1074616400 / 100 → 10746164.00
package domain

import (
	"bytes"
	"fmt"
	"strconv"
	"strings"
	"time"

	"golang.org/x/text/encoding/charmap"
)

// Field byte-offset constants (0-based, half-open: line[start:end]).
// Source: B3 "Séries Históricas — Layout" document.
const (
	offTIPREG    = 0   // [0:2]   Record type
	offDATPRE    = 2   // [2:10]  Date YYYYMMDD
	offCODBDI    = 10  // [10:12] BDI code
	offCODNEG    = 12  // [12:24] Ticker / negotiation code (12 chars, space-padded)
	offTPMERC    = 24  // [24:27] Market type
	offNOMRES    = 27  // [27:39] Abbreviated name (12 chars)
	offESPECI    = 39  // [39:49] Specification: ON, PN, CI, DR3, … (10 chars)
	offPREABE    = 56  // [56:69] Open price  (13 chars, implicit 2 dec)
	offPREMAX    = 69  // [69:82] High price
	offPREMED    = 82  // [82:95] Weighted avg price
	offPREMIN    = 95  // [95:108] Low price
	offTOTNEG    = 134 // [134:139] Number of trades (5 chars)
	offVOLTOT    = 157 // [157:175] Financial volume (18 chars, implicit 2 dec)
	offPREULT    = 175 // [175:188] Last / close price (13 chars)
	offCODISI    = 229 // [229:241] ISIN code (12 chars)
	minRecordLen = 241 // minimum to safely read up through CODISI
)

// CotahistRecord is the parsed representation of one COTAHIST data line.
// Only fields needed for catalog + price upsert are populated.
type CotahistRecord struct {
	Date      time.Time
	CodBDI    string
	Ticker    string
	TpMerc    string
	Name      string
	Especi    string
	IsinCode  string // empty when CODISI is blank or all-zeros
	OpenPrice float64
	HighPrice float64
	AvgPrice  float64
	LowPrice  float64
	Trades    int
	Volume    float64
	ClosePrice float64
}

// ParseLine parses one raw line from a COTAHIST file.
//
//   - Returns (nil, nil) for header (TIPREG="00") and trailer (TIPREG="99") lines
//     — the caller should skip them.
//   - Returns (nil, err) for malformed data lines.
//   - The line must be the raw Windows-1252 bytes exactly as read from the file;
//     only the Name / Especi text fields are decoded to UTF-8.
func ParseLine(line []byte) (*CotahistRecord, error) {
	// Strip Windows CR if present.
	line = bytes.TrimRight(line, "\r\n")

	if len(line) < 2 {
		return nil, nil
	}
	tipreg := string(line[offTIPREG : offTIPREG+2])
	if tipreg != RecordTypeData {
		// Header or trailer — caller skips.
		return nil, nil
	}
	if len(line) < minRecordLen {
		return nil, fmt.Errorf("data record too short: got %d bytes, need %d", len(line), minRecordLen)
	}

	rec := &CotahistRecord{}

	// Date
	rawDate := string(line[offDATPRE : offDATPRE+8])
	t, err := time.Parse("20060102", rawDate)
	if err != nil {
		return nil, fmt.Errorf("invalid DATPRE %q: %w", rawDate, err)
	}
	rec.Date = t

	// BDI code and market type
	rec.CodBDI = string(line[offCODBDI : offCODBDI+2])
	rec.TpMerc = string(line[offTPMERC : offTPMERC+3])

	// Ticker: trim trailing spaces
	rec.Ticker = strings.TrimSpace(string(line[offCODNEG : offCODNEG+12]))
	if rec.Ticker == "" {
		return nil, fmt.Errorf("empty ticker")
	}

	// Name and Especi: Windows-1252 → UTF-8 (Brazilian names may have accents)
	rec.Name = decodeW1252(line[offNOMRES : offNOMRES+12])
	rec.Especi = decodeW1252(line[offESPECI : offESPECI+10])

	// Prices
	rec.OpenPrice, err = parsePrice(line[offPREABE : offPREABE+13])
	if err != nil {
		return nil, fmt.Errorf("PREABE: %w", err)
	}
	rec.HighPrice, err = parsePrice(line[offPREMAX : offPREMAX+13])
	if err != nil {
		return nil, fmt.Errorf("PREMAX: %w", err)
	}
	rec.AvgPrice, err = parsePrice(line[offPREMED : offPREMED+13])
	if err != nil {
		return nil, fmt.Errorf("PREMED: %w", err)
	}
	rec.LowPrice, err = parsePrice(line[offPREMIN : offPREMIN+13])
	if err != nil {
		return nil, fmt.Errorf("PREMIN: %w", err)
	}
	rec.ClosePrice, err = parsePrice(line[offPREULT : offPREULT+13])
	if err != nil {
		return nil, fmt.Errorf("PREULT: %w", err)
	}

	// Trades
	trades, err := parseInt(line[offTOTNEG : offTOTNEG+5])
	if err != nil {
		return nil, fmt.Errorf("TOTNEG: %w", err)
	}
	rec.Trades = int(trades)

	// Financial volume (18 chars, implicit 2 decimal places)
	vol, err := parseIntRaw(line[offVOLTOT : offVOLTOT+18])
	if err != nil {
		return nil, fmt.Errorf("VOLTOT: %w", err)
	}
	rec.Volume = float64(vol) / 100.0

	// ISIN code — treat "000000000000" or all-space as absent
	rawISIN := strings.TrimSpace(string(line[offCODISI : offCODISI+12]))
	if rawISIN != "" && strings.Trim(rawISIN, "0") != "" {
		rec.IsinCode = rawISIN
	}

	return rec, nil
}

// parsePrice parses a 13-char price field with 2 implicit decimal places.
func parsePrice(b []byte) (float64, error) {
	n, err := parseIntRaw(b)
	if err != nil {
		return 0, err
	}
	return float64(n) / 100.0, nil
}

// parseInt parses a left-zero-padded integer field.
func parseInt(b []byte) (int64, error) {
	return parseIntRaw(b)
}

func parseIntRaw(b []byte) (int64, error) {
	s := strings.TrimSpace(string(b))
	if s == "" {
		return 0, nil
	}
	return strconv.ParseInt(s, 10, 64)
}

// decodeW1252 converts a Windows-1252 byte slice to a trimmed UTF-8 string.
// Pure-ASCII content (the common case for B3 tickers) is handled without
// allocation by the decoder.
func decodeW1252(b []byte) string {
	decoded, err := charmap.Windows1252.NewDecoder().Bytes(b)
	if err != nil {
		// Fallback: treat as raw ASCII if decoding fails
		return strings.TrimSpace(string(b))
	}
	return strings.TrimSpace(string(decoded))
}
