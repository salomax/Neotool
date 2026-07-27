---
title: Invistus — Catálogo de Funcionalidades (Shipped)
type: feature-catalog
category: product
status: current
version: 1.0.0
tags: [catalog, features, product, marketing, landing, inventory, shipped]
ai_optimized: true
search_keywords:
  [
    feature catalog,
    catálogo de funcionalidades,
    shipped features,
    landpage,
    marketing,
    personal finance,
    investments,
    MCP,
    Bacen,
  ]
related:
  - 03-features/personal-finance/backlogs/personal-finance-overview.md
  - 03-features/investment-portfolio/investment-portfolio-blueprint.md
  - 03-features/investment-portfolio/holding-events-roadmap.md
  - 03-features/security/README.md
  - 03-features/financial_data
  - 07-frontend/mobile-migration-roadmap.md
  - MANIFEST.md
last_updated: 2026-07-27
---

# Invistus — Catálogo de Funcionalidades (Shipped)

> **One-line summary**: Inventário do que está implementado no código (web, mobile, serviços Kotlin), com maturidade e adequação a docs/landpage/marketing — distinto de blueprints e roadmaps aspiracionais.

## Contexto

Este documento é a **fonte de verdade do produto shipped** para:

1. Atualizar documentação e o `MANIFEST.md`
2. Priorizar copy da landpage e campanhas
3. Separar o que está em produção do que ainda é blueprint (`billing`, Open Finance, advisory B2B)

**Método (jul/2026):** varredura de rotas em `web/src/app` e `mobile/app`, GraphQL/migrations/jobs em `service/kotlin/*`, flags Unleash, e contraste com `docs/03-features/*` e copy em `web/src/app/i18n`.

**Não inventa features.** Itens só em docs, sem código de produto, estão em [Não shipped](#não-shipped--só-em-docs-ou-ausentes).

### Legenda

| Campo | Valores |
|-------|---------|
| **Maturidade** | `production-ready` · `partial` · `experimental` · `stub` |
| **Superfície** | `web` · `mobile` · `both` · `api` · `backend` |
| **Audiência** | `B2C` · `público` · `agente` · `admin` · `plataforma` |
| **Marketing** | `promover agora` · `já na landing` · `mencionar` · `não promover` |

### Resumo (jul/2026)

| Métrica | Valor |
|---------|------:|
| Features catalogadas | 34 |
| Production-ready | ~29 |
| Prontas para promover (fora da landing) | ~12 |
| Já na landing | 4 (landing + 3 novidades) |
| Parciais / stubs | ~5 |

### Gap de marketing

A landing promove só **3 novidades de mercado** (Bacen, indicadores, comparador). O core B2C — dashboard, lançamentos, importações, carteira, MCP — já está **production-ready** e quase não aparece no copy público.

### Regra editorial para copy da landing

Texto novo escrito diretamente em `web/src/app/i18n` para viabilizar uma implementação da landing
é **rascunho de implementação**, não copy final aprovada. Considerar esse texto definitivo somente
quando o sign-off de produto estiver registrado na issue ou no PR correspondente.

---

## Matriz completa

| Feature | Área | Superfície | Maturidade | Audiência | Marketing | Capacidades | Flag |
|---------|------|------------|------------|-----------|-----------|-------------|------|
| Landing page | Marketing | web | production-ready | público | já na landing | Hero, vídeo, cards, SEO PT/EN, JSON-LD | — |
| Instituições Financeiras (Bacen) | Dados de mercado | web | production-ready | público | já na landing | Busca, descoberta, balanços, Basileia, ratings, séries | — |
| Indicadores macroeconômicos | Dados de mercado | web | production-ready | público | já na landing | IPCA, Selic, PIB, IGP, desemprego, balança comercial | — |
| Comparador de renda fixa | Investimentos (público) | web | production-ready | público | já na landing | CDI/pré/IPCA+, IR/IOF, tabela líquida, gráfico | — |
| Fundos CVM (API) | Dados de mercado | api | production-ready | plataforma | mencionar | Search, detalhe, NAV, export | — |
| Ativos listados B3 (API) | Dados de mercado | api | production-ready | plataforma | mencionar | Tickers, preços diários, eventos corporativos | — |
| Dashboard de finanças pessoais | Finanças pessoais | web | production-ready | B2C | promover agora | Widgets: KPIs, cash flow, patrimônio, categorias, alocação, treemap, recorrentes | `personal-finance-dashboard-enabled` |
| Contas financeiras | Finanças pessoais | web | production-ready | B2C | promover agora | Banco, crédito, pagamento, caixa, investimento, empréstimo, benefício | — |
| Lançamentos / movimentos | Finanças pessoais | web | production-ready | B2C | promover agora | Crédito/débito/transferência; filtros; tags; merchants; bulk categorize | — |
| Regras de categorização | Finanças pessoais | web | production-ready | B2C | promover agora | Regras user/system, preview, aplicar histórico, type override | — |
| Faturas de cartão | Finanças pessoais | web | production-ready | B2C | promover agora | Listar/pagar; OPEN/CLOSED/PAID/OVERDUE | — |
| Importação de extratos | Finanças pessoais | web | production-ready | B2C | promover agora | OFX, CSV (Nubank/Itaú/Bradesco/Santander), Excel, PDF; SSE | — |
| Importação de fatura (PDF/CSV + AI) | Finanças pessoais | web | production-ready | B2C | promover agora | Nubank PDF, CSV XP, review de parcelas, Kafka→assistant | — |
| Pagamentos recorrentes | Finanças pessoais | web | production-ready | B2C | promover agora | CRUD, pause/resume, confirmar, lembretes | — |
| Lista de desejos / compras | Finanças pessoais | web | production-ready | B2C | mencionar | Prioridade, reorder, mark purchased + tx | `purchase-wishlist-enabled` |
| NFC-e e preços de produtos | Finanças pessoais | backend | partial | B2C | não promover | Import NFC-e, histórico de preço, gasto por NCM | — |
| Preços & Consumo (UI) | Finanças pessoais | web | stub | B2C | não promover | Rota sem UI de produto | `pricing-enabled` |
| Ações pendentes | Finanças pessoais | web | production-ready | B2C | mencionar | Vencimento, cupom, recorrente, saldo negativo, agent write confirm | — |
| Carteira de investimentos | Investimentos (pessoal) | web | production-ready | B2C | promover agora | Ações, FIIs, ETFs, RF, fundos, crypto, previdência; cash ledger | — |
| Valuation & performance (TWR) | Investimentos (pessoal) | web | production-ready | B2C | promover agora | Summary, TWR, SSE, valuation RF/fundos, come-cotas | — |
| Eventos de holding | Investimentos (pessoal) | web | production-ready | B2C | mencionar | Cupom, vencimento, IR, amortização, empréstimo, custódia | — |
| Importação B3 (posições) | Investimentos (pessoal) | web | production-ready | B2C | mencionar | Excel B3, review, conflitos | `b3-import-feature` |
| Autenticação | Conta & segurança | both | production-ready | B2C | mencionar | Email/senha, Google (web), refresh, verify, reset | — |
| Contas multi-tenant & convites | Conta & segurança | web | production-ready | B2C | mencionar | Invite/accept, roles, leave/remove, avatar | — |
| Admin RBAC | Conta & segurança | web | production-ready | admin | não promover | Users, groups, roles, permissions | — |
| i18n PT/EN + tema | Plataforma UX | both | production-ready | B2C | mencionar | pt-BR / en-US; Light/Dark/System | — |
| Assistente de IA (chat) | IA & agentes | web | partial | B2C | não promover | Chat header, streaming, Gemini/OpenAI | — |
| MCP Finance — leitura | IA & agentes | api | production-ready | agente | promover agora | 13 tools: txs, summaries, cash flow, bills, accounts, categories, rules | — |
| MCP Finance — escrita | IA & agentes | api | production-ready | agente | promover agora | create/update/delete tx, rules; change log + revert; confirmação | — |
| OAuth2 Authorization Server | IA & agentes | api | production-ready | agente | mencionar | Authorize, consent, DCR, JWKS, scopes `finance:*`, rate limit | — |
| Upload de arquivos (assets) | Plataforma | backend | production-ready | plataforma | não promover | Presigned S3/R2, CDN, MIME validation | — |
| E-mail transacional | Plataforma | backend | production-ready | plataforma | não promover | Welcome, verify, reset, invitation | — |
| App mobile (Expo) | Mobile | mobile | partial | B2C | não promover | Landing, login/signup, settings; dashboard/profile stubs | — |

---

## Por área de produto

### Marketing / dados públicos

| Feature | Maturidade | Marketing |
|---------|------------|-----------|
| Landing page | production-ready | já na landing |
| Instituições Bacen | production-ready | já na landing |
| Indicadores macro | production-ready | já na landing |
| Comparador de renda fixa | production-ready | já na landing |
| Fundos CVM (API) | production-ready | mencionar |
| Ativos B3 (API) | production-ready | mencionar |

**Evidência web:** `/`, `/financial-data/**`, `/investments/fixed-income-comparison`  
**Evidência backend:** `service/kotlin/financialdata`

### Finanças pessoais (B2C)

| Feature | Maturidade | Marketing | Flag |
|---------|------------|-----------|------|
| Dashboard | production-ready | promover agora | `personal-finance-dashboard-enabled` |
| Contas | production-ready | promover agora | — |
| Lançamentos | production-ready | promover agora | — |
| Regras | production-ready | promover agora | — |
| Faturas | production-ready | promover agora | — |
| Import extratos | production-ready | promover agora | — |
| Import fatura + AI | production-ready | promover agora | — |
| Recorrentes | production-ready | promover agora | — |
| Wishlist | production-ready | mencionar | `purchase-wishlist-enabled` |
| Ações pendentes | production-ready | mencionar | — |
| NFC-e / preços | partial | não promover | — |
| Preços & Consumo UI | stub | não promover | `pricing-enabled` |

**Evidência web:** `/personal-finance/**`  
**Evidência backend:** `service/kotlin/banking`

### Investimentos pessoais (carteira)

| Feature | Maturidade | Marketing | Flag |
|---------|------------|-----------|------|
| Carteira multi-classe | production-ready | promover agora | — |
| Valuation / TWR / come-cotas | production-ready | promover agora | — |
| Eventos de holding | production-ready | mencionar | — |
| Import B3 | production-ready | mencionar | `b3-import-feature` |

**Evidência web:** `/personal-finance/investments`  
**Detalhe de eventos:** [holding-events-roadmap.md](./investment-portfolio/holding-events-roadmap.md)

### Conta, segurança e admin

| Feature | Maturidade | Marketing |
|---------|------------|-----------|
| Auth (web + mobile básico) | production-ready | mencionar |
| Multi-tenant & convites | production-ready | mencionar |
| Admin RBAC | production-ready | não promover |
| i18n + tema | production-ready | mencionar |

**Evidência:** `web/src/app/(authentication)`, `/profile`, `/settings`, `service/kotlin/security`

### IA & agentes

| Feature | Maturidade | Marketing |
|---------|------------|-----------|
| MCP Finance read | production-ready | promover agora |
| MCP Finance write | production-ready | promover agora |
| OAuth2 AS (MCP) | production-ready | mencionar |
| Chat assistente (header) | partial | não promover |

**Evidência:** `service/kotlin/mcp`, `service/kotlin/assistant`, OAuth2 em `security`  
**ADR:** [ADR-0015 MCP Tool Surface](../92-adr/0015-mcp-tool-surface-and-agent-threat-model.md)

### Plataforma / mobile

| Feature | Maturidade | Marketing |
|---------|------------|-----------|
| Assets (upload) | production-ready | não promover |
| Comms (e-mail) | production-ready | não promover |
| App mobile Expo | partial | não promover |

**Mobile:** login/signup e settings funcionam; dashboard/profile e PF/investimentos **não** têm paridade web. Ver [mobile-migration-roadmap.md](../07-frontend/mobile-migration-roadmap.md).

---

## Prioridade para landpage & campanhas

### P0 — Core B2C (ainda fora da landing)

1. Dashboard de finanças pessoais  
2. Lançamentos + regras + faturas  
3. Importação de extratos e faturas  
4. Carteira + performance (TWR)  
5. Pagamentos recorrentes  

### P1 — Diferencial agentes / MCP

1. MCP finance read + write  
2. OAuth2 AS com scopes `finance:*`  
3. Auditoria, idempotência e revert  
4. Confirmação de writes sensíveis  

### Manter na landing

- Instituições Bacen  
- Indicadores macro  
- Comparador de renda fixa  

Sugestão de estrutura: seção **Produto** (P0) + seção **Mercado** (itens já existentes) + seção **Agentes** (P1), se o funil incluir developers/power users.

---

## Pitch sugerido (alinhado ao código)

**Pitch atual (landing):** “Clareza para sua vida financeira” + 3 tools de mercado público.

**Pitch alinhado ao shipped:**

> Invistus une finanças pessoais e carteira de investimentos num só lugar — com importações bancárias, regras inteligentes, faturas e performance — e ainda abre seus dados a agentes via MCP, com consentimento OAuth2. Dados de mercado (Bacen, macros, renda fixa) complementam as decisões.

**Superfícies:** web production-ready · mobile ainda parcial · i18n pt/en.

---

## Não shipped — só em docs ou ausentes

| Tema | Situação | Onde aparece |
|------|----------|--------------|
| Billing / subscriptions | Blueprint; sem módulo de produto shipped | `docs/03-features/billing/` |
| Open Finance aggregator (Pluggy/Belvo) | Specs; sem connector service | vários docs PF / b2c |
| Advisory B2B / CRM | Ambición no blueprint de portfolio | `investment-portfolio-blueprint.md` |
| Serviço `notifications` | Pasta vazia em `service/kotlin/notifications` | — |
| MCP resources/prompts | Protocolo só tools | `service/kotlin/mcp` |
| Paridade mobile PF/investimentos | Roadmap; não implementado | `07-frontend/mobile-migration-roadmap.md` |

**Regra editorial:** não usar esses temas em landpage/campanha até haver superfície real.

---

## Mapa de serviços (backend)

| Módulo | Papel | Maturidade geral |
|--------|-------|------------------|
| `banking` | Finanças pessoais + carteira | production-ready |
| `security` | Auth, RBAC, OAuth2 AS, tenancy | production-ready |
| `financialdata` | Bacen, indicadores, fundos, B3 | production-ready (read) |
| `mcp` | JSON-RPC + finance tools | production-ready |
| `assistant` | LLM, extração de docs, tool registry | production-ready (worker); chat UI partial |
| `assets` | Upload / CDN | production-ready |
| `comms` | E-mail | production-ready |
| `notifications` | — | ausente |

---

## Como manter este catálogo

1. Ao shippar uma feature user-facing, atualizar a linha na [matriz](#matriz-completa) e a data `last_updated`.  
2. Mudanças só de blueprint/roadmap **não** entram aqui até haver código + superfície.  
3. Detalhes por domínio continuam nos docs especializados (`personal-finance/`, `investment-portfolio/`, etc.). Este arquivo é o **índice transversal shipped**.  
4. Canvas operacional (filtros): gerado na sessão de inventário; a fonte versionada é este markdown.

---

## Próximos passos sugeridos

1. Atualizar cards da landpage com P0; manter Bacen/macros/comparador como “Mercado”.  
2. Separar funis de campanha: controle financeiro · investimentos retail · agentes MCP.  
3. Evitar copy de app mobile / Open Finance / billing até haver superfície real.  
4. Revisitar NFC-e / Preços & Consumo quando a UI sair de stub.
