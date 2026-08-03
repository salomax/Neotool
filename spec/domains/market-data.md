---
id: domain.market-data
status: canonical
kind: domain
source_of_truth: code
last_verified: 2026-08-02
schema: spec-metadata-v1
---

# Market Data

## Boundary

Public or external financial data such as BACEN indicators, CVM data, market prices, rates, indexes, data providers, ingestion jobs, normalization, and caching.

## Not In Scope

User portfolio ownership or user transaction lifecycle, except as downstream consumers.

## Feature Groups

- BACEN indicators
- CVM data
- Market prices
- Rates and indexes
- Data provider ingestion
- Data freshness and caching

## Reverse Spec Notes

Capture external provider contracts, scheduling, retry behavior, data freshness, and idempotency.
