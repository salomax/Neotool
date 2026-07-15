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
