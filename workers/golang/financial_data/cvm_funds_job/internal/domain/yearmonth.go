package domain

import (
	"fmt"
	"time"
)

// YearMonth represents a calendar month without a day component.
type YearMonth struct {
	Year  int
	Month time.Month
}

func NewYearMonth(year int, month time.Month) YearMonth {
	return YearMonth{Year: year, Month: month}
}

// String returns the YYYYMM representation used in CVM file names.
func (ym YearMonth) String() string {
	return fmt.Sprintf("%04d%02d", ym.Year, int(ym.Month))
}

// AddMonths returns the YearMonth n months after ym (negative n goes back).
func (ym YearMonth) AddMonths(n int) YearMonth {
	t := time.Date(ym.Year, ym.Month, 1, 0, 0, 0, 0, time.UTC).AddDate(0, n, 0)
	return NewYearMonth(t.Year(), t.Month())
}

// After reports whether ym is later than other.
func (ym YearMonth) After(other YearMonth) bool {
	if ym.Year != other.Year {
		return ym.Year > other.Year
	}
	return ym.Month > other.Month
}

// LastDay returns the last calendar day of the month as a time.Time (UTC midnight).
func (ym YearMonth) LastDay() time.Time {
	// First day of next month minus one day
	return time.Date(ym.Year, ym.Month+1, 1, 0, 0, 0, 0, time.UTC).AddDate(0, 0, -1)
}
