"use client";

import { formatCnpj } from "@/lib/utils/formatCnpj";
import type { HoldingDetailHeaderVariant } from "./HoldingDetailHeader";
import { getHoldingKind, type HoldingRow } from "./table/investmentTableHelpers";

export interface HoldingDetailHeaderTraits {
  issuerLabel?: string | null;
  assetTypeLabel?: string | null;
  indexerLabel?: string | null;
  taxExemptLabel?: string | null;
  subtitleLabel?: string | null;
  /** Raw fund CNPJ digits/formatted string; presented only for fund holdings. */
  fundCnpj?: string | null;
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

export type TranslateFn = (key: string, options?: Record<string, unknown>) => string;

export function toHeaderVariant(assetType: string | null | undefined): HoldingDetailHeaderVariant {
  const holdingKind = getHoldingKind(assetType);
  if (holdingKind === "fixedIncome") return "fixedIncome";
  if (holdingKind === "fund") return "fund";
  return "equity";
}

function formatPercentRate(
  value: number,
  locale: string,
  options: { minimumFractionDigits: number; maximumFractionDigits: number },
): string {
  return new Intl.NumberFormat(locale, {
    minimumFractionDigits: options.minimumFractionDigits,
    maximumFractionDigits: options.maximumFractionDigits,
  }).format(value * 100);
}

export function buildHoldingDetailHeaderModelFromRow(
  holding: HoldingRow,
  t: TranslateFn,
  locale: string,
): HoldingDetailHeaderModel {
  const indexerLabel =
    holding.indexer != null
      ? t(`investments.detailDrawer.indexers.${holding.indexer}`)
      : null;
  const fixedIncomeIndexerLabel =
    holding.indexerPercentage != null && indexerLabel != null
      ? t("investments.detailDrawer.indexers.withPercentage", {
          label: indexerLabel,
          percentage: formatPercentRate(holding.indexerPercentage, locale, {
            minimumFractionDigits: 1,
            maximumFractionDigits: 2,
          }),
        })
      : holding.fixedAnnualRate != null
        ? t("investments.detailDrawer.indexers.fixedAnnualRate", {
            rate: formatPercentRate(holding.fixedAnnualRate, locale, {
              minimumFractionDigits: 2,
              maximumFractionDigits: 2,
            }),
          })
        : indexerLabel;
  const variant = toHeaderVariant(holding.asset.assetType);

  return {
    variant,
    assetName: holding.asset.name,
    logoUrl: holding.asset.logoAsset?.publicUrl ?? holding.financialAccount?.issuer?.logoAsset?.publicUrl ?? undefined,
    assetType: holding.asset.assetType ?? null,
    account: {
      name: holding.financialAccount?.name ?? null,
    },
    traits:
      variant === "fixedIncome"
        ? {
            issuerLabel:
              holding.financialAccount?.issuer?.name ??
              holding.financialAccount?.issuerName ??
              holding.asset.issuerName ??
              null,
            assetTypeLabel: t(`investments.assetTypes.${holding.asset.assetType}`),
            indexerLabel: fixedIncomeIndexerLabel,
            taxExemptLabel: holding.isTaxExempt ? t("investments.detailDrawer.taxExempt.yes") : t("investments.detailDrawer.taxExempt.no"),
          }
        : {
            issuerLabel: holding.asset.issuerName ?? null,
            assetTypeLabel: t(`investments.assetTypes.${holding.asset.assetType}`),
            subtitleLabel: holding.asset.ticker ?? null,
          },
    metrics: {
      totalValueLabel: "",
      totalValue: null,
      currencyCode: holding.asset.currencyCode || "BRL",
      gainPercent: null,
      locale,
    },
  };
}

function joinUniqueLabelParts(rawParts: Array<string | null | undefined>): string | null {
  const uniqueParts = new Set<string>();
  for (const rawPart of rawParts) {
    const part = (rawPart ?? "").trim();
    if (!part || part === "—") continue;
    uniqueParts.add(part);
  }
  return [...uniqueParts].join(" • ").trim() || null;
}

export function presentHoldingDetailHeaderLabels(model: HoldingDetailHeaderModel): HoldingDetailHeaderLabels {
  const accountName = model.account?.name ?? null;
  const normalizedAccount = accountName?.trim().toLocaleLowerCase() ?? null;
  const {
    issuerLabel,
    assetTypeLabel,
    indexerLabel,
    taxExemptLabel,
    subtitleLabel,
    fundCnpj,
  } = model.traits;

  if (model.variant === "fund") {
    const trimmedFundCnpj = (fundCnpj ?? "").trim();
    return {
      // Asset type ("Fundo de investimento") is redundant inside the funds drawer.
      metaLabel: joinUniqueLabelParts([
        subtitleLabel,
        trimmedFundCnpj ? formatCnpj(trimmedFundCnpj) : null,
      ]),
      // Account line carries custody + issuer so the description stays fund-specific.
      accountLabel: joinUniqueLabelParts([accountName, issuerLabel]),
    };
  }

  if (model.variant === "fixedIncome") {
    return {
      metaLabel: joinUniqueLabelParts([issuerLabel, assetTypeLabel, indexerLabel, taxExemptLabel]),
      accountLabel: accountName,
    };
  }

  const equityMetaParts: Array<string | null | undefined> = [issuerLabel, assetTypeLabel, subtitleLabel];
  const uniqueParts = new Set<string>();
  for (const rawPart of equityMetaParts) {
    const part = (rawPart ?? "").trim();
    if (!part || part === "—") continue;
    if (normalizedAccount && part.toLocaleLowerCase() === normalizedAccount) continue;
    uniqueParts.add(part);
  }

  return {
    metaLabel: [...uniqueParts].join(" • ").trim() || null,
    accountLabel: accountName,
  };
}
