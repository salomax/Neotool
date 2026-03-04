import * as React from "react";
import { ToggleButton, ToggleButtonGroup, ToggleButtonGroupProps, styled, alpha } from "@mui/material";

export interface SegmentedControlOption {
  value: string;
  label: React.ReactNode;
  activeColor?: string;
}

export interface SegmentedControlProps extends Omit<ToggleButtonGroupProps, "onChange"> {
  options: SegmentedControlOption[];
  value: string;
  onChange: (value: string) => void;
  gap?: number;
}

const StyledToggleButtonGroup = styled(ToggleButtonGroup, {
  shouldForwardProp: (prop) => prop !== 'gap',
})<{ gap?: number }>(({ theme, fullWidth, gap }) => ({
  backgroundColor: "transparent",
  padding: 0,
  borderRadius: 12,
  border: "none",
  gap: gap ? theme.spacing(gap) : 0,
  "& .MuiToggleButtonGroup-grouped": {
    margin: 0,
    border: 0,
    borderRadius: 8,
    "&.Mui-disabled": {
      border: 0,
    },
    "&:not(:first-of-type)": {
      borderRadius: 8,
      borderLeft: 0,
    },
    "&:first-of-type": {
      borderRadius: 8,
    },
    "&.Mui-selected": {
      backgroundColor: theme.palette.action.selected,
      color: theme.palette.text.primary,
      fontWeight: 600,
      boxShadow: "0px 2px 8px rgba(0, 0, 0, 0.08)",
      "&:hover": {
        backgroundColor: theme.palette.action.selected,
      },
    },
    "&:hover": {
      backgroundColor: theme.palette.action.hover,
    },
    textTransform: "none",
    padding: "4px 12px",
    minWidth: 60,
    whiteSpace: "nowrap",
    height: 40,
  },
  ...(fullWidth && {
    width: "100%",
    display: "flex",
    "& .MuiToggleButtonGroup-grouped": {
      flex: 1,
    },
  }),
}));

export function SegmentedControl({
  options,
  value,
  onChange,
  gap = 0,
  ...props
}: SegmentedControlProps) {
  const handleChange = (
    event: React.MouseEvent<HTMLElement>,
    newValue: string | null
  ) => {
    if (newValue !== null) {
      onChange(newValue);
    }
  };

  return (
    <StyledToggleButtonGroup
      value={value}
      exclusive
      onChange={handleChange}
      aria-label="segmented control"
      size="small"
      gap={gap}
      {...props}
    >
      {options.map((option) => (
        <ToggleButton 
          key={option.value} 
          value={option.value} 
          aria-label={String(option.label)}
          sx={{
            "&.Mui-selected": {
              backgroundColor: option.activeColor ? `${alpha(option.activeColor, 0.05)} !important` : undefined,
              "&:hover": {
                backgroundColor: option.activeColor ? `${alpha(option.activeColor, 0.1)} !important` : undefined,
              }
            }
          }}
        >
          {option.label}
        </ToggleButton>
      ))}
    </StyledToggleButtonGroup>
  );
}
