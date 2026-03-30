# E2E Test Specification — Personal Finance

> **Objetivo:** Cobertura excepcional das funcionalidades de finanças pessoais com Playwright.
> **Framework:** Playwright + Page Object Model
> **Convenção de escrita:** BDD — `describe` por feature, `describe` por dimensão de teste.

---

## Índice

1. [Estrutura e convenções](#estrutura-e-convenções)
2. [Fixtures e helpers](#fixtures-e-helpers)
3. [FA — Financial Accounts](#fa--financial-accounts)
4. [TX — Transactions](#tx--transactions)
5. [CB — Credit Card Bills](#cb--credit-card-bills)
6. [MS — Monthly Summaries](#ms--monthly-summaries)
7. [IM — Transaction Import](#im--transaction-import)
8. [SEC — Security (Cross-Tenant Isolation)](#sec--security-cross-tenant-isolation)

---

## Estrutura e convenções

### Localização dos arquivos

```
tests/e2e/
├── personal-finance/
│   ├── financial-accounts.e2e.spec.ts
│   ├── transactions.e2e.spec.ts
│   ├── credit-card-bills.e2e.spec.ts
│   ├── monthly-summary.e2e.spec.ts
│   ├── transaction-import.e2e.spec.ts
│   └── security.e2e.spec.ts
├── fixtures/
│   ├── personal-finance.ts          ← fixtures de contas e transações
│   └── index.ts
├── pages/
│   ├── FinanceTransactionsPage.ts
│   ├── TransactionTablePage.ts
│   ├── MonthSummarySection.ts
│   ├── BillManagementPage.ts
│   └── CreateAccountDrawer.ts
└── helpers/
    └── graphql.ts                   ← já existe; estender com mutations de PF
```

### Estrutura de cada spec

```typescript
test.describe('[Feature]', () => {
  test.describe('happy path', () => { ... })
  test.describe('alternative paths', () => { ... })
  test.describe('negative paths', () => { ... })
})
```

### IDs de teste (`data-testid`)

Elementos críticos que precisam de `data-testid` nos componentes:

| Elemento | data-testid |
|----------|-------------|
| Campo "Entradas" no resumo | `month-summary-total-income` |
| Campo "Saídas" no resumo | `month-summary-total-expenses` |
| Campo "Saldo" no resumo | `month-summary-closing-balance` |
| Linha de transação | `transaction-row-{id}` |
| Carrossel de mês atual | `month-year-carousel-current` |
| Seletor de conta | `account-selector` |
| Botão "Nova transação" | `btn-new-transaction` |
| Formulário inline | `inline-transaction-form` |

---

## Fixtures e helpers

### GraphQL mutations necessárias (estender `graphql.ts`)

```typescript
// helpers/personal-finance-graphql.ts

export async function createFinancialAccount(request, token, input) {
  return executeGraphQL(request, token, CREATE_FINANCIAL_ACCOUNT_MUTATION, { input })
}

export async function createTransaction(request, token, input) {
  return executeGraphQL(request, token, CREATE_TRANSACTION_MUTATION, { input })
}

export async function deleteFinancialAccount(request, token, id) {
  return executeGraphQL(request, token, DELETE_FINANCIAL_ACCOUNT_MUTATION, { id })
}
```

### Fixture base de personal finance

```typescript
// fixtures/personal-finance.ts

export const test = base.extend({
  bankAccount: async ({ request, adminUser }, use) => {
    const account = await createFinancialAccount(request, adminUser.token, {
      name: `Conta Corrente Test ${Date.now()}`,
      type: 'BANK',
      subtype: 'CONTA_CORRENTE',
      initialBalance: 1000.00,
    })
    await use(account)
    await deleteFinancialAccount(request, adminUser.token, account.id)
  },

  creditCardAccount: async ({ request, adminUser }, use) => {
    const account = await createFinancialAccount(request, adminUser.token, {
      name: `Cartão Test ${Date.now()}`,
      type: 'CREDIT',
      subtype: 'CREDIT_CARD',
      initialBalance: 0,
      closingDay: 7,
      dueDay: 15,
    })
    await use(account)
    await deleteFinancialAccount(request, adminUser.token, account.id)
  },

  postedExpense: async ({ request, adminUser, bankAccount }, use) => {
    const tx = await createTransaction(request, adminUser.token, {
      financialAccountId: bankAccount.id,
      description: `Despesa Test ${Date.now()}`,
      amount: 100.00,
      creditDebitType: 'DEBIT',
      status: 'POSTED',
      date: today(),
    })
    await use(tx)
  },

  twoTenants: async ({ browser }, use) => {
    const ctxA = await browser.newContext()
    const ctxB = await browser.newContext()
    // sign in com dois usuários distintos
    const tokenA = await signInAndGetToken(ctxA, userA)
    const tokenB = await signInAndGetToken(ctxB, userB)
    await use({ ctxA, ctxB, tokenA, tokenB })
    await ctxA.close()
    await ctxB.close()
  },
})
```

---

## FA — Financial Accounts

**Arquivo:** `financial-accounts.e2e.spec.ts`
**Page Object:** `CreateAccountDrawer.ts`, `FinanceTransactionsPage.ts`

---

### FA — Happy Path

| ID | Cenário | Resultado esperado |
|----|---------|-------------------|
| FA-H-01 | Criar conta bancária (BANK/CONTA_CORRENTE) com saldo inicial R$ 1.000 | Conta aparece no seletor de contas; saldo inicial exibido |
| FA-H-02 | Criar conta de cartão de crédito (CREDIT/CREDIT_CARD) com closingDay=7 e dueDay=15 | Conta aparece no seletor; modo de exibição é calendário |
| FA-H-03 | Criar conta carteira (CASH) sem dados bancários | Conta criada sem campos de banco |
| FA-H-04 | Criar conta de benefício (BENEFIT/VALE_REFEICAO) | Conta criada com tipo correto |
| FA-H-05 | Listar contas — múltiplas contas do mesmo tenant visíveis | Todas as contas do usuário listadas |
| FA-H-06 | Editar nome da conta | Nome atualizado no seletor |
| FA-H-07 | Editar saldo inicial da conta | Saldo refletido corretamente (não retroativo em transações) |
| FA-H-08 | Soft-delete de conta sem transações | Conta removida do seletor; não aparece em listagens |

---

### FA — Alternative Paths

| ID | Cenário | Resultado esperado |
|----|---------|-------------------|
| FA-A-01 | Criar conta com saldo inicial = R$ 0 | Conta criada; saldo zero exibido |
| FA-A-02 | Criar conta com saldo inicial negativo (conta com dívida) | Conta criada; saldo negativo exibido |
| FA-A-03 | Criar duas contas com tipos diferentes (BANK + CREDIT) | Ambas aparecem no seletor; cada uma com seu comportamento |
| FA-A-04 | Criar conta CREDIT com closingDay=28 (limite máximo) | Conta criada; `closingDay=28` salvo corretamente |
| FA-A-05 | Criar conta CREDIT com dueDay igual ao closingDay + 1 (mínimo razoável) | Conta criada sem erro |
| FA-A-06 | Nome da conta com caracteres especiais e acentos (ex: "Nubank – Crédito") | Exibido corretamente em todo o sistema |
| FA-A-07 | Editar `closingDay` de conta de cartão já existente | Alteração salva; novas transações seguem o novo ciclo |
| FA-A-08 | Conta com `bankData` preenchido (agência, conta, banco) | Dados exibidos corretamente no drawer de edição |

---

### FA — Negative Paths

| ID | Cenário | Resultado esperado |
|----|---------|-------------------|
| FA-N-01 | Submeter formulário sem nome | Erro de validação "Nome é obrigatório"; formulário não fecha |
| FA-N-02 | Submeter formulário sem tipo de conta | Erro de validação; formulário não fecha |
| FA-N-03 | Criar conta CREDIT sem informar `closingDay` | Erro de validação; campo obrigatório para CREDIT/CHARGE_CARD |
| FA-N-04 | Criar conta CREDIT sem informar `dueDay` | Erro de validação; campo obrigatório para CREDIT/CHARGE_CARD |
| FA-N-05 | `closingDay` = 0 | Erro de validação; range válido: 1–28 |
| FA-N-06 | `closingDay` = 29 | Erro de validação; range válido: 1–28 |
| FA-N-07 | `dueDay` = 0 | Erro de validação |
| FA-N-08 | `dueDay` = 29 | Erro de validação |
| FA-N-09 | Nome com mais de 255 caracteres | Erro de validação ou truncamento esperado |
| FA-N-10 | Tentar alterar o `type` de uma conta existente (type é imutável) | Campo type desabilitado na edição; alteração bloqueada |

---

## TX — Transactions

**Arquivo:** `transactions.e2e.spec.ts`
**Page Objects:** `FinanceTransactionsPage.ts`, `TransactionTablePage.ts`

---

### TX — Happy Path

| ID | Cenário | Resultado esperado |
|----|---------|-------------------|
| TX-H-01 | Criar despesa POSTED (DEBIT) em conta bancária | Transação aparece na tabela; `totalExpenses` do resumo aumenta |
| TX-H-02 | Criar receita POSTED (CREDIT) em conta bancária | Transação aparece na tabela; `totalIncome` do resumo aumenta |
| TX-H-03 | Criar despesa PENDING | Transação aparece na tabela; afeta `closingBalance`; **não** aparece em `totalExpenses` separadamente (incluso no saldo) |
| TX-H-04 | Criar despesa FUTURE | Transação aparece na tabela com indicação visual; **não** afeta `closingBalance` nem resumo |
| TX-H-05 | Editar descrição de transação inline | Descrição atualizada na tabela sem recarregar página |
| TX-H-06 | Editar valor de transação | `closingBalance` e resumo mensal atualizados |
| TX-H-07 | Editar status PENDING → POSTED | Transação permanece; saldo calculado da mesma forma (PENDING já estava no saldo) |
| TX-H-08 | Editar status FUTURE → POSTED | `closingBalance` aumenta/diminui conforme o tipo |
| TX-H-09 | Deletar transação POSTED | Transação removida da tabela; `closingBalance` e resumo revertidos |
| TX-H-10 | Atribuir categoria à transação | Categoria exibida na linha; resumo por categoria atualizado |
| TX-H-11 | Adicionar tag à transação | Tag exibida; filtragem por tag funciona |
| TX-H-12 | Filtrar transações por categoria | Apenas transações da categoria exibidas |
| TX-H-13 | Filtrar transações por DEBIT/CREDIT | Apenas o tipo selecionado exibido |
| TX-H-14 | Filtrar por tag | Apenas transações com a tag exibidas |
| TX-H-15 | Paginar transações (infinite scroll ou paginação) | Mais transações carregadas ao rolar/avançar |

---

### TX — Alternative Paths

| ID | Cenário | Resultado esperado |
|----|---------|-------------------|
| TX-A-01 | Criar transação do tipo TRANSFER (PIX entre contas próprias) | Aparece na tabela; **não** contabilizado em `totalIncome`/`totalExpenses` |
| TX-A-02 | Criar transação do tipo BALANCE_ADJUSTMENT | Aparece na tabela; **não** contabilizado em `totalIncome`/`totalExpenses`; **não** associada a fatura |
| TX-A-03 | Criar transação no primeiro dia do mês | Aparece no mês correto; `openingBalance` é o `closingBalance` do mês anterior |
| TX-A-04 | Criar transação no último dia do mês | Aparece no mês correto |
| TX-A-05 | Criar transação com valor centavos (R$ 0,01) | Valor exibido e calculado corretamente |
| TX-A-06 | Criar transação com valor alto (R$ 999.999,99) | Valor exibido e calculado corretamente |
| TX-A-07 | Criar transação com notas/observação longa | Notas salvas e exibidas corretamente |
| TX-A-08 | Editar valor de transação para valor maior | `closingBalance` atualizado corretamente |
| TX-A-09 | Editar valor de transação para valor menor | `closingBalance` atualizado corretamente |
| TX-A-10 | Editar data de transação para mês anterior | Transação desaparece do mês atual; aparece no mês anterior; ambos os resumos recalculados (chain recompute) |
| TX-A-11 | Editar data de transação para mês posterior | Idem acima na direção oposta |
| TX-A-12 | Criar transação com parcelamento (3x R$ 100) | 3 transações criadas; meses subsequentes têm as parcelas |
| TX-A-13 | Deletar uma parcela do meio de um parcelamento | Apenas a parcela selecionada removida; demais mantidas |
| TX-A-14 | Editar "transações similares" em lote (SimilarEditDialog) | Todas as similares atualizadas; resumos de todos os meses recalculados |
| TX-A-15 | Mês sem nenhuma transação | Resumo exibe zeros; sem erro |
| TX-A-16 | Transação de cartão de crédito — associação automática à fatura | `billId` preenchido; transação aparece na listagem da fatura |

---

### TX — Negative Paths

| ID | Cenário | Resultado esperado |
|----|---------|-------------------|
| TX-N-01 | Submeter transação sem descrição | Erro de validação; formulário não fecha |
| TX-N-02 | Submeter transação com valor = 0 | Erro de validação "Valor deve ser maior que zero" |
| TX-N-03 | Submeter transação com valor negativo | Erro de validação |
| TX-N-04 | Submeter transação sem `creditDebitType` | Erro de validação |
| TX-N-05 | Submeter transação sem `type` | Erro de validação |
| TX-N-06 | Data anterior à janela permitida (`dateWindow` mínimo) | Erro de validação; data bloqueada no picker |
| TX-N-07 | Data superior à janela permitida (`dateWindow` máximo) | Erro de validação; data bloqueada no picker |
| TX-N-08 | Parcelamento em conta BANK (não permitido, apenas CREDIT/CHARGE_CARD) | Campo de parcelamento não exibido ou bloqueado para contas bancárias |
| TX-N-09 | Deletar transação já deletada (double-delete via API direta) | Retorna erro 404 ou "não encontrada" |
| TX-N-10 | Editar transação inexistente (ID inválido via GraphQL direto) | Erro "Transaction not found" |

---

## CB — Credit Card Bills

**Arquivo:** `credit-card-bills.e2e.spec.ts`
**Page Object:** `BillManagementPage.ts`

> **Nota MVP:** O modo de exibição de faturas (bill mode no seletor) está reservado para fase futura.
> Os testes de fatura validam a **lógica de negócio via GraphQL** (associação correta de transações à fatura,
> totais corretos, status do ciclo) e a **exibição em modo calendário** enquanto o modo fatura não é implementado.

---

### CB — Happy Path

| ID | Cenário | Resultado esperado |
|----|---------|-------------------|
| CB-H-01 | Criar transação em cartão ANTES do `closingDay` (ex: dia 3, closingDay=7) | Fatura do mês corrente criada automaticamente; `billId` preenchido |
| CB-H-02 | Criar transação em cartão DEPOIS do `closingDay` (ex: dia 10, closingDay=7) | Fatura do **próximo** mês criada; `billId` preenchido |
| CB-H-03 | Criar segunda transação no mesmo ciclo | Associada à mesma fatura; `totalAmount` soma as duas |
| CB-H-04 | Criar transação de crédito no cartão (estorno/devolução) | `totalAmount` da fatura reduzido corretamente |
| CB-H-05 | Listar faturas da conta via GraphQL | Retorna lista com as faturas criadas |
| CB-H-06 | `totalAmount` recalculado após editar valor de transação | Fatura reflete o novo valor |
| CB-H-07 | `totalAmount` recalculado após deletar transação | Fatura reflete a remoção |
| CB-H-08 | Status OPEN → CLOSED após `closingDate` passar | Fatura fecha automaticamente (via sweeper ou consulta) |

---

### CB — Alternative Paths

| ID | Cenário | Resultado esperado |
|----|---------|-------------------|
| CB-A-01 | Transação criada exatamente no `closingDay` (dia == closingDay) | Regra: dia <= closingDay → fatura do **mês corrente** |
| CB-A-02 | `closingDay=28`, transação em fevereiro (28 dias) — dia 28 | Fatura fecha em 28/fev; sem crash por mês curto |
| CB-A-03 | `closingDay=31` configurado como 28 (clamping) — transação no dia 28 | Sistema interpreta corretamente o closingDay efetivo |
| CB-A-04 | `dueDay` cai antes do `closingDay` no mesmo mês (ex: closing=20, due=10) | `dueDate` calculado para o mês seguinte ao fechamento |
| CB-A-05 | Fatura com zero transações (todas deletadas) | `totalAmount = 0`; status permanece OPEN/CLOSED dependendo da data |
| CB-A-06 | Transação BALANCE_ADJUSTMENT em conta de cartão | **Não** associada a nenhuma fatura; `billId = null` |
| CB-A-07 | Múltiplas faturas no histórico (6 meses) | Todas as faturas listadas; cada uma com totais corretos |
| CB-A-08 | Fatura CLOSED sem pagamento após `dueDate` | Status muda para OVERDUE |
| CB-A-09 | Dois cartões diferentes — faturas isoladas por conta | Fatura de um cartão não interfere no outro |

---

### CB — Negative Paths

| ID | Cenário | Resultado esperado |
|----|---------|-------------------|
| CB-N-01 | Consultar faturas de conta BANK (não cartão) | Lista vazia ou erro "Conta não é do tipo cartão" |
| CB-N-02 | Consultar fatura com `closingDate` inválida (formato errado via GraphQL) | Erro de validação GraphQL |
| CB-N-03 | Consultar fatura de conta inexistente | Erro "Financial account not found" ou lista vazia |
| CB-N-04 | Status PAID nunca deve reverter para CLOSED/OVERDUE após exclusão do pagamento | **BUG CONHECIDO (B1 no code review):** documentar comportamento atual vs. esperado; teste deve falhar até correção |

---

## MS — Monthly Summaries

**Arquivo:** `monthly-summary.e2e.spec.ts`
**Page Object:** `MonthSummarySection.ts`

---

### MS — Happy Path

| ID | Cenário | Resultado esperado |
|----|---------|-------------------|
| MS-H-01 | Criar despesa POSTED → `totalExpenses` e `closingBalance` atualizados | Resumo do mês reflete imediatamente |
| MS-H-02 | Criar receita POSTED → `totalIncome` e `closingBalance` atualizados | Resumo do mês reflete imediatamente |
| MS-H-03 | Deletar transação → resumo revertido ao estado anterior | Valores voltam ao que eram antes |
| MS-H-04 | Editar valor de transação → resumo atualizado com novo valor | Diferença calculada corretamente |
| MS-H-05 | Navegar para o mês anterior → ver transações e resumo daquele mês | Resumo e tabela do mês anterior exibidos |
| MS-H-06 | Navegar para o mês seguinte → ver transações e resumo daquele mês | Resumo e tabela do mês seguinte exibidos |
| MS-H-07 | `openingBalance` do mês atual = `closingBalance` do mês anterior | Verificar via GraphQL ou UI side-by-side |
| MS-H-08 | Resumo por categoria exibido (breakdown) | Categorias listadas com totais corretos |

---

### MS — Alternative Paths

| ID | Cenário | Resultado esperado |
|----|---------|-------------------|
| MS-A-01 | Mês sem nenhuma transação | `totalIncome=0`, `totalExpenses=0`; `closingBalance = openingBalance` |
| MS-A-02 | Mês apenas com transações FUTURE | `closingBalance` **não** alterado pelas transações FUTURE |
| MS-A-03 | Mês apenas com transações PENDING | `closingBalance` **inclui** PENDING; `totalIncome`/`totalExpenses` também |
| MS-A-04 | Mês apenas com TRANSFERs entre contas próprias | `totalIncome=0`, `totalExpenses=0`; apenas saldo alterado |
| MS-A-05 | Mês apenas com BALANCE_ADJUSTMENT | `totalIncome=0`, `totalExpenses=0`; saldo alterado |
| MS-A-06 | Primeiro mês da conta: `openingBalance = initialBalance` da conta | Chain começa corretamente |
| MS-A-07 | Editar transação de mês anterior → resumos de todos os meses posteriores recalculados (chain) | Verificar que meses subsequentes têm `openingBalance` correto após a mudança |
| MS-A-08 | Criar transação em mês passado via edit de data | Resumo do mês passado e dos seguintes atualizado |
| MS-A-09 | Mês futuro sem transações | Resumo exibe projeção baseada em saldo atual; sem erro |
| MS-A-10 | Resumo com transações de múltiplas categorias | Breakdown por categoria correto; soma = `totalIncome` ou `totalExpenses` |
| MS-A-11 | Resumo com transações tagueadas | Breakdown por tag exibido corretamente |

---

### MS — Negative Paths

| ID | Cenário | Resultado esperado |
|----|---------|-------------------|
| MS-N-01 | Consultar resumo de conta inexistente via GraphQL | Erro "Financial account not found" |
| MS-N-02 | Consultar resumo com `year` inválido (ex: 0 ou 9999) | Erro de validação |
| MS-N-03 | Consultar resumo com `month` inválido (0 ou 13) | Erro de validação |
| MS-N-04 | Conta sem nenhuma transação em nenhum mês | `openingBalance = initialBalance`; restante = zeros; sem erro |

---

## IM — Transaction Import

**Arquivo:** `transaction-import.e2e.spec.ts`
**Page Object:** `ImportDrawerPage.ts`

---

### IM — Happy Path

| ID | Cenário | Resultado esperado |
|----|---------|-------------------|
| IM-H-01 | Importar arquivo CSV válido com 10 transações | Preview exibido; 10 transações listadas para confirmação |
| IM-H-02 | Confirmar importação CSV → transações criadas | Transações aparecem na tabela; resumo mensal atualizado |
| IM-H-03 | Importar arquivo OFX válido | Preview exibido; transações listadas para confirmação |
| IM-H-04 | Confirmar importação OFX → transações criadas | Transações aparecem na tabela |
| IM-H-05 | Importação com transações que já existem (duplicatas detectadas) | Tela de revisão de duplicatas exibida; usuário pode ignorar ou mesclar |
| IM-H-06 | Ignorar duplicatas → apenas novas transações importadas | Duplicatas não criadas; novas criadas corretamente |

---

### IM — Alternative Paths

| ID | Cenário | Resultado esperado |
|----|---------|-------------------|
| IM-A-01 | CSV com transações de múltiplos meses | Todas importadas; resumos de cada mês atualizados |
| IM-A-02 | CSV com mix de débitos e créditos | Ambos os tipos importados com `creditDebitType` correto |
| IM-A-03 | CSV com campo de categoria mapeado | Categoria atribuída automaticamente se matching |
| IM-A-04 | CSV com encoding UTF-8 (acentos e ç) | Nomes com acentos exibidos corretamente |
| IM-A-05 | CSV com encoding Latin-1/ISO-8859-1 | Sistema converte ou exibe corretamente |
| IM-A-06 | OFX com `FITID` repetido (duplicata no mesmo arquivo) | Apenas uma instância importada |
| IM-A-07 | Importação parcial: algumas transações são duplicatas, outras são novas | Revisão de duplicatas exibe separação clara; novas são importadas |
| IM-A-08 | Cancelar importação após preview | Nenhuma transação criada; estado não modificado |

---

### IM — Negative Paths

| ID | Cenário | Resultado esperado |
|----|---------|-------------------|
| IM-N-01 | Upload de arquivo PDF (formato não suportado) | Erro "Formato não suportado; use CSV ou OFX" |
| IM-N-02 | Upload de arquivo CSV malformado (colunas faltando) | Erro descritivo exibido; sem importação |
| IM-N-03 | Upload de arquivo CSV vazio (sem transações) | Mensagem "Arquivo não contém transações" |
| IM-N-04 | Upload de arquivo com tamanho excessivo | Erro de limite de tamanho |
| IM-N-05 | OFX com datas em formato inválido | Erro descritivo; sem importação parcial silenciosa |
| IM-N-06 | Importar para conta inexistente (via GraphQL direto) | Erro "Financial account not found" |

---

## SEC — Security (Cross-Tenant Isolation)

**Arquivo:** `security.e2e.spec.ts`
**Fixture:** `twoTenants`

> **Contexto:** O sistema é multi-tenant. Cada usuário (tenant) só deve ver e modificar seus próprios dados.
> O campo `account_id` é o discriminador de tenant em todas as entidades.
> Estes testes usam dois contextos de browser isolados (`ctxA` = Usuário A, `ctxB` = Usuário B).

---

### SEC — Financial Accounts

| ID | Vetor | Cenário | Resultado esperado |
|----|-------|---------|-------------------|
| SEC-FA-01 | Leitura | Usuário B consulta `financialAccount(id: <id do A>)` via GraphQL | `null` retornado ou erro "Not found" — **nunca** os dados de A |
| SEC-FA-02 | Enumeração | Usuário B consulta `financialAccounts` | Retorna apenas contas do próprio B |
| SEC-FA-03 | Mutação — update | Usuário B tenta `updateFinancialAccount(id: <id do A>, ...)` | Erro "Not found" ou "Forbidden" — conta de A não alterada |
| SEC-FA-04 | Mutação — delete | Usuário B tenta deletar conta de A | Erro "Not found" ou "Forbidden" |

---

### SEC — Transactions

| ID | Vetor | Cenário | Resultado esperado |
|----|-------|---------|-------------------|
| SEC-TX-01 | Leitura | B consulta `transactions(financialAccountId: <id da conta de A>)` | Lista vazia ou erro "Not found" — nunca transações de A |
| SEC-TX-02 | Mutação — create | B tenta criar transação na conta de A (`financialAccountId: <id de A>`) | Erro de autorização; transação não criada |
| SEC-TX-03 | Mutação — update | B tenta `updateTransaction(id: <id de transação de A>, ...)` | Erro "Not found" ou "Forbidden" |
| SEC-TX-04 | Mutação — delete | B tenta deletar transação de A | Erro "Not found" ou "Forbidden" |
| SEC-TX-05 | Filtros | B filtra transações com `categoryId` de categoria de A | Lista vazia ou resultado sem dados de A |

---

### SEC — Monthly Summaries

| ID | Vetor | Cenário | Resultado esperado |
|----|-------|---------|-------------------|
| SEC-MS-01 | Leitura | B consulta `monthSummary(financialAccountId: <id da conta de A>)` | Erro "Not found" ou dados zerados — nunca dados de A |
| SEC-MS-02 | Inferência via saldo | B cria transação na conta de A (SEC-TX-02) e consulta resumo de A | Resumo de A inalterado |

---

### SEC — Credit Card Bills

| ID | Vetor | Cenário | Resultado esperado |
|----|-------|---------|-------------------|
| SEC-CB-01 | Leitura | B consulta `creditCardBills(financialAccountId: <id do cartão de A>)` | Lista vazia ou erro "Not found" |
| SEC-CB-02 | Leitura específica | B consulta `creditCardBill(financialAccountId: <id de A>, closingDate: ...)` | `null` ou erro |
| SEC-CB-03 | Mutação — pay | B tenta `payBill(input: { billId: <id da fatura de A> })` | Erro de autorização; fatura de A não modificada |

---

### SEC — Transaction Import

| ID | Vetor | Cenário | Resultado esperado |
|----|-------|---------|-------------------|
| SEC-IM-01 | Mutação | B tenta `importTransactions(input: { financialAccountId: <id de A>, ... })` | Erro de autorização; nenhuma transação criada na conta de A |
| SEC-IM-02 | Leitura | B consulta `transactionImports(financialAccountId: <id de A>)` | Lista vazia ou erro |

---

### SEC — ID Enumeration (IDOR)

| ID | Vetor | Cenário | Resultado esperado |
|----|-------|---------|-------------------|
| SEC-IDOR-01 | UUID sequencial | B incrementa/decrementa o UUID da conta de A (UUIDs não são sequenciais, mas testar boundary) | Nunca retorna dados de outro tenant |
| SEC-IDOR-02 | ID de outro tipo | B usa ID de uma transação como se fosse ID de conta | Erro de tipo/validação; sem vazamento de dados |

---

### SEC — Token Manipulation

| ID | Vetor | Cenário | Resultado esperado |
|----|-------|---------|-------------------|
| SEC-TOK-01 | Token expirado | Usar token JWT expirado em qualquer mutation | Erro 401 "Unauthorized" |
| SEC-TOK-02 | Token malformado | Usar token JWT com assinatura inválida | Erro 401 |
| SEC-TOK-03 | Sem token | Chamar qualquer query de PF sem Authorization header | Erro 401 |
| SEC-TOK-04 | Token de outro ambiente (staging vs prod) | Token válido em staging usado em prod | Erro 401 (assinatura inválida) |

---

## Resumo de cobertura

| Feature | Happy | Alternative | Negative | Security | Total |
|---------|-------|-------------|----------|----------|-------|
| Financial Accounts | 8 | 8 | 10 | 4 | **30** |
| Transactions | 15 | 16 | 10 | 5 | **46** |
| Credit Card Bills | 8 | 9 | 4 | 3 | **24** |
| Monthly Summaries | 8 | 11 | 4 | 2 | **25** |
| Transaction Import | 6 | 8 | 6 | 2 | **22** |
| Security (IDOR + Token) | — | — | — | 6 | **6** |
| **Total** | **45** | **52** | **34** | **22** | **153** |

---

## Prioridade de implementação

### Sprint 1 — Fundação
1. Fixtures: `bankAccount`, `creditCardAccount`, `postedExpense`, `twoTenants`
2. Page Objects base: `FinanceTransactionsPage`, `TransactionTablePage`, `MonthSummarySection`
3. Adicionar `data-testid` nos elementos críticos dos componentes
4. Implementar: `FA-H-*`, `FA-N-*` (30 casos)

### Sprint 2 — Core de transações
1. Page Object: `CreateTransactionForm`
2. Implementar: `TX-H-*`, `TX-A-01..08`, `TX-N-01..07` (26 casos)

### Sprint 3 — Resumo mensal
1. Estender `MonthSummarySection` com leitura de totais
2. Implementar: `MS-H-*`, `MS-A-*`, `MS-N-*` (25 casos)
3. Implementar: `TX-A-09..16`, `TX-N-08..10` (restante de TX — 9 casos)

### Sprint 4 — Faturas
1. Page Object: `BillManagementPage`
2. Implementar: `CB-H-*`, `CB-A-*`, `CB-N-*` (24 casos)

### Sprint 5 — Importação
1. Page Object: `ImportDrawerPage`
2. Implementar: `IM-H-*`, `IM-A-*`, `IM-N-*` (22 casos)

### Sprint 6 — Segurança
1. Fixture `twoTenants`
2. Implementar: `SEC-*` completo (28 casos)

---

*Documento gerado em: 2026-03-25*
*Total de casos: 153*
