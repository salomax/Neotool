# Registro Manual de Cash em Conta Investimento

Permitir que o usuário registre movimentações de dinheiro diretamente em uma conta de investimento, visualize o saldo de cash como um grupo distinto na tabela de investimentos, e explore o histórico de movimentações paginado.

Complementa o registro automático já implementado (vencimentos, resgates, dividendos) com uma entrada manual para aportes, saques e outros eventos não cobertos automaticamente.

---

## Contexto

A infraestrutura de cash já está implementada (ver [cash-in-investments.md](cash-in-investments.md)):
- `banking.transactions` com `investment_transaction_id` é o ledger de cash
- `getInvestmentCashBalance` retorna o saldo atual
- Cash automático já é criado em SALE, MATURITY, DIVIDEND, INTEREST_PAYMENT, AMORTIZATION
- `RecordCashDrawer` e `createInvestmentCashTransaction` já implementados (V1 concluído)

O que resta é:
1. Exibir cash como `AssetGroupCard` dedicado na tabela (inline preview + drawer de histórico)
2. Transfer pair com conta corrente (V2)

---

## Decisões de Design

| # | Questão | Decisão |
|---|---------|---------|
| 1 | Como exibir cash na tabela? | `AssetGroupCard` dedicado, primeiro da lista, sem holdings, accordion com prévia de movimentações |
| 2 | Quantas movimentações inline? | 5 — prévia suficiente sem poluir a tela |
| 3 | Histório completo? | Drawer `CashMovementsDrawer` com cursor pagination ("Carregar mais") |
| 4 | Registro de cash no drawer de histórico? | Sim — botão "Registrar" no `CashMovementsDrawer` navega para step de registro (drawer com steps, não stacked) |
| 5 | Saldo negativo | Não bloquear. Exibir badge visual na conta quando saldo < 0. Comportamento alinhado com benchmark (Monarch, Organizze) |
| 6 | Transfer pair | UI explícita no momento do registro (não preferência de conta). Campo opcional de conta de origem/destino no `RecordCashDrawer`. Se preenchido, cria par atômico. |
| 7 | Benchmark de referência | Monarch Money e Empower para exibição de cash; Organizze e Mobills para UX de transfer pair |
| 8 | Allocation % do cash | Preferência da `FinancialAccount` — `includeCashInAllocation: Boolean`, default `true`. Quando ativo, cash compõe o denominador e aparece como fatia no breakdown de alocação. |
| 9 | Saldo negativo na conta peer (V2) | Não bloquear. Criar `AccountPendingAction` tipo `BANK_ACCOUNT_NEGATIVE_BALANCE` — usuário resolve via pending actions (dispensar ou corrigir). |
| 10 | Una ou duas mutations para transfer pair (V2)? | Estender `createInvestmentCashTransaction` com campo `peerFinancialAccountId` opcional — sem mutation nova. |
| 11 | Conta pré-selecionada no seletor peer (V2) | A `financialAccountId` ativa no contexto da página — sempre há uma válida. |

---

## Status por escopo

| Escopo | Status |
|--------|--------|
| V1 — registro manual (`RecordCashDrawer` + mutation backend) | ✅ Concluído |
| V1.5 — `AssetGroupCard` de cash + prévia inline + `CashMovementsDrawer` | 🔲 A implementar |
| V2 — transfer pair com conta corrente | 🔲 Planejado |

---

## Requisitos — V1.5

### RF-10 — Card de Cash na tabela de investimentos

O saldo de cash deve ser exibido como um `AssetGroupCard` dedicado, o **primeiro da lista**, acima de todos os grupos de ativos.

- **Título:** "Caixa disponível"
- **Ícone:** `AccountBalanceWalletIcon` (ou equivalente)
- **Valor exibido no header:** saldo formatado (`investmentCashBalance`)
- **Allocation %:** percentual do cash sobre o total do portfólio (cash / (soma holdings + cash))
- **Gain %:** não exibido (cash não tem rendimento neste contexto)
- Quando saldo = 0 e não há movimentações: card ainda exibido, com valor "R$ 0,00"
- Quando saldo < 0: valor exibido em vermelho; badge de aviso no header do card

### RF-11 — Prévia de movimentações inline (accordion)

Ao expandir o `AssetGroupCard` de cash, exibir as **5 movimentações mais recentes** da conta de investimento selecionada.

Colunas da tabela inline:
| Coluna | Conteúdo |
|--------|----------|
| Data | `tradeDate` formatada |
| Descrição | `description` da transaction |
| Tipo | chip/label: Aporte, Saque, Dividendo, Resgate, Rendimento, Ajuste |
| Valor | `amount` com prefixo `+` (CREDIT) ou `−` (DEBIT), colorido |

- Query: `investmentCashTransactions(financialAccountId, first: 5)` — sem paginação, apenas os 5 mais recentes
- Se não há movimentações: exibir estado vazio com CTA "Registrar primeiro aporte"
- Rodapé da tabela inline: botão **"Ver todas as movimentações →"** — abre `CashMovementsDrawer`

### RF-12 — `CashMovementsDrawer` (histórico completo paginado)

Drawer lateral com histórico completo de movimentações de cash da conta de investimento.

**Header do drawer:**
- Título: "Movimentações de Caixa"
- Subtítulo: nome da conta de investimento selecionada
- Botão **"+ Registrar"** no canto superior direito — abre `RecordCashDrawer` por cima (stacked)

**Corpo:**
- Mesmas colunas da prévia inline (Data, Descrição, Tipo, Valor)
- Cursor pagination: carrega 20 por vez
- Rodapé: botão **"Carregar mais"** visível enquanto `pageInfo.hasNextPage = true`
- Quando não há mais itens: label "Todas as movimentações carregadas"
- Estado vazio: "Nenhuma movimentação registrada"

**Navegação por steps no drawer:**
- O `CashMovementsDrawer` tem dois steps: `LIST` e `RECORD`
- Step `LIST`: histórico paginado + botão "+ Registrar" → avança para step `RECORD`
- Step `RECORD`: formulário do `RecordCashDrawer` embutido (sem ser um drawer separado)
- Ao salvar no step `RECORD`: volta para step `LIST` + refetch das movimentações + refetch do `INVESTMENT_CASH_BALANCE`
- Botão voltar no step `RECORD` retorna para `LIST` sem salvar
- A abertura direta via dropdown "Registrar" continua abrindo direto no step `RECORD` (sem histórico)

### RF-13 — Badge de saldo negativo

Quando `investmentCashBalance < 0`:
- Valor no header do `AssetGroupCard` exibido em `color="error"`
- Ícone de aviso (⚠) ao lado do valor
- Tooltip: "O saldo de caixa está negativo. Verifique se há movimentações não registradas."

Não bloquear nenhum lançamento. Apenas sinalizar.

---

## Backlog V1.5

### Backend

**[BE-04] Preferência `includeCashInAllocation` na `FinancialAccount`**

- Migration: adicionar coluna `include_cash_in_allocation BOOLEAN NOT NULL DEFAULT TRUE` em `banking.financial_accounts`
- `FinancialAccount.kt`: campo `includeCashInAllocation: Boolean = true`
- GraphQL: expor no tipo `FinancialAccount` e aceitar no input de criação/edição de conta
- Lógica no frontend: quando `false`, não calcular nem exibir `allocationPercent` no card de cash

**[BE-05] Query `investmentCashTransactions` com cursor pagination**

Atualizar em:
- `schema.graphqls` (banking service)
- `contracts/graphql/subgraphs/banking/schema.graphqls`
- supergraph
- Migration: novo arquivo para DBs que já rodaram sem a coluna

```graphql
type InvestmentCashTransactionConnection {
  edges: [InvestmentCashTransactionEdge!]!
  pageInfo: PageInfo!
}

type InvestmentCashTransactionEdge {
  node: Transaction!
  cursor: String!
}

# Query adicionada em Query type:
investmentCashTransactions(
  financialAccountId: ID
  first: Int
  after: String
): InvestmentCashTransactionConnection!
```

Regras:
- Filtra `banking.transactions` por `financial_account_id` (se fornecido) ou todas as contas INVESTMENT/BENEFIT do usuário
- Filtra por `type IN ('TRANSFER', 'INVESTMENT_RETURN', 'BALANCE_ADJUSTMENT')`
- Ordena por `date DESC, created_at DESC`
- Cursor baseado em `(date, id)` para estabilidade de paginação
- Default `first: 20`; máximo `first: 50`
- Permissão: `BANKING_TRANSACTIONS_READ`

Atualizar em:
- `InvestmentQueryService.kt` — método `getInvestmentCashTransactions`
- `InvestmentResolver.kt` — query resolver

---

### Frontend

**[UI-06] `AssetGroupCard` de cash em `InvestmentTable`**

- Arquivo: `InvestmentTable.tsx`
- Remover o bloco de texto simples atual (`investments.table.cashInAccount`)
- Adicionar card de cash **antes** do `groups.map(...)`, renderizado separadamente
- Usar `AssetGroupCard` com:
  - `title={t('investments.cash.card.title')}`
  - `leadingIcon={<AccountBalanceWalletIcon />}`
  - `summary={cashFormatted}`
  - `appliedValue={cashFormatted}` (não há "valor aplicado" distinto)
  - `gainPercent={null}`
  - `allocationPercent={cashAllocationPercent}` — calculado como `cashBalance / (totalHoldingsGross + cashBalance)`, **somente** quando `financialAccount.includeCashInAllocation = true`
  - `expanded={cashCardExpanded}` + `onToggle`
  - `summaryAdornment`: ícone de aviso quando saldo negativo
- `children`: `<CashMovementsInlineTable />` (ver abaixo)

**[UI-07] `CashMovementsInlineTable.tsx`**

Componente interno usado como `children` do `AssetGroupCard` de cash.

- Query: `INVESTMENT_CASH_TRANSACTIONS` com `first: 5`
- Renderiza tabela com colunas: Data, Descrição, Tipo (chip), Valor
- Estado de loading: skeleton rows
- Estado vazio: mensagem + botão "Registrar primeiro aporte" → abre `RecordCashDrawer`
- Rodapé: botão "Ver todas as movimentações →" → chama `onOpenDrawer()`

Props:
```ts
interface CashMovementsInlineTableProps {
  financialAccountId?: string
  onOpenDrawer: () => void
  onOpenRecord: () => void
}
```

**[UI-08] `CashMovementsDrawer.tsx`**

Drawer lateral com dois steps: histórico e registro.

Props:
```ts
interface CashMovementsDrawerProps {
  open: boolean
  onClose: () => void
  financialAccountId?: string
  financialAccounts: FinancialAccount[]
  initialStep?: 'LIST' | 'RECORD'  // default: 'LIST'
}
```

Step `LIST`:
- Header: título "Movimentações de Caixa" + subtítulo (nome da conta) + botão "+ Registrar"
- Body: lista de movimentações com cursor pagination
  - Query: `INVESTMENT_CASH_TRANSACTIONS(financialAccountId, first: 20)`
  - Botão "Carregar mais": `fetchMore` com `after: pageInfo.endCursor` — acumula resultados
  - Quando `!pageInfo.hasNextPage`: label "Todas as movimentações carregadas"
- Botão "+ Registrar" → `setStep('RECORD')`

Step `RECORD`:
- Header: botão "← Voltar" + título "Registrar movimentação"
- Body: formulário do `RecordCashDrawer` (reutilizar form + validação, sem o wrapper de drawer)
- Ao salvar: voltar para step `LIST` + refetch movimentações + refetch `INVESTMENT_CASH_BALANCE`
- Botão "Voltar": `setStep('LIST')` sem salvar

Quando aberto via dropdown "Registrar" da página: `initialStep='RECORD'`

**[UI-09] Integração no `InvestmentTable.tsx`**

- Estado: `isCashMovementsDrawerOpen`
- Passar `onOpenDrawer` para `CashMovementsInlineTable`
- Renderizar `CashMovementsDrawer` no final do componente

**[UI-10] Badge de saldo negativo**

- Em `AssetGroupCard` de cash: quando `cashBalance < 0`, passar `summaryAdornment={<WarningAmberIcon color="error" />}`
- Valor no header: `color="error"` quando negativo
- Tooltip no ícone de aviso

**[UI-11] Codegen**

- Rodar codegen após schema atualizado para gerar tipos e hooks de `INVESTMENT_CASH_TRANSACTIONS`

**[UI-12] Internacionalização**

Novas chaves em `pt.json` / `en.json`:
```
investments.cash.card.title          — "Caixa disponível"
investments.cash.card.emptyState     — "Nenhuma movimentação registrada"
investments.cash.card.firstRecord    — "Registrar primeiro aporte"
investments.cash.card.viewAll        — "Ver todas as movimentações"
investments.cash.drawer.movements.title     — "Movimentações de Caixa"
investments.cash.drawer.movements.loadMore  — "Carregar mais"
investments.cash.drawer.movements.allLoaded — "Todas as movimentações carregadas"
investments.cash.movement.type.deposit      — "Aporte"
investments.cash.movement.type.withdrawal   — "Saque"
investments.cash.movement.type.dividend     — "Dividendo"
investments.cash.movement.type.redemption   — "Resgate"
investments.cash.movement.type.income       — "Rendimento"
investments.cash.movement.type.adjustment   — "Ajuste"
investments.cash.negativeBalance.tooltip    — "O saldo de caixa está negativo..."
```

---

## Backlog V2

### Transfer pair com conta corrente

**Contexto:** benchmark (Organizze, Mobills, Monarch) — ao registrar aporte/saque, o usuário seleciona opcionalmente a conta de origem/destino. Se preenchida, o sistema cria automaticamente o par DEBIT/CREDIT linkado via `transferPeerTransactionId`. Elimina o duplo registro. Campo opcional: sem preenchimento, comportamento V1.5 (só o lado da corretora).

**[V2-UI-01] Campo "Conta de origem/destino" no `RecordCashDrawer`**
- Exibido apenas para DEPOSIT e WITHDRAWAL (não para ADJUSTMENT)
- Lista contas bancárias do usuário (CONTA_CORRENTE, CONTA_POUPANCA, CONTA_DIGITAL, CONTA_SALARIO)
- Pré-selecionada: a `financialAccountId` ativa no contexto da página (sempre há uma válida)
- Opcional: se limpar a seleção, registra apenas o lado investimento
- Label: "Conta de origem" (DEPOSIT) / "Conta de destino" (WITHDRAWAL)

**[V2-UI-02] Feedback após salvar com par**
- Toast com resumo: "Debitado R$ X de [Nubank] e creditado em [XP Investimentos]"
- Refetch de ambas as contas envolvidas

**[V2-BE-01] Estender `createInvestmentCashTransaction` com `peerFinancialAccountId`**

Sem mutation nova — adicionar campo opcional ao input existente:

```graphql
input CreateInvestmentCashTransactionInput {
  financialAccountId: ID!
  type: InvestmentCashTransactionType!
  amount: Float!
  date: Date!
  description: String
  peerFinancialAccountId: ID   # novo — opcional; quando presente, cria par DEBIT/CREDIT
}
```

Regras de negócio quando `peerFinancialAccountId` preenchido:
- Validar que a conta peer pertence ao mesmo `accountId`
- Validar que a conta peer é do tipo BANK (subtypes: CONTA_CORRENTE, CONTA_POUPANCA, CONTA_DIGITAL, CONTA_SALARIO)
- Criar par atomicamente (`@Transactional`): DEBIT na conta peer + CREDIT na conta de investimento (Aporte) ou vice-versa (Saque)
- Ligar os dois via `transferPeerTransactionId`
- Não permitido para ADJUSTMENT (apenas DEPOSIT e WITHDRAWAL)

**[V2-BE-02] `AccountPendingAction` para saldo negativo na conta peer**

Quando o DEBIT na conta de origem resultar em saldo negativo, criar um `account_pending_actions` do tipo `BANK_ACCOUNT_NEGATIVE_BALANCE`:
- `entityType = FINANCIAL_ACCOUNT`, `entityId = peerFinancialAccountId`
- `severity = WARNING`
- `relatedTransactionId` = ID da transaction DEBIT criada
- `visibleFrom = now()`
- Payload: `{ accountName, newBalance, transactionAmount }`

O usuário resolve a pendência via o sistema de pending actions existente (marcar como visto ou corrigir o saldo). Não bloqueia o lançamento.

---

### Ajuste de saldo — UX simplificada

**Contexto:** remover a complexidade atual de ADJUSTMENT como tipo separado no toggle. Substituir por checkbox discreto.

**[V2-UI-03] Checkbox "Registrar como ajuste de saldo" no `RecordCashDrawer`**
- Toggle Aporte/Saque permanece como controle principal
- Checkbox abaixo: "Registrar como ajuste de saldo"
- Quando marcado: `type = ADJUSTMENT`, `description` vira obrigatória
- Helper text no checkbox: "Use quando o saldo do app diverge do extrato da corretora. Ex: a corretora mostra R$ 500 mas o app mostra R$ 450."
- Aporte + checkbox marcado = CREDIT BALANCE_ADJUSTMENT; Saque + checkbox = DEBIT BALANCE_ADJUSTMENT

---

### Cash no total do portfólio

**Dependência:** implementação dos Summaries de portfólio.

**[V2-BE-03] `investmentCashBalance` nos Summaries**
- Somar saldo de cash ao total de holdings na conta de investimento
- Exibir "Cash" como categoria no breakdown de alocação do portfólio

---

## Em Aberto

- **FX em custódia internacional** — como tratar cash em USD/EUR em contas de custódia no exterior. Avaliar benchmark (Avenue, Fidelity, Interactive Brokers) antes de definir modelo.
