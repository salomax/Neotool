// Package domain contains the COTAHIST fixed-width record parser.
//
// B3 COTAHIST format reference: "Séries Históricas — Layout" (B3 official doc).
// Each data record (TIPREG="01") is exactly 245 bytes. Fields are positional —
// no delimiters, no quoting.
//
// Positions in the layout are 1-based and inclusive; constants below are
// converted to 0-based half-open slices: line[start:end].
//
// Price fields format (11)V99 — 13 chars, last 2 digits are decimals:
//
//	"0000000003417" → 3417 / 100 → 34.17
//
// VOLTOT format (16)V99 — 18 chars, last 2 digits are decimals:
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
// Derived from B3 layout positions by: start = POS_INIC - 1, end = POS_FINAL.
const (
	offTIPREG = 0   // [0:2]     TIPREG  pos 01-02  Record type (2)
	offDATPRE = 2   // [2:10]    DATPRE  pos 03-10  Date YYYYMMDD (8)
	offCODBDI = 10  // [10:12]   CODBDI  pos 11-12  BDI code (2)
	offCODNEG = 12  // [12:24]   CODNEG  pos 13-24  Negotiation code / ticker (12)
	offTPMERC = 24  // [24:27]   TPMERC  pos 25-27  Market type (3)
	offNOMRES = 27  // [27:39]   NOMRES  pos 28-39  Abbreviated name (12)
	offESPECI = 39  // [39:49]   ESPECI  pos 40-49  Specification (10)
	// PRAZOT [49:52] and MODREF [52:56] are not used.
	offPREABE = 56  // [56:69]   PREABE  pos 57-69  Open price  (13, implicit 2 dec)
	offPREMAX = 69  // [69:82]   PREMAX  pos 70-82  High price  (13)
	offPREMIN = 82  // [82:95]   PREMIN  pos 83-95  Low price   (13)
	offPREMED = 95  // [95:108]  PREMED  pos 96-108 Avg price   (13)
	offPREULT = 108 // [108:121] PREULT  pos 109-121 Close/last price (13)
	// PREOFC [121:134] and PREOFV [134:147] (best bid/ask) are not used.
	offTOTNEG = 147 // [147:152] TOTNEG  pos 148-152 Number of trades (5)
	// QUATOT [152:170] (quantity of shares traded) is not used.
	offVOLTOT = 170 // [170:188] VOLTOT  pos 171-188 Financial volume (18, implicit 2 dec)
	// PREEXE [188:201], INDOPC [201:202], DATVEN [202:210],
	// FATCOT [210:217], PTOEXE [217:230] are not used.
	offCODISI = 230 // [230:242] CODISI  pos 231-242 ISIN code (12)
	// DISMES [242:245] is not used.

	minRecordLen = 242 // must read through CODISI (end pos 242)
)

// CotahistRecord is the parsed representation of one COTAHIST data line.
// Only fields needed for catalog + price upsert are populated.
type CotahistRecord struct {
	Date       time.Time
	CodBDI     string
	Ticker     string
	TpMerc     string
	Name       string
	Especi     string
	IsinCode   string  // empty when CODISI is blank or all-zeros
	OpenPrice  float64 // PREABE
	HighPrice  float64 // PREMAX
	LowPrice   float64 // PREMIN
	AvgPrice   float64 // PREMED
	ClosePrice float64 // PREULT
	Trades     int     // TOTNEG
	// Volume is the raw VOLTOT integer (implicit 2 decimal places).
	// Stored unscaled to preserve full int64 precision; divided by 100 in SQL.
	Volume int64 // VOLTOT
}

// ParseLine parses one raw line from a COTAHIST file.
//
//   - Returns (nil, nil) for header (TIPREG="00") and trailer (TIPREG="99") lines.
//   - Returns (nil, err) for malformed data lines.
//   - The line must be the raw Windows-1252 bytes as read from the file;
//     only NOMRES and ESPECI text fields are decoded to UTF-8.
func ParseLine(line []byte) (*CotahistRecord, error) {
	// Strip Windows CR/LF if present.
	line = bytes.TrimRight(line, "\r\n")

	if len(line) < 2 {
		return nil, nil
	}
	tipreg := string(line[offTIPREG : offTIPREG+2])
	if tipreg != RecordTypeData {
		return nil, nil // header or trailer — caller skips
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

	// Name and Especi: Windows-1252 → UTF-8 (Brazilian names may contain accents)
	rec.Name = decodeW1252(line[offNOMRES : offNOMRES+12])
	rec.Especi = decodeW1252(line[offESPECI : offESPECI+10])

	// Prices — all (11)V99 format, 13 chars each
	rec.OpenPrice, err = parsePrice(line[offPREABE : offPREABE+13])
	if err != nil {
		return nil, fmt.Errorf("PREABE: %w", err)
	}
	rec.HighPrice, err = parsePrice(line[offPREMAX : offPREMAX+13])
	if err != nil {
		return nil, fmt.Errorf("PREMAX: %w", err)
	}
	rec.LowPrice, err = parsePrice(line[offPREMIN : offPREMIN+13])
	if err != nil {
		return nil, fmt.Errorf("PREMIN: %w", err)
	}
	rec.AvgPrice, err = parsePrice(line[offPREMED : offPREMED+13])
	if err != nil {
		return nil, fmt.Errorf("PREMED: %w", err)
	}
	rec.ClosePrice, err = parsePrice(line[offPREULT : offPREULT+13])
	if err != nil {
		return nil, fmt.Errorf("PREULT: %w", err)
	}

	// Number of trades
	trades, err := parseIntRaw(line[offTOTNEG : offTOTNEG+5])
	if err != nil {
		return nil, fmt.Errorf("TOTNEG: %w", err)
	}
	rec.Trades = int(trades)

	// Financial volume — (16)V99 format, 18 chars.
	// Stored as raw int64 to avoid float64 precision loss for large values.
	// Division by 100 is done in SQL using ::numeric / 100.
	vol, err := parseIntRaw(line[offVOLTOT : offVOLTOT+18])
	if err != nil {
		return nil, fmt.Errorf("VOLTOT: %w", err)
	}
	rec.Volume = vol

	// ISIN code — treat all-zeros or blank as absent
	rawISIN := strings.TrimSpace(string(line[offCODISI : offCODISI+12]))
	if rawISIN != "" && strings.Trim(rawISIN, "0") != "" {
		rec.IsinCode = rawISIN
	}

	return rec, nil
}

// parsePrice parses a 13-char (11)V99 price field with 2 implicit decimal places.
func parsePrice(b []byte) (float64, error) {
	n, err := parseIntRaw(b)
	if err != nil {
		return 0, err
	}
	return float64(n) / 100.0, nil
}

func parseIntRaw(b []byte) (int64, error) {
	s := strings.TrimSpace(string(b))
	if s == "" {
		return 0, nil
	}
	return strconv.ParseInt(s, 10, 64)
}

// decodeW1252 converts a Windows-1252 byte slice to a trimmed UTF-8 string.
func decodeW1252(b []byte) string {
	decoded, err := charmap.Windows1252.NewDecoder().Bytes(b)
	if err != nil {
		return strings.TrimSpace(string(b))
	}
	return strings.TrimSpace(string(decoded))
}
