"use client";
import React from "react";
import {
  TextField as MUITextField,
  TextFieldProps as MUITextFieldProps,
} from "@mui/material";

export interface TextFieldProps extends Omit<MUITextFieldProps, "inputProps"> {
  /**
   * Whether the input is read-only.
   * When true, the input cannot be edited but can still be focused and selected.
   */
  readOnly?: boolean;
  /**
   * Additional props to pass to the underlying input element.
   */
  inputProps?: MUITextFieldProps["inputProps"];
}

/**
 * TextField - A reusable text input component with readOnly support
 * 
 * This component wraps Material-UI's TextField and adds support for a `readOnly` prop
 * that properly handles read-only state styling and behavior.
 * 
 * @example
 * ```tsx
 * <TextField
 *   label="Email"
 *   value={email}
 *   readOnly
 *   placeholder="user@example.com"
 * />
 * ```
 */
export const TextField = React.forwardRef<HTMLInputElement, TextFieldProps>(
  ({ readOnly, inputProps, InputProps, sx, ...props }, ref) => {
    // Merge readOnly into inputProps (for the native input element)
    const mergedInputProps = {
      ...inputProps,
      ...(readOnly && { readOnly: true }),
    };

    // Add readOnly-specific styling
    const readOnlySx = readOnly
      ? {
          "& .MuiInputBase-root": {
            backgroundColor: "background.paper",
            "& .MuiInputBase-input": {
              cursor: "default",
              WebkitTextFillColor: "text.secondary",
              color: "text.secondary",
            },
            "& .MuiOutlinedInput-notchedOutline": {
              border: "none",
            },
            "&:hover": {
              "& .MuiOutlinedInput-notchedOutline": {
                border: "none",
              },
            },
            "&.Mui-focused": {
              "& .MuiOutlinedInput-notchedOutline": {
                border: "none",
              },
            },
          },
          ...sx,
        }
      : sx;

    return (
      <MUITextField
        ref={ref}
        inputProps={mergedInputProps}
        InputProps={InputProps}
        sx={readOnlySx}
        {...props}
      />
    );
  }
);

TextField.displayName = "TextField";

export default TextField;
