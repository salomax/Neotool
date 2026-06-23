'use client';

import { alpha, IconButton, InputAdornment, TextField, useTheme } from '@mui/material';
import type { SxProps, Theme } from '@mui/material/styles';
import SearchIcon from '@mui/icons-material/Search';
import ClearIcon from '@mui/icons-material/Clear';

export interface ExpandableSearchInputProps {
  searchQuery: string;
  onSearchQueryChange: (query: string) => void;
  placeholder: string;
  ariaClearLabel: string;
  collapsedWidth?: number;
  expandedWidth?: number | string;
  inputMaxWidth?: number;
  height?: number;
  collapsedSearchIconMarginLeft?: number | string;
  sx?: SxProps<Theme>;
}

/**
 * Collapsible search field (icon-only until hover/focus or when the query is non-empty).
 * Matches the interaction pattern used on the finance transactions header.
 */
export function ExpandableSearchInput({
  searchQuery,
  onSearchQueryChange,
  placeholder,
  ariaClearLabel,
  collapsedWidth = 90,
  expandedWidth = 320,
  inputMaxWidth = 240,
  height = 48,
  collapsedSearchIconMarginLeft = 0,
  sx,
}: ExpandableSearchInputProps) {
  const theme = useTheme();
  const normalizedSearchQuery = searchQuery.trim();

  const actionButtonsRevealCollapsedPx = 1;
  const actionButtonsRevealExpandedPx = 0.75;
  const actionButtonsBorderRadiusPx = `${(theme as any).custom?.radius?.button ?? theme.shape.borderRadius}px`;
  const actionButtonsOutlinedBorderColor = alpha(theme.palette.primary.main, 0.5);
  const actionButtonsRevealWidthDurationMs = 180;
  const actionButtonsRevealLabelDurationMs = 260;
  const actionButtonsRevealLabelOpacityDurationMs = 180;
  const actionButtonsRevealLabelDelayMs = 160;

  const actionButtonsRevealButtonTransition = `width ${actionButtonsRevealWidthDurationMs}ms ease, padding ${actionButtonsRevealWidthDurationMs}ms ease`;
  const actionButtonsRevealBgTransition = `background-color ${actionButtonsRevealWidthDurationMs}ms ease`;
  const actionButtonsRevealStartIconTransition = `margin-right ${actionButtonsRevealWidthDurationMs}ms ease`;
  const actionButtonsRevealEndIconTransition = `margin-left ${actionButtonsRevealWidthDurationMs}ms ease`;
  const actionButtonsRevealLabelHiddenTransition = `max-width ${actionButtonsRevealLabelDurationMs}ms ease 0ms, opacity ${actionButtonsRevealLabelOpacityDurationMs}ms ease 0ms, transform ${actionButtonsRevealLabelDurationMs}ms ease 0ms`;
  const actionButtonsRevealLabelShownTransition = `max-width ${actionButtonsRevealLabelDurationMs}ms ease ${actionButtonsRevealLabelDelayMs}ms, opacity ${actionButtonsRevealLabelOpacityDurationMs}ms ease ${actionButtonsRevealLabelDelayMs}ms, transform ${actionButtonsRevealLabelDurationMs}ms ease ${actionButtonsRevealLabelDelayMs}ms`;

  return (
    <TextField
      data-has-value={!!normalizedSearchQuery}
      placeholder={placeholder}
      value={searchQuery}
      onChange={(event) => onSearchQueryChange(event.target.value)}
      InputProps={{
        startAdornment: (
          <InputAdornment position="start">
            <SearchIcon fontSize="small" color="primary" />
          </InputAdornment>
        ),
        endAdornment: normalizedSearchQuery ? (
          <InputAdornment position="end">
            <IconButton
              size="small"
              onMouseDown={(event) => event.preventDefault()}
              onClick={() => onSearchQueryChange('')}
              aria-label={ariaClearLabel}
            >
              <ClearIcon fontSize="small" />
            </IconButton>
          </InputAdornment>
        ) : undefined,
      }}
      sx={[
        {
          width: collapsedWidth,
          maxWidth: '100%',
          minWidth: 0,
          display: 'block',
          transition: actionButtonsRevealButtonTransition,
          '& .MuiOutlinedInput-root': {
            height,
            minHeight: height,
            boxSizing: 'border-box',
            display: 'flex',
            alignItems: 'center',
            overflow: 'hidden',
            borderRadius: actionButtonsBorderRadiusPx,
            transition: `${actionButtonsRevealButtonTransition}, ${actionButtonsRevealBgTransition}`,
            px: actionButtonsRevealCollapsedPx,
            justifyContent: 'center',
            backgroundColor: 'transparent',
            '& .MuiOutlinedInput-notchedOutline': {
              borderColor: actionButtonsOutlinedBorderColor,
              borderWidth: 1,
              borderRadius: actionButtonsBorderRadiusPx,
            },
          },
          '& .MuiInputBase-input': {
            py: 0,
            display: 'block',
            width: 0,
            minWidth: 0,
            flex: '0 1 0px',
            overflow: 'hidden',
            whiteSpace: 'nowrap',
            color: theme.palette.text.primary,
            caretColor: theme.palette.text.primary,
            WebkitTextFillColor: theme.palette.text.primary,
            maxWidth: 0,
            opacity: 0,
            transform: 'translateX(-8px)',
            transition: actionButtonsRevealLabelHiddenTransition,
          },
          '& .MuiInputBase-input::placeholder': {
            color: theme.palette.text.secondary,
            opacity: 1,
            WebkitTextFillColor: theme.palette.text.secondary,
          },
          '& .MuiInputAdornment-root': {
            height: '100%',
            display: 'flex',
            alignItems: 'center',
          },
          '& .MuiInputAdornment-positionStart': {
            flex: '0 0 auto',
            width: '100%',
            minWidth: 0,
            mr: '0 !important',
            ml: 0,
            justifyContent: 'center',
            transition: actionButtonsRevealStartIconTransition,
            '& .MuiSvgIcon-root': {
              marginLeft: collapsedSearchIconMarginLeft,
              transition: actionButtonsRevealStartIconTransition,
            },
          },
          '& .MuiInputAdornment-positionEnd': {
            ml: 0,
            transition: actionButtonsRevealEndIconTransition,
            opacity: 0,
            maxWidth: 0,
            overflow: 'hidden',
          },
          '&:hover .MuiInputBase-input, &:focus-within .MuiInputBase-input, &[data-has-value="true"] .MuiInputBase-input': {
            transition: actionButtonsRevealLabelShownTransition,
            width: 'auto',
            minWidth: 0,
            flex: '1 1 0%',
            maxWidth: inputMaxWidth,
            opacity: 1,
            transform: 'translateX(0)',
            color: theme.palette.text.primary,
            caretColor: theme.palette.text.primary,
            WebkitTextFillColor: theme.palette.text.primary,
          },
          '&:hover .MuiInputAdornment-positionStart, &:focus-within .MuiInputAdornment-positionStart, &[data-has-value="true"] .MuiInputAdornment-positionStart': {
            width: 'auto',
            mr: 0.75,
            ml: 0,
            flex: '0 0 auto',
            justifyContent: 'flex-start',
          },
          '&:hover .MuiInputAdornment-positionStart .MuiSvgIcon-root, &:focus-within .MuiInputAdornment-positionStart .MuiSvgIcon-root, &[data-has-value="true"] .MuiInputAdornment-positionStart .MuiSvgIcon-root': {
            marginLeft: 0,
          },
          '&:hover .MuiInputAdornment-positionEnd, &:focus-within .MuiInputAdornment-positionEnd, &[data-has-value="true"] .MuiInputAdornment-positionEnd': {
            ml: -3,
            opacity: 1,
            maxWidth: inputMaxWidth,
          },
          '&:hover .MuiOutlinedInput-root, &:focus-within .MuiOutlinedInput-root, &[data-has-value="true"] .MuiOutlinedInput-root': {
            px: actionButtonsRevealExpandedPx,
            justifyContent: 'flex-start',
            backgroundColor: theme.palette.background.paper,
            '& .MuiOutlinedInput-notchedOutline': {
              borderColor: theme.palette.primary.main,
              borderWidth: 1,
            },
          },
          '&:hover, &:focus-within, &[data-has-value="true"]': {
            width: expandedWidth,
          },
        },
        ...(sx ? (Array.isArray(sx) ? sx : [sx]) : []),
      ]}
    />
  );
}
