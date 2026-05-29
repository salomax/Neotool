# Repositories - JPA / SQL

## Interface JPA Base

```kotlin
@Repository
interface MonthlyBalanceSnapshotEntityRepository :
    JpaRepository<MonthlyBalanceSnapshotEntity, MonthlyBalanceSnapshotId>
```

## Native Queries

Use `@Query(nativeQuery = true)` para SQL complexo, relatórios, locking ou UPSERT.

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
    nativeQuery = true,
)
fun upsert(...): Unit
```

## Criação Idempotente

Para criação idempotente, prefira `ON CONFLICT DO NOTHING` ou um método
`insertIfAbsent` equivalente. Não use exceção de constraint como fluxo normal.

```kotlin
@Query(
    value = "INSERT INTO ... ON CONFLICT DO NOTHING",
    nativeQuery = true,
)
fun insertIfAbsent(...): Unit
```

## Migrations

- Localização: `service/kotlin/banking/src/main/resources/db/migration/`.
- Naming atual: `V0_<numero>__<snake_case_description>.sql`.
- Antes de criar migration, liste as existentes e use o próximo número.
- Tabelas do banking ficam no schema PostgreSQL `banking`.

## Gotchas

- Composite keys usam `@Embeddable` + `@EmbeddedId`; veja `MonthlyBalanceSnapshotId`.
- Queries de transações devem filtrar `deleted_at IS NULL`.
- Se uma query participa de listagem paginada, preserve ordenação determinística.
- Mappers devem manter campos novos em `toDomain`, `toEntity` e updates parciais.
