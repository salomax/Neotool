# Phase 3 — Refatoração de Transaction Rules

## Objetivo

Refinar o sistema de regras de transação para:

1. dar transparência sobre quais regras foram aplicadas
2. unificar o comportamento entre criação manual e import
3. separar regras manuais e automáticas na UI
4. preparar a exibição da ordem real de avaliação

---

## Avaliação Atual do Código

Data da revisão: 2026-04-02

### Resumo executivo

- **Fase 1 — Track applied rules:** backend concluído; transparência na UI ainda pendente
- **Fase 2 — Unificar aplicação manual/import:** backend concluído; cobertura de testes ainda insuficiente
- **Fase 3 — Separação UI manual vs automática:** concluída
- **Fase 4 — Position / ordem de match:** backend pronto, exploração de UX adiada

### Ajustes importantes neste documento

- A coluna `applied_rule_ids` **já existe** no schema base de `transactions`; não é mais uma migração pendente
- A query `transactionRules` **não retorna mais lista simples**; hoje ela é uma connection paginada com `edges/pageInfo/totalCount`
- O backend já implementa `position`, `isSystem`, guards para system rules e `appliedRuleIds`

---

## Status por Fase

## Fase 1 — Rastreamento de Regras Aplicadas

> Objetivo: persistir quais regras contribuíram para o enriquecimento automático da transação.

### Status

- **Concluído no backend**
- **Pendente na experiência do usuário**

### O que já foi feito

- `Transaction.appliedRuleIds` existe no domínio
- `TransactionEntity.appliedRuleIds` está mapeado em JSONB
- `TransactionMapper` serializa e desserializa corretamente
- `TransactionService` persiste `appliedRuleIds` na criação manual
- `ImportProcessor` persiste `appliedRuleIds` no fluxo de import
- GraphQL expõe `Transaction.appliedRuleIds`
- Existe payload adicional `appliedRules` no create quando `ruleApplicationMode = ALWAYS_PROMPT`

### O que falta

- Expor a informação de forma navegável na UI de transações
- Decisão atual:
  - quando houver regras aplicadas, o usuário poderá ver isso pelo menu de item (`MoreVert`)
  - a apresentação pode ser via `Dialog` ou `Drawer`
  - a decisão fina de componente visual pode ser tomada durante implementação

### Status das tasks originais

- Task 1.1: **não aplicável como tarefa nova**
  - a coluna já está no schema base
- Task 1.2: **feito**
- Task 1.3: **feito**
- Task 1.4: **feito**
- Task 1.5: **feito**
- Task 1.6: **feito**
- Task 1.7: **feito no backend**
- Task 1.8: **parcial**
  - mapper coberto
  - criação manual coberta parcialmente
  - import sem cobertura suficiente para `appliedRuleIds`
  - resolver/graphql sem cobertura específica

---

## Fase 2 — Unificar Aplicação de Regras (Import + Manual)

> Objetivo: regras sempre ajudam, mas não sobrescrevem campos já definidos pelo usuário.

### Status

- **Concluído no backend**
- **Pendente em testes de regressão**

### O que já foi feito

- `TransactionService` não filtra mais rules por `source == MANUAL`
- criação manual usa `transactionRuleEngine.suggest(...)` diretamente
- categoria preenchida manualmente não é sobrescrita
- descrição manual nunca é sobrescrita
- tags manuais preenchidas não são sobrescritas
- import mantém comportamento de enriquecimento automático

### Observações de comportamento atuais

| Campo | Manual | Import |
|-------|--------|--------|
| `category` | mantém valor do usuário quando informado; aplica regra quando vazio | aplica regra |
| `merchant` | regra pode preencher | regra pode preencher |
| `description` | mantém descrição do usuário | regra pode sobrescrever |
| `tags` | mantém tags do usuário quando informadas; aplica regra quando vazio | regra pode preencher |

### Status das tasks originais

- Task 2.1: **feito**
- Task 2.2: **feito**
- Task 2.3: **parcial**
  - faltam testes explícitos para cada proteção por campo
  - falta regressão mais clara para fluxo manual + regra implícita

---

## Fase 3 — Separação UI: Regras Manuais vs Automáticas

> Objetivo: organizar regras em abas separadas. System rules seguem read-only.

### Status

- **Concluído**

### O que já foi feito

- GraphQL aceita `transactionRules(isSystem: Boolean, search, first, after)`
- backend retorna:
  - `null` = todas
  - `false` = apenas user rules
  - `true` = apenas system rules
- repositório possui query dedicada para system rules
- system rules não podem ser editadas ou excluídas
- UI já possui abas separadas para manuais e automáticas
- ações ficam ocultas na aba automática

### Status das tasks originais

- Task 3.1: **feito**
- Task 3.2: **feito**
- Task 3.3: **feito**
- Task 3.4: **feito**
  - existe cobertura de resolver/service para filtro e guards

---

## Fase 4 — Posição de Match na Tela de Regras

> Objetivo: mostrar ao usuário a ordem real de avaliação das regras.

### Status

- **Backend pronto**
- **Frontend e decisão de produto adiados**

### O que já foi feito

- `TransactionRule.position` existe no schema
- `TransactionRuleDTO` carrega `position`
- resolver calcula posição 1-based a partir da lista ordenada
- reorder devolve posições reindexadas
- algoritmo de matching está documentado no engine

### O que ainda não foi feito

- frontend não consome `position`
- tela ainda mostra `priority`, não a ordem real de avaliação
- mutation de reorder existe, mas a experiência ainda não foi fechada

### Decisão atual

- **não fechar agora**
- manter como tópico para exploração posterior:
  - diferença entre `priority` e `position`
  - se a tela deve mostrar ambos ou apenas `position`
  - se reorder continuará sendo por prioridade explícita ou por ordem visual

### Status das tasks originais

- Task 4.1: **parcial**
  - backend feito
  - frontend pendente
- Task 4.2: **feito**
- Task 4.3: **parcial**
  - resolver cobre cálculo básico
  - falta cobertura mais direta de consistência ponta a ponta

---

## Escopo Para Fechar Agora

### 1. Atualizar a documentação para refletir o sistema real

**Status:** concluído com esta revisão

Objetivo:

- remover tarefas já absorvidas pelo código
- marcar o que está concluído, parcial e adiado
- alinhar o documento com o schema e os fluxos atuais

### 2. Dar transparência sobre regras aplicadas na UI de transações

**Status:** pendente

Implementar:

- adicionar uma ação no menu `MoreVert` do item de transação quando houver regra aplicada
- abrir um detalhe com:
  - regra(s) aplicada(s)
  - tipo da regra
  - categoria/merchant/tags/descrição que foram contribuídos
- componente visual a decidir durante implementação:
  - `Dialog`
  - `Drawer`

Critérios de aceite:

- usuário consegue descobrir que a transação foi enriquecida por regra
- quando existir mais de uma regra contribuinte, todas são listadas
- quando uma regra referenciada não existir mais, a UI trata sem quebrar

### 3. Fechar cobertura de testes do refactor

**Status:** pendente

Prioridade:

1. `TransactionServiceTest`
2. `ImportProcessorTest`
3. `TransactionResolverTest`
4. testes web da tela de regras / transparência de regras aplicadas

Coberturas faltantes mais importantes:

- manual com categoria preenchida não sobrescreve
- manual com tags preenchidas não sobrescreve
- manual sem categoria/tags recebe sugestão
- import persiste `appliedRuleIds`
- múltiplas regras contribuindo campos diferentes
- `appliedRuleIds` / `appliedRules` expostos corretamente em GraphQL

Observação:

- antes de encerrar a fase, a suíte de testes do módulo banking precisa voltar a compilar de forma estável
- hoje há testes quebrados e imports desatualizados fora deste escopo direto, então pode haver uma pequena etapa de saneamento do módulo de testes

---

## Fora do Escopo Por Agora

### Explorar melhor `position`

Deixar para uma iteração posterior:

- sem mexer agora na apresentação da ordem de match na tela
- sem fechar ainda a UX de reorder orientada por posição
- sem alterar o texto de ajuda para diferenciar formalmente `priority` vs `position`

---

## Plano de Execução Recomendado

### Agora

1. Implementar visualização de regras aplicadas no menu de transação
2. Atualizar/ajustar testes de backend ligados ao refactor
3. Validar ponta a ponta criação manual + import + leitura GraphQL

### Depois

1. Explorar o papel de `position` na UI
2. decidir exibição de `priority`, `position` ou ambos
3. avaliar reorder visual e semântica final

---

## Critério de Encerramento da Fase

Esta fase poderá ser considerada encerrada quando:

- o usuário conseguir identificar regras aplicadas em uma transação
- a suíte relevante do backend estiver estável para os cenários de rules
- não houver divergência entre documentação, schema e comportamento real

Até lá, o status adequado é:

- **backend majoritariamente concluído**
- **frontend de transparência e testes ainda pendentes**
