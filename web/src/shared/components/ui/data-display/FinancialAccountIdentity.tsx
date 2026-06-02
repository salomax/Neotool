"use client";

import * as React from "react";
import { Avatar, Box, Typography } from "@mui/material";
import { FINANCE_TABLE_BODY_FONT_SIZE } from "./Table";

/** Minimal account shape for custodian / “Corretora” display (matches investment cash table column). */
export type FinancialAccountIdentityAccount = {
  id?: string;
  name: string;
  themeColor?: string | null;
  issuerName?: string | null;
  issuer?: { name?: string | null; logoAsset?: { publicUrl?: string | null } | null } | null;
};

export interface FinancialAccountIdentityProps {
  account: FinancialAccountIdentityAccount | null | undefined;
  /** Table cells align the block to the end; drawers and forms usually start. */
  justify?: "flex-start" | "flex-end";
  /** Avatar edge length in px */
  avatarSize?: number;
  /** Overrides the `title` tooltip on the account name line (e.g. fallback to account id). */
  nameTitle?: string;
  "data-testid"?: string;
}

/**
 * Consistent logo + account name + issuer line for investment custodian accounts
 * (same visual language as the “Corretora” column on investment cash movements).
 */
export function FinancialAccountIdentity({
  account,
  justify = "flex-start",
  avatarSize = 36,
  nameTitle,
  "data-testid": dataTestId,
}: FinancialAccountIdentityProps) {
  const logoUrl = account?.issuer?.logoAsset?.publicUrl || undefined;
  const nameLine = account?.name || "—";
  const title = nameTitle ?? (account ? (account.name || account.id || "—") : "—");
  const issuerLine = account ? account.issuer?.name || account.issuerName || "—" : "—";
  const fallbackLetter = account?.name ? account.name.slice(0, 1).toUpperCase() : "?";

  return (
    <Box
      data-testid={dataTestId}
      sx={{
        display: "flex",
        alignItems: "center",
        justifyContent: justify,
        gap: 1,
        width: justify === "flex-end" ? "100%" : undefined,
        minWidth: 0,
      }}
    >
      <Avatar
        variant="rounded"
        src={logoUrl || undefined}
        slotProps={{ img: { style: { objectFit: "contain" } } }}
        sx={{
          bgcolor: logoUrl
            ? "transparent"
            : account
              ? (account.themeColor || "primary.main")
              : "grey.400",
          width: avatarSize,
          height: avatarSize,
          borderRadius: 1,
          flexShrink: 0,
        }}
      >
        {fallbackLetter}
      </Avatar>
      <Box sx={{ minWidth: 0, textAlign: justify === "flex-end" ? "right" : "left" }}>
        <Typography
          variant="body2"
          noWrap
          title={title}
          sx={{ fontSize: FINANCE_TABLE_BODY_FONT_SIZE, lineHeight: 1.25 }}
        >
          {nameLine}
        </Typography>
        <Typography variant="caption" color="text.secondary" noWrap sx={{ lineHeight: 1.2 }}>
          {issuerLine}
        </Typography>
      </Box>
    </Box>
  );
}
