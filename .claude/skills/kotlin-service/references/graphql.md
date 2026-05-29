# GraphQL - Micronaut / Banking Service

## Wiring

Resolvers são registrados no wiring GraphQL do banking. Ao adicionar novo
resolver, query, mutation ou scalar, procure o wiring em `graphql/config/` e
registre a ligação correspondente.

## Schema

Localização: `service/kotlin/banking/src/main/resources/graphql/schema.graphqls`.

Toda alteração de schema, como novo tipo, campo, query ou mutation, deve ser
feita no schema antes da implementação no resolver. Depois disso, atualize DTOs,
resolvers e rode codegen do frontend quando o contrato for consumido pelo web.

## Estrutura Real

```text
graphql/
├── config/          # wiring e scalars
├── transaction/     # TransactionResolver, DTOs de transaction, bills e summary
├── account/         # contas financeiras
├── investment/      # investimentos
└── ...              # demais domínios
```

## Padrão de Resolver

```kotlin
fun monthSummary(
    financialAccountId: String,
    year: Int?,
    month: Int?,
): MonthSummaryDTO? {
    val summary = monthSummaryQueryService.getMonthSummary(
        UUID.fromString(financialAccountId),
        year,
        month,
    )
    return summary?.let(MonthSummaryDTO::from)
}
```

## Tipos Escalares Customizados

- `Date`: `LocalDate`
- `DateTime`: `OffsetDateTime`
- `UUID`/`ID`: `java.util.UUID` ou `String` convertido no resolver conforme o wiring.

## Gotchas

- GraphQL usa `ID!` para muitos UUIDs. Confirme se o resolver recebe `String`
  ou `UUID` antes de implementar.
- DTO GraphQL não deve expor entidade JPA diretamente.
- Resolver deve ser fino: valida input, chama service e mapeia output.
- Ao mudar schema, atualize os generated types no frontend antes de ajustar uso web.
