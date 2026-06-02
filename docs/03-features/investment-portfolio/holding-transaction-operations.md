---
title: Holding Transaction Operations — Aportar, Comprar, Resgatar, Vender
type: feature-spec
category: investment-portfolio
status: in-progress
version: 1.0.0
tags: [investment, portfolio, transaction, holding, aporte, resgate, cash-flow, performance]
ai_optimized: true
search_keywords: [aporte, compra, resgate, venda, holding, transaction, cash-flow, performance, pending-action]
related:
  - 03-features/investment-portfolio/investment-portfolio-blueprint.md
  - 03-features/investment-portfolio/investment-performance.md
  - 03-features/investment-portfolio/cash-in-investments.md
  - 03-features/investment-portfolio/coupon-payments-full-requirements.md
  - 05-backend/patterns/service-pattern.md
  - 07-frontend/patterns/form-pattern.md
last_updated: 2026-04-18
---

# Holding Transaction Operations — Aportar, Comprar, Resgatar, Vender

> **One-line summary**: Define os requisitos e a arquitetura unificada para as operações de entrada (aporte/compra) e saída (resgate/venda) em todos os segmentos de investimento, garantindo comportamentos adjacentes comuns como fluxo de caixa e recálculo de performance.

---

## Overview

**Problem Context**: O portfólio de investimentos é segmentado em cinco classes — Renda Fixa, Fundos, Ações, FIIs e Cripto. Cada classe suporta duas operações primárias do usuário: entrar em uma posição (aporte/compra) e sair de uma posição (resgate/venda). Apesar da nomenclatura diferir por classe, todos os cenários compartilham comportamentos adjacentes idênticos: o movimento deve ser refletido no saldo da conta custodiante, a performance da carteira precisa ser recalculada, e pending actions relacionadas ao holding devem ser sincronizadas. Atualmente essa lógica está corretamente centralizada no backend mas dispersa entre três drawers de frontend (~100KB de código duplicado).

**Goals**:
1. Documentar os requisitos precisos de cada operação por classe de ativo.
2. Definir os comportamentos adjacentes comuns como contrato formal, não como efeito colateral implícito.
3. Propor uma arquitetura backend (side-effect handler chain) e frontend (base hook + form decomposition) que elimine duplicação e suporte extensão por classe sem modificar o núcleo.

**Target Users**: Investidores retail (B2C) gerenciando a própria carteira manualmente.

---

## Functional Requirements

### Classes de Ativo e Terminologia

| Classe | Grid Key | Operação de Entrada | Operação de Saída | Transaction Type (INFLOW) | Transaction Type (OUTFLOW) |
|---|---|---|---|---|---|
| Renda Fixa | `fixed-income` | Aportar | Resgatar | `PURCHASE` | `SALE` / `MATURITY` |
| Fundos | `funds` | Aportar | Resgatar | `PURCHASE` / `SUBSCRIPTION` | `SALE` / `MATURITY` |
| Ações | `stocks` | Comprar | Vender | `PURCHASE` | `SALE` |
| FIIs | `reits` | Comprar | Vender | `PURCHASE` | `SALE` |
| Cripto | `crypto` | Comprar | Vender | `PURCHASE` | `SALE` |

> **Nota**: A operação de MATURITY (vencimento) é iniciada pelo sistema via pending action, não diretamente pelo usuário neste fluxo.

---

### FR-1: Operação de Entrada (Aporte / Compra)

**Descrição**: O usuário registra a compra ou aplicação em um ativo, criando ou incrementando um holding.

**Priority**: Critical

**Acceptance Criteria comuns a todas as classes**:
- O usuário pode escolher a conta custodiante de destino (quando há múltiplas contas do tipo correto).
- Campos obrigatórios: ativo, data do trade, valor (grossValue OU quantidade × preço unitário).
- Campos opcionais: data de liquidação, IR, IOF, notas de corretagem (quando aplicável).
- Ao confirmar, cria ou incrementa um `Holding` e salva um `InvestmentTransaction` do tipo `PURCHASE`.
- **[Comportamento adjacente]** Um lançamento DEBIT é criado na conta custodiante (ledger), espelhando a saída de caixa.
- **[Comportamento adjacente]** A performance da carteira é recalculada a partir da data do trade.
- **[Comportamento adjacente]** Pending actions do holding são sincronizadas (maturity, coupon).
- Em edição: os mesmos campos estão disponíveis; o holding é recalculado via replay de todas as transações.

**Especificidades por classe**:

#### Renda Fixa (Aportar)
- Seleção de tipo de ativo: CDB, RDB, LCI, LCA, LC, LF, LIG, Debêntures, CRI, CRA, Tesouro Direto.
- Seleção de emissor (instituição financeira) obrigatória.
- Indexador obrigatório (CDI, DI, IPCA, IGP-M, SELIC, Pré-Fixado, Outros).
- Taxa indexador (%) ou taxa anual pré-fixada (%) conforme indexador selecionado.
- Data de emissão, data de vencimento (obrigatória), data de carência (opcional).
- Frequência de cupom (AT_MATURITY, MONTHLY, QUARTERLY, SEMI_ANNUAL, ANNUAL) + dia do mês do cupom quando aplicável.
- Isenção de IR: detectada automaticamente para LCI, LCA, CRI, CRA; editável manualmente.
- Quantidade pode ser entrada implícita (1 cota = grossValue) ou explícita.

#### Fundos (Aportar)
- Seleção de fundo via autocomplete CVM (por CNPJ ou nome) ou criação manual.
- Modo de precificação: `MARKET` (CVM) ou `MANUAL` (usuário provê o valor da cota na data de valorização).
- Em modo manual: campo de valor total + data de valorização (valuedAt).
- Quantidade em cotas + preço unitário da cota OU somente grossValue (o sistema divide por preço de cota).
- Transaction type: `PURCHASE` para aplicação inicial, `SUBSCRIPTION` para aportes subsequentes.

#### Ações (Comprar)
- Busca de ativo via autocomplete B3 (ticker, nome) ou criação manual.
- Campos de corretagem: taxas de corretagem (brokerage fee), liquidação (settlement fee), IRRF retido na fonte.
- Fator de preço (priceFactor): relevante após splits/inplits para normalizar preço histórico.
- Quantidade obrigatória + preço unitário obrigatório.
- Data de liquidação padrão D+2 (editável).

#### FIIs (Comprar)
- Igual a Ações; filtro de autocomplete B3 restrito a `REAL_ESTATE_FUND` e `AGRIBUSINESS_FUND`.
- Mesmos campos de corretagem.

#### Cripto (Comprar)
- Ativo criado manualmente (sem autocomplete B3/CVM); nome + ticker + rede opcional.
- A exchange é representada pela conta custodiante (`FinancialAccount` subtipo `CRYPTO`) — não há campo `exchange` separado (DEC-2).
- Sem campos de corretagem; sem settlementDate obrigatório.
- Quantidade obrigatória + preço unitário obrigatório.

---

### FR-2: Operação de Saída (Resgate / Venda)

**Descrição**: O usuário registra o resgate parcial ou total de uma posição existente.

**Priority**: Critical

**Acceptance Criteria comuns a todas as classes**:
- O usuário seleciona um holding ativo da conta custodiante.
- Campos obrigatórios: data do trade, quantidade (ou valor de resgate calculado), netValue.
- O sistema valida `quantidade_resgate ≤ quantidade_atual_do_holding`.
- Ao confirmar, diminui a quantidade do `Holding` e salva um `InvestmentTransaction` do tipo `SALE`.
- **[Comportamento adjacente]** Um lançamento CREDIT é criado na conta custodiante (ledger), refletindo entrada de caixa.
- **[Comportamento adjacente]** A performance da carteira é recalculada a partir da data do trade.
- **[Comportamento adjacente]** Pending actions do holding são sincronizadas.
- Se quantidade resultante = 0: o holding não é deletado; fecha-se a posição (quantidade = 0).
- Conta de destino do caixa: padrão = mesma conta custodiante; opcional = outra conta de investimento ou conta bancária.

**Especificidades por classe**:

#### Renda Fixa (Resgatar)
- Resgate parcial ou total.
- Campos: grossValue de resgate, netValue (pós-IR), IR retido.
- Não exige quantidade explícita — sistema assume resgate proporcional ao valor informado; quantidade derivada de `grossValue / averageCost`.
- Alternativa: quantidade explícita em cotas.

#### Fundos (Resgatar)
- O usuário sempre informa ou confirma o `netValue` do resgate (DEC-3).
- Para `pricingMode = MARKET`: sistema pode sugerir `quantidade × última cota CVM na data`; campo permanece editável.
- Valora na data de liquidação (D+N conforme regulamento do fundo).

#### Ações e FIIs (Vender)
- Quantidade explícita obrigatória.
- Preço de venda por cota obrigatório.
- Campos de corretagem (fees, IRRF).
- Data de liquidação D+2 padrão.

#### Cripto (Vender)
- Quantidade explícita obrigatória.
- Preço de venda obrigatório.
- Sem corretagem; sem settlementDate.

---

### FR-3: Comportamentos Adjacentes Comuns (Cross-Cutting Side Effects)

Estes comportamentos são **partes integrantes** de toda operação de entrada ou saída, independente da classe de ativo. Eles não são opcionais e devem ser tratados como parte do contrato transacional da operação.

#### FR-3.1: Sincronização do Ledger da Conta Custodiante (Cash Ledger)

- **PURCHASE / SUBSCRIPTION**: cria lançamento `DEBIT` na conta custodiante. Categoria: `Investimento aplicado > {subcategoria por AssetType}`. Data: `settlementDate ?? tradeDate`.
- **SALE / MATURITY**: cria lançamento `CREDIT` na conta custodiante (ou conta de destino, se informada). Categoria: `Investimentos > Resgate`. Data: `settlementDate ?? tradeDate`.
- **DIVIDEND / INTEREST_PAYMENT / AMORTIZATION / INTEREST_ON_EQUITY**: cria lançamento `CREDIT`. Categoria mapeada por tipo.
- Idempotente: se já existe um lançamento com `investment_transaction_id` = id, não cria duplicata.
- Em deleção de transação: o lançamento correspondente é soft-deleted e o peer de transferência (se houver) também.

#### FR-3.2: Recálculo de Performance da Carteira (Portfolio Flow Event)

- **PURCHASE / SUBSCRIPTION**: registra evento `DEPOSIT` em `investment_portfolio_flow_event`.
- **SALE / MATURITY**: registra evento `WITHDRAWAL`.
- **DIVIDEND / INTEREST_PAYMENT / AMORTIZATION / PRIZE**: registra evento `DIVIDEND`.
- **WITHHOLDING_TAX**: registra evento `INCOME_TAX`.
- Demais tipos: dispara `requestRecalculation` a partir de `tradeDate`.
- Em deleção: remove o evento de portfolio flow correspondente.
- Em atualização: remove o evento antigo e recria (via replay do holding).

#### FR-3.3: Sincronização de Pending Actions do Holding

**Maturity (INVESTMENT_MATURED)**:
- Upsert: se holding tem `maturityDate` e `quantity > 0` → cria/atualiza pending action com `visibleFrom = maturityDate + 1d`.
- Cancel: se `quantity == 0` ou `maturityDate` removida → cancela a pending action.

**Coupon (COUPON_PAYMENT_DUE)**:
- Upsert: se holding suporta cupom (`quantity > 0 AND couponFrequency != AT_MATURITY AND maturityDate != null`) → upsert pending action com `visibleFrom = proximaDataCupom - 7d`.
- Complete: quando `INTEREST_PAYMENT` é registrado → completa a pending action corrente.
- Cancel: se holding não suporta mais cupom → cancela.

---

### FR-4: Integração com Financial Account (Conta Custodiante)

- Cada holding pertence a exatamente uma `FinancialAccount` do tipo `INVESTMENT` ou `BENEFIT`.
- O saldo de caixa da conta custodiante é afetado por toda operação via FR-3.1.
- O usuário pode optar por redirecionar o caixa do resgate para outra conta (`cashDestinationAccountId`).
- A conta de origem é sempre a conta custodiante do holding.

---

## Business Rules

**BR-1: Custo Médio Ponderado (Weighted Average Cost)**
- PURCHASE/SUBSCRIPTION: recalcula `averageCost = (existingQty × existingCost + newQty × unitPrice) / totalQty`.
- SALE: `averageCost` não muda; apenas `quantity` decresce.
- STOCK_SPLIT / BONUS_SHARES: `quantity` aumenta; custo total preservado → `averageCost` dilui.
- REVERSE_SPLIT: `quantity` decresce; custo total preservado → `averageCost` concentra.

**BR-2: Quantidade Mínima**
- `quantity` resultante nunca pode ser negativa.
- SALE/MATURITY: sistema valida `quantity_vendida ≤ quantity_atual` antes de salvar.

**BR-3: Fonte das Transações**
- Apenas transações `source = MANUAL` podem ser editadas ou deletadas via UI.
- Transações `OPEN_FINANCE` ou `FILE_IMPORT` são read-only.

**BR-4: Holding Órfão**
- Se deleção de transação deixar o holding sem nenhuma transação → holding é soft-deleted automaticamente.
- Pending actions do holding são canceladas.

**BR-5: Data de Liquidação**
- `settlementDate` quando informada é usada para o lançamento no ledger (não a `tradeDate`).
- Padrões: Ações/FIIs = D+2; RF/Fundos = D+0 ou D+1 conforme o produto; Cripto = D+0.

---

## Architecture Proposal

> Esta seção propõe as refatorações necessárias para tratar os comportamentos comuns de forma genérica, mantendo extensibilidade por classe.

### Backend: Side-Effect Handler Chain

**Problema atual**: Os quatro comportamentos adjacentes (`syncInvestmentCashLedger`, `syncMaturityPendingAction`, `syncCouponPendingAction`, `syncPortfolioPerformance`) são métodos privados chamados em sequência hardcoded em `record()`, `update()` e `delete()`. Adicionar um novo comportamento requer modificar o serviço central. A reversão (`removeCustodianLedgerLegsLinkedToInvestmentTransactions`, `removePortfolioFlowSideEffects`) está espalhada em múltiplos métodos.

**Pattern adotado**: Chain of Responsibility para side effects de transações de investimento.

#### Componentes

```
InvestmentTransactionSideEffectHandler (interface)
├── CustodianCashLedgerHandler       ← sincroniza ledger
├── MaturityPendingActionHandler      ← gerencia INVESTMENT_MATURED
├── CouponPendingActionHandler        ← gerencia COUPON_PAYMENT_DUE
└── PortfolioFlowEventHandler         ← registra portfolio flow event
```

**Interface**:
```kotlin
interface InvestmentTransactionSideEffectHandler {
    /** Predicate — handler apenas executa para os tipos/assets onde é relevante. */
    fun appliesTo(context: InvestmentTransactionContext): Boolean = true

    fun onRecord(context: InvestmentTransactionContext)
    fun onUpdate(context: InvestmentTransactionContext, previous: InvestmentTransaction)
    fun onDelete(context: InvestmentTransactionContext)
}

data class InvestmentTransactionContext(
    val transaction: InvestmentTransaction,
    val holding: Holding,
    val asset: Asset,
    val command: RecordInvestmentTransactionCommand?,
    val accountId: UUID,
    val userId: UUID,
)
```

**Orquestrador**:
```kotlin
@Singleton
class InvestmentTransactionLifecycleOrchestrator(
    private val handlers: List<InvestmentTransactionSideEffectHandler>
) {
    fun onRecord(ctx: InvestmentTransactionContext) =
        handlers.filter { it.appliesTo(ctx) }.forEach { it.onRecord(ctx) }

    fun onUpdate(ctx: InvestmentTransactionContext, previous: InvestmentTransaction) =
        handlers.filter { it.appliesTo(ctx) }.forEach { it.onUpdate(ctx, previous) }

    fun onDelete(ctx: InvestmentTransactionContext) =
        handlers.filter { it.appliesTo(ctx) }.forEach { it.onDelete(ctx) }
}
```

#### Atomicidade e Boundaries Transacionais

Os handlers são chamados **sincronamente dentro do `@Transactional` boundary** aberto pelo `InvestmentTransactionService.record()`. O Micronaut/JPA amarra a conexão JDBC à thread corrente via `ThreadLocal` — qualquer write feito por um handler usa essa mesma conexão. Uma exceção em qualquer handler propaga até `record()` e dispara rollback de tudo, incluindo o que handlers anteriores já persistiram.

**Regra obrigatória**: handlers **nunca** devem ser anotados com `@Transactional(propagation = REQUIRES_NEW)`. Isso suspenderia a transação pai, e um rollback posterior não desfaria o que o handler persistiu — quebrando a atomicidade silenciosamente.

**Guard em tempo de execução**: os métodos `onRecord`, `onUpdate` e `onDelete` de cada implementação concreta devem ser anotados com `@Transactional(propagation = Propagation.MANDATORY)`. Isso faz o Micronaut lançar `IllegalTransactionStateException` imediatamente se um handler for chamado fora de um boundary transacional ativo — falha rápida em vez de comportamento incorreto silencioso.

```kotlin
// Exemplo em CustodianCashLedgerHandler (padrão para todos os handlers):
@Singleton
class CustodianCashLedgerHandler(...) : InvestmentTransactionSideEffectHandler {

    @Transactional(propagation = Propagation.MANDATORY)
    override fun onRecord(context: InvestmentTransactionContext) { ... }

    @Transactional(propagation = Propagation.MANDATORY)
    override fun onUpdate(context: InvestmentTransactionContext, previous: InvestmentTransaction) { ... }

    @Transactional(propagation = Propagation.MANDATORY)
    override fun onDelete(context: InvestmentTransactionContext) { ... }
}
```

**Resultado**: `InvestmentTransactionService.record()` reduz de 4 chamadas explícitas para `orchestrator.onRecord(context)`. Novos comportamentos (ex: notificação em tempo real, webhook) são adicionados como novos `@Singleton` que implementam a interface — zero modificação no serviço central.

**Handlers com comportamento específico por classe** (exemplo):
```kotlin
// Handler existente mas agora com appliesTo explícito
class CouponPendingActionHandler : InvestmentTransactionSideEffectHandler {
    override fun appliesTo(ctx: InvestmentTransactionContext) =
        ctx.asset.assetType in FIXED_INCOME_ASSET_TYPES  // só executa para RF
    ...
}
```

#### Código a Refatorar

- `InvestmentTransactionService.kt`: extrair os 4 métodos `syncXxx` para handlers dedicados; substituir chamadas por `orchestrator.on{Record,Update,Delete}(context)`.
- Criar: `InvestmentTransactionSideEffectHandler.kt`, `InvestmentTransactionContext.kt`, `InvestmentTransactionLifecycleOrchestrator.kt`.
- Handlers: `CustodianCashLedgerHandler.kt`, `MaturityPendingActionHandler.kt`, `CouponPendingActionHandler.kt`, `PortfolioFlowEventHandler.kt`.
- Todos no package `banking.service.investment.lifecycle`.

---

### Frontend: Shared Form Abstraction

**Problema atual**: `RecordFixedIncomeDrawer.tsx`, `RecordEquityHoldingDrawer.tsx` e `RecordFundHoldingDrawer.tsx` (~33KB cada) duplicam:
- `useMutation(RECORD_INVESTMENT_TRANSACTION)` + `useMutation(UPDATE_INVESTMENT_TRANSACTION)`
- Cache invalidation (mesmas 3 queries: `INVESTMENT_HOLDINGS`, `INVESTMENT_CASH_BALANCE`, `INVESTMENT_PORTFOLIO_SUMMARY`)
- `useToast()` + `toast.success()`
- `FormProvider` + `zodResolver(schema)`
- `Drawer.ActionButtons` com `isLoading` + `onCancel`
- `ErrorAlert` binding
- Campos base: `direction` toggle, `tradeDate`, `grossValue`, `netValue`, `incomeTax`, `iof`

**Pattern adotado**: Custom hook `useRecordHoldingForm` + componente `BaseTransactionFields`.

#### Hook Compartilhado

```typescript
// useRecordHoldingForm.ts
interface UseRecordHoldingFormConfig<TFields extends BaseTransactionFormData> {
  schema: ZodSchema<TFields>
  financialAccountId: string | null
  editTransaction?: InvestmentTransaction | null
  onBuildInput: (data: TFields) => RecordInvestmentTransactionInput
  onSuccess?: () => void
}

interface UseRecordHoldingFormReturn<TFields> {
  form: UseFormReturn<TFields>
  onSubmit: (e: React.FormEvent) => void
  isLoading: boolean
  mutationError: ApolloError | undefined
}
```

O hook encapsula:
1. `useMutation` com `refetchQueries` pré-configuradas.
2. `useForm<TFields>({ resolver: zodResolver(schema) })`.
3. `handleSubmit` que chama `onBuildInput(data)` → mutation.
4. `toast.success` no `onCompleted`.
5. Branching create vs. edit via `editTransaction`.

#### Componente de Campos Comuns

```typescript
// BaseTransactionFields.tsx
interface BaseTransactionFieldsProps {
  form: UseFormReturn<BaseTransactionFormData>
  direction: InvestmentTransactionDirection
  onDirectionChange: (d: InvestmentTransactionDirection) => void
  directionOptions: { inflow: string; outflow: string }
  minDate?: Date
}
```

Renderiza: direction toggle, tradeDate (DatePickerField), grossValue (MoneyInput), netValue, incomeTax, iof.

#### Estrutura Resultante dos Drawers

```
RecordFixedIncomeDrawer
  └── useRecordHoldingForm(fixedIncomeSchema, buildFixedIncomeInput)
  └── InvestmentRecordDrawer (account picker — já existe)
      └── FormProvider
          ├── BaseTransactionFields (campos comuns)
          └── FixedIncomeSpecificFields (indexer, rates, coupon, maturity)
          └── Drawer.ActionButtons

RecordEquityDrawer
  └── useRecordHoldingForm(equitySchema, buildEquityInput)
  └── InvestmentRecordDrawer
      └── FormProvider
          ├── BaseTransactionFields
          └── EquitySpecificFields (autocomplete B3, broker note, priceFactor)
          └── Drawer.ActionButtons

RecordFundDrawer
  └── useRecordHoldingForm(fundSchema, buildFundInput)
  └── InvestmentRecordDrawer
      └── FormProvider
          ├── BaseTransactionFields
          └── FundSpecificFields (autocomplete CVM, pricingMode, manualValuation)
          └── Drawer.ActionButtons
```

**Estimativa de redução**: cada drawer de ~33KB → ~15KB de campos específicos + ~5KB compartilhados no hook/componente. Total: ~100KB → ~55KB + ~10KB compartilhados = ~35% de redução sem perda de funcionalidade.

#### Código a Criar/Refatorar

- Criar: `hooks/useRecordHoldingForm.ts`
- Criar: `components/BaseTransactionFields.tsx`
- Refatorar: `RecordFixedIncomeDrawer.tsx` — extrair campos específicos para `FixedIncomeSpecificFields.tsx`
- Refatorar: `RecordEquityHoldingDrawer.tsx` — extrair campos específicos para `EquitySpecificFields.tsx`
- Refatorar: `RecordFundHoldingDrawer.tsx` — extrair campos específicos para `FundSpecificFields.tsx`

---

## Implementation Plan

### Phase 1 — Backend: Side-Effect Handler Chain

**Scope**: Refatorar `InvestmentTransactionService` sem alterar comportamento externo.

1. Criar `InvestmentTransactionContext` e `InvestmentTransactionSideEffectHandler`.
2. Criar `InvestmentTransactionLifecycleOrchestrator`.
3. Extrair `CustodianCashLedgerHandler` (de `syncInvestmentCashLedger` + `removeCustodianLedgerLegsLinkedToInvestmentTransactions`).
4. Extrair `MaturityPendingActionHandler` (de `syncMaturityPendingAction`).
5. Extrair `CouponPendingActionHandler` (de `syncCouponPendingAction`).
6. Extrair `PortfolioFlowEventHandler` (de `syncPortfolioPerformance` + `removePortfolioFlowSideEffects`).
7. Substituir chamadas no `InvestmentTransactionService`.
8. Testes unitários por handler (cada um isolado de seus peers).

**Critério de done**: comportamento externo idêntico; todos os testes existentes passam; novos testes de handler unitários adicionados.

### Phase 2 — Frontend: Shared Hook e Componentes

**Scope**: Extrair abstrações sem alterar UX ou comportamento dos drawers.

1. Criar `useRecordHoldingForm.ts` com campos comuns de mutation e form.
2. Criar `BaseTransactionFields.tsx`.
3. Refatorar `RecordFixedIncomeDrawer` para usar o hook e extrair `FixedIncomeSpecificFields`.
4. Refatorar `RecordEquityHoldingDrawer` para usar o hook e extrair `EquitySpecificFields`.
5. Refatorar `RecordFundHoldingDrawer` para usar o hook e extrair `FundSpecificFields`.

**Critério de done**: comportamento visual idêntico ao atual; tamanho de cada drawer < 400 linhas.

### Phase 3 — Extensão: Cripto como classe standalone

**Scope**: Separar drawer de Cripto do EquityDrawer (atualmente usa `mixed` variant), com campos específicos (rede, exchange, sem corretagem).

1. Criar `CryptoSpecificFields.tsx`.
2. Adaptar `investmentGridConfig.ts`: `RECORD_DRAWER_BY_CLASS.crypto = "crypto"`.
3. Criar `RecordCryptoDrawer.tsx` usando o hook compartilhado.

---

## Testing Strategy

**Backend (Phase 1)**:
- Testes unitários para cada `SideEffectHandler` com mocks dos repositórios.
- Teste de integração: `InvestmentTransactionService.record()` verifica que todos os handlers são chamados na ordem correta.
- Regressão: executar suite existente de `InvestmentTransactionServiceTest`.

**Frontend (Phase 2)**:
- Testes unitários para `useRecordHoldingForm` via `renderHook`.
- Testes de componente para `BaseTransactionFields`.
- Smoke tests E2E existentes não devem quebrar.

---

## Code References

**Backend**:
- `invistus/service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/investment/InvestmentTransactionService.kt` — serviço a refatorar
- `invistus/service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/investment/` — package alvo

**Frontend**:
- `invistus/web/src/app/personal-finance/investments/components/RecordFixedIncomeDrawer.tsx`
- `invistus/web/src/app/personal-finance/investments/components/RecordEquityHoldingDrawer.tsx`
- `invistus/web/src/app/personal-finance/investments/components/RecordFundHoldingDrawer.tsx`
- `invistus/web/src/app/personal-finance/investments/components/InvestmentRecordDrawer.tsx` — wrapper já existe, não alterar

---

## Decisions

**DEC-1: MATURITY via pending action — reutiliza drawer de Resgate**
O fluxo de vencimento iniciado pelo usuário via pending action (`INVESTMENT_MATURED`) reaproveita o drawer de Resgate da Renda Fixa (`RecordFixedIncomeDrawer`), pré-populado com os dados do holding. Não há drawer dedicado para MATURITY.

**DEC-2: Cripto exchange — reutiliza o mecanismo de `issuerName` / conta custodiante**
Não há campo `exchange` separado no V1. A exchange de cripto é representada pela conta custodiante (`FinancialAccount` de subtipo `CRYPTO`) — cada exchange cadastrada pelo usuário é uma conta custodiante distinta. O `issuerName` do asset pode complementar quando necessário, seguindo o mesmo padrão de emissores de RF. Nenhum novo modelo de dado é necessário.

**DEC-3: Resgate de Fundos — usuário sempre confirma o valor; sistema pode sugerir**
Independente do `pricingMode`, o usuário sempre informa ou confirma o `netValue` do resgate. Para `pricingMode = MARKET`, o sistema pode pré-calcular uma sugestão (`quantidade × última cota CVM na data`), mas o campo permanece editável. O valor confirmado pelo usuário é o que é salvo.

**DEC-4: Custo médio de Ações — base de custo deve ser `netValue` (pós-corretagem)**
O custo de aquisição para fins de apuração de ganho de capital deve incluir corretagem e emolumentos, conforme Instrução Normativa RFB 1.585/2015. Kinvo e Gorila adotam o mesmo critério. O `unitPrice` efetivo para o custo médio deve ser calculado como `netValue / quantity` quando `netValue` for informado.

> **Impacto no código atual**: `InvestmentTransactionService` usa `unitPrice` (= `grossValue / quantity`) no custo médio. Para ações com corretagem, `unitPrice` deveria ser `netValue / quantity`. Isso representa uma divergência de precisão para usuários que informam corretagem. Corrigir em backlog como `COST_BASIS_NET_VALUE_FIX`.

## Open Questions

- [ ] `COST_BASIS_NET_VALUE_FIX`: vale corrigir o custo médio para usar `netValue / qty` como unitPrice efetivo em ações/FIIs? Requer recalcular posições existentes via replay — avaliar impacto de migração.

---

## Changelog

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.1.0 | 2026-04-18 | salomax | Resolved open questions: MATURITY drawer reuse, crypto exchange model, fund resgate confirmation, cost basis net value |
| 1.0.0 | 2026-04-18 | salomax | Initial version — requisitos, side-effect handler pattern, frontend abstraction proposal |
