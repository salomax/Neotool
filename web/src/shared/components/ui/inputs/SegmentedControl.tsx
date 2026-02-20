import * as React from "react";
import { ToggleButton, ToggleButtonGroup, ToggleButtonGroupProps, styled } from "@mui/material";

export interface SegmentedControlOption {
  value: string;
  label: React.ReactNode;
}

export interface SegmentedControlProps extends Omit<ToggleButtonGroupProps, "onChange"> {
  options: SegmentedControlOption[];
  value: string;
  onChange: (value: string) => void;
}

const StyledToggleButtonGroup = styled(ToggleButtonGroup)(({ theme, fullWidth }) => ({
  backgroundColor: theme.palette.background.paper,
  padding: 0,
  borderRadius: theme.shape.borderRadius,
  border: "none",
  "& .MuiToggleButtonGroup-grouped": {
    margin: 0,
    border: 0,
    borderRadius: theme.shape.borderRadius,
    "&.Mui-disabled": {
      border: 0,
    },
    "&:not(:first-of-type)": {
      borderRadius: theme.shape.borderRadius,
    },
    "&:first-of-type": {
      borderRadius: theme.shape.borderRadius,
    },
    "&.Mui-selected": {
      backgroundColor: theme.palette.action.selected,
      color: theme.palette.text.primary,
      fontWeight: 600,
      boxShadow: "0px 1px 2px rgba(0, 0, 0, 0.1)",
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
      {...props}
    >
      {options.map((option) => (
        <ToggleButton key={option.value} value={option.value} aria-label={String(option.label)}>
          {option.label}
        </ToggleButton>
      ))}
    </StyledToggleButtonGroup>
  );
}
