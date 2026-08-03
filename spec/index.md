---
id: spec.index
status: canonical
kind: agent-loading-index
source_of_truth: code
last_verified: 2026-08-02
schema: spec-metadata-v1
---

# Agent Loading Index

Use this file first when an agent needs feature or impact context.

## Loading Strategy

1. Load `spec/README.md` for global rules.
2. Load the relevant domain file from `spec/domains/`.
3. Load only the impacted feature specs from `spec/features/<domain>/`.
4. Load contracts only when the task touches metadata, confidence, evidence, permissions, or GraphQL/API behavior.
5. Load architecture docs only when the task changes durable rules, cross-cutting patterns, or system boundaries.
6. Do not load `removed historical docs` unless investigating historical intent or a suspected stale gap.

## Domains

- `security`: authentication, authorization, permissions, sessions, identity, tokens, OAuth2 AS, service principals, connected agents. **Pilot reverse-spec complete** — see `spec/domains/security.md` + `spec/features/security/`.
- `transactions`: expenses, income, categorization, imports, credit card bills, financial movements.
- `investment-portfolio`: portfolios, positions, allocations, broker imports, assets owned by the user.
- `market-data`: public indicators and external data such as BACEN, CVM, prices, indexes, rates.
- `financial-planning`: simulations, accounts payable, wishlist, planning utilities.

## Cross-Cutting Contracts

- `spec/contracts/frontmatter.md`: required metadata fields.
- `spec/contracts/confidence-model.md`: how to classify evidence quality.
- `spec/contracts/evidence-model.md`: how to cite code and test evidence.
- `spec/contracts/agent-loading-contract.md`: token discipline for future agents.

## Durable Architecture

- `spec/architecture/ai-agent-spec-model.md`: Graph Engineering model for specs.
- `spec/architecture/reverse-spec-from-code.md`: process for deriving specs from current implementation.
- `spec/architecture/backend-authority.md`: backend authority and frontend UX permission hints.

## Templates

- `spec/templates/feature-spec-template.md`
- `spec/templates/domain-spec-template.md`
- `spec/templates/reverse-spec-agent-prompt.md`
