package shared

import (
	"fmt"
	"strconv"
	"time"
)

// ParseAnoMes parses YYYYMM format to year and month
func ParseAnoMes(anoMes string) (int, int, error) {
	if len(anoMes) != 6 {
		return 0, 0, fmt.Errorf("invalid ano_mes format: %s, must be YYYYMM", anoMes)
	}

	year, err := strconv.Atoi(anoMes[:4])
	if err != nil {
		return 0, 0, fmt.Errorf("invalid year in ano_mes: %s", anoMes)
	}

	month, err := strconv.Atoi(anoMes[4:])
	if err != nil {
		return 0, 0, fmt.Errorf("invalid month in ano_mes: %s", anoMes)
	}

	if month < 1 || month > 12 {
		return 0, 0, fmt.Errorf("invalid month %d in ano_mes: %s", month, anoMes)
	}

	return year, month, nil
}

// FormatAnoMes formats year and month to YYYYMM
func FormatAnoMes(year, month int) string {
	return fmt.Sprintf("%04d%02d", year, month)
}

// IsQuarterMonth checks if a month is a quarter end (03, 06, 09, 12)
func IsQuarterMonth(month int) bool {
	return month == 3 || month == 6 || month == 9 || month == 12
}

// GetNextQuarter returns the next quarterly month from the given ano_mes
func GetNextQuarter(anoMes string) (string, error) {
	year, month, err := ParseAnoMes(anoMes)
	if err != nil {
		return "", err
	}

	// Find next quarter month
	var nextMonth int
	switch {
	case month < 3:
		nextMonth = 3
	case month < 6:
		nextMonth = 6
	case month < 9:
		nextMonth = 9
	case month < 12:
		nextMonth = 12
	default:
		// Roll to next year Q1
		year++
		nextMonth = 3
	}

	return FormatAnoMes(year, nextMonth), nil
}

// GetQuarterEndDate returns the last day of the quarter
func GetQuarterEndDate(anoMes string) (time.Time, error) {
	year, month, err := ParseAnoMes(anoMes)
	if err != nil {
		return time.Time{}, err
	}

	// Get the last day of the month
	return time.Date(year, time.Month(month+1), 0, 23, 59, 59, 0, time.UTC), nil
}

// GetPublicationDate calculates the publication date for a quarter
// Q1-Q3: 60 days after quarter end
// Q4: 90 days after quarter end
func GetPublicationDate(anoMes string) (time.Time, error) {
	_, month, err := ParseAnoMes(anoMes)
	if err != nil {
		return time.Time{}, err
	}

	if !IsQuarterMonth(month) {
		return time.Time{}, fmt.Errorf("month %d is not a quarter end", month)
	}

	quarterEnd, err := GetQuarterEndDate(anoMes)
	if err != nil {
		return time.Time{}, err
	}

	// Q4 (December) has 90-day window, others have 60-day window
	var daysToAdd int
	if month == 12 {
		daysToAdd = 90
	} else {
		daysToAdd = 60
	}

	publicationDate := quarterEnd.AddDate(0, 0, daysToAdd)
	return publicationDate, nil
}

// IsPublicationReady checks if the publication window has been reached
func IsPublicationReady(anoMes string, now time.Time) (bool, error) {
	publicationDate, err := GetPublicationDate(anoMes)
	if err != nil {
		return false, err
	}

	return now.After(publicationDate) || now.Equal(publicationDate), nil
}

// GetPreviousQuarter returns the previous quarterly ano_mes
func GetPreviousQuarter(anoMes string) (string, error) {
	year, month, err := ParseAnoMes(anoMes)
	if err != nil {
		return "", err
	}

	// Move back to previous quarter
	switch month {
	case 3:
		// Q1 -> previous year Q4
		year--
		month = 12
	case 6:
		month = 3
	case 9:
		month = 6
	case 12:
		month = 9
	default:
		return "", fmt.Errorf("month %d is not a quarter end", month)
	}

	return FormatAnoMes(year, month), nil
}

// GetHistoricalStartQuarter returns the earliest quarter to start from (201603)
func GetHistoricalStartQuarter() string {
	return "201603"
}

// GetLatestClosedQuarter returns the most recent quarter that has ended for the given time.
// E.g. Jan 2026 -> Q1 2026 (202603) -> previous = 202512.
func GetLatestClosedQuarter(now time.Time) (string, error) {
	year := now.Year()
	month := int(now.Month())
	var qMonth int
	switch {
	case month <= 3:
		qMonth = 3
	case month <= 6:
		qMonth = 6
	case month <= 9:
		qMonth = 9
	default:
		qMonth = 12
	}
	currentQuarter := FormatAnoMes(year, qMonth)
	return GetPreviousQuarter(currentQuarter)
}

// GetQuartersInRange returns all quarters from start to end inclusive (YYYYMM string comparison).
// start and end must be valid quarter months (03, 06, 09, 12).
func GetQuartersInRange(start, end string) ([]string, error) {
	if _, month, err := ParseAnoMes(start); err != nil {
		return nil, err
	} else if !IsQuarterMonth(month) {
		return nil, fmt.Errorf("start %s is not a quarter end month", start)
	}
	if _, month, err := ParseAnoMes(end); err != nil {
		return nil, err
	} else if !IsQuarterMonth(month) {
		return nil, fmt.Errorf("end %s is not a quarter end month", end)
	}
	if start > end {
		return []string{}, nil
	}
	var out []string
	for q := start; q <= end; {
		out = append(out, q)
		if q == end {
			break
		}
		next, err := GetNextQuarter(q)
		if err != nil {
			return nil, err
		}
		q = next
	}
	return out, nil
}
