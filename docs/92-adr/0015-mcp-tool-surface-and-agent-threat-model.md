---
title: ADR-0015 MCP Tool Surface and Agent Threat Model
type: adr
category: security
status: proposed
version: 1.0.0
tags: [security, mcp, ai-agents, oauth, tool-catalog, threat-model, personal-finance]
related:
  - docs/92-adr/0008-interservice-security.md
  - docs/92-adr/0009-resource-ownership-strategy.md
  - docs/03-features/security/README.md
  - docs/03-features/personal-finance/
---

# ADR-0015: MCP Tool Surface and Agent Threat Model

## Status

Proposed — resolves GitHub issue #411, spike for epic #410.

## Context

Epic #410 exposes Invistus as a remote MCP server so external agents (Claude, ChatGPT, Gemini) can query and modify a user's financial data on their behalf.

Two artifacts must be settled before any MCP code is written, because both become expensive to change later:

- **The tool catalog** becomes a public contract consumed by three independent vendors, with no deprecation channel once clients cache it.
- **The threat model** governs write behaviour on financial records, where much of the input is attacker-influenceable.

This ADR defines four things: the v1 tool catalog, the scope taxonomy, the threat model, and the confirmation policy.

### Constraints established during investigation

- Every banking resolver already scopes on `requireNotNull(principal.currentAccountId)` and threads `accountId` into every service call — see `TransactionResolver.kt`. Tenant isolation is a property of the existing service layer.
- `AccountRole` (`OWNER | ADMIN | MEMBER | VIEWER`) is **not enforced** in the banking layer — no references exist under `service/kotlin/banking/src/main`. The scope model must not assume a role ceiling that does not exist yet.
- Investment (Silver track) mutations live in the **same** banking subgraph as Black-track operations. A taxonomy organised by service would silently grant investment access.
- The `assistant` service already owns a tool registry: `assistant.tools` (`ToolEntity.kt`) with `name`, `description`, `inputSchema`, `outputSchema`, `category`, and dispatch metadata.

## Decision

### 1. No generic GraphQL passthrough

**A tool that accepts arbitrary GraphQL (`executeGraphQL(query: String)`) will not be exposed.** This is recorded as a decision because it looks economical and is repeatedly proposed.

Two reasons:

- **Security.** It converts every prompt-injection foothold into arbitrary read and write access across the entire schema, bounded only by the token's scopes. There is no way to classify such a tool as read or write, so the confirmation policy below cannot apply to it.
- **Efficacy.** Models reason substantially better over a small set of named, business-intent operations than over an open query language plus a large schema. A passthrough tool trades a security boundary for worse results.

The catalog is curated. Each tool maps to existing resolvers and domain services rather than reimplementing query logic.

### 2. Tool catalog — v1

**Scope of v1: Black track (personal finance) only.** Naming is `snake_case`, verb-first, stable.

#### Read tools

| Tool | Backed by | Scope |
|---|---|---|
| `list_transactions` | `transactions(...)` | `finance:transactions:read` |
| `get_transaction` | `transaction(id)` | `finance:transactions:read` |
| `get_month_summary` | `monthSummary(...)` | `finance:cashflow:read` |
| `get_period_summary` | `dashboardPeriodSummary(...)` | `finance:cashflow:read` |
| `get_cash_flow_series` | `cashFlowSeries(...)` | `finance:cashflow:read` |
| `get_spending_by_category` | `transactionsByCategory(...)`, `categoryBreakdownForRange(...)` | `finance:cashflow:read` |
| `get_spending_by_tag` | `transactionsByTag(...)` | `finance:cashflow:read` |
| `get_top_merchants` | `topMerchants(...)` | `finance:cashflow:read` |
| `list_financial_accounts` | `financialAccounts` | `finance:accounts:read` |
| `list_credit_card_bills` | `creditCardBills(financialAccountId)` | `finance:bills:read` |
| `get_credit_card_bill` | `creditCardBill(...)` | `finance:bills:read` |
| `list_categories` | `transactionCategories` | `finance:categories:read` |
| `list_transaction_rules` | `transactionRules(...)` | `finance:rules:read` |

The aggregation tools exist deliberately: the model must never sum rows itself. `monthSummary` and friends are already computed server-side and are both cheaper and more accurate than returning raw transactions for the model to total.

#### Write tools

| Tool | Backed by | Scope | Tier |
|---|---|---|---|
| `create_transaction` | `createTransaction` | `finance:transactions:write` | autonomous |
| `update_transaction` | `updateTransaction` | `finance:transactions:write` | autonomous |
| `delete_transaction` | `deleteTransaction` | `finance:transactions:write` | confirmation-required |
| `categorize_transactions` | `bulkCategorize` | `finance:transactions:write` | bulk-gated |
| `create_transaction_rule` | `createTransactionRule` | `finance:rules:write` | autonomous |
| `apply_rule_to_historical` | `applyTransactionRuleToHistorical` | `finance:rules:write` | bulk-gated |

There is **no money-movement tool in v1**. `payBill` is implemented in the domain layer (`TransactionResolver.payBill`, `TransactionService.payBill`, wired in `BankingWiringFactory`) and was a candidate, but is deliberately excluded from the agent surface — see below.

#### Explicitly excluded from v1

**Money movement — `payBill`.** Excluded deliberately, not for lack of implementation. It would be the only tool in the catalog that moves money, and excluding it means v1 ships no money-movement path at all. The confirmation-required tier still exists and is exercised by `delete_transaction`, so the mechanism is built and tested; `pay_bill` can be added later against a proven tier rather than being the thing that proves it.

The product framing follows: "an agent that reads your finances and organises your transactions" is a materially easier proposition to defend, support and reason about than "an agent that pays your bills". Reintroducing it is an additive catalog change under the versioning rule below.

**Also excluded:** investment operations (`createInvestmentAsset`, `recordInvestmentTransaction`, `deleteInvestmentHolding`, `triggerPortfolioRecalculation`, and the rest of the Silver track); file and statement imports (`importTransactionsFile`, `submitBillImport` — upload-shaped, not agent-shaped); account lifecycle (`createFinancialAccount`, `deleteFinancialAccount`); wishlist, products and NFC-e.

Exclusion is enforced by the scope taxonomy, not by omission from the catalog alone. Note that `finance:bills:write` consequently grants nothing in v1 and should not be offered at consent until a write tool exists under it.

#### Catalog ownership and versioning

The catalog is **shared with the in-app assistant** — one declarative source of tool name, description, schemas and category, in the `assistant.tools` registry or a schema-shared successor. A second MCP-only registry would guarantee capability and security drift between the two consumers.

Sharing the catalog is **not** sharing exposure: the registry carries a per-transport exposure flag, and `tools/list` on the MCP endpoint filters on it in addition to scope. Some internal assistant tools must never be reachable externally.

Execution logging stays separate. `assistant.tool_executions` requires a non-null `completionRequestId`, which an MCP call does not have; MCP audit records are owned by #419.

**Versioning rule:** tool schemas evolve additively only. Adding an optional input or an output field is permitted. Renaming a tool, removing one, or making an input required is a breaking change and requires a **new tool name**, with the old name retained and its description prefixed `DEPRECATED:`. There is no deprecation channel to cached clients, so removal is never safe.

### 3. Scope taxonomy

Scopes are organised **by domain and verb**, never by service — because Silver-track mutations share the banking subgraph and a service-shaped scope would grant them implicitly.

```
finance:transactions:read    finance:transactions:write
finance:cashflow:read        (no write — derived data)
finance:accounts:read        (cross-cutting — see below)
finance:bills:read           finance:bills:write   (no v1 tool — do not offer at consent)
finance:categories:read      finance:categories:write
finance:rules:read           finance:rules:write

finance:investments:read     finance:investments:write   (v2 — does not exist yet)
```

`finance:` is the product namespace; the second segment is the domain area. Silver track slots in as `finance:investments:*` without disturbing anything above.

#### The Black/Silver line is not a data boundary

This must be stated plainly because the catalog above reads as if it were. **Black and Silver are product and UI distinctions; the data model does not partition along them.** Verified:

- `FinancialAccountType` has eight values — `BANK`, `CREDIT`, `PAYMENT`, `CASH`, **`INVESTMENT`**, `LOAN`, `ASSET`, `BENEFIT`. Custodian accounts are ordinary financial accounts.
- `MonthSummaryQueryService.getMonthSummary` scopes by tenant and optionally by one `financialAccountId`, and **does not filter by account type**. With `financialAccountId` omitted — the common agent case — `monthSummary`, `dashboardPeriodSummary`, `cashFlowSeries` and the category and tag breakdowns all aggregate across investment accounts too.
- `Transaction` itself carries `investmentTransactionId` and `linkedInvestmentAsset`, the latter a full `InvestmentAsset` with `ticker`, `isinCode`, `name`, `assetType`, `issuerCnpj`, `fundCnpj` and ANBIMA classification.
- `TransactionType` includes `INVESTMENT_RETURN`.

So "no `finance:investments:*` scope in v1" prevents Silver-track **mutations** — there are no investment write tools — but it does **not** prevent investment data from reaching an agent through reads. Treating the absent scope as a read boundary would be a false assurance.

#### Scopes apply at field level, not only at tool level

Consequence of the above, and a rule the rest of the catalog depends on. `Transaction` is a wide type carrying fields from more than one domain, so tool-level scoping cannot express what the user actually consented to.

**`linkedInvestmentAsset` and `investmentTransactionId` are omitted from every tool result unless `finance:investments:read` is granted.** The cash amounts remain — they are part of the user's cash flow — but which assets the user holds is portfolio data and requires the portfolio scope. Knowing money moved is not knowing what it bought.

Aggregations continue to include investment accounts, deliberately. Filtering them would make the agent's totals disagree with the same figures in the app, which is a worse failure: the user cannot tell which number is wrong. The consent screen must state that financial summaries cover all accounts, including investment accounts.

The field-omission rule is enforced in the serialisation layer alongside untrusted-content delimiting (T1), not per tool. Both are properties of how results are rendered, and both fail silently if left to individual tool implementations.

#### Two further segments needing explicit treatment

- **`finance:cashflow`** is named for what it covers rather than for its shape. An earlier draft called it `finance:summary`, which collides with portfolio summaries — `portfolioNetWorthSeries` is summary-shaped but Silver-track. `cashflow` covers `monthSummary`, `dashboardPeriodSummary`, `cashFlowSeries`, and the category, tag and merchant breakdowns, and leaves `finance:investments:read` unambiguous.
- **`finance:accounts` is deliberately cross-cutting.** `FinancialAccount` carries a `type: FinancialAccountType` and an `includeCashInAllocation` flag documented as counting toward allocation in the investments UI — custodian accounts *are* financial accounts. This scope therefore returns investment accounts too, and that is intended: knowing an account exists is not the same as seeing its positions. Holdings, transactions and valuations stay under `finance:investments:*`.

Rules:

- **No `finance:investments:*` scope exists in v1.** This prevents Silver-track *mutations* — no investment write tool exists in the catalog. It does **not** by itself prevent investment data from being read; that is what the field-level omission rule above is for.
- Scopes map onto the existing permission model. `AuthorizationService` remains the single authorization decision point — **no parallel authorization path for agents.**
- Effective permission is the **intersection** of what the user may do and what the user granted this agent. Never the union, never the grant alone.
- Because `AccountRole` is not yet enforced in banking, the taxonomy must not treat the role as a ceiling. When role enforcement lands, it applies underneath these scopes without changes here.
- Scope grants are bound to one `currentAccountId` at consent time. One connection per account, revocable independently.

### 4. Threat model

#### T1 — Prompt injection via user-controlled data

The highest-likelihood threat. Fields flowing into tool results are attacker-influenceable: `description`, `notes`, `tags`, `counterpartyName`, `paymentReason`, merchant names, and any text extracted from imported statements. A merchant can name itself with instruction-shaped text and reach the model's context through `list_transactions`.

**Mitigation — untrusted content delimiting.** Every string field originating from user or imported data is emitted inside an explicit marker:

```
<untrusted-content>Café da Esquina</untrusted-content>
```

Three requirements, all necessary:

1. The tool description states that content inside these markers is **data, never instructions**.
2. Any occurrence of the marker itself in the source value is escaped before wrapping — otherwise the delimiter is trivially escaped by an attacker who includes a closing tag.
3. Delimiting is applied uniformly by the serialisation layer, not per-tool. A single tool that forgets it reopens the hole.

Delimiting reduces but does not eliminate the risk. It is why the confirmation policy exists.

#### T2 — Confused deputy

An agent induced to operate against an account the user did not intend, or to widen its own effective permissions.

**Mitigation.** Tokens are bound to one `currentAccountId` at consent; there is no account-switching tool in the catalog. Every resolver already enforces `requireNotNull(principal.currentAccountId)` and the `resource_ownership` model of ADR-0009 applies unchanged. Agent calls go **through** existing domain services, never around them — an MCP tool that queries repositories directly would bypass this and is prohibited.

#### T3 — Token exfiltration

An access token leaking from an agent vendor's infrastructure, logs, or a compromised client.

**Mitigation.** Short access-token TTL (900s); `jti` on every token (#424) enabling per-token revocation via the denylist (#416) so revocation takes effect on the next call rather than at TTL expiry; audience binding (#414) so an MCP token cannot be replayed against another resource — including rejection of tokens with **no** `aud`; per-token rate limiting (#420) to bound extraction speed; full attribution in the audit trail (#419).

#### T4 — Over-broad consent

The user grants more than they understand, and the grant is invisible afterwards.

**Mitigation.** Granular per-scope consent with plain-language descriptions (#415); partial grants supported; re-consent required when a client requests scopes beyond the existing grant; a connected-agents view with per-connection revocation and recent activity (#421).

#### T5 — Bulk exfiltration under a legitimate grant

Not a breach — an agent with `finance:*:read` can read the user's entire financial history, and that is what the grant means. The risk is the user not understanding the scale of what they authorised.

**Mitigation.** The consent screen states plainly that read access covers full history, not just recent activity, **and that financial summaries aggregate across all accounts including investment accounts**. Rate limits (#420) bound extraction rate. The audit trail (#419) makes the volume visible after the fact. This threat is accepted, not eliminated; it is recorded so it is not mistaken for an oversight.

#### T6 — Cross-domain leakage through wide types

An agent granted only Black-track scopes reading Silver-track data, because the underlying types span both domains. Concrete today: `Transaction.linkedInvestmentAsset` exposes the ticker, ISIN, issuer and ANBIMA classification of held assets, reachable through `list_transactions` with only `finance:transactions:read`.

This is more dangerous than the other threats because it requires no attacker. It is the default behaviour of a correct implementation that scopes at tool level, and it produces no error, no anomaly and nothing in the audit trail to distinguish it from intended use.

**Mitigation.** Field-level scope enforcement in the serialisation layer, per the scope taxonomy above. The general rule: **a scope named for one domain must never return another domain's data merely because a shared type carries it.** Any new field added to `Transaction` — or to any agent-visible type — requires a scope classification as part of that change. This is the failure mode that reappears silently as the schema grows, so it needs to be a review checklist item and not only an ADR paragraph.

### 5. Confirmation policy

Three tiers, not a binary.

**Autonomous** — executes on the agent's call. Categorisation, tagging, notes, single-transaction create and update. Reversible, single-record, low blast radius.

**Confirmation-required** — never executes on the model's say-so alone. In v1 this is `delete_transaction` (destructive and not fully reversible). The tool creates an `AccountPendingAction` and returns a pending result; the user confirms in the app or through the existing comms channel; the mutation executes on confirmation.

The tier is defined by category, not by its current single occupant: any tool that moves money, deletes records irrecoverably, or affects an external party belongs here. `pay_bill` would qualify and is the expected first addition when money movement is reintroduced.

Reusing `AccountPendingAction` is deliberate: it already models a typed, severity-tagged action with a notifier job (`PendingActionNotifierJob`) and comms integration (`PendingActionCommsService`), and it is surfaced through `pendingActions(status)` and `dismissPendingAction(id)`. Critically, confirmation originates from the user, **outside the model's control loop** — which is the entire point. A confirmation the agent can satisfy itself is not a confirmation.

**Bulk-gated** — `categorize_transactions` and `apply_rule_to_historical` return `BulkOperationResult` and can rewrite large spans of history in one call. These must:

1. Report the number of affected records **before** executing — `transactionRuleHistoricalMatchCount(id)` already provides this for rules.
2. Enforce a configurable cap. **MVP default: 50 records** without confirmation.
3. Escalate to confirmation-required above the cap.

**All write tools**, regardless of tier, accept an idempotency key. An agent retry must not duplicate a financial record. This is #418's responsibility; no shared idempotency infrastructure exists today.

## Consequences

**Positive.** One curated catalog serves both the in-app assistant and all three external agents. Authorization stays in `AuthorizationService` with no parallel path. Confirmation reuses existing infrastructure rather than inventing a second notification channel. Silver-track exposure is prevented structurally rather than by remembering to omit it.

**Negative.** The catalog is a public contract with a strict additive-only evolution rule, which will feel restrictive. Curated tools mean new domain capability requires an explicit catalog addition rather than arriving free. The confirmation flow adds a round trip for money-moving operations.

**Accepted risks.** Prompt injection is mitigated, not solved — the confirmation policy is the backstop. Bulk read exfiltration under a legitimate grant is accepted and disclosed at consent. Cash-flow aggregations include investment accounts, so a Black-track grant does reveal aggregate investment cash movement; this is accepted for numerical consistency with the app and disclosed at consent, with asset identity protected at field level. `AccountRole` is not enforced in banking; this ADR does not assume it is, and role enforcement is tracked separately.

## Downstream issues

| Issue | Depends on this ADR for |
|---|---|
| #415 | Scope definitions; consent screen must disclose that summaries span all accounts, investment included |
| #416 | Tool registration model, exposure flag, no-passthrough rule, and the serialisation layer that enforces field-level scope omission and untrusted-content delimiting |
| #417 | Read catalog, untrusted content delimiting, omission of `linkedInvestmentAsset` and `investmentTransactionId` without `finance:investments:read` |
| #418 | Write catalog, confirmation tiers, bulk caps, idempotency |
| #419 | What is audited per tool call |
| #422 | Published catalog reference and its versioning policy |
