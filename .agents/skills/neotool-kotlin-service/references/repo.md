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
