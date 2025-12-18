import React, { useState, useCallback } from 'react';
import {
  Box,
  Typography,
  Switch as MuiSwitch,
  FormControl,
  FormControlLabel,
  FormLabel,
  FormHelperText,
  Stack
} from '@/shared/ui/mui-imports';
import { getTestIdProps } from '@/shared/utils/testid';

export interface SwitchProps {
  /** Whether the switch is checked */
  checked?: boolean;
  /** Default checked state (uncontrolled) */
  defaultChecked?: boolean;
  /** Whether the switch is disabled */
  disabled?: boolean;
  /** Whether the switch is read-only */
  readOnly?: boolean;
  /** Size of the switch */
  size?: 'small' | 'medium' | 'large';
  /** Color theme of the switch */
  color?: 'primary' | 'secondary' | 'error' | 'warning' | 'info' | 'success' | 'default';
  /** Label for the switch */
  label?: string;
  /** Helper text below the switch */
  helperText?: string;
  /** Error state */
  error?: boolean;
  /** Whether to show the label */
  showLabel?: boolean;
  /** Label placement */
  labelPlacement?: 'start' | 'end' | 'top' | 'bottom';
  /** Custom label component */
  labelComponent?: React.ReactNode;
  /** Custom checked label */
  checkedLabel?: string;
  /** Custom unchecked label */
  uncheckedLabel?: string;
  /** Whether to show status text */
  showStatus?: boolean;
  /** Custom status formatter */
  statusFormatter?: (checked: boolean) => string;
  /** Callback fired when the state changes */
  onChange?: (checked: boolean) => void;
  /** Custom CSS class name */
  className?: string;
  /** Test identifier */
  'data-testid'?: string;
  /** Name for generating test ID */
  name?: string;
}

const Switch: React.FC<SwitchProps> = ({
  checked,
  defaultChecked = false,
  disabled = false,
  readOnly = false,
  size = 'medium',
  color = 'primary',
  label,
  helperText,
  error = false,
  showLabel = true,
  labelPlacement = 'end',
  labelComponent,
  checkedLabel,
  uncheckedLabel,
  showStatus = false,
  statusFormatter = (checked) => checked ? 'On' : 'Off',
  onChange,
  className,
  'data-testid': dataTestId,
  name
}) => {
  // Generate data-testid from component name and optional name prop
  // Only use testIdProps for FormControl if no explicit dataTestId is provided
  // (when dataTestId is provided, it goes on MuiSwitch, not FormControl)
  const testIdProps = dataTestId ? {} : getTestIdProps('Switch', name);
  
  const [internalChecked, setInternalChecked] = useState(defaultChecked);
  
  const isControlled = checked !== undefined;
  const currentChecked = isControlled ? checked : internalChecked;

  const handleChange = useCallback((event: React.ChangeEvent<HTMLInputElement>) => {
    if (readOnly || disabled) return;
    
    const newChecked = event.target.checked;
    
    if (!isControlled) {
      setInternalChecked(newChecked);
    }
    
    onChange?.(newChecked);
  }, [readOnly, disabled, isControlled, onChange]);

  const renderLabel = () => {
    if (!showLabel) return null;

    if (labelComponent) {
      return labelComponent;
    }

    if (!label) return null;

    return (
      <Typography 
        variant="body2" 
        color={error ? 'error' : 'text.primary'}
        sx={{ 
          fontWeight: 500,
          ...(disabled && { color: 'text.disabled' })
        }}
      >
        {label}
      </Typography>
    );
  };

  const renderStatus = () => {
    if (!showStatus) return null;

    return (
      <Typography 
        variant="caption" 
        color={currentChecked ? 'primary.main' : 'text.secondary'}
        sx={{ 
          fontWeight: 500,
          ...(disabled && { color: 'text.disabled' })
        }}
      >
        {statusFormatter(currentChecked)}
      </Typography>
    );
  };

  const renderCustomLabels = () => {
    if (!checkedLabel && !uncheckedLabel) return null;

    return (
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
        <Typography 
          variant="caption" 
          color={!currentChecked ? 'primary.main' : 'text.secondary'}
          sx={{ 
            fontWeight: 500,
            ...(disabled && { color: 'text.disabled' })
          }}
        >
          {uncheckedLabel}
        </Typography>
        <Typography 
          variant="caption" 
          color={currentChecked ? 'primary.main' : 'text.secondary'}
          sx={{ 
            fontWeight: 500,
            ...(disabled && { color: 'text.disabled' })
          }}
        >
          {checkedLabel}
        </Typography>
      </Box>
    );
  };

  // Handle custom 'large' size with styling
  const switchSize = size === 'large' ? 'medium' : size;
  const largeSx = size === 'large' ? {
    width: 70,
    height: 38,
    '& .MuiSwitch-switchBase': {
      padding: 1,
      '&.Mui-checked': {
        transform: 'translateX(28px)',
        '& + .MuiSwitch-track': {
          opacity: 1,
        },
      },
    },
    '& .MuiSwitch-thumb': {
      width: 26,
      height: 26,
      boxShadow: '0 2px 1px -1px rgba(0,0,0,.2), 0 1px 1px 0 rgba(0,0,0,.14), 0 1px 3px 0 rgba(0,0,0,.12)',
    },
    '& .MuiSwitch-track': {
      borderRadius: 19,
      opacity: 0.38,
    },
    ...(readOnly && {
      '& .MuiSwitch-thumb': {
        cursor: 'default',
      },
      '& .MuiSwitch-track': {
        cursor: 'default',
      },
    }),
  } : {
    ...(readOnly && {
      '& .MuiSwitch-thumb': {
        cursor: 'default',
      },
      '& .MuiSwitch-track': {
        cursor: 'default',
      },
    }),
  };

  const switchElement = (
    <MuiSwitch
      checked={currentChecked}
      disabled={disabled}
      size={switchSize}
      color={color}
      onChange={handleChange}
      inputProps={{ 'aria-label': label || 'switch' }}
      sx={largeSx}
      {...(dataTestId && { 'data-testid': dataTestId })}
    />
  );

  if (labelPlacement === 'start') {
    return (
      <FormControl 
        fullWidth 
        disabled={disabled}
        error={error}
        {...(className && { className })}
        {...testIdProps}
      >
        <Stack direction="row" spacing={2} alignItems="center">
          {renderLabel()}
          <Box>
            {switchElement}
            {renderCustomLabels()}
            {renderStatus()}
          </Box>
        </Stack>
        {helperText && (
          <FormHelperText sx={{ mt: 1 }}>
            {helperText}
          </FormHelperText>
        )}
      </FormControl>
    );
  }

  if (labelPlacement === 'top') {
    return (
      <FormControl 
        fullWidth 
        disabled={disabled}
        error={error}
        {...(className && { className })}
        {...testIdProps}
      >
        <Stack spacing={1} alignItems="flex-start">
          {renderLabel()}
          <Box>
            {switchElement}
            {renderCustomLabels()}
            {renderStatus()}
          </Box>
        </Stack>
        {helperText && (
          <FormHelperText sx={{ mt: 1 }}>
            {helperText}
          </FormHelperText>
        )}
      </FormControl>
    );
  }

  if (labelPlacement === 'bottom') {
    return (
      <FormControl 
        fullWidth 
        disabled={disabled}
        error={error}
        {...(className && { className })}
        {...testIdProps}
      >
        <Stack spacing={1} alignItems="flex-start">
          <Box>
            {switchElement}
            {renderCustomLabels()}
            {renderStatus()}
          </Box>
          {renderLabel()}
        </Stack>
        {helperText && (
          <FormHelperText sx={{ mt: 1 }}>
            {helperText}
          </FormHelperText>
        )}
      </FormControl>
    );
  }

  // Default: labelPlacement === 'end'
  return (
    <FormControl 
      fullWidth 
      disabled={disabled}
      error={error}
      {...(className && { className })}
      {...testIdProps}
    >
      <Stack direction="row" spacing={2} alignItems="center">
        <Box>
          {switchElement}
          {renderCustomLabels()}
          {renderStatus()}
        </Box>
        {renderLabel()}
      </Stack>
      {helperText && (
        <FormHelperText sx={{ mt: 1 }}>
          {helperText}
        </FormHelperText>
      )}
    </FormControl>
  );
};

export { Switch };
export default Switch;
