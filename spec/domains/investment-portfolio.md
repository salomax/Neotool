---
id: domain.investment-portfolio
status: canonical
kind: domain
source_of_truth: code
last_verified: 2026-08-02
schema: spec-metadata-v1
---

# Investment Portfolio

## Boundary

User-owned portfolios, positions, assets held by users, asset allocation, broker imports, portfolio performance, and portfolio-specific calculations.

## Not In Scope

Public market data ingestion itself, unless the data is consumed to compute portfolio behavior.

## Feature Groups

- Portfolio structure
- Positions and custody
- Asset allocation
- Broker import
- Portfolio performance
- Portfolio events and calculations

## Reverse Spec Notes

Separate user-owned portfolio behavior from public market data. Cite the boundary explicitly when a feature consumes market data.
