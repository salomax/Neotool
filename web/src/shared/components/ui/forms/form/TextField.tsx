"use client";
import React, { useState } from "react";
import {
  TextField as MUITextField,
  TextFieldProps as MUITextFieldProps,
  InputAdornment,
  IconButton,
} from "@mui/material";
import SearchIcon from "@mui/icons-material/Search";
import EmailIcon from "@mui/icons-material/Email";
import VisibilityIcon from "@mui/icons-material/Visibility";
import VisibilityOffIcon from "@mui/icons-material/VisibilityOff";

export interface TextFieldProps extends Omit<MUITextFieldProps, "size"> {
  /** Size of the input. One of: "small", "medium", "large" */
  size?: 'small' | 'medium' | 'large';
  /** Icon name or element to display at the start of the input */
  startIcon?: string | React.ReactNode;
  /** Icon name or element to display at the end of the input */
  endIcon?: string | React.ReactNode;
}

const iconMap: Record<string, React.ReactNode> = {
  search: <SearchIcon />,
  email: <EmailIcon />,
  visibility: <VisibilityIcon />,
  'visibility-off': <VisibilityOffIcon />,
};

export const TextField: React.FC<TextFieldProps> = ({
  size = 'medium',
  startIcon,
  endIcon,
  type = 'text',
  InputProps,
  inputProps,
  onKeyDown,
  sx,
  ...props
}) => {
  const [showPassword, setShowPassword] = useState(false);

  // Handle custom 'large' size with styling
  const muiSize = size === 'large' ? 'medium' : size;
  const largeSx = size === 'large' ? {
    '& .MuiInputBase-root': {
      fontSize: '1.125rem',
      '& .MuiInputBase-input': {
        padding: '16.5px 14px',
      },
    },
    ...sx,
  } : sx;

  // Convert string icon names to icon components
  const startAdornment = startIcon ? (
    <InputAdornment position="start">
      {typeof startIcon === 'string' ? iconMap[startIcon] || null : startIcon}
    </InputAdornment>
  ) : null;

  // Handle password visibility toggle (only if no endIcon provided)
  const passwordIcon = type === 'password' && !endIcon ? (
    <InputAdornment position="end">
      <IconButton
        aria-label="toggle password visibility"
        onClick={() => setShowPassword(!showPassword)}
        edge="end"
      >
        {showPassword ? <VisibilityOffIcon /> : <VisibilityIcon />}
      </IconButton>
    </InputAdornment>
  ) : null;

  const endAdornment = endIcon ? (
    <InputAdornment position="end">
      {typeof endIcon === 'string' ? iconMap[endIcon] || null : endIcon}
    </InputAdornment>
  ) : passwordIcon;

  // Determine actual input type (toggle password visibility)
  const inputType = type === 'password' && showPassword ? 'text' : type;

  // Merge with existing InputProps
  const mergedInputProps = {
    ...InputProps,
    startAdornment: startAdornment || InputProps?.startAdornment,
    endAdornment: endAdornment || InputProps?.endAdornment,
  };

  // For numeric types, restrict key input and hint virtual keyboards
  const numericKeydown: React.KeyboardEventHandler<HTMLInputElement> = (e) => {
    if (type !== 'number') {
      onKeyDown?.(e);
      return;
    }
    const allowedKeys = [
      'Backspace','Tab','ArrowLeft','ArrowRight','Delete','Home','End'
    ];
    // Allow Ctrl/Cmd+A/C/V/X/Z/Y
    if ((e.ctrlKey || e.metaKey) && ['a','c','v','x','z','y'].includes(e.key.toLowerCase())) {
      onKeyDown?.(e);
      return;
    }
    if (allowedKeys.includes(e.key)) {
      onKeyDown?.(e);
      return;
    }
    // Allow one dot for decimals
    if (e.key === '.') {
      onKeyDown?.(e);
      return;
    }
    // Block anything that's not 0-9
    if (!/^[0-9]$/.test(e.key)) {
      e.preventDefault();
      onKeyDown?.(e);
      return;
    }
    onKeyDown?.(e);
  };

  const mergedInputPropsAttrs = {
    ...inputProps,
    ...(type === 'number' ? { inputMode: 'decimal' as const, pattern: '[0-9]*' } : {}),
    ...(onKeyDown ? { onKeyDown: numericKeydown } : {}),
  };

  return (
    <MUITextField
      fullWidth
      type={inputType}
      size={muiSize}
      InputProps={mergedInputProps}
      inputProps={mergedInputPropsAttrs}
      sx={largeSx}
      {...props}
    />
  );
};

export default TextField;
