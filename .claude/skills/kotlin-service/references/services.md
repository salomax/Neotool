# Services - Kotlin/Micronaut

## Padrão de Camadas

No banking service, o domínio fica em `domain/*`, a orquestração de casos de uso
fica em `service/*`, persistência fica em `repo/*` e `model/*`, e delivery fica
em `graphql/*` ou `rest/*`.

Fluxo típico de aggregate:

1. Modelo e portas em `domain/[aggregate]/`.
2. Service em `service/[aggregate]/` quando houver orquestração.
3. Entidade e mapper em `model/[aggregate]/`.
4. Repository interface/impl em `repo/[aggregate]/`.
5. DTO/resolver em `graphql/[aggregate]/`.

## Anotações Micronaut

```kotlin
@Singleton
@Transactional
@Inject
```

Use imports de `jakarta.inject` e `jakarta.transaction` quando aplicável.

## Padrão de Implementação

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

## Exemplos Existentes

- `service/transaction/SnapshotComputationService.kt`: recompute de snapshots e cadeia mensal.
- `service/transaction/MonthSummaryQueryService.kt`: read path com healing de snapshots dirty.
- `service/transaction/CreditCardBillService.kt`: cycle arithmetic, find-or-create e recompute.
- `service/transaction/TransactionService.kt`: CRUD, regras e orquestração de side effects.
- `service/investment/*`: lifecycle orchestration para investimentos.

## Gotchas

- Não coloque validação de tenant/account scope só no resolver; a camada de service
  ou adapter de segurança precisa proteger o caso de uso.
- Evite transações longas envolvendo chamadas externas.
- Se uma mutation altera saldo, fatura ou snapshot, procure os side effects
  existentes antes de adicionar um novo caminho.
