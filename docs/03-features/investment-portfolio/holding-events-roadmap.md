---
title: Holding Events — Inventário e Roadmap de Implementação
type: feature-spec
category: investment-portfolio
status: in-progress
version: 1.0.0
tags: [investment, portfolio, holding, events, corporate-events, come-cotas, amortization, roadmap]
ai_optimized: true
search_keywords: [come-cotas, amortização, aluguel, transferência custódia, dividendo, split, bonificação, subscrição, staking, roadmap]
related:
  - 03-features/investment-portfolio/investment-portfolio-blueprint.md
  - 03-features/investment-portfolio/holding-transaction-operations.md
  - 03-features/investment-portfolio/coupon-payments-full-requirements.md
last_updated: 2026-04-18
---

# Holding Events — Inventário e Roadmap de Implementação

> **One-line summary**: Inventário completo de todos os eventos e operações manuais que podem ocorrer em um holding, classificados por classe de ativo, com status de implementação e plano de desenvolvimento em etapas.

---

## Contexto

Este documento é resultado de uma análise completa do estado atual da carteira de investimentos (abril 2026). O objetivo é ter clareza sobre:

1. **O que está implementado** — operações funcionando em produção ou em desenvolvimento ativo
2. **O que está no enum mas sem UI** — tipo já existe no backend, falta o drawer/fluxo frontend
3. **O que falta completamente** — nem enum nem frontend
4. **A ordem de ataque** — etapas priorizadas por impacto e dependência

---

## Inventário por Classe de Ativo

### Renda Fixa

Tipos: CDB, RDB, LCI, LCA, LC, LF, LIG, Debêntures, CRI, CRA, Tesouro Direto, ETF Renda Fixa

| Operação | `InvestmentTransactionType` | Status | Drawer / Serviço |
|---|---|---|---|
| Aportar (nova posição ou incremento) | `PURCHASE` | ✅ Implementado | `RecordFixedIncomeDrawer` |
| Resgatar antecipado | `SALE` + `closeReason = REDEEMED_EARLY` | ✅ Implementado | Via `MaturityConfirmDrawer` |
| Vencimento | `MATURITY` + `closeReason = MATURED` | ✅ Implementado | `MaturityConfirmDrawer` + pending action |
| Pagamento de cupom | `INTEREST_PAYMENT` | ✅ Implementado | `CouponPaymentConfirmDrawer` + pending action |
| Amortização | `AMORTIZATION` | ⚠️ Enum existe, sem drawer | — |
| Prêmio (CRI/CRA) | `PRIZE` | ⚠️ Enum existe, sem drawer | — |
| Multa / mora | `FINE`, `LATE_PAYMENT_INTEREST` | ⚠️ Enum existe, sem drawer | — |
| Transferência de custódia | `CUSTODY_TRANSFER` | ⚠️ Enum existe, sem UI | — |

---

### Fundos de Investimento

Tipos: MUTUAL_FUND (categorias ANBIMA: Renda Fixa, Ações, Multimercado, Cambial)

| Operação | `InvestmentTransactionType` | Status | Drawer / Serviço |
|---|---|---|---|
| Aplicar | `PURCHASE` | ✅ Implementado | `RecordFundHoldingDrawer` |
| Resgatar | `SALE` | ✅ Implementado | `RecordFundHoldingDrawer` |
| Come-cotas (imposto semestral) | `WITHHOLDING_TAX` | ❌ Sem drawer | Enum existe |
| Transferência de cotas | `QUOTA_TRANSFER` | ⚠️ Enum existe, sem UI | — |

> **Come-cotas**: Evento semestral (maio e novembro) onde o IR é recolhido diretamente via redução de cotas. Reduz `quantity` sem alterar o valor unitário da cota. Não é uma saída — é um evento tributário.

---

### Ações, ETFs, BDRs

Tipos: STOCK, STOCK_RIGHTS, BDR, ETF, ETF_INTERNATIONAL

| Operação | `InvestmentTransactionType` | Status | Drawer / Serviço |
|---|---|---|---|
| Comprar | `PURCHASE` | ✅ Implementado | `RecordEquityHoldingDrawer` |
| Vender | `SALE` | ✅ Implementado | `RecordEquityHoldingDrawer` |
| Dividendos | `DIVIDEND` | ✅ Implementado | `RecordCorporateEventDrawer` |
| JCP (Juros sobre Capital Próprio) | `INTEREST_ON_EQUITY` | ✅ Implementado | `RecordCorporateEventDrawer` |
| Desdobramento (Split) | `STOCK_SPLIT` | ✅ Implementado | `RecordCorporateEventDrawer` |
| Grupamento (Reverse split) | `REVERSE_SPLIT` | ✅ Implementado | `RecordCorporateEventDrawer` |
| Bonificação em ações | `BONUS_SHARES` | ✅ Implementado | `RecordCorporateEventDrawer` |
| Subscrição | `SUBSCRIPTION` | ✅ Implementado | `RecordCorporateEventDrawer` |
| Aluguel de ações (BTC B3) | `STOCK_LENDING` | ⚠️ Enum existe, sem drawer | — |
| Transferência de custódia | `CUSTODY_TRANSFER` | ⚠️ Enum existe, sem UI | — |
| Transferência de titularidade | `OWNERSHIP_TRANSFER` | ⚠️ Enum existe, sem UI | — |

---

### FIIs e Fundos de Agronegócio

Tipos: REAL_ESTATE_FUND, AGRIBUSINESS_FUND

| Operação | `InvestmentTransactionType` | Status | Drawer / Serviço |
|---|---|---|---|
| Comprar | `PURCHASE` | ✅ Implementado | `RecordEquityHoldingDrawer` |
| Vender | `SALE` | ✅ Implementado | `RecordEquityHoldingDrawer` |
| Rendimentos / Proventos | `DIVIDEND` | ✅ Implementado | `RecordCorporateEventDrawer` |
| Amortização de cota | `AMORTIZATION` | ⚠️ Enum existe, sem drawer | Comum em FIIs de papel |
| Subscrição (novas cotas) | `SUBSCRIPTION` | ✅ Implementado | `RecordCorporateEventDrawer` |
| Desdobramento / Grupamento | `STOCK_SPLIT` / `REVERSE_SPLIT` | ✅ Implementado | `RecordCorporateEventDrawer` |

---

### Cripto

Tipo: CRYPTO

| Operação | `InvestmentTransactionType` | Status | Drawer / Serviço |
|---|---|---|---|
| Comprar | `PURCHASE` | ✅ Implementado | `RecordEquityHoldingDrawer` (variant mixed) |
| Vender | `SALE` | ✅ Implementado | `RecordEquityHoldingDrawer` |
| Staking / Rendimento passivo | _(não mapeado)_ | ❌ Sem enum, sem drawer | Rendimento recorrente em exchanges/DeFi |

> **Nota técnica**: Cripto ainda usa `RecordEquityHoldingDrawer` com `variant="mixed"`. A separação em `RecordCryptoDrawer` standalone está prevista mas não é prioridade neste ciclo.

---

## Resumo de Status

| Status | Qtd | Operações |
|---|---|---|
| ✅ Implementado | 18 | Aporte RF, Resgate RF, Vencimento, Cupom, Aplicar/Resgatar Fundo, Compra/Venda Ações/FII/Cripto, Dividendo, JCP, Split, Reverse Split, Bonificação, Subscrição |
| ⚠️ Enum existe, sem UI | 8 | Amortização, Prêmio, Multa/Mora, STOCK_LENDING, CUSTODY_TRANSFER, OWNERSHIP_TRANSFER, QUOTA_TRANSFER |
| ❌ Não mapeado | 2 | Come-cotas (Fundos), Staking/Yield (Cripto) |

---

## Roadmap de Desenvolvimento

As etapas abaixo estão ordenadas por impacto (quantos usuários afeta) e complexidade de implementação.

---

### Etapa 1 — Come-cotas (Fundos de Investimento)

**Prioridade**: Alta  
**Impacto**: Todos os usuários com fundos de investimento  
**Complexidade**: Média

**O que é**: Tributação semestral (maio/novembro) onde o IR é recolhido pela redução de cotas. A cota unitária não muda — apenas a quantidade de cotas do holding diminui.

**O que falta**:
- [ ] Backend: `ConfirmWithholdingTaxService` (análogo a `ConfirmCouponPaymentService`)
- [ ] Backend: mutation `confirmWithholdingTax(input: ConfirmWithholdingTaxInput!)`
- [ ] Backend: GraphQL input/output types
- [ ] Frontend: `WithholdingTaxDrawer` — campos: `tradeDate`, `irValue`, `quotasRedeemed`, `destinationAccountId`
- [ ] Frontend: Pending action type `WITHHOLDING_TAX_DUE` (opcional para V1 — pode ser evento manual sem pending action)
- [ ] Efeito colateral: reduz `quantity` do holding (não o `unitPrice`)

**Campos do formulário**:
- `tradeDate` — data do evento (padrão: hoje)
- `irValue` — valor do IR retido (R$)
- `quotasDeducted` — quantidade de cotas resgatadas para pagar o IR (calculado automaticamente se `unitPrice` disponível)
- `destinationAccountId` — conta que registra o débito fiscal (opcional)

---

### Etapa 2 — Amortização

**Prioridade**: Média-Alta  
**Impacto**: Usuários com CRI, CRA, Debêntures, FIIs de papel  
**Complexidade**: Baixa (similar ao cupom, mas reduz o principal)

**O que é**: Pagamento parcial do principal antes do vencimento. Reduz o `netValue` (saldo devedor atualizado) do holding. Comum em CRI/CRA com amortização periódica e em FIIs de papel.

**O que falta**:
- [ ] Frontend: `RecordAmortizationDrawer` — campos: `tradeDate`, `grossValue`, `netValue`, `incomeTax`, `destinationAccountId`
- [ ] Backend: o tipo `AMORTIZATION` já existe; verificar se `InvestmentTransactionService` já trata o efeito de redução de principal ou se precisa de handler dedicado
- [ ] Definir comportamento: amortização reduz `netValue` do holding (não a quantidade nem o preço unitário)

**Campos do formulário**:
- `tradeDate` — data do pagamento
- `grossValue` — valor bruto amortizado
- `netValue` — valor líquido recebido
- `incomeTax` — IR retido
- `destinationAccountId` — conta de crédito (opcional)

---

### Etapa 3 — Aluguel de Ações (BTC B3)

**Prioridade**: Média  
**Impacto**: Investidores ativos com carteira de ações  
**Complexidade**: Baixa

**O que é**: Receita periódica pelo aluguel da posição na B3 (BTC — Banco de Títulos). O holding permanece inalterado em quantidade; o evento só registra a receita.

**O que falta**:
- [ ] Frontend: entrada no `RecordCorporateEventDrawer` para tipo `STOCK_LENDING`, ou drawer separado
- [ ] Campos: `tradeDate`, `grossValue`, `netValue`, `incomeTax`, `destinationAccountId`
- [ ] Backend: verificar se `STOCK_LENDING` já é tratado como `DIVIDEND`-like (não altera posição) no `InvestmentTransactionService`

---

### Etapa 4 — Transferência de Custódia

**Prioridade**: Média  
**Impacto**: Usuários migrando de corretora  
**Complexidade**: Média-Alta (envolve dois lados: origem e destino)

**O que é**: Movimentação de um ativo entre corretoras sem compra/venda. A posição (quantidade e preço médio) é mantida integralmente.

**O que falta**:
- [ ] Definir modelo: transferência gera dois registros (OUTFLOW na conta origem + INFLOW na conta destino) ou um único evento de custódia?
- [ ] Backend: lógica de transferência entre `investmentAccounts` do mesmo usuário
- [ ] Frontend: wizard de 2 passos — (1) selecionar holding de origem + conta destino, (2) confirmar

**Decisão pendente**: Transferência intra-usuário (mesma pessoa, corretoras diferentes) é o escopo V1. Transferência inter-usuário (titularidade) fica para V2.

---

### Etapa 5 — Staking / Yield Cripto

**Prioridade**: Baixa (V2)  
**Impacto**: Usuários com cripto em staking/DeFi  
**Complexidade**: Baixa (similar a dividendo)

**O que é**: Rendimento passivo recebido por manter cripto em staking ou em protocolos DeFi.

**O que falta**:
- [ ] Definir `InvestmentTransactionType` — reusar `DIVIDEND` ou criar `STAKING_REWARD`
- [ ] Frontend: entrada no drawer de cripto para registrar rendimento
- [ ] Efeito: credita valor na conta custodiante; não altera posição principal

---

### Etapa 6 — Prêmio, Multa e Mora (Renda Fixa de Crédito)

**Prioridade**: Baixa  
**Impacto**: Pequeno (edge cases de Debêntures/CRI/CRA)  
**Complexidade**: Baixa

- **Prêmio** (`PRIZE`): Pagamento adicional ao rendimento contratado, comum em CRI/CRA estruturados
- **Multa** (`FINE`) / **Mora** (`LATE_PAYMENT_INTEREST`): Penalidades por atraso do emissor

**O que falta**:
- [ ] Frontend: entradas no `RecordCorporateEventDrawer` ou drawer separado de "eventos especiais RF"
- [ ] Campos comuns: `tradeDate`, `grossValue`, `netValue`, `incomeTax`

---

## Artefatos de Referência

| Artefato | Localização |
|---|---|
| Enums de tipos de transação | `banking/src/.../domain/InvestmentTransaction.kt` |
| Serviço de transações | `banking/src/.../service/investment/InvestmentTransactionService.kt` |
| Serviço de cupom (referência de padrão) | `banking/src/.../service/investment/ConfirmCouponPaymentService.kt` |
| Schema GraphQL | `contracts/graphql/subgraphs/banking/schema.graphqls` |
| Drawers existentes | `web/src/app/personal-finance/investments/components/` |
| Spec de operações primárias | `docs/03-features/investment-portfolio/holding-transaction-operations.md` |
| Spec de cupom completo | `docs/03-features/investment-portfolio/coupon-payments-full-requirements.md` |

---

## Próximo Passo

Atacar **Etapa 1 — Come-cotas**, que tem maior impacto e segue o mesmo padrão já estabelecido pelo `ConfirmCouponPaymentService`. O drawer de cupom serve como blueprint direto para o drawer de come-cotas.
