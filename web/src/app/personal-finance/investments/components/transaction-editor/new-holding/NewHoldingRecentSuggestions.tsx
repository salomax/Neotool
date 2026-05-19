"use client";

import { Fragment, type ReactNode } from "react";
import ChevronRightIcon from "@mui/icons-material/ChevronRight";
import { Box, Divider, ListItemButton, Paper, Stack, Typography } from "@mui/material";
import { Label } from "@/shared/components/ui/primitives/Label";

export interface NewHoldingRecentSuggestionItem {
  key: string;
  /** Short label for the blue text tile when `avatar` is not used. */
  tile: string;
  /** Leading visual (e.g. `NewHoldingStepAssetAvatar`). */
  avatar?: ReactNode;
  /** Bold primary line. */
  primary: ReactNode;
  /** Smaller grey subtitle. */
  secondary?: ReactNode;
  onPick: () => void;
}

export interface NewHoldingRecentSuggestionsProps {
  title: string;
  items: NewHoldingRecentSuggestionItem[];
}

function tileLabel(raw: string, maxLen = 6): string {
  const t = raw.trim().toUpperCase();
  if (t.length <= maxLen) return t;
  return `${t.slice(0, maxLen - 1)}…`;
}

/** First token of fund name, uppercased (prototype short line). */
export function recentFundPrimaryShort(name: string): string {
  const first = name.trim().split(/\s+/)[0] || name.trim();
  return first.toUpperCase().slice(0, 24);
}

/** Acronym-style tile for fund / manual names (letters/digits only, max 4). */
export function recentSuggestionTileFromName(name: string): string {
  const compact = name.replace(/[^a-zA-Z0-9]/g, "").toUpperCase();
  if (compact.length >= 2) return tileLabel(compact, 4);
  return tileLabel(name.trim().toUpperCase() || "—", 4);
}

/** Primary line for B3-style tickers: strip trailing digits (e.g. CMIG3 → CMIG). */
export function recentEquityPrimaryFromTicker(ticker: string): string {
  const t = ticker.trim().toUpperCase();
  const base = t.replace(/\d+$/, "");
  return (base.length > 0 ? base : t).toUpperCase();
}

/**
 * MRU list for step-1 new-holding gates: `Label` + single outlined container with row dividers.
 * Returns `null` when there is no historical list to show.
 */
export function NewHoldingRecentSuggestions({ title, items }: NewHoldingRecentSuggestionsProps) {
  if (items.length === 0) return null;

  return (
    <Stack spacing={2} data-testid="new-holding-recent-suggestions">
      <Label sx={{ mb: 0 }}>{title}</Label>

      <Paper
        variant="outlined"
        elevation={0}
        sx={{
          borderRadius: 2,
          borderColor: "divider",
          overflow: "hidden",
          bgcolor: "background.paper",
        }}
      >
        {items.map((item, index) => (
          <Fragment key={item.key}>
            {index > 0 ? <Divider sx={{ borderColor: "divider" }} /> : null}
            <ListItemButton
              component="button"
              type="button"
              onClick={item.onPick}
              sx={{
                width: '100%',
                borderRadius: 0,
                display: "flex",
                alignItems: "center",
                gap: 0,
                py: 1.5,
                px: 2,
                boxShadow: "none",
                "&:hover": {
                  bgcolor: "action.hover",
                },
              }}
            >
              <Box sx={{ display: "flex", alignItems: "center", gap: 2, flex: 1, minWidth: 0 }}>
                {item.avatar != null ? (
                  <Box sx={{ flexShrink: 0, display: "flex", alignItems: "center", alignSelf: "center" }}>{item.avatar}</Box>
                ) : (
                  <Box
                    sx={{
                      width: 40,
                      height: 40,
                      borderRadius: "12px",
                      flexShrink: 0,
                      overflow: "hidden",
                      display: "flex",
                      alignItems: "center",
                      justifyContent: "center",
                      bgcolor: "primary.main",
                      px: 0.5,
                    }}
                  >
                    <Typography
                      variant="caption"
                      component="span"
                      sx={{
                        color: "primary.contrastText",
                        fontWeight: 800,
                        fontSize: "0.68rem",
                        lineHeight: 1.15,
                        textAlign: "center",
                        letterSpacing: "-0.02em",
                        display: "-webkit-box",
                        WebkitLineClamp: 2,
                        WebkitBoxOrient: "vertical",
                        overflow: "hidden",
                        wordBreak: "break-word",
                      }}
                    >
                      {tileLabel(String(item.tile), 6)}
                    </Typography>
                  </Box>
                )}

                <Box sx={{ flex: 1, minWidth: 0, textAlign: "left" }}>
                  <Typography variant="subtitle1" fontWeight="bold" noWrap>
                    {item.primary}
                  </Typography>
                  {item.secondary != null ? (
                    <Typography variant="body2" color="text.secondary" noWrap sx={{ mt: 0.25 }}>
                      {item.secondary}
                    </Typography>
                  ) : null}
                </Box>
              </Box>

              <ChevronRightIcon
                sx={{ color: "action.disabled", fontSize: 22, flexShrink: 0, display: "block" }}
                aria-hidden
              />
            </ListItemButton>
          </Fragment>
        ))}
      </Paper>
    </Stack>
  );
}
