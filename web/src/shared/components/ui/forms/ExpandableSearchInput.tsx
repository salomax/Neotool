import { alpha, IconButton, InputAdornment, TextField, useTheme } from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import ClearIcon from '@mui/icons-material/Clear';

interface ExpandableSearchInputProps {
  searchQuery: string;
  onSearchQueryChange: (query: string) => void;
  placeholder: string;
  ariaClearLabel: string;
}

export function ExpandableSearchInput({
  searchQuery,
  onSearchQueryChange,
  placeholder,
  ariaClearLabel,
}: ExpandableSearchInputProps) {
  const theme = useTheme();
  const normalizedSearchQuery = searchQuery.trim();

  const actionButtonsRevealCollapsedWidth = 90;
  const actionButtonsRevealSearchExpandedWidth = 320;
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
      sx={{
        width: actionButtonsRevealCollapsedWidth,
        transition: actionButtonsRevealButtonTransition,
        '& .MuiOutlinedInput-root': {
          height: '48px',
          minHeight: '48px',
          boxSizing: 'border-box',
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
          display: 'inline-block',
          overflow: 'hidden',
          whiteSpace: 'nowrap',
          maxWidth: 0,
          opacity: 0,
          transform: 'translateX(-8px)',
          transition: actionButtonsRevealLabelHiddenTransition,
        },
        '& .MuiInputAdornment-root': {
          height: '100%',
          display: 'flex',
          alignItems: 'center',
        },
        '& .MuiInputAdornment-positionStart': {
          flex: 1,
          mr: '0 !important',
          ml: 3.25,
          justifyContent: 'center',
          transition: actionButtonsRevealStartIconTransition,
        },
        '& .MuiInputAdornment-positionEnd': {
          ml: 0.5,
          transition: actionButtonsRevealEndIconTransition,
          opacity: 0,
          maxWidth: 0,
          overflow: 'hidden',
        },
        '&:hover .MuiInputBase-input, &:focus-within .MuiInputBase-input': {
          transition: actionButtonsRevealLabelShownTransition,
          maxWidth: 240,
          opacity: 1,
          transform: 'translateX(0)',
        },
        '&:hover .MuiInputAdornment-positionStart, &:focus-within .MuiInputAdornment-positionStart': {
          mr: 0.5,
          pr: 0.5,
          ml: 1,
          flex: '0 0 auto',
          justifyContent: 'flex-start',
        },
        '&:hover .MuiInputAdornment-positionEnd, &:focus-within .MuiInputAdornment-positionEnd': {
          ml: 1,
          opacity: 1,
          maxWidth: 240,
        },
        '&:hover .MuiOutlinedInput-root, &:focus-within .MuiOutlinedInput-root': {
          px: actionButtonsRevealExpandedPx,
          justifyContent: 'flex-start',
          '& .MuiOutlinedInput-notchedOutline': {
            borderColor: theme.palette.primary.main,
            borderWidth: 1,
          },
        },
        '&:hover, &:focus-within': {
          width: actionButtonsRevealSearchExpandedWidth,
        },
        ...(normalizedSearchQuery && {
          width: actionButtonsRevealSearchExpandedWidth,
          '& .MuiOutlinedInput-root': {
            px: actionButtonsRevealExpandedPx,
            justifyContent: 'flex-start',
            '& .MuiOutlinedInput-notchedOutline': {
              borderColor: theme.palette.primary.main,
              borderWidth: 1,
            },
          },
          '& .MuiInputBase-input': {
            maxWidth: 240,
            opacity: 1,
            transform: 'translateX(0)',
          },
          '& .MuiInputAdornment-positionStart': {
            mr: 0.5,
            pr: 0.5,
            ml: 1,
            flex: '0 0 auto',
            justifyContent: 'flex-start',
          },
          '& .MuiInputAdornment-positionEnd': {
            ml: 1,
            opacity: 1,
            maxWidth: 240,
          },
        }),
      }}
    />
  );
}
