# Institution Data Restriction – Operations

SQL templates for managing institution financial data restrictions. Restrictions are managed directly via database (no admin API).

**Table**: `financial_data.institution_data_restriction`

## Add restriction

```sql
INSERT INTO financial_data.institution_data_restriction
    (bacen_cod_inst, reason, notes, created_by)
VALUES
    ('12345678', 'INSTITUTION_REQUEST', 'Requested via email on 2024-01-15, ticket #ABC-123', 'admin@invistus.com');
```

## Remove restriction (soft delete)

```sql
UPDATE financial_data.institution_data_restriction
SET is_active = FALSE, updated_at = now()
WHERE bacen_cod_inst = '12345678';
```

## List active restrictions

```sql
SELECT bacen_cod_inst, reason, notes, created_at, created_by
FROM financial_data.institution_data_restriction
WHERE is_active = TRUE
ORDER BY created_at DESC;
```

## Notes

- `bacen_cod_inst` must exist in `financial_data.bacen_institution` (or the institution must be loadable by the application).
- Valid `reason` values: `INSTITUTION_REQUEST` (extensible via migration if new reasons are added).
- After adding or removing a restriction, the change takes effect on the next API request (no cache invalidation).
