# Phase 3 — Refatoração de Transaction Rules

## Visão Geral

Refatoração do sistema de regras de transação para melhorar transparência, consistência e experiência do usuário.

**Mudanças principais:**
1. Rastrear quais regras foram aplicadas em cada transação
2. Unificar aplicação de regras (import + manual) com proteção de campos do usuário
3. Separar "Regras manuais" vs "Regras automáticas" na UI
4. Exibir ordem de match na tela de regras

---

## Fase 1 — Rastreamento de Regras Aplicadas

> **Objetivo:** Persistir na transação quais regras foram aplicadas, dando transparência ao usuário.

### Task 1.1 — Migração DB: `applied_rule_ids`

**Arquivo:** `db/migration/V0_23__add_applied_rule_ids_to_transactions.sql`

```sql
ALTER TABLE banking.transactions ADD COLUMN applied_rule_ids JSONB;
```

**Critérios de aceite:**
- Migração Flyway executa sem erro
- Coluna aceita array de UUIDs: `["uuid1", "uuid2"]`
- `NULL` para transações existentes (sem backfill)

---

### Task 1.2 — TransactionEntity: mapear nova coluna

**Arquivo:** `model/transaction/TransactionEntity.kt`

Adicionar campo `appliedRuleIds: String?` com `@JdbcTypeCode(SqlTypes.JSON)` seguindo o padrão de `sourceData`.

**Critérios de aceite:**
- `@Column(name = "applied_rule_ids", columnDefinition = "jsonb")`
- Default `null`

---

### Task 1.3 — Transaction (domínio): adicionar campo

**Arquivo:** `domain/transaction/Transaction.kt`

Adicionar `val appliedRuleIds: List<UUID> = emptyList()`.

**Critérios de aceite:**
- Default vazio não quebra construtores existentes

---

### Task 1.4 — TransactionMapper: serializar/deserializar

**Arquivo:** `model/transaction/TransactionMapper.kt`

Converter entre `List<UUID>` (domínio) e `String?` JSON (entity) usando `objectMapper` já existente.

**Critérios de aceite:**
- `toDomain`: JSON `["uuid1"]` → `List<UUID>`; null/vazio → `emptyList()`
- `toEntity`: `List<UUID>` → JSON string; lista vazia → `null`
- `updateEntity`: atualiza `entity.appliedRuleIds`

---

### Task 1.5 — TransactionService: persistir appliedRuleIds na criação

**Arquivo:** `service/transaction/TransactionService.kt`

Ao construir o `Transaction` para salvar, incluir:
```kotlin
appliedRuleIds = ruleSuggestion?.appliedRuleIds ?: emptyList()
```

**Critérios de aceite:**
- `appliedRuleIds` persistido ao criar transação (MANUAL e IMPORT)
- Quando `ruleSuggestion` é null → `emptyList()`

---

### Task 1.6 — ImportProcessor: persistir appliedRuleIds no import

**Arquivo:** `service/import/ImportProcessor.kt`

Ao construir `Transaction` no import, passar `appliedRuleIds = categorySuggestion?.appliedRuleIds ?: emptyList()`.

**Critérios de aceite:**
- Transações importadas registram quais regras foram aplicadas

---

### Task 1.7 — GraphQL: expor `appliedRuleIds`

**Arquivos:** `schema.graphqls`, `TransactionDTO.kt`, `TransactionResolver`

```graphql
type Transaction {
  # ... campos existentes
  appliedRuleIds: [ID!]!
}
```

**Critérios de aceite:**
- Campo visível na query `transactions`
- Lista vazia para transações sem regras aplicadas

---

### Task 1.8 — Testes unitários: Fase 1

- TransactionMapper (serializar/deserializar `appliedRuleIds`)
- TransactionService (persistência no create)
- ImportProcessor (persistência no import)
- Cenários: sem regra, uma regra, múltiplas regras

---

## Fase 2 — Unificar Aplicação de Regras (Import + Manual)

> **Objetivo:** Remover a diferença entre import e manual. Regras sempre tentam ajudar, mas nunca pisam no que o usuário já definiu.

### Task 2.1 — Remover filtro de source no `suggestRuleForTransaction`

**Arquivo:** `service/transaction/TransactionService.kt`

Remover o `if (source == TransactionSource.MANUAL)` que filtra apenas system rules. O método simplifica para:

```kotlin
private fun suggestRuleForTransaction(
    accountId: UUID,
    description: String?,
    creditDebitType: CreditDebitType,
): RuleSuggestion? =
    transactionRuleEngine.suggest(accountId, description, creditDebitType)
```

**Critérios de aceite:**
- Regras de usuário são aplicadas tanto no manual quanto no import
- Prioridade respeitada (system rules tipicamente priority mais baixo)

---

### Task 2.2 — Lógica de "não sobrescrever campos do usuário" no manual

**Arquivo:** `service/transaction/TransactionService.kt`

Para transações manuais, proteger campos já preenchidos:

| Campo | Import | Manual (preenchido) | Manual (vazio) |
|-------|--------|---------------------|----------------|
| `category` | Aplica regra | Mantém do usuário | Aplica regra |
| `merchant` | Aplica regra | Aplica regra | Aplica regra |
| `description` | Aplica regra | **Nunca sobrescreve** | **Nunca sobrescreve** |
| `tags` | Aplica regra | Mantém do usuário | Aplica regra |

```kotlin
val finalDescription = if (source == TransactionSource.MANUAL) {
    command.description  // usuário sempre fornece descrição em MANUAL
} else {
    ruleSuggestion?.descriptionOverride ?: command.description
}

val finalTags = if (source == TransactionSource.MANUAL && normalizedTags.isNotEmpty()) {
    normalizedTags  // usuário já forneceu tags
} else {
    ruleSuggestion?.tagsOverride ?: normalizedTags
}
```

**Critérios de aceite:**
- Manual com `categoryId` preenchido → regra NÃO sobrescreve
- Manual sem `categoryId` → regra pode sugerir
- Manual: `description` NUNCA sobrescrita pela regra
- Manual com tags → tags NÃO sobrescritas
- Manual sem tags → regra pode sugerir tags
- Import: comportamento anterior mantido

---

### Task 2.3 — Testes unitários: Fase 2

- `suggestRuleForTransaction` aplica todas as regras para MANUAL
- Lógica de proteção de campos (todos os cenários da Task 2.2)
- Regressão: import continua funcionando como antes
- Edge case: regra implícita + transação manual com categoria preenchida

---

## Fase 3 — Separação UI: Regras Manuais vs Automáticas

> **Objetivo:** Organizar regras em abas separadas na UI. System rules são read-only.

### Task 3.1 — GraphQL: filtro `isSystem` na query

**Arquivos:** `schema.graphqls`, `TransactionCategoryResolver.kt`, `TransactionRuleService.kt`

```graphql
type Query {
  transactionRules(isSystem: Boolean): [TransactionRule!]!
}
```

- `isSystem == null` → retorna TODAS as regras (user + system)
- `isSystem == false` → apenas regras do usuário (aba "Regras manuais")
- `isSystem == true` → apenas regras de sistema (aba "Regras automáticas")

**Critérios de aceite:**
- Backward-compatible (parâmetro opcional)
- Frontend pode filtrar por aba

---

### Task 3.2 — Repository: query para regras de sistema

**Arquivos:** `CategoryRuleRepository.kt`, `CategoryRuleEntityRepository.kt`, `DefaultCategoryRuleRepository.kt`

Adicionar `findSystemRules()` ou refatorar `findRulesForAccount(accountId, isSystem?)`.

**Critérios de aceite:**
- Retorna regras de sistema ordenadas por `priority ASC, matchCount DESC`
- Regras com `account_id IS NULL` (globais) incluídas

---

### Task 3.3 — Verificar bloqueio de edição/exclusão de system rules

**Arquivo:** `TransactionRuleService.kt`

Verificar que os guards existentes lançam `SystemTransactionRuleNotEditableException` / `SystemTransactionRuleNotDeletableException`. Adicionar teste explícito se não existir.

**Critérios de aceite:**
- Double-check no backend confirmado
- Teste explícito existe

---

### Task 3.4 — Testes unitários: Fase 3

- `transactionRules(isSystem: true)` retorna apenas system rules
- `transactionRules(isSystem: false)` retorna apenas user rules
- `transactionRules()` sem parâmetro retorna todas
- Editar/excluir system rule retorna erro

---

## Fase 4 — Posição de Match na Tela de Regras

> **Objetivo:** Mostrar ao usuário a ordem em que as regras são avaliadas.

### Task 4.1 — Campo `position` no TransactionRule

**Arquivos:** `schema.graphqls`, `TransactionRuleDTO.kt` (se existir, ou dentro do resolver)

```graphql
type TransactionRule {
  # ... campos existentes
  position: Int!
}
```

Calculado no resolver como índice 1-based na lista já ordenada:
```kotlin
rules.mapIndexed { index, rule -> rule.toDTO(position = index + 1) }
```

**Critérios de aceite:**
- `position` é 1-based
- Reflete a ordem real de avaliação do motor de regras
- Reordenamento (`reorderTransactionRules`) atualiza posições

---

### Task 4.2 — Documentar algoritmo de matching

Adicionar documentação (comments no schema + KDoc no `DefaultTransactionRuleEngine`):

1. Regras avaliadas por `priority ASC` (menor = maior precedência)
2. Empate: `matchCount DESC` (mais matches = maior precedência)
3. Empate: `createdAt DESC` (mais recente primeiro)
4. Primeira regra que faz match define cada campo (first-write-wins por campo)
5. Múltiplas regras podem contribuir campos diferentes

---

### Task 4.3 — Testes unitários: Fase 4

- `position` calculado corretamente
- `position` correto após reordenamento
- Consistência entre posição retornada e ordem de avaliação real

---

## Dependências e Ordem de Implementação

```
Fase 1 (Track applied rules) ─── pode começar imediatamente
    │
    ▼
Fase 2 (Unificar regras)     ─── depende da Fase 1

Fase 3 (Separação UI)        ─── independente, pode ser paralela à Fase 1
Fase 4 (Position)            ─── independente, pode ser paralela à Fase 1
```

**Ordem sugerida:**
1. **Fase 1** primeiro (fundação para rastreamento)
2. **Fase 3** e **Fase 4** em paralelo (independentes entre si e da Fase 1)
3. **Fase 2** por último (maior risco, depende da Fase 1, mais testes de regressão)

---

## Riscos e Edge Cases

| Risco | Impacto | Mitigação |
|-------|---------|-----------|
| Regras implícitas no fluxo manual unificado | Categorização indesejada por regras aprendidas | Lógica de "não sobrescrever campos fornecidos" (Task 2.2) protege |
| Backward-compatibility GraphQL | Clientes existentes quebram | Campos e parâmetros novos são opcionais/aditivos |
| Regra deletada com UUID persistido na transação | `appliedRuleIds` aponta para regra inexistente | Frontend trata gracefully ("regra removida") |
| Performance da coluna JSONB | Overhead de storage | Arrays pequenos (1-3 UUIDs), sem necessidade de índice GIN |
| Migração em produção | Downtime | `ALTER TABLE ADD COLUMN DEFAULT NULL` é operação leve no PostgreSQL |
| Outros fluxos de criação (ex: BillImportService) | `appliedRuleIds` não rastreado | Verificar todos os entry points de criação de transação |
