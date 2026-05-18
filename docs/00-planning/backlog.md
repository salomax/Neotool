# Backlog Master

> Última atualização: 2026-04-09
> Referência: `docs/03-features/`

---

## Como ler este arquivo

- **Trilha:** ⚫ Black (transactions + NFC-e/purchase) · 🥈 Silver (investimentos)
- **Prioridade:** P0 (crítico/bloqueante) → P1 (próximo sprint) → P2 (planejado) → P3 (futuro)
- **Status:** `todo` | `in-progress` | `done` | `blocked` | `deferred`
- **Agent:** Claude / Codex / Cursor / TRAE
- **Esforço:** XS (< 1h) · S (1-3h) · M (3-8h) · L (dia+) · XL (2+ dias)

---

## ⚫ Trilha Black — Movimentação Financeira & Preços/Consumo

### P0 — Foco atual

#### [BK-1] Phase 3 — Transaction Rules Refactoring (Backend)
- **Status:** `in-progress` (iniciado 2026-04-02)
- **Agent:** Codex
- **Esforço:** XL
- **Spec:** `docs/03-features/personal-finance/phase-3-transaction-rules-refactoring.md`
- **Fases:**
  - **Fase 1** — `applied_rule_ids`: migração DB + entity + domain + mapper + service + import + GraphQL (Tasks 1.1–1.8)
  - **Fase 2** — Unificar aplicação (manual + import), proteção de campos do usuário (Tasks 2.1–2.3) — *depende Fase 1*
  - **Fase 3** — Filtro `isSystem` no GraphQL, separação UI (Tasks 3.1–3.4) — *paralela à Fase 1*
  - **Fase 4** — Campo `position` + documentação do algoritmo de matching (Tasks 4.1–4.3) — *paralela à Fase 1*
- **Ordem sugerida:** Fase 1 → (Fase 3 + Fase 4 em paralelo) → Fase 2
- **Dependências:** nenhuma

#### [BK-2] Phase 4 v3 — Billing month filter + displayDate (Backend)
- **Status:** `todo`
- **Agent:** Codex
- **Esforço:** L
- **Spec:** `docs/03-features/personal-finance/backlogs/phase-4-v3-backlog.md` (B4, B5, B6)
- **Dependências:** nenhuma

#### [BK-3] Phase 4 v3 — PDF Parser fixes (Backend)
- **Status:** `todo`
- **Agent:** Codex
- **Esforço:** M
- **Spec:** `docs/03-features/personal-finance/backlogs/phase-4-v3-backlog.md` (B1, B2, B3)
- **Dependências:** nenhuma

#### [BK-4] Phase 4 v3 — PDF Upload com suporte a senha (Backend)
- **Status:** `todo`
- **Agent:** Codex
- **Esforço:** S
- **Spec:** `docs/03-features/personal-finance/backlogs/phase-4-v3-backlog.md` (B7)
- **Dependências:** nenhuma

#### [BK-5] Phase 4 v3 — PDF upload UI com detecção de senha (Frontend)
- **Status:** `todo`
- **Agent:** TRAE
- **Esforço:** S
- **Spec:** `docs/03-features/personal-finance/backlogs/phase-4-v3-backlog.md` (F1)
- **Dependências:** BK-4

### P1

#### [BK-12] BILL_PAYMENT transaction type (Backend + Frontend)
- **Status:** `todo`
- **Agent:** Codex (backend) + TRAE (frontend)
- **Esforço:** S
- **Spec:** `docs/03-features/personal-finance/backlogs/bill-payment-transaction-type.md`
- **Contexto:** Evitar duplicação de despesas quando o usuário importa o extrato da conta corrente junto com a fatura do cartão. O pagamento da fatura (DEBIT no extrato) deve ser excluído dos totais de receita/despesa, pois os itens individuais já estão na fatura.
- **Dependências:** nenhuma

#### [BK-6] Phase 4 v3 — Consolidated view (Frontend)
- **Status:** `todo`
- **Agent:** TRAE
- **Esforço:** M
- **Spec:** `docs/03-features/personal-finance/backlogs/phase-4-v3-backlog.md` (F2, F3)
- **Dependências:** BK-2 (B4, B5 no backend)

#### [BK-7] Phase 4 v3 — Credit card view (Frontend)
- **Status:** `todo`
- **Agent:** TRAE
- **Esforço:** M
- **Spec:** `docs/03-features/personal-finance/backlogs/phase-4-v3-backlog.md` (F4, F5)
- **Dependências:** BK-2 (B4, B5)

#### [BK-8] Phase 5 — Future transactions reconciliation
- **Status:** `todo`
- **Agent:** Claude (revisão spec) → Codex
- **Esforço:** XL
- **Spec:** `docs/03-features/personal-finance/backlogs/phase-5-future-transactions-reconciliation.md`
- **Nota:** Revisar spec antes de atribuir ao Codex

#### [BK-9] Phase 6 — Transaction categories
- **Status:** `todo`
- **Agent:** Claude (revisão spec) → Codex
- **Esforço:** XL
- **Spec:** `docs/03-features/personal-finance/backlogs/phase-6-transaction-categories.md`

### P2

#### [BK-10] Foreign currency accounts — use case and phased support
- **Status:** `todo`
- **Agent:** Claude (revisão spec) → Codex
- **Esforço:** M
- **Spec:** `docs/03-features/personal-finance/requirements/01-financial-accounts.md` + `docs/03-features/personal-finance/requirements/02-transactions.md`
- **Objetivo:** definir o caso de uso para contas em moeda estrangeira antes de expor `currencyCode` no drawer de contas
- **Escopo sugerido:**
  - Criar fluxo para contas em moeda diferente de `BRL` (ex.: conta internacional, cartão internacional, saldo em USD/EUR)
  - Definir comportamento de criação manual sem conversão automática obrigatória
  - Definir como exibir saldo e transações em moeda da conta e, quando existir, valor convertido em BRL
  - Especificar impactos em monthly summary, importadores e filtros
  - Decidir rollout por fases: persistência já existente → UI de conta → UI de transação/import → reporting

#### [BK-11] Financial account deactivation / soft delete
- **Status:** `todo`
- **Agent:** Claude (revisão spec) → Codex
- **Esforço:** M
- **Spec:** `docs/03-features/personal-finance/requirements/01-financial-accounts.md`
- **Objetivo:** definir e implementar a remoção lógica ou desativação de contas financeiras sem perder integridade histórica
- **Escopo sugerido:**
  - Decidir comportamento de produto: `soft-delete` definitivo no app, status `disabled`, ou ambos
  - Definir impacto em transações históricas, bills, monthly summary, seletores e imports
  - Especificar regras para impedir uso futuro da conta sem quebrar o histórico existente
  - Expor operação segura no backend/API e fluxo correspondente na UI
  - Definir estratégia de visibilidade: ocultar por padrão, listar inativas opcionalmente, ou permitir restauração

### Deferred (data indeterminada)

#### [BK-D1] Bill View — visualização de fatura (Frontend)
- **Status:** `deferred`
- **Motivo:** Baixa prioridade — postergar até decisão estratégica
- **Spec:** `docs/03-features/personal-finance/backlogs/phase-4-v3-backlog.md` (bill view)

---

## 🥈 Trilha Silver — Investimentos

### P0 — Foco atual

#### [SV-1] CVM Investment Funds Job (Golang worker)
- **Status:** `todo`
- **Agent:** Codex
- **Esforço:** L
- **Spec:** `docs/03-features/investment-portfolio/tasks/data-sources/03-cvm-investment-funds.md`
- **Padrão:** seguir `bacen_ifdata_job` + `indicators_job`
- **Dependências:** migração V1_15 (shared `investment_asset` + `asset_daily_price`)

#### [SV-2] Create Investment Account (Frontend)
- **Status:** `todo`
- **Agent:** TRAE
- **Esforço:** M
- **Spec:** `docs/03-features/investment-portfolio/tasks/create-investment-account.md`
- **Nota:** Backend já implementado; criar `CreateInvestmentAccountDrawer` dedicado (não reutilizar o de banking)
- **Dependências:** nenhuma

#### [SV-3] Record First Holding — Fixed Income (Frontend + Backend)
- **Status:** `todo`
- **Agent:** Codex (backend: `createInvestmentAsset` mutation) + TRAE (frontend: drawer)
- **Esforço:** M-L
- **Spec:** `docs/03-features/investment-portfolio/tasks/record-first-holding.md`
- **Dependências:** SV-2 (conta de investimento precisa existir)

### P1

#### [SV-4] B3 Cotahist — Equities (Golang worker)
- **Status:** `todo`
- **Agent:** Codex
- **Esforço:** L
- **Spec:** `docs/03-features/investment-portfolio/tasks/data-sources/02-b3-cotahist-equities.md`
- **Dependências:** SV-1 (mesmas tabelas compartilhadas)

#### [SV-5] Tesouro Nacional prices (Golang worker)
- **Status:** `todo`
- **Agent:** Codex
- **Esforço:** M
- **Spec:** `docs/03-features/investment-portfolio/tasks/data-sources/04-tesouro-nacional-prices.md`

#### [SV-6] Add Issuer Category
- **Status:** `todo`
- **Agent:** Cursor
- **Esforço:** S
- **Spec:** `docs/03-features/investment-portfolio/tasks/add-issuer-category.md`

### P2

#### [SV-7] Crypto prices — CoinGecko (Golang worker)
- **Status:** `todo`
- **Agent:** Codex
- **Esforço:** M
- **Spec:** `docs/03-features/investment-portfolio/tasks/data-sources/05-coingecko-crypto-prices.md`

---

## P3 — Infra / Tooling

#### [IN-1] ~~TRAE context setup~~ — CONCLUÍDO 2026-04-02
- `.trae/rules/project_rules.md` criado com stack, patterns, guardrails, domain context

#### [IN-2] Automação do daily planning
- **Status:** `todo`
- **Agent:** Claude
- **Nota:** Script ou hook para gerar `daily/YYYY-MM-DD.md` automaticamente

---

## Concluído recentemente

- [x] Monthly Balance Snapshots (phase-2) — backend completo
- [x] Credit Card Bills / Fatura — domain + infra + GraphQL
- [x] Phase 4 — PDF bill import (v1/v2 base)
- [x] Installment display refactor (phase-4-v3 spec + design)
- [x] Sistema de planning (`docs/00-planning/`) + TRAE context
