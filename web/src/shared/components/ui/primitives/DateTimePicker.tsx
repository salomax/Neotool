import React, { useState, useCallback } from 'react';
import {
  Box,
  TextField,
  FormControl,
  FormLabel,
  FormHelperText,
  InputAdornment,
  IconButton,
  Stack,
  Typography
} from '@/shared/ui/mui-imports';
import { DateTimePicker as MuiDateTimePicker } from '@mui/x-date-pickers/DateTimePicker';
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider';
import { AdapterDayjs } from '@mui/x-date-pickers/AdapterDayjs';
import dayjs, { Dayjs } from 'dayjs';
import { CalendarTodayIcon, AccessTimeIcon } from '@/shared/ui/mui-imports';

export interface DateTimePickerProps {
  /** Current value of the date time picker */
  value?: Dayjs | null;
  /** Default value (uncontrolled) */
  defaultValue?: Dayjs | null;
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
  /** Size of the picker */
  size?: 'small' | 'medium';
  /** Variant of the input */
  variant?: 'standard' | 'outlined' | 'filled';
  /** Whether to show time picker */
  showTime?: boolean;
  /** Whether to show date picker */
  showDate?: boolean;
  /** Format for display */
  format?: string;
  /** Minimum selectable date */
  minDateTime?: Dayjs | null;
  /** Maximum selectable date */
  maxDateTime?: Dayjs | null;
  /** Whether to disable past dates */
  disablePast?: boolean;
  /** Whether to disable future dates */
  disableFuture?: boolean;
  /** Whether to show seconds in time picker */
  showSeconds?: boolean;
  /** Whether to use 24-hour format */
  use24HourFormat?: boolean;
  /** Whether to show calendar icon */
  showCalendarIcon?: boolean;
  /** Whether to show clock icon */
  showClockIcon?: boolean;
  /** Custom calendar icon */
  calendarIcon?: React.ReactNode;
  /** Custom clock icon */
  clockIcon?: React.ReactNode;
  /** Callback fired when the value changes */
  onChange?: (value: Dayjs | null) => void;
  /** Callback fired when the picker opens */
  onOpen?: () => void;
  /** Callback fired when the picker closes */
  onClose?: () => void;
  /** Custom CSS class name */
  className?: string;
  /** Test identifier */
  'data-testid'?: string;
}

const DateTimePicker: React.FC<DateTimePickerProps> = ({
  value,
  defaultValue = null,
  disabled = false,
  readOnly = false,
  required = false,
  label,
  placeholder = 'Select date and time',
  helperText,
  error = false,
  errorMessage,
  size = 'medium',
  variant = 'outlined',
  showTime = true,
  showDate = true,
  format,
  minDateTime,
  maxDateTime,
  disablePast = false,
  disableFuture = false,
  showSeconds = false,
  use24HourFormat = true,
  showCalendarIcon = true,
  showClockIcon = true,
  calendarIcon,
  clockIcon,
  onChange,
  onOpen,
  onClose,
  className,
  'data-testid': dataTestId
}) => {
  const [internalValue, setInternalValue] = useState<Dayjs | null>(defaultValue);
  
  const isControlled = value !== undefined;
  const currentValue = isControlled ? value : internalValue;

  // Determine the display format
  const getDisplayFormat = () => {
    if (format) return format;
    
    if (showDate && showTime) {
      return use24HourFormat 
        ? `DD/MM/YYYY HH:mm${showSeconds ? ':ss' : ''}`
        : `DD/MM/YYYY hh:mm${showSeconds ? ':ss' : ''} A`;
    } else if (showDate) {
      return 'DD/MM/YYYY';
    } else if (showTime) {
      return use24HourFormat 
        ? `HH:mm${showSeconds ? ':ss' : ''}`
        : `hh:mm${showSeconds ? ':ss' : ''} A`;
    }
    
    return 'DD/MM/YYYY HH:mm';
  };

  const handleChange = useCallback((newValue: Dayjs | null) => {
    if (readOnly) return;
    
    if (!isControlled) {
      setInternalValue(newValue);
    }
    
    onChange?.(newValue);
  }, [readOnly, isControlled, onChange]);

  const handleOpen = useCallback(() => {
    if (readOnly || disabled) return;
    onOpen?.();
  }, [readOnly, disabled, onOpen]);

  const handleClose = useCallback(() => {
    onClose?.();
  }, [onClose]);

  const renderInput = (params: any) => {
    const inputProps = {
      ...params,
      size,
      variant,
      disabled,
      required,
      error: error || !!errorMessage,
      placeholder: placeholder || 'Select date and time',
      InputProps: {
        ...params.InputProps,
        endAdornment: (
          <InputAdornment position="end">
            <Stack direction="row" spacing={0.5}>
              {showDate && showCalendarIcon && (
                <IconButton
                  size="small"
                  disabled={disabled || readOnly}
                  onClick={handleOpen}
                  data-testid="calendar-icon"
                >
                  {calendarIcon || <CalendarTodayIcon />}
                </IconButton>
              )}
              {showTime && showClockIcon && (
                <IconButton
                  size="small"
                  disabled={disabled || readOnly}
                  onClick={handleOpen}
                  data-testid="clock-icon"
                >
                  {clockIcon || <AccessTimeIcon />}
                </IconButton>
              )}
            </Stack>
          </InputAdornment>
        ),
      },
    };

    return <TextField {...inputProps} />;
  };

  const getMinDateTime = () => {
    if (minDateTime) return minDateTime;
    if (disablePast) return dayjs();
    return undefined;
  };

  const getMaxDateTime = () => {
    if (maxDateTime) return maxDateTime;
    if (disableFuture) return dayjs();
    return undefined;
  };

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
        
        <MuiDateTimePicker
          value={currentValue}
          onChange={handleChange}
          onOpen={handleOpen}
          onClose={handleClose}
          minDateTime={getMinDateTime()}
          maxDateTime={getMaxDateTime()}
          format={getDisplayFormat()}
          disabled={disabled}
          readOnly={readOnly}
          openTo={showDate ? 'day' : 'hours'}
          views={showDate && showTime ? ['year', 'month', 'day', 'hours', 'minutes'] : 
                 showDate ? ['year', 'month', 'day'] : 
                 ['hours', 'minutes']}
          slotProps={{
            textField: {
              size,
              variant,
              fullWidth: true,
            },
          }}
        />
        
        {(helperText || errorMessage) && (
          <FormHelperText>
            {errorMessage || helperText}
          </FormHelperText>
        )}
      </FormControl>
    </LocalizationProvider>
  );
};

export { DateTimePicker };
export default DateTimePicker;
