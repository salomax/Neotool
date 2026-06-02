# Task: Record First Holding (Fixed Income)

> **Goal**: Allow users to manually record a fixed income position in their investment account, creating the asset inline and recording the initial PURCHASE transaction. The investments page should then display the holding in the portfolio table.
> **Entry point**: The investments page when ≥1 account exists but has no holdings — "Registrar ativo" CTA
> **Backend**: `createInvestmentAsset` mutation is missing — must be added. `recordInvestmentTransaction` already exists.
> **Effort**: Medium-Large

---

## Architectural decisions

### No asset catalog for MVP
There is no seeded asset catalog. Rather than blocking on a B3/CVM data pipeline, we allow the user to describe the fixed income asset inline when recording the first transaction. The asset is created on the fly with just the information they have on hand: name, type, and issuer.

### One holding per (account, asset)
This is a DB-level constraint. For fixed income, each investment contract (e.g. "CDB Banco Inter 120% CDI venc 2027") becomes its own Asset, so quantity=1 and unitPrice=invested amount. This keeps the model consistent without needing lot-level tracking in MVP.

### Two-call flow (asset → transaction)
The drawer calls `createInvestmentAsset` first, then uses the returned `assetId` to call `recordInvestmentTransaction`. The UX is a single form — no multi-step wizard. Failure of either call surfaces an error banner without partial state issues (asset without a transaction is harmless and will be cleaned up in future).

### Fixed income only in this task
Equities require a market-data service for live pricing, which is out of scope. Fixed income is self-contained: the user has all the data (rate, maturity date, invested amount) and does not need live quotes to track it.

---

## Mandatory: Load context before starting

| Doc | Why |
|-----|-----|
| `docs/07-frontend/patterns/i18n-pattern.md` | i18n co-location rules |
| `docs/07-frontend/patterns/styling-pattern.md` | No hardcoded colors rule |
| `docs/07-frontend/patterns/graphql-mutation-pattern.md` | `refetchQueries` with correct variables |
| `docs/07-frontend/patterns/testing-pattern.md` | `data-testid`, `describe.sequential`, `@testing-library/user-event` |
| `docs/07-frontend/patterns/shared-components-pattern.md` | Shared form components reference |
| `docs/94-validation/feature-checklist.md` | Run before marking done |

Also read:
- `web/src/app/personal-finance/investments/page.tsx` — the page being modified
- `web/src/app/personal-finance/investments/components/InvestmentTable.tsx` — the table being wired up
- `web/src/app/personal-finance/investments/components/CreateInvestmentAccountDrawer.tsx` — follow same drawer pattern
- `service/kotlin/banking/.../graphql/resolver/InvestmentResolver.kt` — add the new mutation here
- `service/kotlin/banking/.../domain/investment/AssetRepository.kt` — `save(asset)` already exists

---

## Specification (Gherkin)

```gherkin
Feature: Record First Fixed Income Holding
  As a user
  I want to manually record a fixed income investment
  So that I can see it in my portfolio

  Background:
    Given I am authenticated
    And I am on /personal-finance/investments
    And I have at least one investment account
    And the selected account has no holdings

  Scenario: Empty holdings state shows a record CTA
    When the investments page loads with an account selected
    Then I see an empty state with message "Nenhum ativo registrado"
    And I see a primary button "Registrar ativo"
    And I do NOT see the "Registrar ativo" button when no account is selected (consolidated view)

  Scenario: Open the record fixed income drawer
    When I click "Registrar ativo"
    Then a drawer opens with title "Registrar ativo de Renda Fixa"
    And the form shows all required fields

  Scenario: Record a CDI-indexed CDB
    Given the drawer is open
    When I fill in:
      | Nome do ativo         | CDB Banco Inter 120% CDI |
      | Tipo                  | CDB                       |
      | Emissor               | Banco Inter               |
      | Indexador             | CDI                       |
      | % do indexador        | 120                       |
      | Valor aplicado (R$)   | 10000                     |
      | Data da aplicação     | 2026-03-12                |
      | Data de vencimento    | 2028-03-12                |
      | Isento de IR          | false                     |
    And I click "Registrar"
    Then `createInvestmentAsset` is called with:
      | name       | CDB Banco Inter 120% CDI |
      | assetType  | CDB                      |
      | issuerName | Banco Inter              |
    Then `recordInvestmentTransaction` is called with:
      | financialAccountId  | <selected account id> |
      | assetId             | <returned asset id>   |
      | direction           | INFLOW                |
      | type                | PURCHASE              |
      | quantity            | 1                     |
      | unitPrice           | 10000                 |
      | grossValue          | 10000                 |
      | tradeDate           | 2026-03-12            |
      | settlementDate      | 2026-03-12            |
      | source              | MANUAL                |
      | indexer             | CDI                   |
      | indexerPercentage   | 1.20                  |
      | issueDate           | 2026-03-12            |
      | maturityDate        | 2028-03-12            |
      | isTaxExempt         | false                 |
    And the drawer closes
    And the holdings table shows the new position

  Scenario: Record a pre-fixed LCI (tax-exempt)
    Given the drawer is open
    When I fill in:
      | Tipo      | LCI                   |
      | Indexador | Prefixado             |
      | Taxa (%)  | 13.5                  |
      | Isento de IR | true               |
    Then `recordInvestmentTransaction` is called with:
      | indexer         | PRE_FIXADO |
      | preFixedRate    | 0.135      |
      | isTaxExempt     | true       |
    And the indexer percentage field is hidden when indexer = PRE_FIXADO

  Scenario: Record an IPCA+ Tesouro Direto
    Given the drawer is open
    When I select Tipo = "Tesouro Direto"
    Then the Emissor field auto-fills with "Tesouro Nacional" and becomes read-only

  Scenario: Validation — required fields
    Given the drawer is open
    When I click "Registrar" without filling in any fields
    Then I see validation errors for:
      - Nome do ativo
      - Tipo
      - Indexador
      - Valor aplicado
      - Data da aplicação
      - Data de vencimento

  Scenario: Holdings table populates after recording
    Given I have recorded a holding in the selected account
    When the investments page loads
    Then InvestmentTable calls investmentHoldings(financialAccountId: <selected id>)
    And the table shows a row with the asset name, type, quantity, and gross value

  Scenario: Consolidated view shows all holdings
    Given I have accounts A and B each with holdings
    When I select "Consolidado" (no account selected, selectedAccountId = null)
    Then InvestmentTable calls allInvestmentHoldings()
    And the table shows holdings from all accounts
```

---

## Context & Existing Infrastructure

| File | Purpose |
|------|---------|
| `service/kotlin/banking/.../graphql/schema/investment.graphqls` | Add new mutation + input type here |
| `service/kotlin/banking/.../graphql/resolver/InvestmentResolver.kt` | Add `createInvestmentAsset` resolver method |
| `service/kotlin/banking/.../domain/investment/AssetRepository.kt` | `save(asset)` and `findByTicker` already exist |
| `service/kotlin/banking/.../domain/investment/Asset.kt` | Domain model |
| `service/kotlin/banking/.../domain/investment/InvestmentEnums.kt` | `AssetType`, `Indexer` enums |
| `web/src/lib/graphql/operations/banking/queries.ts` | Add `INVESTMENT_HOLDINGS`, `ALL_INVESTMENT_HOLDINGS` |
| `web/src/lib/graphql/operations/banking/mutations.ts` | Add `CREATE_INVESTMENT_ASSET`, `RECORD_INVESTMENT_TRANSACTION` |
| `web/src/app/personal-finance/investments/page.tsx` | Add empty-holdings state + CTA |
| `web/src/app/personal-finance/investments/components/InvestmentTable.tsx` | Wire holdings query |

### Existing GraphQL resolver methods (InvestmentResolver.kt)

```
QUERIES (already exist):
  investmentHoldings(financialAccountId: ID!): [InvestmentHolding!]!
  allInvestmentHoldings: [InvestmentHolding!]!
  investmentHolding(id: ID!): InvestmentHolding
  investmentTransactions(holdingId: ID!): [InvestmentTransaction!]!
  investmentTransaction(id: ID!): InvestmentTransaction
  investmentAsset(id: ID!): InvestmentAsset

MUTATIONS (already exist):
  recordInvestmentTransaction(input: RecordInvestmentTransactionInput!): InvestmentTransaction!
  deleteInvestmentTransaction(id: ID!): Boolean!

MUTATIONS (missing — add in this task):
  createInvestmentAsset(input: CreateInvestmentAssetInput!): InvestmentAsset!
```

---

## Activities

---

### Activity 1 — Backend: Add `createInvestmentAsset` mutation

**Files to modify**:
- `service/kotlin/banking/src/main/resources/graphql/investment.graphqls` (or wherever the schema lives)
- `service/kotlin/banking/.../graphql/resolver/InvestmentResolver.kt`

#### 1a — GraphQL schema additions

```graphql
input CreateInvestmentAssetInput {
  name: String!
  assetType: AssetType!
  issuerName: String
  # Optional — for future enrichment:
  ticker: String
  isinCode: String
  currencyCode: String  # defaults to BRL
}

type Mutation {
  # ... existing mutations ...
  createInvestmentAsset(input: CreateInvestmentAssetInput!): InvestmentAsset!
}
```

#### 1b — Resolver method

Add to `InvestmentResolver.kt`:

```kotlin
@MutationMapping
fun createInvestmentAsset(
    @Argument input: CreateInvestmentAssetInput,
    @AuthenticationPrincipal principal: SecurityPrincipal,
): InvestmentAssetDTO {
    val asset = Asset(
        id = null,
        ticker = input.ticker,
        isinCode = input.isinCode,
        name = input.name,
        assetType = AssetType.valueOf(input.assetType),
        currencyCode = input.currencyCode ?: "BRL",
        exchange = null,
        issuerName = input.issuerName,
        issuerCnpj = null,
        fundCnpj = null,
        anbimaCategory = null,
        anbimaClass = null,
        anbimaSubclass = null,
        priceFactor = null,
        providerId = null,
    )
    val saved = assetRepository.save(asset)
    return saved.toDTO()
}

// CreateInvestmentAssetInput data class (in InvestmentResolver or a commands file):
data class CreateInvestmentAssetInput(
    val name: String,
    val assetType: String,
    val issuerName: String?,
    val ticker: String?,
    val isinCode: String?,
    val currencyCode: String?,
)
```

> **Note**: No account scoping — assets are global (no `accountId`). This matches the existing `AssetRepository` design.

#### 1c — Acceptance criteria

- [ ] `createInvestmentAsset` mutation returns `InvestmentAsset` with a generated `id`
- [ ] Asset is persisted in `banking.investment_assets`
- [ ] No authentication beyond being logged in (assets are global, not tenant-scoped)
- [ ] Duplicate assets (same name) are allowed — no unique constraint on name for manual assets

---

### Activity 2 — Frontend: Add GraphQL operations

**File to modify**: `web/src/lib/graphql/operations/banking/queries.ts`

```typescript
// Add these two queries:

export const INVESTMENT_HOLDINGS = gql`
  query InvestmentHoldings($financialAccountId: ID!) {
    investmentHoldings(financialAccountId: $financialAccountId) {
      id
      quantity
      averageCost
      grossValue
      netValue
      indexer
      indexerPercentage
      preFixedRate
      issueDate
      maturityDate
      isTaxExempt
      source
      asset {
        id
        name
        assetType
        ticker
        issuerName
        currencyCode
      }
    }
  }
`;

export const ALL_INVESTMENT_HOLDINGS = gql`
  query AllInvestmentHoldings {
    allInvestmentHoldings {
      id
      quantity
      averageCost
      grossValue
      netValue
      indexer
      indexerPercentage
      preFixedRate
      issueDate
      maturityDate
      isTaxExempt
      source
      asset {
        id
        name
        assetType
        ticker
        issuerName
        currencyCode
      }
    }
  }
`;
```

**File to modify**: `web/src/lib/graphql/operations/banking/mutations.ts`

```typescript
// Add these two mutations:

export const CREATE_INVESTMENT_ASSET = gql`
  mutation CreateInvestmentAsset($input: CreateInvestmentAssetInput!) {
    createInvestmentAsset(input: $input) {
      id
      name
      assetType
      issuerName
      currencyCode
    }
  }
`;

export const RECORD_INVESTMENT_TRANSACTION = gql`
  mutation RecordInvestmentTransaction($input: RecordInvestmentTransactionInput!) {
    recordInvestmentTransaction(input: $input) {
      id
      type
      direction
      quantity
      unitPrice
      grossValue
      tradeDate
      source
    }
  }
`;
```

> After adding, run codegen to update `@/lib/graphql/types/__generated__/graphql.ts`.

---

### Activity 3 — Update `InvestmentTable` to load real data

**File to modify**: `web/src/app/personal-finance/investments/components/InvestmentTable.tsx`

The table currently renders a static empty state regardless of data. Wire it to the holdings queries.

#### 3a — Updated props

```typescript
interface InvestmentTableProps {
  selectedAccountId: string | null;  // null = consolidated view
  onRecordHolding?: () => void;       // CTA callback — used by empty-holdings row
}
```

#### 3b — Query logic

```typescript
// selectedAccountId = null → consolidated view → call allInvestmentHoldings
// selectedAccountId set  → single account   → call investmentHoldings(financialAccountId)

const { data: holdingsData, loading: holdingsLoading } = useQuery(
  selectedAccountId ? INVESTMENT_HOLDINGS : ALL_INVESTMENT_HOLDINGS,
  {
    variables: selectedAccountId ? { financialAccountId: selectedAccountId } : undefined,
    skip: false,
  },
);

const holdings = selectedAccountId
  ? holdingsData?.investmentHoldings ?? []
  : holdingsData?.allInvestmentHoldings ?? [];
```

#### 3c — Table rows

For each holding, render a row with:

| Column | Value |
|--------|-------|
| Ativo | `holding.asset.name` |
| Tipo | Translated `holding.asset.assetType` (CDB → "CDB", LCI → "LCI", etc.) |
| Indexador | e.g. "CDI 120%" or "IPCA+" or "13,5% a.a." |
| Valor Bruto | `holding.grossValue` formatted as BRL currency |
| Vencimento | `holding.maturityDate` formatted as locale date |

If `holdings.length === 0` and `selectedAccountId` is set: render empty state row with inline CTA.

```tsx
// Empty state inside the table (not a full-page empty state):
<TableRow>
  <TableCell colSpan={5} sx={{ py: 6, textAlign: 'center' }}>
    <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
      {t('investments.table.emptyState')}
    </Typography>
    {onRecordHolding && (
      <Button
        variant="outlined"
        size="small"
        startIcon={<AddIcon />}
        onClick={onRecordHolding}
        data-testid="record-holding-inline-btn"
      >
        {t('investments.table.recordFirstHolding')}
      </Button>
    )}
  </TableCell>
</TableRow>
```

#### 3d — Indexer display helper

```typescript
function formatIndexer(holding: InvestmentHolding): string {
  if (holding.indexer === 'PRE_FIXADO') {
    const rate = holding.preFixedRate
      ? `${(holding.preFixedRate * 100).toFixed(2)}% a.a.`
      : '—';
    return rate;
  }
  const label = indexerLabels[holding.indexer] ?? holding.indexer;
  return holding.indexerPercentage
    ? `${label} ${(holding.indexerPercentage * 100).toFixed(0)}%`
    : label;
}
```

---

### Activity 4 — Update `page.tsx`: add empty-holdings state + top-level CTA

**File to modify**: `web/src/app/personal-finance/investments/page.tsx`

The page currently shows `InvestmentTable` inside the `hasInvestmentAccounts` branch without a "record holding" CTA. Add:

1. **State**: `const [recordHoldingOpen, setRecordHoldingOpen] = useState(false);`

2. **Update existing top-bar button** (currently opens `createAccountOpen`) to be the "Add account" button, and add a new "Registrar ativo" button next to it. Disable "Registrar ativo" when no specific account is selected (consolidated view):

```tsx
{hasInvestmentAccounts && (
  <Stack direction="row" spacing={2} sx={{ width: '100%', mb: 2 }} justifyContent="flex-end">
    <Button
      variant="outlined"
      startIcon={<AddIcon />}
      onClick={() => setCreateAccountOpen(true)}
      data-testid="create-investment-account-btn"
    >
      {t('investments.actions.addAccount')}
    </Button>
    <Button
      variant="contained"
      startIcon={<PlaylistAddIcon />}
      onClick={() => setRecordHoldingOpen(true)}
      disabled={!selectedInvestmentAccountId}
      data-testid="record-holding-btn"
      sx={{
        boxShadow: `0 10px 24px ${alpha(theme.palette.primary.main, 0.28)}`,
        '&:hover': { boxShadow: `0 12px 28px ${alpha(theme.palette.primary.main, 0.36)}` },
      }}
    >
      {t('investments.actions.recordHolding')}
    </Button>
  </Stack>
)}
```

3. **Pass `onRecordHolding` callback to `InvestmentTable`**:

```tsx
<InvestmentTable
  selectedAccountId={selectedInvestmentAccountId}
  onRecordHolding={
    selectedInvestmentAccountId ? () => setRecordHoldingOpen(true) : undefined
  }
/>
```

4. **Render the new drawer** (at the end of JSX, alongside `CreateInvestmentAccountDrawer`):

```tsx
<RecordFixedIncomeDrawer
  open={recordHoldingOpen}
  onClose={() => setRecordHoldingOpen(false)}
  financialAccountId={selectedInvestmentAccountId!}
  refetchHoldings={() => {
    // Apollo will re-fetch thanks to refetchQueries in the drawer mutation
    // router.refresh() only if needed for RSC caches
  }}
/>
```

---

### Activity 5 — Create `RecordFixedIncomeDrawer`

**File to create**: `web/src/app/personal-finance/investments/components/RecordFixedIncomeDrawer.tsx`

#### 5a — Props

```typescript
interface RecordFixedIncomeDrawerProps {
  open: boolean;
  onClose: () => void;
  financialAccountId: string;
  onRecorded?: () => void;
}
```

#### 5b — Form data type & Zod schema

```typescript
type RecordFixedIncomeFormData = {
  // Asset fields
  assetName: string;
  assetType: FixedIncomeAssetType;
  issuerName: string;
  // Transaction / holding fields
  indexer: Indexer;
  indexerPercentage?: number;   // only when indexer ≠ PRE_FIXADO
  preFixedRate?: number;         // only when indexer = PRE_FIXADO
  grossValue: number;            // invested amount in BRL
  tradeDate: string;             // ISO date — application date
  maturityDate: string;          // ISO date
  isTaxExempt: boolean;
};

// Fixed income asset types (subset of AssetType)
type FixedIncomeAssetType =
  | 'CDB' | 'RDB' | 'LCI' | 'LCA' | 'LC' | 'LF' | 'LIG'
  | 'DEBENTURES' | 'CRI' | 'CRA' | 'TREASURY';

type Indexer = 'CDI' | 'DI' | 'IPCA' | 'IGP_M' | 'SELIC' | 'PRE_FIXADO' | 'OUTROS';

const schema = z.object({
  assetName: z.string().min(1, t('investments.recordHolding.validation.assetNameRequired')),
  assetType: z.enum([...fixedIncomeAssetTypes], {
    required_error: t('investments.recordHolding.validation.assetTypeRequired'),
  }),
  issuerName: z.string().min(1, t('investments.recordHolding.validation.issuerRequired')),
  indexer: z.enum([...indexerValues], {
    required_error: t('investments.recordHolding.validation.indexerRequired'),
  }),
  indexerPercentage: z.number().positive().optional(),
  preFixedRate: z.number().positive().optional(),
  grossValue: z.number({ required_error: t('investments.recordHolding.validation.grossValueRequired') })
    .positive(t('investments.recordHolding.validation.grossValuePositive')),
  tradeDate: z.string().min(1, t('investments.recordHolding.validation.tradeDateRequired')),
  maturityDate: z.string().min(1, t('investments.recordHolding.validation.maturityDateRequired')),
  isTaxExempt: z.boolean().default(false),
}).superRefine((data, ctx) => {
  if (data.indexer !== 'PRE_FIXADO' && !data.indexerPercentage) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      message: t('investments.recordHolding.validation.indexerPercentageRequired'),
      path: ['indexerPercentage'],
    });
  }
  if (data.indexer === 'PRE_FIXADO' && !data.preFixedRate) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      message: t('investments.recordHolding.validation.preFixedRateRequired'),
      path: ['preFixedRate'],
    });
  }
  if (data.maturityDate <= data.tradeDate) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      message: t('investments.recordHolding.validation.maturityAfterTrade'),
      path: ['maturityDate'],
    });
  }
});
```

#### 5c — Special rule: Tesouro Direto auto-fills issuer

```typescript
const watchedAssetType = watch('assetType');

useEffect(() => {
  if (watchedAssetType === 'TREASURY') {
    setValue('issuerName', 'Tesouro Nacional');
  }
}, [watchedAssetType, setValue]);
```

#### 5d — Mutations

```typescript
const [createAsset, { loading: creatingAsset }] = useMutation(CREATE_INVESTMENT_ASSET);

const [recordTransaction, { loading: recordingTx, error: txError }] = useMutation(
  RECORD_INVESTMENT_TRANSACTION,
  {
    refetchQueries: [
      { query: INVESTMENT_HOLDINGS, variables: { financialAccountId } },
      { query: ALL_INVESTMENT_HOLDINGS },
    ],
    onCompleted: () => {
      success(t('investments.recordHolding.success'));
      onRecorded?.();
      onClose();
    },
  },
);

const onSubmit = async (data: RecordFixedIncomeFormData) => {
  // Step 1: create asset
  const assetResult = await createAsset({
    variables: {
      input: {
        name: data.assetName,
        assetType: data.assetType,
        issuerName: data.issuerName,
        currencyCode: 'BRL',
      },
    },
  });

  const assetId = assetResult.data?.createInvestmentAsset.id;
  if (!assetId) return;  // error handled by Apollo errorPolicy

  // Step 2: record transaction
  await recordTransaction({
    variables: {
      input: {
        financialAccountId,
        assetId,
        direction: 'INFLOW',
        type: 'PURCHASE',
        quantity: 1,
        unitPrice: data.grossValue,
        grossValue: data.grossValue,
        tradeDate: data.tradeDate,
        settlementDate: data.tradeDate,
        source: 'MANUAL',
        // Holding terms (applied on first PURCHASE):
        indexer: data.indexer,
        indexerPercentage: data.indexer !== 'PRE_FIXADO'
          ? data.indexerPercentage! / 100
          : undefined,
        preFixedRate: data.indexer === 'PRE_FIXADO'
          ? data.preFixedRate! / 100
          : undefined,
        issueDate: data.tradeDate,
        maturityDate: data.maturityDate,
        isTaxExempt: data.isTaxExempt,
      },
    },
  });
};
```

#### 5e — Form layout (visual reference)

```
┌────────────────────────────────────────────────────┐
│  Registrar ativo de Renda Fixa             [×]     │  ← Drawer.Header
├────────────────────────────────────────────────────┤
│                                                    │
│  SOBRE O ATIVO                                     │  ← Section label (overline)
│                                                    │
│  Nome do ativo *                                   │
│  [ CDB Banco Inter 120% CDI            ]           │  ← free text
│                                                    │
│  Tipo *                                            │
│  [ CDB                             ▾ ]             │  ← SelectField
│                                                    │
│  Emissor *                                         │
│  [ Banco Inter                         ]           │  ← read-only when TREASURY
│                                                    │
│  ────────────────────────────────────────────────  │
│                                                    │
│  DETALHES DA APLICAÇÃO                             │  ← Section label
│                                                    │
│  Indexador *                                       │
│  [ CDI                             ▾ ]             │
│                                                    │
│  % do indexador *     (hidden if PRE_FIXADO)       │
│  [ 120                   ]  %                      │
│                                                    │
│  Taxa (% a.a.) *      (shown only if PRE_FIXADO)   │
│  [ 13,5                  ]  %                      │
│                                                    │
│  Valor aplicado *                                  │
│  [ R$ 10.000,00                        ]           │
│                                                    │
│  Data da aplicação *                               │
│  [ 12/03/2026                          ]           │
│                                                    │
│  Data de vencimento *                              │
│  [ 12/03/2028                          ]           │
│                                                    │
│  [ ] Isento de IR (LCI, LCA, CRI, CRA)             │  ← Checkbox
│                                                    │
│  ────────────────────────────────────────────────  │
│                                     [Registrar]    │
└────────────────────────────────────────────────────┘
```

#### 5f — Auto-suggest `isTaxExempt` based on asset type

LCI, LCA, CRI, CRA are always tax-exempt for individuals in Brazil. Auto-check the checkbox when the user selects these types, but allow override.

```typescript
useEffect(() => {
  const taxExemptTypes = ['LCI', 'LCA', 'CRI', 'CRA'];
  if (taxExemptTypes.includes(watchedAssetType)) {
    setValue('isTaxExempt', true);
  } else {
    setValue('isTaxExempt', false);
  }
}, [watchedAssetType, setValue]);
```

---

### Activity 6 — i18n keys

**Files**: `web/src/app/personal-finance/i18n/locales/pt.json` and `en.json`

Add under existing `investments` namespace:

#### Additions to `pt.json`

```json
{
  "investments": {
    "actions": {
      "addAccount": "Adicionar conta",
      "recordHolding": "Registrar ativo"
    },
    "table": {
      "columns": {
        "asset": "Ativo",
        "type": "Tipo",
        "indexer": "Indexador",
        "currentValue": "Valor atual",
        "maturityDate": "Vencimento"
      },
      "emptyState": "Nenhum ativo registrado nesta conta",
      "recordFirstHolding": "Registrar primeiro ativo"
    },
    "recordHolding": {
      "title": "Registrar ativo de Renda Fixa",
      "sections": {
        "assetInfo": "Sobre o ativo",
        "applicationDetails": "Detalhes da aplicação"
      },
      "fields": {
        "assetName": "Nome do ativo",
        "assetType": "Tipo",
        "issuerName": "Emissor",
        "indexer": "Indexador",
        "indexerPercentage": "% do indexador",
        "preFixedRate": "Taxa (% a.a.)",
        "grossValue": "Valor aplicado (R$)",
        "tradeDate": "Data da aplicação",
        "maturityDate": "Data de vencimento",
        "isTaxExempt": "Isento de IR (LCI, LCA, CRI, CRA)"
      },
      "assetTypes": {
        "CDB": "CDB",
        "RDB": "RDB",
        "LCI": "LCI",
        "LCA": "LCA",
        "LC": "LC",
        "LF": "LF",
        "LIG": "LIG",
        "DEBENTURES": "Debêntures",
        "CRI": "CRI",
        "CRA": "CRA",
        "TREASURY": "Tesouro Direto"
      },
      "indexers": {
        "CDI": "CDI",
        "DI": "DI",
        "IPCA": "IPCA+",
        "IGP_M": "IGP-M+",
        "SELIC": "SELIC",
        "PRE_FIXADO": "Prefixado",
        "OUTROS": "Outro"
      },
      "validation": {
        "assetNameRequired": "Nome do ativo é obrigatório",
        "assetTypeRequired": "Tipo de ativo é obrigatório",
        "issuerRequired": "Emissor é obrigatório",
        "indexerRequired": "Indexador é obrigatório",
        "indexerPercentageRequired": "% do indexador é obrigatório",
        "preFixedRateRequired": "Taxa é obrigatória",
        "grossValueRequired": "Valor aplicado é obrigatório",
        "grossValuePositive": "Valor deve ser maior que zero",
        "tradeDateRequired": "Data da aplicação é obrigatória",
        "maturityDateRequired": "Data de vencimento é obrigatória",
        "maturityAfterTrade": "Vencimento deve ser após a data da aplicação"
      },
      "actions": {
        "submit": "Registrar",
        "submitting": "Registrando..."
      },
      "success": "Ativo registrado com sucesso"
    }
  }
}
```

#### Additions to `en.json`

```json
{
  "investments": {
    "actions": {
      "addAccount": "Add account",
      "recordHolding": "Record holding"
    },
    "table": {
      "columns": {
        "asset": "Asset",
        "type": "Type",
        "indexer": "Indexer",
        "currentValue": "Current value",
        "maturityDate": "Maturity"
      },
      "emptyState": "No holdings recorded in this account",
      "recordFirstHolding": "Record first holding"
    },
    "recordHolding": {
      "title": "Record Fixed Income Holding",
      "sections": {
        "assetInfo": "Asset details",
        "applicationDetails": "Application details"
      },
      "fields": {
        "assetName": "Asset name",
        "assetType": "Type",
        "issuerName": "Issuer",
        "indexer": "Indexer",
        "indexerPercentage": "Indexer %",
        "preFixedRate": "Rate (% p.a.)",
        "grossValue": "Invested amount (BRL)",
        "tradeDate": "Application date",
        "maturityDate": "Maturity date",
        "isTaxExempt": "Tax-exempt (LCI, LCA, CRI, CRA)"
      },
      "assetTypes": {
        "CDB": "CDB",
        "RDB": "RDB",
        "LCI": "LCI",
        "LCA": "LCA",
        "LC": "LC",
        "LF": "LF",
        "LIG": "LIG",
        "DEBENTURES": "Debentures",
        "CRI": "CRI",
        "CRA": "CRA",
        "TREASURY": "Treasury Direct"
      },
      "indexers": {
        "CDI": "CDI",
        "DI": "DI",
        "IPCA": "IPCA+",
        "IGP_M": "IGP-M+",
        "SELIC": "SELIC",
        "PRE_FIXADO": "Pre-fixed",
        "OUTROS": "Other"
      },
      "validation": {
        "assetNameRequired": "Asset name is required",
        "assetTypeRequired": "Asset type is required",
        "issuerRequired": "Issuer is required",
        "indexerRequired": "Indexer is required",
        "indexerPercentageRequired": "Indexer % is required",
        "preFixedRateRequired": "Rate is required",
        "grossValueRequired": "Invested amount is required",
        "grossValuePositive": "Amount must be greater than zero",
        "tradeDateRequired": "Application date is required",
        "maturityDateRequired": "Maturity date is required",
        "maturityAfterTrade": "Maturity must be after the application date"
      },
      "actions": {
        "submit": "Record",
        "submitting": "Recording..."
      },
      "success": "Holding recorded successfully"
    }
  }
}
```

---

### Activity 7 — Unit tests

**File to create**: `web/src/app/personal-finance/investments/components/RecordFixedIncomeDrawer.test.tsx`

```typescript
import { describe, it, expect, afterEach, vi } from 'vitest';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RecordFixedIncomeDrawer } from './RecordFixedIncomeDrawer';

describe.sequential('RecordFixedIncomeDrawer', () => {
  afterEach(() => { cleanup(); });

  it('renders all required fields', () => {
    render(<RecordFixedIncomeDrawer open onClose={vi.fn()} financialAccountId="acc-1" />, { wrapper });
    expect(screen.getByLabelText(/nome do ativo/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/tipo/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/emissor/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/indexador/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/valor aplicado/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/data da aplicação/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/data de vencimento/i)).toBeInTheDocument();
  });

  it('shows indexer percentage when indexer is CDI', async () => {
    const user = userEvent.setup();
    render(<RecordFixedIncomeDrawer open onClose={vi.fn()} financialAccountId="acc-1" />, { wrapper });
    await selectOption(user, 'indexer', 'CDI');
    expect(screen.getByLabelText(/% do indexador/i)).toBeInTheDocument();
    expect(screen.queryByLabelText(/taxa/i)).not.toBeInTheDocument();
  });

  it('hides indexer percentage and shows pre-fixed rate when indexer is Prefixado', async () => {
    const user = userEvent.setup();
    render(<RecordFixedIncomeDrawer open onClose={vi.fn()} financialAccountId="acc-1" />, { wrapper });
    await selectOption(user, 'indexer', 'PRE_FIXADO');
    expect(screen.queryByLabelText(/% do indexador/i)).not.toBeInTheDocument();
    expect(screen.getByLabelText(/taxa/i)).toBeInTheDocument();
  });

  it('auto-fills issuer as "Tesouro Nacional" when type is Tesouro Direto', async () => {
    const user = userEvent.setup();
    render(<RecordFixedIncomeDrawer open onClose={vi.fn()} financialAccountId="acc-1" />, { wrapper });
    await selectOption(user, 'assetType', 'TREASURY');
    expect(screen.getByLabelText(/emissor/i)).toHaveValue('Tesouro Nacional');
  });

  it('auto-checks isTaxExempt when type is LCI', async () => {
    const user = userEvent.setup();
    render(<RecordFixedIncomeDrawer open onClose={vi.fn()} financialAccountId="acc-1" />, { wrapper });
    await selectOption(user, 'assetType', 'LCI');
    expect(screen.getByRole('checkbox', { name: /isento de ir/i })).toBeChecked();
  });

  it('shows validation errors when submitting empty form', async () => {
    const user = userEvent.setup();
    render(<RecordFixedIncomeDrawer open onClose={vi.fn()} financialAccountId="acc-1" />, { wrapper });
    await user.click(screen.getByRole('button', { name: /registrar/i }));
    await waitFor(() => {
      expect(screen.getByText(/nome do ativo é obrigatório/i)).toBeInTheDocument();
      expect(screen.getByText(/tipo de ativo é obrigatório/i)).toBeInTheDocument();
    });
  });

  it('validates maturity date must be after trade date', async () => {
    const user = userEvent.setup();
    render(<RecordFixedIncomeDrawer open onClose={vi.fn()} financialAccountId="acc-1" />, { wrapper });
    await fillValidForm(user, { tradeDate: '2026-12-01', maturityDate: '2026-01-01' });
    await user.click(screen.getByRole('button', { name: /registrar/i }));
    await waitFor(() => {
      expect(screen.getByText(/vencimento deve ser após/i)).toBeInTheDocument();
    });
  });

  it('calls createInvestmentAsset then recordInvestmentTransaction on valid submit', async () => {
    const createAssetSpy = mockCreateAssetMutation({ id: 'asset-1' });
    const recordTxSpy = mockRecordTransactionMutation({ id: 'tx-1' });
    const user = userEvent.setup();
    render(
      <RecordFixedIncomeDrawer open onClose={vi.fn()} financialAccountId="acc-1" />,
      { wrapper: withMocks([createAssetSpy, recordTxSpy]) },
    );
    await fillValidForm(user);
    await user.click(screen.getByRole('button', { name: /registrar/i }));
    await waitFor(() => {
      expect(createAssetSpy).toHaveBeenCalledOnce();
      expect(recordTxSpy).toHaveBeenCalledWith(
        expect.objectContaining({
          variables: {
            input: expect.objectContaining({
              assetId: 'asset-1',
              direction: 'INFLOW',
              type: 'PURCHASE',
              source: 'MANUAL',
            }),
          },
        }),
      );
    });
  });

  it('calls onRecorded and onClose after successful recording', async () => {
    const onRecorded = vi.fn();
    const onClose = vi.fn();
    // ... setup with resolving mutation mocks ...
    await waitFor(() => {
      expect(onRecorded).toHaveBeenCalled();
      expect(onClose).toHaveBeenCalled();
    });
  });

  it('shows error banner if createInvestmentAsset fails', async () => {
    // ... setup with rejecting mutation mock ...
    // ... submit valid form ...
    await waitFor(() => {
      expect(screen.getByRole('alert')).toBeInTheDocument();
      // onClose NOT called
    });
  });
});
```

---

## File Change Summary

| File | Change | Notes |
|------|--------|-------|
| `service/kotlin/.../graphql/investment.graphqls` | **Modify** | Add `CreateInvestmentAssetInput` and `createInvestmentAsset` mutation |
| `service/kotlin/.../graphql/resolver/InvestmentResolver.kt` | **Modify** | Add `createInvestmentAsset` resolver method |
| `web/src/lib/graphql/operations/banking/queries.ts` | **Modify** | Add `INVESTMENT_HOLDINGS`, `ALL_INVESTMENT_HOLDINGS` |
| `web/src/lib/graphql/operations/banking/mutations.ts` | **Modify** | Add `CREATE_INVESTMENT_ASSET`, `RECORD_INVESTMENT_TRANSACTION` |
| `web/src/app/personal-finance/investments/components/InvestmentTable.tsx` | **Modify** | Wire holdings queries; real data rows; inline empty state CTA |
| `web/src/app/personal-finance/investments/page.tsx` | **Modify** | Add `recordHoldingOpen` state; add "Registrar ativo" button; pass `onRecordHolding` to table; render `<RecordFixedIncomeDrawer>` |
| `web/src/app/personal-finance/investments/components/RecordFixedIncomeDrawer.tsx` | **Create** | New drawer for recording fixed income |
| `web/src/app/personal-finance/investments/components/RecordFixedIncomeDrawer.test.tsx` | **Create** | Unit tests |
| `web/src/app/personal-finance/i18n/locales/pt.json` | **Modify** | Add keys under `investments.actions`, `investments.table`, `investments.recordHolding` |
| `web/src/app/personal-finance/i18n/locales/en.json` | **Modify** | Same, in English |
| `CreateInvestmentAccountDrawer.tsx` | **Do not modify** | |

---

## Acceptance Criteria

- [ ] `createInvestmentAsset` backend mutation creates an asset and returns its `id`
- [ ] "Registrar ativo" button is visible when an account is selected; disabled in consolidated view
- [ ] Clicking "Registrar ativo" opens `RecordFixedIncomeDrawer`
- [ ] The inline empty-state CTA in the table also opens the drawer
- [ ] Form has all required fields (asset name, type, issuer, indexer, rate/%, value, dates, tax-exempt)
- [ ] Tesouro Direto auto-fills issuer = "Tesouro Nacional" (read-only)
- [ ] LCI/LCA/CRI/CRA auto-checks "Isento de IR"
- [ ] Indexer % shown only when indexer ≠ PRE_FIXADO; pre-fixed rate shown only when PRE_FIXADO
- [ ] Validation blocks submit when required fields are missing or maturityDate ≤ tradeDate
- [ ] On submit: calls `createInvestmentAsset` then `recordInvestmentTransaction` in sequence
- [ ] `indexerPercentage` and `preFixedRate` sent as decimals (e.g. 120% → 1.20, 13.5% → 0.135)
- [ ] After success: toast shown, drawer closes, `INVESTMENT_HOLDINGS` query refetched
- [ ] Holdings table shows the new row with asset name, type, indexer, gross value, maturity
- [ ] All strings use i18n keys; all colors use `theme.palette.*`
- [ ] `data-testid` on: record-holding button, each form field
- [ ] All Gherkin scenarios pass; unit tests pass with no console errors

---

## Out of scope

- Live market pricing (gross value is user-provided, no external API call)
- Multiple lots (quantity > 1) — fixed for MVP at quantity=1
- Editing or deleting a recorded holding
- Equities, FIIs, ETFs, crypto (separate tasks)
- Open Finance auto-sync
- Portfolio performance chart (requires pricing service)
- Sidebar summary with real numbers (separate task — currently hardcoded R$ 0,00)
