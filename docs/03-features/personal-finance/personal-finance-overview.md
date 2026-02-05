# Personal Finance Module — Requirements, Milestones, and Backlog

## Scope
- New `personal-finance` module (separate from `financial-data`).
- Manual data entry for MVP; Open Finance ingestion planned later.
- Multi-currency support (foreign currency transactions on cards).
- Desktop web only for now; responsive layout required.
- Infinite scroll for transactions list.

## Assumptions
- Permission checks are required for every action (view/create/edit/delete).
- LGPD compliance required, details to be defined later.
- No strict performance constraints for MVP.

## Functional Requirements
**Phase 1 — Accounts/Cards**
- Backend: accounts/cards model + CRUD endpoints (create, view, edit, delete).
- Frontend: account list/cards UI and account selector.
- Shared components: `AccountSelector`, `AccountBadge`, `CardBadge`.
- Tests: API + UI.

**Phase 2 — Transactions (includes Aggregated View)**
- Backend: transactions model + CRUD, list with filters, aggregation for “All Accounts”.
- Frontend: transactions list with date grouping and infinite scroll.
- Frontend: inline new transaction row and transfer support.
- Shared components: `TransactionTable`, `TransactionRow`, `InlineTransactionFormRow`, `DateGroupHeader`, `CurrencyAmount`.
- Multi-currency: store currency code and display original currency and converted amount (if conversion exists).
- Tests: API + UI.

**Phase 3 — Categories**
- Backend: categories model + CRUD, transaction category linking.
- Frontend: category selector + category pills.
- Frontend: category breakdown in sidebar.
- Shared components: `CategoryPill`, `CategoryBreakdownBar`.
- Tests: API + UI.

**Phase 4 — Summary & Payments**
- Backend: summary metrics + scheduled payments endpoints.
- Frontend: sidebar summary metrics and upcoming payments list.
- Shared components: `SummaryMetricCard`.
- Tests: API + UI.

**Phase 5 — Permissions (cross-cutting)**
- Backend: permission middleware + enforcement on all endpoints.
- Frontend: hide/disable actions based on permission matrix.
- Tests: permission coverage for APIs and UI.
- Note: start after endpoint shapes stabilize, but define the permission matrix early.

## Nice-to-Have Functional Requirements
- Tags (global + user-created).
- Filter transactions by tag.
- Statement import (CSV/OFX).
- Receipt upload and attachment to transactions.
- Grouped transactions with expand/collapse.
- Income by category chart.

## Non-Functional Requirements
- Responsive desktop layout (no mobile layout required now).
- Infinite scroll in transaction list.
- LGPD compliance (data minimization, consent, export/delete policy to be defined).
- Auditability: keep provider raw payloads for Open Finance adapters.
- Security: permission checks on every API route and UI action.

---

## Milestones

1. Milestone 1 — Foundations (Models + Permissions)
- Define core data models.
- Implement permission system and checks.
- Build base API scaffolding.

2. Milestone 2 — MVP Transaction Experience
- List transactions with date grouping and infinite scroll.
- Manual transaction creation and transfer support.
- Account selector with “All Accounts”.

3. Milestone 3 — Insights Sidebar
- Summary metrics.
- Category breakdown.
- Upcoming payments.

4. Milestone 4 — Nice-to-Have Enhancements
- Tags and filtering.
- Receipt upload.
- Statement import.
- Grouped transactions.

5. Milestone 5 — Open Finance Readiness
- Provider adapter interface.
- Store provider raw payloads.
- Contract mapping draft (Pluggy-aligned).

---

## Backlog (Must-Have)

### Backend
- Accounts/Cards model.
- Categories model.
- Transactions model.
- Transfers model.
- Scheduled payments model.
- Permissions model and middleware.
- API: List accounts/cards.
- API: List categories.
- API: List transactions with filters (date, account, type).
- API: Create transaction.
- API: Create transfer.
- API: Aggregated “All Accounts” view.
- API: Summary metrics.
- API: Category breakdown.
- API: Scheduled payments list.

### Frontend
- Route: `personal-finance` module shell.
- Account selector with “All Accounts”.
- Transactions list with date grouping.
- Infinite scroll in list.
- Inline transaction creation row.
- Transfer creation UI.
- Category pills and account badges.
- Amount formatting and currency display.
- Month selector.
- Sidebar summary metrics.
- Sidebar category breakdown.
- Upcoming payments list.

### Shared/Common Components
- `AccountSelector` (supports “All Accounts”).
- `TransactionTable` (shell).
- `TransactionRow`.
- `DateGroupHeader`.
- `CategoryPill`.
- `AccountBadge`.
- `CurrencyAmount`.
- `InlineTransactionFormRow`.
- `SummaryMetricCard`.
- `CategoryBreakdownBar`.

---

## Backlog (Nice-to-Have)

### Backend
- Tags model with scope:
- Global tags (system-defined, read-only for users).
- User tags (user-created, editable by owner).
- Transaction-Tags join.
- API: CRUD tags.
- API: Add/remove tags on transaction.
- API: Filter transactions by tags.
- Statement import API (CSV/OFX).
- Receipt upload API and link to transaction.
- Grouped transactions API.
- Pagination cursor API.

### Frontend
- Tag chips on transaction rows.
- Tag selector in transaction edit/create.
- Tag filter bar.
- Statement import flow.
- Receipt upload UI and attachment indicator.
- Grouped transactions expand/collapse.
- “Load more” pagination (if you move away from infinite scroll).
- Income by category module.

### Shared/Common Components
- `TagChip`.
- `TagSelector`.
- `TagFilterBar`.
- `UploadButton`.
- `PaginationFooter`.
- `GroupedTransactionRow`.

---

## Dependencies (High-level)
- Permissions middleware must exist before any API action is exposed.
- Accounts/Cards and Categories models must exist before Transactions.
- Transactions model must exist before summary metrics and category breakdown.
- Aggregated view depends on multi-account queries.
- Tags depend on transactions.
