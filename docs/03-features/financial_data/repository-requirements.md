# Repository Layer Requirements - Financial Data

## Overview

This document defines the requirements for refactoring the repository layer in the `financial_data` service to implement custom queries following the pattern established in `UserRepositoryCustom` and its implementation.

## Reference Pattern

The implementation should follow the pattern used in:
- `UserRepositoryCustom` interface
- `UserRepositoryImpl` implementation
- `SortingHelpers` for building ORDER BY clauses and cursor predicates
- `InstitutionOrderBy` and `InstitutionOrderField` for sorting specifications

## Search Query Requirements

### Filter Institutions by CNPJ and Name (Full Text Search)

**Query Method**: `searchByCnpjOrName`

**Search Fields**:
The search should perform case-insensitive partial matching on the following fields:
- `parent_institution.trade_name`
- `parent_institution.legal_name`
- `institution.nome_instituicao`
- `institution.bacen_cod_inst` (only if it does NOT start with `C`)

**Search Logic**:
- When query is null or empty, return all institutions (list behavior)
- When query is provided, perform OR matching across all searchable fields
- Use case-insensitive LIKE matching with `%query%` pattern
- Filter out `bacen_cod_inst` values that start with `C` from the search

**Join Requirements**:
- Must join `institution` table with `parent_institution` table
- Join condition: `institution.cnpj_instituicao_lider = parent_institution.parent_cnpj`
- Use LEFT JOIN to include institutions without parent data

**Return Fields**:
The query must return the following fields for each institution:
- Logo path (`parent_institution.logo_storage_key`)
- Trade name (`parent_institution.trade_name`)
- Legal name (`parent_institution.legal_name`)
- `tipoConsolidacao` (`institution.tipo_consolidacao`)
- Lucro (Profit) - calculated from `bank_report_data`
- Patrimonio (Equity) - calculated from `bank_report_data`
- Crescimento YoY (Year-over-Year Growth) - calculated from `bank_report_data`
- Basiléia (Basel Ratio) - calculated from `bank_report_data`

**Pagination**:
- Must implement Relay pagination using `CompositeCursor`
- Support `first` parameter for page size
- Support `after` cursor for cursor-based pagination
- Support `orderBy` parameter for sorting specifications

**Count Method**: `countByCnpjOrName`
- Must return total count matching the same WHERE clause as `searchByCnpjOrName`
- Should not include pagination logic
- Used for calculating `totalCount` in Relay connections

## Analytic Queries

### General Requirements

All analytic queries must:
- Implement Relay pagination using `CompositeCursor`
- Support `first` parameter for page size
- Support `after` cursor for cursor-based pagination
- Support `orderBy` parameter for sorting specifications
- Return calculated financial metrics

### Financial Metrics Calculation

The following metrics need to be calculated from `bank_report_data`:

#### Lucro (Profit)
- Source: `bank_report_data.saldo` where account/column matches profit indicators
- Calculation: Sum of relevant profit accounts for the latest reporting period
- Data source: Latest `bank_report.ano_mes` for each institution

#### Patrimonio (Equity)
- Source: `bank_report_data.saldo` where account/column matches equity indicators
- Calculation: Sum of relevant equity accounts for the latest reporting period
- Data source: Latest `bank_report.ano_mes` for each institution

#### Crescimento YoY (Year-over-Year Growth)
- Source: Comparison of current period vs same period previous year
- Calculation: `((current_value - previous_year_value) / previous_year_value) * 100`
- Data source: Latest `bank_report.ano_mes` vs same quarter from previous year

#### Basiléia (Basel Ratio)
- Source: Capital adequacy ratio calculation from `bank_report_data`
- Calculation: `(Tier 1 Capital + Tier 2 Capital) / Risk-Weighted Assets`
- Data source: Latest `bank_report.ano_mes` for each institution

### Query Structure

Each analytic query should:
1. Join `institution` with `parent_institution`
2. Join with `bank_report` to get latest report data
3. Join with `bank_report_data` to calculate metrics
4. Apply filters and sorting
5. Apply pagination using cursor-based approach

## Implementation Checklist

### Phase 1: Basic Search Query
- [ ] Create `InstitutionRepositoryCustom` interface
- [ ] Create `InstitutionOrderBy` and `InstitutionOrderField` enums
- [ ] Create `SortingHelpers` for Institution entity
- [ ] Create `InstitutionRepositoryImpl` with `searchByCnpjOrName` method
- [ ] Implement search filter predicate builder
- [ ] Implement JOIN logic between `institution` and `parent_institution`
- [ ] Implement cursor-based pagination
- [ ] Implement `countByCnpjOrName` method
- [ ] Add unit tests for search functionality

### Phase 2: Return DTO
- [ ] Create DTO class for search results with all required fields
- [ ] Map entity results to DTO
- [ ] Include logo path, trade name, legal name, tipoConsolidacao

### Phase 3: Financial Metrics Integration
- [ ] Design query structure for joining with `bank_report` and `bank_report_data`
- [ ] Implement Lucro calculation
- [ ] Implement Patrimonio calculation
- [ ] Implement Crescimento YoY calculation
- [ ] Implement Basiléia calculation
- [ ] Add unit tests for metric calculations

### Phase 4: Analytic Queries
- [ ] Define specific analytic query requirements
- [ ] Implement analytic query methods in `InstitutionRepositoryCustom`
- [ ] Implement Relay pagination for analytic queries
- [ ] Add unit tests for analytic queries

## Database Considerations

### Indexes
Ensure appropriate indexes exist for:
- `institution.cnpj_instituicao_lider` (for JOIN with parent_institution)
- `parent_institution.parent_cnpj` (for JOIN)
- `parent_institution.trade_name` (for search)
- `parent_institution.legal_name` (for search)
- `institution.nome_instituicao` (for search)
- `institution.bacen_cod_inst` (for search and ordering)
- `bank_report.cod_inst` and `bank_report.ano_mes` (for metrics calculation)

### Performance
- Consider using materialized views for complex metric calculations
- Cache frequently accessed metric calculations
- Use appropriate JOIN types (LEFT JOIN vs INNER JOIN) based on requirements

## Testing Requirements

### Unit Tests
- Test search with null/empty query (returns all)
- Test search with query matching trade_name
- Test search with query matching legal_name
- Test search with query matching nome_instituicao
- Test search with query matching bacen_cod_inst (non-C prefix)
- Test search filtering out bacen_cod_inst starting with 'C'
- Test pagination with cursor
- Test count method matches search results
- Test sorting with different orderBy specifications

### Integration Tests
- Test JOIN between institution and parent_institution
- Test metric calculations accuracy
- Test pagination across large datasets
- Test performance with realistic data volumes

## Notes

- The `bacen_cod_inst` field is a String (TEXT in database) to preserve leading zeros
- The relationship between `institution` and `parent_institution` is via `cnpj_instituicao_lider` field
- Financial metrics require joining with `bank_report` and `bank_report_data` tables
- Consider creating a view or materialized view for complex metric calculations if performance becomes an issue

