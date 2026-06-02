# Agent Skills — Implementation Spec

> **Para o agente implementador:** Este documento contém tudo que você precisa para criar a estrutura completa de Agent Skills do projeto Invistus. Não questione as decisões arquiteturais listadas na seção 2 — elas já foram tomadas. Siga o checklist em ordem.

---

## 1. Objetivo

Implementar a estrutura de [Agent Skills](https://agentskills.io) em `.claude/skills/`, seguindo o padrão aberto do mercado. As skills fornecem conhecimento específico do projeto para agentes de IA (Claude Code, TRAE, Codex, Gemini) via progressive disclosure — o agente carrega apenas `name + description` no startup e o conteúdo completo somente quando relevante.

O resultado final habilita:
- Claude Code a auto-descobrir e auto-ativar skills por contexto
- Outros agentes (TRAE, Codex, Gemini) a consumir as skills via referência explícita nos spec files

---

## 2. Decisões Arquiteturais (não re-questionar)

| Decisão | Valor |
|---|---|
| Localização canônica | `.claude/skills/` dentro do projeto Invistus |
| Padrão | [agentskills.io](https://agentskills.io/specification) — SKILL.md com frontmatter YAML |
| Granularidade técnica | Coarse skills + `references/` para profundidade (não uma skill por camada) |
| Granularidade de domínio | Uma skill `personal-finance/` cobre todos os conceitos (Transaction, Bill, Snapshot) |
| Scripts | Documentar neotool actions existentes — não criar scripts novos |
| Outros agentes | Consomem via referência explícita no spec, não via auto-discovery |
| Symlinks | Não usar — `.claude/skills/` é o canônico direto |

---

## 3. Estrutura Final a Criar

```
invistus/.claude/skills/
├── shared-conventions/
│   ├── SKILL.md
│   └── references/
│       ├── auth-permissions.md
│       ├── error-handling.md
│       └── i18n-strategy.md
│
├── kotlin-service/
│   ├── SKILL.md
│   ├── references/
│   │   ├── services.md
│   │   ├── repo.md
│   │   ├── graphql.md
│   │   └── tests.md
│   ├── examples/
│   │   └── README.md          (ponteiros para features existentes como referência)
│   └── scripts/
│       └── README.md          (neotool/gradle commands disponíveis)
│
├── nextjs-app/
│   ├── SKILL.md
│   ├── references/
│   │   ├── graphql.md
│   │   ├── mutations.md
│   │   ├── styling.md
│   │   └── tests.md
│   └── scripts/
│       └── README.md          (npm/neotool commands disponíveis)
│
└── personal-finance/
    ├── SKILL.md
    └── references/
        ├── transaction.md
        ├── credit-card-bill.md
        └── monthly-summary.md
```

---

## 4. Formato SKILL.md

Todo `SKILL.md` deve seguir este formato:

```markdown
---
name: <nome-da-pasta>        # obrigatório, deve ser igual ao nome do diretório
description: >               # obrigatório, max 1024 chars
  O que a skill faz e QUANDO usar. Seja específico sobre triggers.
  O agente usa isso para decidir quando ativar automaticamente.
paths: glob/pattern/**       # opcional: limita auto-ativação a esses arquivos
disable-model-invocation: true  # opcional: só invocação manual com /nome
allowed-tools: Bash(./gradlew *) # opcional: ferramentas pré-aprovadas
---

# Corpo em Markdown

Instruções concisas. Referências a arquivos em references/:

- Para padrões detalhados de X, leia [references/x.md](references/x.md)
- Para padrões de Y, leia [references/y.md](references/y.md)

## Regras não-negociáveis
(gotchas e regras que o agente erraria sem ser informado)
```

**Constraints:**
- `name` deve ser idêntico ao nome do diretório pai
- Corpo do `SKILL.md`: máximo 500 linhas / 5000 tokens
- Detalhe vai em `references/`, não no corpo principal
- Instrua o agente QUANDO carregar cada reference file (não "veja references/ para detalhes")

---

## 5. Checklist de Implementação

Execute em ordem. Marque cada item ao concluir.

### Setup
- [ ] Verificar que `.claude/skills/` existe em `invistus/` (criar se não existir)
- [ ] Verificar que `.claude/settings.local.json` não tem regras que bloqueiem skills

### Skill 1 — shared-conventions
- [ ] Criar `shared-conventions/SKILL.md` (ver seção 6.1)
- [ ] Criar `shared-conventions/references/auth-permissions.md` (ver seção 6.2)
- [ ] Criar `shared-conventions/references/error-handling.md` (ver seção 6.3)
- [ ] Criar `shared-conventions/references/i18n-strategy.md` (ver seção 6.4)

### Skill 2 — kotlin-service
- [ ] Criar `kotlin-service/SKILL.md` (ver seção 7.1)
- [ ] Criar `kotlin-service/references/services.md` (ver seção 7.2)
- [ ] Criar `kotlin-service/references/repo.md` (ver seção 7.3)
- [ ] Criar `kotlin-service/references/graphql.md` (ver seção 7.4)
- [ ] Criar `kotlin-service/references/tests.md` (ver seção 7.5)
- [ ] Criar `kotlin-service/examples/README.md` (ver seção 7.6)
- [ ] Criar `kotlin-service/scripts/README.md` (ver seção 7.7)

### Skill 3 — nextjs-app
- [ ] Criar `nextjs-app/SKILL.md` (ver seção 8.1)
- [ ] Criar `nextjs-app/references/graphql.md` (ver seção 8.2)
- [ ] Criar `nextjs-app/references/mutations.md` (ver seção 8.3)
- [ ] Criar `nextjs-app/references/styling.md` (ver seção 8.4)
- [ ] Criar `nextjs-app/references/tests.md` (ver seção 8.5)
- [ ] Criar `nextjs-app/scripts/README.md` (ver seção 8.6)

### Skill 4 — personal-finance
- [ ] Criar `personal-finance/SKILL.md` (ver seção 9.1)
- [ ] Criar `personal-finance/references/transaction.md` (ver seção 9.2)
- [ ] Criar `personal-finance/references/credit-card-bill.md` (ver seção 9.3)
- [ ] Criar `personal-finance/references/monthly-summary.md` (ver seção 9.4)

### Validação
- [ ] Rodar Claude Code e verificar `/skills` lista as 4 skills
- [ ] Testar auto-ativação: abrir arquivo em `service/kotlin/banking/` e perguntar algo técnico — `kotlin-service` deve ativar
- [ ] Testar auto-ativação: abrir arquivo em `web/src/features/personal-finance/` — `nextjs-app` e `personal-finance` devem ativar
- [ ] Confirmar que descriptions são claras o suficiente (ver critérios na seção 10)

---

## 6. Conteúdo — shared-conventions

### 6.1 SKILL.md

```markdown
---
name: shared-conventions
description: >
  Convenções cross-cutting que se aplicam a TODO desenvolvimento no Invistus,
  independente de ser backend ou frontend. Cobre: fronteiras de segurança
  (auth enforcement), propagação de erros, estratégia de i18n, e padrões
  que cruzam as camadas. Use quando a questão envolve auth/permissões,
  tratamento de erros cross-layer, ou internacionalização.
---

# Shared Conventions — Invistus

Convenções que se aplicam a backend E frontend. Carregue os arquivos de referência
conforme necessário:

- Dúvidas sobre auth, permissões, ou segurança → leia [references/auth-permissions.md](references/auth-permissions.md)
- Dúvidas sobre tratamento de erros ou feedback ao usuário → leia [references/error-handling.md](references/error-handling.md)
- Dúvidas sobre i18n, strings, ou localização → leia [references/i18n-strategy.md](references/i18n-strategy.md)

## Regra fundamental

**Frontend é UX apenas. Backend é autoridade.**
Nunca tome decisões de segurança no frontend. Nunca assuma que uma verificação
de permissão no frontend substitui a do backend.
```

### 6.2 references/auth-permissions.md

```markdown
# Auth & Permissions

## Fronteira de segurança

Frontend permission checks são **somente para esconder UI**. O backend
enforça tudo. Nunca salte uma chamada ao backend baseado em check de frontend.

```typescript
// ✅ Correto — esconde elemento de UI
{hasPermission('banking:transaction:edit') && <EditButton />}

// ❌ Nunca — bypass de lógica baseado em permissão frontend
if (!hasPermission('banking:transaction:edit')) return;
```

## Regra de ouro

Se o usuário manipular o frontend para esconder o check de permissão,
a operação AINDA DEVE falhar no backend. O frontend é conveniente, não seguro.
```

### 6.3 references/error-handling.md

```markdown
# Error Handling

## Frontend: sempre mostrar toast

Toda mutation DEVE mostrar feedback ao usuário — sucesso e erro.

```typescript
import { useToast } from '@/shared/hooks/useToast';

const { showSuccess, showError } = useToast();

try {
  await executeMutation(...);
  showSuccess('Operação realizada com sucesso');
} catch (error) {
  showError(extractErrorMessage(error));
}
```

## XSS — nunca usar dangerouslySetInnerHTML sem sanitização

```typescript
// ❌ PROIBIDO
<div dangerouslySetInnerHTML={{ __html: content }} />

// ✅ Se HTML for inevitável
import DOMPurify from 'isomorphic-dompurify';
<div dangerouslySetInnerHTML={{ __html: DOMPurify.sanitize(content) }} />
```

## Backend: erros não devem vazar detalhes internos

Mensagens de erro no backend não devem expor stack traces, queries SQL,
ou detalhes de implementação para o cliente.
```

### 6.4 references/i18n-strategy.md

```markdown
# i18n Strategy

## Frontend

Usar `next-intl` para todas as strings visíveis ao usuário.
**Strings hardcoded em JSX são proibidas** — mesmo pt-BR.

```typescript
// ❌ PROIBIDO
<Button>Salvar</Button>
<Typography>Erro ao carregar</Typography>

// ✅ Correto
import { useTranslations } from 'next-intl';
const t = useTranslations('common');
<Button>{t('save')}</Button>
```

## Checklist antes de finalizar qualquer componente

- [ ] Nenhuma string pt-BR/en hardcoded em JSX
- [ ] Todas as strings passam por `t('key')`
- [ ] Chaves adicionadas aos arquivos de mensagem (pt-BR e en)

## Localização dos arquivos de mensagem

`web/messages/` — arquivos por locale (pt-BR.json, en.json)
```

---

## 7. Conteúdo — kotlin-service

### 7.1 SKILL.md

```markdown
---
name: kotlin-service
description: >
  Padrões de implementação para serviços Kotlin/Micronaut no Invistus.
  Use ao implementar qualquer coisa no backend: domain services, repositories,
  GraphQL resolvers, Flyway migrations, ou testes. Aplica-se ao banking service
  e a qualquer futuro serviço Kotlin/Micronaut no projeto.
  Trigger: editar arquivos em service/kotlin/, criar novos endpoints GraphQL,
  implementar regras de negócio no backend, ou escrever testes de integração.
paths: service/kotlin/**
allowed-tools: Bash(./gradlew *)
---

# Kotlin Service Patterns — Invistus

Stack: **Kotlin + Micronaut + JPA + GraphQL + Flyway + PostgreSQL**

Carregue os arquivos de referência conforme a tarefa:

- Implementando domain service ou use case → [references/services.md](references/services.md)
- Implementando repository ou query SQL → [references/repo.md](references/repo.md)
- Mapeando tipos GraphQL ou implementando resolver → [references/graphql.md](references/graphql.md)
- Escrevendo testes unit ou de integração → [references/tests.md](references/tests.md)
- Consultando exemplos de features completas → [examples/README.md](examples/README.md)
- Consultando comandos disponíveis → [scripts/README.md](scripts/README.md)

## Regras não-negociáveis

1. **Clean Architecture é obrigatória**: domain nunca depende de infraestrutura
2. **Entities não vazam para GraphQL**: use DTOs em `graphql/dto/`
3. **Micronaut, não Spring**: `@Singleton` (não `@Service`), `@MicronautTest` (não `@SpringBootTest`)
4. **UUIDs como IDs**: todos os aggregates usam UUID como primary key
5. **Soft delete**: queries SEMPRE incluem `deleted_at IS NULL` onde aplicável

## Estrutura de pacotes

```
io.github.salomax.neotool.banking
├── domain/
│   └── [aggregate]/         # interfaces + data classes (zero deps de infra)
├── model/
│   └── [aggregate]/         # @Entity JPA + @Embeddable + Mappers
├── repo/
│   └── Default*.kt          # implementações de repositório
│   └── *EntityRepository.kt # interfaces JPA
├── graphql/
│   ├── dto/                 # tipos de entrada/saída GraphQL
│   └── resolver/            # resolvers
└── infrastructure/          # jobs, schedulers, adapters externos
```
```

### 7.2 references/services.md

```markdown
# Services — Kotlin/Micronaut

## Padrão Clean Architecture

Cada aggregate segue a sequência:
1. Interface em `domain/[aggregate]/[Name]Repository.kt`
2. Implementação em `repo/Default[Name]Repository.kt`
3. Service em `domain/[aggregate]/[Name]Service.kt` (se necessário)

## Anotações Micronaut

```kotlin
@Singleton          // equivalente ao @Service do Spring
@Transactional      // jakarta.transaction.Transactional
@Inject             // jakarta.inject.Inject
```

## Padrão de implementação de service

```kotlin
@Singleton
class DefaultMonthlyBalanceSnapshotRepository(
    private val entityRepository: MonthlyBalanceSnapshotEntityRepository,
    private val mapper: MonthlyBalanceSnapshotMapper,
) : MonthlyBalanceSnapshotRepository {

    override fun save(snapshot: MonthlyBalanceSnapshot): MonthlyBalanceSnapshot {
        val entity = mapper.toEntity(snapshot)
        return mapper.toDomain(entityRepository.save(entity))
    }
}
```

## Exemplos de services existentes

- `SnapshotComputationService` — recomputeSnapshot, recomputeChain
- `MonthSummaryQueryService` — read path com healing de snapshots dirty
- `CreditCardBillService` — cycle arithmetic + findOrCreate + recompute
- `TransactionService` — CRUD + orquestração de recomputes
```

### 7.3 references/repo.md

```markdown
# Repositories — JPA / SQL

## Interface JPA base

```kotlin
@Repository
interface MonthlyBalanceSnapshotEntityRepository
    : JpaRepository<MonthlyBalanceSnapshotEntity, MonthlyBalanceSnapshotId>
```

## Native queries

Usar `@Query(nativeQuery = true)` para SQL complexo ou UPSERT:

```kotlin
@Query(
    value = """
        INSERT INTO banking.monthly_balance_snapshots (...)
        VALUES (...)
        ON CONFLICT (financial_account_id, year, month)
        DO UPDATE SET
            closing_balance = EXCLUDED.closing_balance,
            is_dirty = false,
            updated_at = NOW()
    """,
    nativeQuery = true
)
fun upsert(...): Unit
```

## insertIfAbsent

Para criação idempotente (sem throw em duplicata):

```kotlin
@Query(
    value = "INSERT INTO ... ON CONFLICT DO NOTHING",
    nativeQuery = true
)
fun insertIfAbsent(...): Unit
```

## Gotchas

- Composite keys usam `@Embeddable` + `@EmbeddedId` (ver `MonthlyBalanceSnapshotId`)
- Migrations em `src/main/resources/db/migration/V0_N__description.sql`
- Naming das migrations: `V0_<próximo_número>__<snake_case_description>.sql`
- Schema PostgreSQL: `banking.*` (todas as tabelas no schema banking)
- Sempre filtrar `deleted_at IS NULL` em queries de transações
```

### 7.4 references/graphql.md

```markdown
# GraphQL — Micronaut / Banking Service

## Wiring

Todos os resolvers são registrados em `BankingWiringFactory`.
Ao adicionar novo resolver ou tipo, registrar lá.

## Estrutura

```
graphql/
├── dto/
│   ├── MonthSummaryDTO.kt      # tipos de saída
│   └── TransactionInputDTO.kt  # tipos de entrada (mutations)
└── resolver/
    └── TransactionResolver.kt  # queries e mutations
```

## Schema

Localização: `service/kotlin/banking/src/main/resources/schema.graphqls`

Toda alteração de schema (novo tipo, novo campo, nova query/mutation)
deve ser feita neste arquivo antes da implementação.

## Padrão de resolver

```kotlin
fun monthSummary(
    financialAccountId: UUID,
    year: Int?,
    month: Int?
): MonthSummaryDTO? {
    val summary = monthSummaryQueryService.getMonthSummary(
        financialAccountId, year, month
    )
    return summary?.let { MonthSummaryDTO.from(it) }
}
```

## Tipos escalares customizados

- `Date` → LocalDate
- `DateTime` → OffsetDateTime
- `UUID` → java.util.UUID

## Gotcha

GraphQL schema usa `ID!` para UUIDs de entidades. No Kotlin, esses chegam como `String`
no resolver e devem ser convertidos: `UUID.fromString(financialAccountId)`.
```

### 7.5 references/tests.md

```markdown
# Tests — Kotlin/Micronaut

## Stack de testes

- **JUnit 5** — framework base
- **Mockito-Kotlin** — mocks (`mock()`, `whenever()`, `verify()`)
- **AssertJ** — assertions (`assertThat(...).isEqualTo(...)`)
- **@MicronautTest** — injeção de dependências nos testes

## Unit tests

```kotlin
@MicronautTest
class SnapshotComputationServiceTest {

    @MockBean(MonthlyBalanceSnapshotRepository::class)
    fun snapshotRepository(): MonthlyBalanceSnapshotRepository = mock()

    @Inject
    lateinit var service: SnapshotComputationService

    @Test
    fun `should recompute closing balance correctly`() {
        // arrange
        whenever(snapshotRepository.findByMonth(any())).thenReturn(...)

        // act
        val result = service.recomputeSnapshot(...)

        // assert
        assertThat(result.closingBalance).isEqualByComparingTo(BigDecimal("100.00"))
    }
}
```

## Integration tests

- Estendem `BankingPostgresIntegrationTest`
- Requerem Docker (PostgreSQL sobe automaticamente via Testcontainers)
- Testam o fluxo completo: HTTP → service → banco

## Comandos

```bash
# Unit tests (sem Docker)
./gradlew :banking:testUnit

# Integration tests (requer Docker)
./gradlew :banking:testIntegration
```

## Gotchas

- Testes de integração são lentos — não rodar no loop de desenvolvimento
- `@MicronautTest` inicia o contexto completo — preferir unit tests com mocks quando possível
- Para mocks de `@Repository`, usar `@MockBean` (não `@Mock`)
```

### 7.6 examples/README.md

```markdown
# Exemplos de Referência — kotlin-service

Features completamente implementadas que servem como referência canônica
para novos desenvolvimentos. Leia o código dessas features antes de implementar
algo similar.

## Credit Card Bills (referência primária)

Feature completa de fatura de cartão de crédito. Cobre todo o ciclo:
domain → infra → GraphQL → testes.

Arquivos-chave:
- Domain: `domain/transaction/CreditCardBill.kt`
- Domain interface: `domain/transaction/CreditCardBillRepository.kt`
- Service: `domain/transaction/CreditCardBillService.kt`
- Entity: `model/transaction/CreditCardBillEntity.kt`
- Mapper: `model/transaction/CreditCardBillMapper.kt`
- Repo (JPA): `repo/CreditCardBillEntityRepository.kt`
- Repo (impl): `repo/DefaultCreditCardBillRepository.kt`
- GraphQL DTO: `graphql/dto/CreditCardBillDTO.kt`
- Resolver: método em `graphql/resolver/TransactionResolver.kt`
- Migration: `V0_5__create_credit_card_bills.sql`

## Monthly Balance Snapshots

Feature de snapshot mensal. Mais complexa — inclui job agendado e healing.

Arquivos-chave:
- Domain: `domain/transaction/MonthlyBalanceSnapshot.kt`
- Service de leitura: `domain/transaction/MonthSummaryQueryService.kt`
- Service de cálculo: `domain/transaction/SnapshotComputationService.kt`
- Job: `infrastructure/snapshot/MonthlySnapshotSweeperJob.kt`
- Migration: `V0_11__create_monthly_balance_snapshots.sql`
```

### 7.7 scripts/README.md

```markdown
# Scripts & Commands — kotlin-service

## Gradle (banking service)

Rodar a partir de `service/kotlin/`:

```bash
# Compilar
./gradlew :banking:build

# Unit tests (sem Docker, rápido)
./gradlew :banking:testUnit

# Integration tests (requer Docker)
./gradlew :banking:testIntegration

# Rodar todos os testes
./gradlew :banking:test

# Check completo (build + test + lint)
./gradlew :banking:check
```

## neotool

Consultar `invistus/neotool` para comandos adicionais disponíveis.
```

---

## 8. Conteúdo — nextjs-app

### 8.1 SKILL.md

```markdown
---
name: nextjs-app
description: >
  Padrões de implementação para features frontend no Invistus.
  Use ao construir componentes React, páginas, hooks, ou qualquer feature
  de frontend — web ou mobile. Cobre: integração Apollo/GraphQL com tipos
  gerados, mutations com feedback obrigatório, MUI theme tokens, i18n com
  next-intl, e testes Vitest/Playwright.
  Trigger: criar componentes, implementar páginas, adicionar queries/mutations,
  estilizar com MUI, ou escrever testes de frontend.
paths: web/src/**, mobile/src/**
---

# Next.js App Patterns — Invistus

Stack: **Next.js 14+ App Router + TypeScript + Apollo Client + MUI v5 + Zustand + next-intl**

Carregue os arquivos de referência conforme a tarefa:

- Buscando dados com Apollo/GraphQL → [references/graphql.md](references/graphql.md)
- Executando mutations (create/update/delete) → [references/mutations.md](references/mutations.md)
- Estilizando componentes com MUI → [references/styling.md](references/styling.md)
- Escrevendo testes → [references/tests.md](references/tests.md)
- Consultando comandos disponíveis → [scripts/README.md](scripts/README.md)

## Regras não-negociáveis

1. **Zero cores hardcoded** — apenas tokens do MUI theme (ver references/styling.md)
2. **Tipos gerados obrigatórios** — importar de `@/lib/graphql/operations/*.generated.ts`
3. **Mutations usam `useMutationWithRefetch`** — nunca `useMutation` direto
4. **Toast obrigatório** — toda mutation mostra sucesso/erro ao usuário
5. **XSS** — nunca `dangerouslySetInnerHTML` sem DOMPurify
6. **i18n** — zero strings pt-BR/en hardcoded em JSX
7. **TypeScript strict** — sem `any` a não ser que absolutamente inevitável

## Estrutura de diretórios

```
web/src/
  app/                  # Next.js App Router (route groups)
  features/             # módulos por feature
    [feature]/
      components/       # componentes específicos da feature
      hooks/            # hooks da feature
      widgets/          # blocos de UI compostos
  shared/
    components/ui/      # primitivos, forms, patterns, feedback
    hooks/              # hooks compartilhados
  lib/
    graphql/operations/ # tipos gerados (*.generated.ts) — não editar manualmente
  styles/themes/        # design tokens (tokens.ts)

mobile/src/
  features/             # módulos mobile por feature
  shared/               # componentes compartilhados mobile
```

**Regra:** nunca criar arquivos fora dessas pastas.

## Checklist antes de finalizar

- [ ] Zero cores hardcoded
- [ ] Tipos GraphQL gerados (sem `gql` raw)
- [ ] Mutations usam `useMutationWithRefetch`
- [ ] Toasts de sucesso e erro adicionados
- [ ] TypeScript compila (sem `any` não justificado)
- [ ] Strings usam chaves i18n
- [ ] Sem `dangerouslySetInnerHTML` sem DOMPurify
```

### 8.2 references/graphql.md

```markdown
# GraphQL — Apollo Client / Frontend

## Regra fundamental

**Sempre usar tipos gerados.** Nunca escrever `gql` strings em componentes.

```typescript
// ✅ Correto
import { useGetTransactionsQuery } from '@/lib/graphql/operations/banking/queries.generated';

const { data, loading, error } = useGetTransactionsQuery({
  variables: { financialAccountId },
});

// ❌ Proibido
import { gql, useQuery } from '@apollo/client';
const GET_TRANSACTIONS = gql`query { ... }`;
```

## Onde ficam os arquivos gerados

`web/src/lib/graphql/operations/` — organizados por serviço/domínio.
Arquivos `*.generated.ts` são gerados automaticamente — não editar manualmente.

## Paginação Relay-style

Para queries paginadas, configurar `relayStylePagination` no Apollo cache.

```typescript
// apollo-client.ts
const cache = new InMemoryCache({
  typePolicies: {
    Query: {
      fields: {
        transactions: relayStylePagination(['financialAccountId']),
      },
    },
  },
});
```

## Referência de padrões completos

`docs/07-frontend/patterns/graphql-query-pattern.md`
```

### 8.3 references/mutations.md

```markdown
# Mutations — useMutationWithRefetch

## Regra

Toda mutation que afeta dados de lista/tabela DEVE usar `useMutationWithRefetch`.

```typescript
import { useMutationWithRefetch } from '@/shared/hooks/mutations';
import { useToast } from '@/shared/hooks/useToast';

const { showSuccess, showError } = useToast();

const { executeMutation } = useMutationWithRefetch({
  refetchQuery: GetTransactionsDocument,
  refetchVariables: { financialAccountId },
  onRefetch: refetch,
  errorMessage: 'Falha ao atualizar transação',
});

// No handler:
const handleSave = async (data: FormData) => {
  try {
    await executeMutation(UpdateTransactionMutation, { input: data }, uniqueKey);
    showSuccess('Transação atualizada com sucesso');
  } catch (error) {
    showError(extractErrorMessage(error));
  }
};
```

## Por que não usar `useMutation` diretamente?

`useMutationWithRefetch` garante:
1. Refetch automático da query pai após sucesso
2. Deduplicação de chamadas concorrentes via `uniqueKey`
3. Tratamento de erro consistente

## Referência completa

`docs/07-frontend/patterns/mutation-pattern.md`
```

### 8.4 references/styling.md

```markdown
# Styling — MUI Design Tokens

## Regra absoluta: zero cores hardcoded

```typescript
// ❌ PROIBIDO — nunca fazer isso
<Box sx={{ backgroundColor: '#FF5733' }} />
<Box sx={{ color: 'rgba(0,0,0,0.5)' }} />
<Box sx={{ borderColor: 'red' }} />

// ✅ Correto — sempre usar tokens
<Box sx={{ backgroundColor: 'error.main' }} />
<Box sx={{ color: 'text.secondary' }} />
<Box sx={{ borderColor: 'divider' }} />
```

## Tokens disponíveis (MUI padrão)

```
Cores semânticas:    primary.main, secondary.main, error.main, warning.main, success.main
Texto:               text.primary, text.secondary, text.disabled
Background:          background.default, background.paper
Bordas:              divider
```

## Tokens customizados do Invistus

Localização: `web/src/styles/themes/tokens.ts`

Sempre consultar este arquivo antes de criar uma nova variável de cor —
o token necessário provavelmente já existe.

## useTheme para lógica dinâmica

```typescript
import { useTheme } from '@mui/material';

const theme = useTheme();
const color = theme.palette.primary.main; // para uso fora do sx prop
```

## Referência completa

`docs/07-frontend/patterns/styling-pattern.md`
```

### 8.5 references/tests.md

```markdown
# Tests — Frontend

## Stack

- **Vitest** — testes unitários de componentes e hooks
- **Playwright** — testes E2E
- **Mobile** — React Native com Expo (Vitest para unit)

## Unit tests com Vitest

```typescript
import { renderHook } from '@testing-library/react';
import { useTransactionSummary } from './useTransactionSummary';

describe('useTransactionSummary', () => {
  it('should calculate total correctly', () => {
    const { result } = renderHook(() => useTransactionSummary(mockTransactions));
    expect(result.current.total).toBe(150);
  });
});
```

## E2E com Playwright

Testes E2E ficam em `web/e2e/` ou `web/tests/`.
Consultar `docs/07-frontend/patterns/` para padrões de teste E2E estabelecidos.

## Gotcha

- Não mockar Apollo Client nos testes unitários — usar `MockedProvider`
- Para componentes com i18n, wrapping em `NextIntlClientProvider` é necessário
```

### 8.6 scripts/README.md

```markdown
# Scripts & Commands — nextjs-app

## Web

```bash
# Dev server
npm run dev          # ou yarn dev

# Build
npm run build

# Type check
npm run type-check   # ou tsc --noEmit

# Lint
npm run lint

# Testes unitários
npm run test         # Vitest

# Testes E2E
npm run test:e2e     # Playwright
```

## Codegen (tipos GraphQL)

```bash
npm run codegen      # gera *.generated.ts a partir do schema
```

Rodar após qualquer alteração no schema GraphQL do backend.

## neotool

Consultar `invistus/neotool` para comandos adicionais disponíveis.
```

---

## 9. Conteúdo — personal-finance

### 9.1 SKILL.md

```markdown
---
name: personal-finance
description: >
  Conhecimento de domínio do módulo Personal Finance do Invistus.
  Use ao trabalhar com transações financeiras, faturas de cartão de crédito,
  resumos mensais, ou qualquer feature de finanças pessoais — seja no backend
  (banking service) ou no frontend (web/mobile).
  Trigger: mencionar Transaction, CreditCardBill, MonthSummary, fatura,
  lançamento financeiro, saldo, ou trabalhar em arquivos de personal-finance.
paths: web/src/features/personal-finance/**, mobile/src/features/personal-finance/**, service/kotlin/banking/**
---

# Personal Finance — Domain Knowledge

Conhecimento de negócio do módulo principal do Invistus.

Carregue os arquivos de referência conforme o conceito em questão:

- Trabalhando com transações (Transaction) → [references/transaction.md](references/transaction.md)
- Trabalhando com faturas (CreditCardBill) → [references/credit-card-bill.md](references/credit-card-bill.md)
- Trabalhando com resumos mensais (MonthSummary/Snapshot) → [references/monthly-summary.md](references/monthly-summary.md)

## Visão geral do domínio

O módulo Personal Finance gira em torno de três aggregates principais:

- **Transaction** — lançamento financeiro individual (crédito ou débito)
- **CreditCardBill** — fatura mensal de cartão de crédito (agrupa transactions)
- **MonthSummary** — resumo mensal por conta (snapshot calculado)

**Relações:**
```
FinancialAccount
    ├── Transaction (many)
    │       └── CreditCardBill (optional FK, para cartões)
    └── MonthlyBalanceSnapshot (one per month)
```
```

### 9.2 references/transaction.md

```markdown
# Transaction — Domain Model

## Colunas relevantes (banking.transactions)

| Coluna | Tipo | Valores |
|---|---|---|
| `credit_debit_type` | enum | `CREDIT`, `DEBIT` |
| `status` | enum | `POSTED`, `PENDING`, `FUTURE` |
| `type` | enum | `TRANSFER`, `BALANCE_ADJUSTMENT`, `SALARY`, `CREDIT_CARD`, `CHARGE_CARD`, outros |
| `financial_account_id` | UUID | FK para financial_accounts |
| `account_id` | UUID | tenant ID |
| `deleted_at` | timestamp | NULL = não deletado (soft delete) |
| `bill_id` | UUID? | FK para credit_card_bills (apenas CREDIT_CARD e CHARGE_CARD) |

## Regras de negócio

**Soft delete:** queries SEMPRE devem incluir `WHERE deleted_at IS NULL`.

**Atribuição de fatura:**
- Apenas transações com type `CREDIT_CARD` ou `CHARGE_CARD` recebem `bill_id`
- Transações `BALANCE_ADJUSTMENT` NUNCA são atribuídas a uma fatura

**Status e cálculos:**
- `POSTED` e `PENDING` entram nos cálculos de saldo
- `FUTURE` é excluído dos cálculos de saldo (mas visível na listagem)

**Cálculos de totais:**
- `totalIncome` e `totalExpenses` excluem tipos `TRANSFER` e `BALANCE_ADJUSTMENT`
- Apenas os outros tipos (SALARY, compras, etc.) entram nesses totais

## Write flow

Ao criar/atualizar/deletar Transaction:
1. `TransactionService.create/update/delete`
2. → `CreditCardBillService.recomputeBill()` (se CREDIT_CARD/CHARGE_CARD)
3. → `SnapshotComputationService.recomputeChain()` (recomputa do mês afetado até hoje)
```

### 9.3 references/credit-card-bill.md

```markdown
# CreditCardBill — Domain Model

## O que é

Representa a fatura mensal de uma conta de cartão de crédito.
Agrupa as transações do ciclo de faturamento.

## Colunas relevantes (banking.credit_card_bills)

| Coluna | Tipo | Descrição |
|---|---|---|
| `financial_account_id` | UUID | conta do cartão |
| `closing_date` | date | data de fechamento da fatura |
| `due_date` | date | data de vencimento |
| `total_amount` | decimal | total da fatura |
| `status` | enum | `OPEN`, `CLOSED`, `PAID`, `OVERDUE` |

**Constraint unique:** `uq_credit_card_bills_account_closing` em `(financial_account_id, closing_date)`

## Aritmética do ciclo

```
computeClosingDate(transactionDate, closingDay):
  se dia da transação <= closingDay → fecha no closingDay do mês atual
  caso contrário → fecha no closingDay do próximo mês

computeDueDate(closingDate, dueDay):
  primeiro dueDay após closingDate
```

## Cálculo do total

```sql
totalAmount = SUM(DEBIT) - SUM(CREDIT)
FROM transactions
WHERE bill_id = :billId
  AND deleted_at IS NULL
```

## Regras de negócio

- Fatura é criada automaticamente na primeira transação do ciclo (`findOrCreate`)
- `ON CONFLICT DO NOTHING` garante idempotência — nunca duplica
- `BALANCE_ADJUSTMENT` nunca é atribuído a uma fatura
- `TransactionMapper.updateEntity` deve incluir `entity.billId = domain.billId`

## Write flow

Após qualquer mutation em Transaction (CREDIT_CARD/CHARGE_CARD):
`CreditCardBillService.recomputeBill(billId)` → atualiza `total_amount` e `status`
```

### 9.4 references/monthly-summary.md

```markdown
# MonthlyBalanceSnapshot — Domain Model

## O que é

Snapshot mensal do saldo de uma conta financeira. Pré-calculado e persistido
para performance. Pode ficar "dirty" (desatualizado) e é recomputado on-demand
ou pelo sweeper job.

## Fórmula principal

```
closingBalance = openingBalance + totalCredit - totalDebit
```

onde:
- `openingBalance` = `closingBalance` do mês anterior (ou saldo inicial)
- `totalCredit` / `totalDebit` = soma das transactions com status `POSTED` ou `PENDING`
- Status `FUTURE` é **excluído** dos cálculos
- Tipos `TRANSFER` e `BALANCE_ADJUSTMENT` são **excluídos** de `totalIncome`/`totalExpenses`

## Campos principais

| Campo | Descrição |
|---|---|
| `openingBalance` | saldo no início do mês |
| `closingBalance` | saldo no fim do mês |
| `totalCredit` | soma de créditos do mês |
| `totalDebit` | soma de débitos do mês |
| `totalIncome` | créditos excluindo TRANSFER/BALANCE_ADJUSTMENT |
| `totalExpenses` | débitos excluindo TRANSFER/BALANCE_ADJUSTMENT |
| `is_dirty` | true = snapshot desatualizado, precisa recompute |

## Mecanismo de atualização

**Eager (síncrono):** após cada mutation de Transaction →
`SnapshotComputationService.recomputeChain(fromMonth, accountId)`
recomputa o mês afetado e todos os meses subsequentes (efeito cascata em saldo).

**Sweeper (assíncrono):** `MonthlySnapshotSweeperJob` roda a cada PT30M
e recomputa snapshots com `is_dirty = true`. Safety net — não é o caminho principal.

## GraphQL

Query disponível: `monthSummary(financialAccountId: ID!, year: Int, month: Int)`
Omitir year/month retorna o mês atual.

## Gotcha

O sweeper existe como safety net — se a recomputa síncrona falhar silenciosamente,
o sweeper corrige. Não depender do sweeper como caminho principal.
```

---

## 10. Critérios de Aceitação

### Estrutura
- [ ] Todos os 4 diretórios de skill criados em `.claude/skills/`
- [ ] Cada skill tem `SKILL.md` com frontmatter válido (`name` = nome do diretório)
- [ ] Todos os arquivos `references/*.md` criados e referenciados no `SKILL.md` pai
- [ ] `examples/README.md` e `scripts/README.md` criados onde especificado

### Qualidade do conteúdo
- [ ] `SKILL.md` de cada skill tem menos de 500 linhas
- [ ] Cada referência a `references/` diz QUANDO carregar (não genérico "veja references/")
- [ ] Gotchas documentados para as situações em que o agente erraria sem guia
- [ ] Nenhuma duplicação de conteúdo entre skills (DRY)

### Validação funcional (testar com Claude Code)
- [ ] `What skills are available?` → lista as 4 skills
- [ ] Abrir arquivo em `service/kotlin/banking/` + fazer pergunta técnica → `kotlin-service` ativa
- [ ] Abrir arquivo em `web/src/features/personal-finance/` → `nextjs-app` + `personal-finance` ativam
- [ ] `/shared-conventions` invocável manualmente
- [ ] Descriptions são específicas o suficiente para não ativar em contextos errados

---

## 11. Como Outros Agentes Usam as Skills

**TRAE, Codex, Gemini:** não fazem auto-discovery. Consomem via referência explícita
no spec file da feature sendo implementada. Exemplo de como o spec deve referenciar:

```markdown
## Context for workers
Antes de implementar, leia:
- `.claude/skills/nextjs-app/SKILL.md`
- `.claude/skills/nextjs-app/references/mutations.md`
- `.claude/skills/personal-finance/references/credit-card-bill.md`
```

O implementador das skills **não precisa configurar nada adicional** para os outros agentes —
isso é responsabilidade do spec de cada feature.

---

## 12. Referências

- [Agent Skills Specification](https://agentskills.io/specification)
- [Agent Skills Best Practices](https://agentskills.io/skill-creation/best-practices)
- [Claude Code Skills Docs](https://code.claude.com/docs/en/skills)
- [Exemplos de referência (Anthropic)](https://github.com/anthropics/skills)
- Conteúdo fonte: `invistus/.trae/rules/project_rules.md`
- Domínio fonte: `invistus/docs/03-features/personal-finance/`
