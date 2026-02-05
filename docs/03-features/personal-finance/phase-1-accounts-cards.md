---
title: Phase 1 Accounts/Cards
type: milestone
category: personal-finance
status: planned
version: 1.0.0
tags: [feature, personal-finance, accounts, cards, phase-1]
ai_optimized: true
search_keywords: [personal finance, accounts, cards, card badge, account selector, carousel, bin, issuer]
related:
  - 03-features/personal-finance/personal-finance-overview.md
---

# Phase 1 Accounts/Cards

> **Purpose**: Deliver full accounts/cards CRUD with the account selector and card carousel UI, plus shared components for reuse.

## Overview
This phase establishes the accounts and cards foundation for the personal finance module. It includes backend CRUD endpoints, UI for listing and selecting accounts/cards, and reusable shared components (`AccountSelector`, `AccountBadge`, `CardBadge`).

## Scope
**In scope**
- Backend: accounts/cards model and CRUD endpoints (create, view, edit, delete).
- Frontend: account list/cards UI (carousel) and account selector.
- Shared components: `AccountSelector`, `AccountBadge`, `CardBadge`.
- Tests: API and UI.

**Out of scope**
- Transaction list and aggregation.
- Categories and insights.
- Permissions enforcement (defined later, implemented after endpoint shapes stabilize).

## UI Requirements
- Account carousel displaying card visuals with next/previous navigation.
- Compact account selector for use in sidebar and filters.
- "Add new account or card" action below the carousel.
- Card visuals show: name, masked number, issuer logo, scheme logo, and theme color.

## API Endpoints
- `POST /personal-finance/accounts` create account.
- `GET /personal-finance/accounts` list accounts.
- `GET /personal-finance/accounts/{id}` get account by id.
- `PATCH /personal-finance/accounts/{id}` update account.
- `DELETE /personal-finance/accounts/{id}` delete account.

## Data Model
**Account/Card**
- `id`
- `userId`
- `type` (BANK, CREDIT_CARD, DEBIT_CARD)
- `subtype` (CHECKING_ACCOUNT, SAVINGS, etc.)
- `name`
- `maskedNumber`
- `currencyCode`
- `issuerName`
- `issuerCountry`
- `scheme` (ELO, VISA, MASTERCARD)
- `brand` (e.g., ALIMENTACAO)
- `bin` (first 6 or 8 digits)
- `issuerPhone`
- `issuerUrl`
- `themeColor`
- `logoKey` (issuer logo or uploaded)
- `createdAt`
- `updatedAt`

## BIN Enrichment Rules
- On card creation, accept the BIN (first 6 or 8 digits).
- Lookup BIN in local CSV or BIN service to enrich scheme, issuer, brand, and country.
- If lookup fails, keep user-provided values and mark enrichment as unavailable.
- Theme color defaults by scheme and can be overridden by the user.

## Shared Components
- `AccountSelector`: compact selector with account name and bank.
- `AccountBadge`: list item for account summaries.
- `CardBadge`: visual card with theme color, logos, and masked number.

## User Flows
- Create account or card from the carousel action.
- Edit account or card from list view.
- Delete account or card with confirmation.
- Select account from selector for downstream features.

## Error Handling
- Validate BIN format (6 or 8 digits).
- Validate unique account name per user (if required).
- Return clear errors for duplicate accounts and invalid inputs.

## Testing
- API tests for CRUD endpoints and validation errors.
- UI tests for carousel navigation and selector behavior.
- Snapshot tests for `CardBadge` with different schemes.

## Done Criteria
- All CRUD endpoints available and documented.
- Carousel and selector UI wired to API.
- Shared components exported and reusable.
- Tests passing for API and UI.

## Open Questions
- Should account deletion be soft-delete?
- Should we allow multiple cards per account entity or model cards as separate accounts?
