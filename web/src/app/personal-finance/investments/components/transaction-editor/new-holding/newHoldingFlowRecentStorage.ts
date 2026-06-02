"use client";

import type { AssetType } from "@/lib/graphql/types/__generated__/graphql";
import type { FundAutocompleteValue } from "../../FundAutocomplete";
import type { EquityAutocompleteValue } from "../../EquityAutocomplete";

const STORAGE_KEY = "invistus.newHoldingFlow.recent.v1";
const MAX_ITEMS = 5;

export type NewHoldingRecentScope = "fund_cvm" | "fund_manual" | "equity_stock" | "equity_reit" | "crypto";

export type NewHoldingRecentFundManual = {
  manualFundName: string;
  manualFundCnpj: string;
  manualAssetType: AssetType;
};

export type NewHoldingRecentCrypto = {
  name: string;
  ticker: string;
  network: string;
};

type Bucket = Partial<Record<NewHoldingRecentScope, unknown[]>>;

function readBucket(): Bucket {
  if (typeof window === "undefined") return {};
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return {};
    const parsed = JSON.parse(raw) as unknown;
    if (!parsed || typeof parsed !== "object") return {};
    return parsed as Bucket;
  } catch {
    return {};
  }
}

function writeBucket(bucket: Bucket): void {
  if (typeof window === "undefined") return;
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(bucket));
  } catch {
    /* quota / private mode */
  }
}

function pushUnique<T>(scope: NewHoldingRecentScope, item: T, keyOf: (row: T) => string): void {
  const bucket = readBucket();
  const prev = (bucket[scope] as T[] | undefined) ?? [];
  const key = keyOf(item);
  const next = [item, ...prev.filter((row) => keyOf(row) !== key)].slice(0, MAX_ITEMS);
  bucket[scope] = next;
  writeBucket(bucket);
}

export function readRecentFundCvm(): FundAutocompleteValue[] {
  const rows = readBucket().fund_cvm;
  if (!Array.isArray(rows)) return [];
  return rows.filter(isFundAutocompleteValue);
}

export function pushRecentFundCvm(fund: FundAutocompleteValue): void {
  pushUnique("fund_cvm", fund, (f) => f.id);
}

export function readRecentFundManual(): NewHoldingRecentFundManual[] {
  const rows = readBucket().fund_manual;
  if (!Array.isArray(rows)) return [];
  return rows.filter(isRecentFundManual);
}

export function pushRecentFundManual(entry: NewHoldingRecentFundManual): void {
  pushUnique("fund_manual", entry, (r) => `${r.manualFundName.trim().toLowerCase()}|${r.manualFundCnpj.trim()}|${r.manualAssetType}`);
}

export function readRecentEquityStock(): EquityAutocompleteValue[] {
  return readEquityList("equity_stock");
}

export function readRecentEquityReit(): EquityAutocompleteValue[] {
  return readEquityList("equity_reit");
}

function readEquityList(scope: "equity_stock" | "equity_reit"): EquityAutocompleteValue[] {
  const rows = readBucket()[scope];
  if (!Array.isArray(rows)) return [];
  return rows.filter(isEquityAutocompleteValue);
}

export function pushRecentEquityStock(asset: EquityAutocompleteValue): void {
  pushUnique("equity_stock", normalizeEquityRecent(asset), (a) => a.id);
}

export function pushRecentEquityReit(asset: EquityAutocompleteValue): void {
  pushUnique("equity_reit", normalizeEquityRecent(asset), (a) => a.id);
}

function normalizeEquityRecent(asset: EquityAutocompleteValue): EquityAutocompleteValue {
  return {
    ...asset,
    logoUrl: asset.logoUrl ?? null,
  };
}

export function readRecentCrypto(): NewHoldingRecentCrypto[] {
  const rows = readBucket().crypto;
  if (!Array.isArray(rows)) return [];
  return rows.filter(isRecentCrypto);
}

export function pushRecentCrypto(entry: NewHoldingRecentCrypto): void {
  pushUnique("crypto", entry, (c) => `${c.ticker.trim().toUpperCase()}|${c.name.trim().toLowerCase()}`);
}

function isFundAutocompleteValue(row: unknown): row is FundAutocompleteValue {
  if (!row || typeof row !== "object") return false;
  const r = row as Partial<FundAutocompleteValue>;
  return typeof r.id === "string" && typeof r.name === "string" && typeof r.cnpj === "string" && typeof r.assetType === "string" && typeof r.isActive === "boolean";
}

function isRecentFundManual(row: unknown): row is NewHoldingRecentFundManual {
  if (!row || typeof row !== "object") return false;
  const r = row as Partial<NewHoldingRecentFundManual>;
  return typeof r.manualFundName === "string" && typeof r.manualFundCnpj === "string" && r.manualAssetType != null;
}

function isEquityAutocompleteValue(row: unknown): row is EquityAutocompleteValue {
  if (!row || typeof row !== "object") return false;
  const r = row as Partial<EquityAutocompleteValue>;
  return (
    typeof r.id === "string" &&
    typeof r.ticker === "string" &&
    typeof r.name === "string" &&
    typeof r.assetType === "string" &&
    typeof r.isActive === "boolean"
  );
}

function isRecentCrypto(row: unknown): row is NewHoldingRecentCrypto {
  if (!row || typeof row !== "object") return false;
  const r = row as Partial<NewHoldingRecentCrypto>;
  return typeof r.name === "string" && typeof r.ticker === "string" && typeof r.network === "string";
}
