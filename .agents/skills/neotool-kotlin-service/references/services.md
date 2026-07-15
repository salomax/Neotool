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

## Padrão de implementação de repository

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
