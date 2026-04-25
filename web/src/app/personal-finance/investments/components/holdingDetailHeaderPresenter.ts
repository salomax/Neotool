"use client";

import type { HoldingDetailHeaderVariant } from "./HoldingDetailHeader";

export interface HoldingDetailHeaderTraits {
  issuerLabel?: string | null;
  assetTypeLabel?: string | null;
  indexerLabel?: string | null;
  taxExemptLabel?: string | null;
  subtitleLabel?: string | null;
}

export interface HoldingDetailHeaderMetrics {
  totalValueLabel: string;
  totalValue: number | null;
  currencyCode: string;
  gainPercent: number | null;
  locale: string;
}

export interface HoldingDetailHeaderModel {
  variant: HoldingDetailHeaderVariant;
  assetName: string;
  logoUrl?: string;
  assetType?: string | null;
  account?: {
    name?: string | null;
  } | null;
  traits: HoldingDetailHeaderTraits;
  metrics: HoldingDetailHeaderMetrics;
}

export interface HoldingDetailHeaderLabels {
  metaLabel: string | null;
  accountLabel: string | null;
}

export function presentHoldingDetailHeaderLabels(model: HoldingDetailHeaderModel): HoldingDetailHeaderLabels {
  const accountLabel = model.account?.name ?? null;
  const normalizedAccount = accountLabel?.trim().toLocaleLowerCase() ?? null;
  const {
    issuerLabel,
    assetTypeLabel,
    indexerLabel,
    taxExemptLabel,
    subtitleLabel,
  } = model.traits;

  const rawParts =
    model.variant === "fixedIncome"
      ? [issuerLabel, assetTypeLabel, indexerLabel, taxExemptLabel]
      : [issuerLabel, subtitleLabel || assetTypeLabel];

  const uniqueParts = new Set<string>();
  for (const rawPart of rawParts) {
    const part = (rawPart ?? "").trim();
    if (!part) continue;
    if (normalizedAccount && part.toLocaleLowerCase() === normalizedAccount) continue;
    uniqueParts.add(part);
  }

  const metaLabel = [...uniqueParts].join(" • ").trim() || null;

  return { metaLabel, accountLabel };
}

