import React, { useState, useCallback, useEffect, useRef } from 'react';
import {
  FormControl,
  FormLabel,
  FormHelperText,
  InputAdornment,
  IconButton,
  TextField,
  Dialog,
  DialogContent,
  DialogActions,
  Button,
  Box,
  Stack,
  Typography,
} from '@/shared/ui/mui-imports';
import { StaticDatePicker } from '@mui/x-date-pickers/StaticDatePicker';
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider';
import { AdapterDayjs } from '@mui/x-date-pickers/AdapterDayjs';
import dayjs, { Dayjs } from 'dayjs';
import { CalendarTodayIcon } from '@/shared/ui/mui-imports';

export interface DateRange {
  start: Dayjs | null;
  end: Dayjs | null;
}

export interface DateRangePickerProps {
  /** Current value of the date range picker */
  value?: DateRange | null;
  /** Default value (uncontrolled) */
  defaultValue?: DateRange | null;
  /** Whether the picker is disabled */
  disabled?: boolean;
  /** Whether the picker is read-only */
  readOnly?: boolean;
  /** Whether the picker is required */
  required?: boolean;
  /** Label for the picker */
  label?: string;
  /** Placeholder text */
  placeholder?: string;
  /** Helper text below the picker */
  helperText?: string;
  /** Error state */
  error?: boolean;
  /** Error message */
  errorMessage?: string;
  /** Format for display */
  format?: string;
  /** Minimum selectable date */
  minDate?: Dayjs | null;
  /** Maximum selectable date */
  maxDate?: Dayjs | null;
  /** Whether to disable past dates */
  disablePast?: boolean;
  /** Whether to disable future dates */
  disableFuture?: boolean;
  /** Whether to show calendar icon */
  showCalendarIcon?: boolean;
  /** Custom calendar icon */
  calendarIcon?: React.ReactNode;
  /** Callback fired when the value changes */
  onChange?: (value: DateRange | null) => void;
  /** Callback fired when the picker opens */
  onOpen?: () => void;
  /** Callback fired when the picker closes */
  onClose?: () => void;
  /** Custom CSS class name */
  className?: string;
  /** Test identifier */
  'data-testid'?: string;
}

const DateRangePicker: React.FC<DateRangePickerProps> = ({
  value,
  defaultValue = null,
  disabled = false,
  readOnly = false,
  required = false,
  label,
  placeholder = 'Select date range',
  helperText,
  error = false,
  errorMessage,
  format = 'DD/MM/YYYY',
  minDate,
  maxDate,
  disablePast = false,
  disableFuture = false,
  showCalendarIcon = true,
  calendarIcon,
  onChange,
  onOpen,
  onClose,
  className,
  'data-testid': dataTestId
}) => {
  const [internalValue, setInternalValue] = useState<DateRange | null>(
    defaultValue || { start: null, end: null }
  );
  const [open, setOpen] = useState(false);
  const [tempStart, setTempStart] = useState<Dayjs | null>(null);
  const [tempEnd, setTempEnd] = useState<Dayjs | null>(null);
  const [leftCalendarMonth, setLeftCalendarMonth] = useState<Dayjs>(dayjs());
  const [rightCalendarMonth, setRightCalendarMonth] = useState<Dayjs>(dayjs().add(1, 'month'));
  const isSelectingDateRef = useRef(false);
  
  const isControlled = value !== undefined;
  const currentValue = isControlled ? value : internalValue;
  const rangeValue = currentValue || { start: null, end: null };

  // Initialize temp values and calendar months when dialog opens
  useEffect(() => {
    if (open) {
      setTempStart(rangeValue.start);
      setTempEnd(rangeValue.end);
      
      // Set initial calendar months based on existing selection or default
      // DateRangeCalendar behavior: keep calendars adjacent (one month apart)
      if (rangeValue.start) {
        const startMonth = dayjs(rangeValue.start);
        setLeftCalendarMonth(startMonth);
        if (rangeValue.end) {
          const endMonth = dayjs(rangeValue.end);
          // If end is in same month or next month, show start and next month
          if (endMonth.isSame(startMonth, 'month') || endMonth.diff(startMonth, 'month') === 1) {
            setRightCalendarMonth(startMonth.add(1, 'month'));
          } else {
            setRightCalendarMonth(endMonth);
          }
        } else {
          setRightCalendarMonth(startMonth.add(1, 'month'));
        }
      } else if (rangeValue.end) {
        const endMonth = dayjs(rangeValue.end);
        setRightCalendarMonth(endMonth);
        setLeftCalendarMonth(endMonth.subtract(1, 'month'));
      } else {
        // Default: current month and next month (adjacent)
        const now = dayjs();
        setLeftCalendarMonth(now);
        setRightCalendarMonth(now.add(1, 'month'));
      }
    }
  }, [open, rangeValue.start, rangeValue.end]);

  const getDisplayText = useCallback(() => {
    // Normalize for display (ensure start <= end)
    let displayStart = rangeValue.start;
    let displayEnd = rangeValue.end;
    
    if (displayStart && displayEnd && displayStart.isAfter(displayEnd, 'day')) {
      // Swap for display if needed
      [displayStart, displayEnd] = [displayEnd, displayStart];
    }
    
    if (!displayStart && !displayEnd) {
      return '';
    }
    if (displayStart && displayEnd) {
      return `${displayStart.format(format)} - ${displayEnd.format(format)}`;
    }
    if (displayStart) {
      return `${displayStart.format(format)} - ...`;
    }
    if (displayEnd) {
      return `... - ${displayEnd.format(format)}`;
    }
    return '';
  }, [rangeValue, format]);

  const handleOpen = useCallback(() => {
    if (readOnly || disabled) return;
    setOpen(true);
    onOpen?.();
  }, [readOnly, disabled, onOpen]);

  const handleClose = useCallback(() => {
    setOpen(false);
    // Reset temp values
    setTempStart(null);
    setTempEnd(null);
    onClose?.();
  }, [onClose]);

  // Get the actual start and end dates (normalized so start <= end)
  const getNormalizedRange = useCallback(() => {
    if (!tempStart && !tempEnd) return { start: null, end: null };
    if (tempStart && !tempEnd) return { start: tempStart, end: null };
    if (!tempStart && tempEnd) return { start: null, end: tempEnd };
    
    // Both exist - normalize
    if (tempStart!.isBefore(tempEnd!, 'day') || tempStart!.isSame(tempEnd!, 'day')) {
      return { start: tempStart, end: tempEnd };
    }
    return { start: tempEnd, end: tempStart };
  }, [tempStart, tempEnd]);

  const handleApply = useCallback(() => {
    if (!tempStart || !tempEnd) return;
    
    // Normalize the range (ensure start <= end)
    const normalized = getNormalizedRange();
    const updatedRange: DateRange = {
      start: normalized.start,
      end: normalized.end,
    };
    
    if (!isControlled) {
      setInternalValue(updatedRange);
    }
    
    onChange?.(updatedRange);
    handleClose();
  }, [tempStart, tempEnd, getNormalizedRange, isControlled, onChange, handleClose]);

  const handleClear = useCallback(() => {
    setTempStart(null);
    setTempEnd(null);
  }, []);

  // Unified date change handler that works for both calendars
  const handleDateChange = useCallback((newValue: Dayjs | null) => {
    if (!newValue) return;

    // Mark that we're selecting a date (to prevent auto month navigation)
    isSelectingDateRef.current = true;

    // If no start date, set it
    if (!tempStart) {
      setTempStart(newValue);
      setTempEnd(null);
    } else if (tempStart && !tempEnd) {
      // If start date is set but no end date
      // If selected date is before or same as start, swap them
      if (newValue.isBefore(tempStart, 'day') || newValue.isSame(tempStart, 'day')) {
        setTempStart(newValue);
        setTempEnd(tempStart);
      } else {
        // Otherwise, set it as end date
        setTempEnd(newValue);
      }
    } else {
      // If both are set, reset and start over with new click
      setTempStart(newValue);
      setTempEnd(null);
    }

    // Reset the flag after a short delay
    setTimeout(() => {
      isSelectingDateRef.current = false;
    }, 100);
  }, [tempStart, tempEnd]);

  const getMinDate = () => {
    if (minDate) return minDate;
    if (disablePast) return dayjs();
    return undefined;
  };

  const getMaxDate = () => {
    if (maxDate) return maxDate;
    if (disableFuture) return dayjs();
    return undefined;
  };

  const shouldDisableDate = useCallback((date: Dayjs) => {
    const min = getMinDate();
    const max = getMaxDate();
    
    if (min && date.isBefore(min, 'day')) return true;
    if (max && date.isAfter(max, 'day')) return true;
    
    return false;
  }, [minDate, maxDate, disablePast, disableFuture]);

  // Helper function to check if a day is in range (works across both calendars)
  const isInRange = useCallback((day: Dayjs): { isStart: boolean; isEnd: boolean; isBetween: boolean } => {
    if (!tempStart || !tempEnd) {
      // If only start is selected, highlight just that
      if (tempStart && day.isSame(tempStart, 'day')) {
        return { isStart: true, isEnd: false, isBetween: false };
      }
      return { isStart: false, isEnd: false, isBetween: false };
    }

    // Ensure start is before end for highlighting
    const actualStart = tempStart.isBefore(tempEnd, 'day') ? tempStart : tempEnd;
    const actualEnd = tempStart.isBefore(tempEnd, 'day') ? tempEnd : tempStart;

    const isStart = day.isSame(actualStart, 'day');
    const isEnd = day.isSame(actualEnd, 'day');
    const isBetween = day.isAfter(actualStart, 'day') && day.isBefore(actualEnd, 'day');

    return { isStart, isEnd, isBetween };
  }, [tempStart, tempEnd]);

  // Custom slot props to highlight range for both calendars
  const getDaySlotProps = useCallback(() => ({
    day: {
      sx: (ownerState: any) => {
        const day = ownerState.day;
        if (!day) return {};
        
        const { isStart, isEnd, isBetween } = isInRange(day);
        
        if (isStart) {
          return {
            borderTopLeftRadius: '50%',
            borderBottomLeftRadius: '50%',
            backgroundColor: 'primary.main',
            color: 'primary.contrastText',
            fontWeight: 'bold',
            '&:hover': {
              backgroundColor: 'primary.dark',
            },
            '&.Mui-selected': {
              backgroundColor: 'primary.main',
            },
          };
        }
        
        if (isEnd) {
          return {
            borderTopRightRadius: '50%',
            borderBottomRightRadius: '50%',
            backgroundColor: 'primary.main',
            color: 'primary.contrastText',
            fontWeight: 'bold',
            '&:hover': {
              backgroundColor: 'primary.dark',
            },
            '&.Mui-selected': {
              backgroundColor: 'primary.main',
            },
          };
        }
        
        if (isBetween) {
          return {
            borderRadius: 0,
            backgroundColor: 'action.selected',
            color: 'text.primary',
            '&:hover': {
              backgroundColor: 'action.selected',
            },
            '&.Mui-selected': {
              backgroundColor: 'action.selected',
            },
          };
        }
        
        return {};
      },
    },
  }), [isInRange]);

  return (
    <LocalizationProvider dateAdapter={AdapterDayjs}>
      <FormControl 
        fullWidth 
        disabled={disabled}
        error={error || !!errorMessage}
        {...(className && { className })}
        data-testid={dataTestId}
      >
        {label && (
          <FormLabel sx={{ mb: 1 }}>
            {label}
            {required && <span style={{ color: 'red', marginLeft: 4 }}>*</span>}
          </FormLabel>
        )}
        
        <TextField
          value={getDisplayText()}
          placeholder={placeholder}
          onClick={handleOpen}
          onFocus={handleOpen}
          disabled={disabled}
          readOnly={readOnly}
          required={required}
          error={error || !!errorMessage}
          fullWidth
          InputProps={{
            readOnly: true,
            endAdornment: showCalendarIcon ? (
              <InputAdornment position="end">
                <IconButton
                  size="small"
                  disabled={disabled || readOnly}
                  onClick={handleOpen}
                  data-testid="calendar-icon"
                >
                  {calendarIcon || <CalendarTodayIcon />}
                </IconButton>
              </InputAdornment>
            ) : null,
          }}
        />
        
        <Dialog
          open={open}
          onClose={handleClose}
          maxWidth="lg"
          PaperProps={{
            sx: {
              '& .MuiDialogContent-root': {
                padding: 0,
                margin: 0,
              },
              '& .MuiDialog-paper': {
                overflow: 'visible',
              },
            },
          }}
        >
          <DialogContent sx={{ 
            p: 0, 
            m: 0, 
            display: 'flex', 
            justifyContent: 'center',
            alignItems: 'center',
            '&.MuiDialogContent-root': { 
              padding: 0,
              margin: 0,
            },
          }}>
            <Stack direction="row" spacing={0} sx={{ width: 'auto', margin: 0, padding: 0 }}>
              {/* Left Calendar */}
              <Box sx={{ margin: 0, padding: 0 }}>
                <StaticDatePicker
                  displayStaticWrapperAs="desktop"
                  value={tempStart || null}
                  onChange={handleDateChange}
                  minDate={getMinDate()}
                  maxDate={getMaxDate()}
                  shouldDisableDate={shouldDisableDate}
                  disablePast={disablePast}
                  disableFuture={disableFuture}
                  defaultCalendarMonth={leftCalendarMonth}
                  onMonthChange={(newMonth) => {
                    // Only update month when user manually navigates, not when selecting dates
                    // DateRangeCalendar behavior: keep calendars adjacent
                    if (!isSelectingDateRef.current) {
                      setLeftCalendarMonth(newMonth);
                      // Keep right calendar one month ahead
                      setRightCalendarMonth(dayjs(newMonth).add(1, 'month'));
                    }
                  }}
                  slotProps={{
                    ...getDaySlotProps(),
                    // Hide any action buttons from the calendar (they have their own navigation)
                    actionBar: {
                      actions: [],
                    },
                    layout: {
                      sx: {
                        padding: 0,
                        margin: 0,
                      },
                    },
                  } as any}
                  sx={{
                    margin: 0,
                    padding: 0,
                    '& .MuiPickersLayout-root': {
                      padding: 0,
                      margin: 0,
                    },
                  }}
                />
              </Box>

              {/* Right Calendar */}
              <Box sx={{ margin: 0, padding: 0 }}>
                <StaticDatePicker
                  displayStaticWrapperAs="desktop"
                  value={tempEnd || null}
                  onChange={handleDateChange}
                  minDate={getMinDate()}
                  maxDate={getMaxDate()}
                  shouldDisableDate={shouldDisableDate}
                  disablePast={disablePast}
                  disableFuture={disableFuture}
                  defaultCalendarMonth={rightCalendarMonth}
                  onMonthChange={(newMonth) => {
                    // Only update month when user manually navigates, not when selecting dates
                    // DateRangeCalendar behavior: keep calendars adjacent
                    if (!isSelectingDateRef.current) {
                      setRightCalendarMonth(newMonth);
                      // Keep left calendar one month behind
                      setLeftCalendarMonth(dayjs(newMonth).subtract(1, 'month'));
                    }
                  }}
                  slotProps={{
                    ...getDaySlotProps(),
                    // Hide any action buttons from the calendar (they have their own navigation)
                    actionBar: {
                      actions: [],
                    },
                    layout: {
                      sx: {
                        padding: 0,
                        margin: 0,
                      },
                    },
                  } as any}
                  sx={{
                    margin: 0,
                    padding: 0,
                    '& .MuiPickersLayout-root': {
                      padding: 0,
                      margin: 0,
                    },
                  }}
                />
              </Box>
            </Stack>
          </DialogContent>
          
          <DialogActions>
            <Button onClick={handleClear} color="inherit">
              Clear
            </Button>
            <Button onClick={handleClose} color="inherit">
              Cancel
            </Button>
            <Button 
              onClick={handleApply} 
              variant="contained"
              disabled={!tempStart || !tempEnd}
            >
              Apply
            </Button>
          </DialogActions>
        </Dialog>
        
        {(helperText || errorMessage) && (
          <FormHelperText>
            {errorMessage || helperText}
          </FormHelperText>
        )}
      </FormControl>
    </LocalizationProvider>
  );
};

export { DateRangePicker };
export default DateRangePicker;
