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

const TOGGLE_BUTTON_BASE_SX = {
  whiteSpace: "nowrap",
  "&&": {
    fontWeight: 400,
    backgroundColor: "transparent",
    border: "0 !important",
    boxShadow: "none",
    outline: "none",
    height: "var(--segment-height)",
    minHeight: "var(--segment-height)",
    paddingTop: 0,
    paddingBottom: 0,
    lineHeight: 1.2,
  },
  "&&::before, &&::after": {
    display: "none",
  },
  "&&:hover, &&:focus, &&:focus-visible": {
    backgroundColor: "transparent",
    boxShadow: "none",
    outline: "none",
  },
  "&&.Mui-selected, &&.Mui-selected:hover, &&.Mui-selected:focus, &&.Mui-selected:focus-visible": {
    backgroundColor: "transparent !important",
    boxShadow: "none",
    outline: "none",
    fontWeight: 500,
  },
} as const;

const StyledToggleButtonGroup = styled(ToggleButtonGroup, {
  shouldForwardProp: (prop) => prop !== "gap" && prop !== "optionsCount" && prop !== "selectedIndex" && prop !== "segmentSize",
})<{ gap?: number; optionsCount: number; selectedIndex: number; segmentSize: "small" | "medium" }>(({ theme, fullWidth, gap, optionsCount, selectedIndex, segmentSize }) => ({
  "--segment-height": segmentSize === "small" ? "40px" : "49px",
  "--segment-thumb-inset": "5px",
  "--segment-thumb-radius": segmentSize === "small" ? "8px" : "10px",
  "--segment-root-radius": segmentSize === "small" ? "10px" : "12px",
  "--segment-gap": gap ? theme.spacing(gap) : "14px",
  backgroundColor: alpha(theme.palette.text.primary, 0.04),
  borderRadius: "var(--segment-root-radius)",
  border: "0 !important",
  boxShadow: "none",
  minHeight: "var(--segment-height)",
  height: "var(--segment-height)",
  flexWrap: "nowrap",
  position: "relative",
  isolation: "isolate",
  display: "grid",
  columnGap: "var(--segment-gap)",
  overflow: "hidden",
  "&::before": {
    content: '""',
    position: "absolute",
    top: "var(--segment-thumb-inset)",
    bottom: "var(--segment-thumb-inset)",
    left: "var(--segment-thumb-inset)",
    width: `calc((100% - (var(--segment-thumb-inset) * 2) - (${Math.max(optionsCount - 1, 0)} * var(--segment-gap))) / ${Math.max(optionsCount, 1)})`,
    backgroundColor: theme.palette.background.paper,
    borderRadius: "var(--segment-thumb-radius)",
    boxShadow: "0px 3px 8px rgba(0, 0, 0, 0.12)",
    transform: `translateX(calc(${Math.max(selectedIndex, 0)} * (100% + var(--segment-gap))))`,
    transition: "transform 260ms cubic-bezier(0.2, 0.85, 0.3, 1)",
    zIndex: 0,
  },
  "& .MuiToggleButtonGroup-grouped": {
    position: "relative",
    zIndex: 1,
    transition: "color 220ms ease, font-weight 220ms ease",
    "&.Mui-disabled": {
      border: 0,
    },
    "&:not(:first-of-type)": {
      borderRadius: "var(--segment-thumb-radius)",
      borderLeft: "0 !important",
    },
    "&:first-of-type": {
      borderRadius: "var(--segment-thumb-radius)",
    },
    "&.Mui-selected": {
      backgroundColor: "transparent",
      color: theme.palette.text.primary,
      fontWeight: 600,
      "&:hover": {
        backgroundColor: "transparent",
      },
    },
    "&.MuiToggleButton-root.Mui-selected": {
      backgroundColor: "transparent !important",
      boxShadow: "none",
    },
    "&:hover": {
      backgroundColor: "transparent",
    },
    "&:focus, &:focus-visible": {
      outline: "none",
      boxShadow: "none",
      backgroundColor: "transparent",
    },
    "&.Mui-focusVisible": {
      backgroundColor: "transparent",
      outline: "none",
      boxShadow: "none",
    },
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
  size = "medium",
  gap = 0,
  ...props
}: SegmentedControlProps) {
  const normalizedSize: "small" | "medium" = size === "small" ? "small" : "medium";

  const selectedIndex = React.useMemo(
    () => options.findIndex((option) => option.value === value),
    [options, value]
  );

  const handleChange = (_event: React.MouseEvent<HTMLElement>, newValue: string | null) => {
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
      size={size}
      gap={gap}
      optionsCount={options.length}
      selectedIndex={selectedIndex < 0 ? 0 : selectedIndex}
      segmentSize={normalizedSize}
      {...props}
    >
      {options.map((option) => (
        <ToggleButton
          key={option.value}
          value={option.value}
          aria-label={String(option.label)}
          disableRipple
          disableFocusRipple
          sx={{
            ...TOGGLE_BUTTON_BASE_SX,
            "&.Mui-selected": {
              color: option.activeColor ? `${option.activeColor} !important` : undefined,
            },
          }}
        >
          {option.label}
        </ToggleButton>
      ))}
    </StyledToggleButtonGroup>
  );
}
