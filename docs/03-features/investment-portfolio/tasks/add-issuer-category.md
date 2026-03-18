# Task 0: Add Issuer Category

> **Goal**: Classify financial institutions by category so the investment account form can filter the issuer autocomplete by context (show brokerages for BROKERAGE accounts, exchanges for CRYPTO accounts).
> **Blocks**: `create-investment-account` task — must be completed first.
> **Scope**: Backend (migration + domain + GraphQL) + minimal frontend query update.

---

## Why

The `banking.issuers` table today is a flat list with no distinction between banks, brokerages, benefit providers, and crypto exchanges. Without a `category` field:

- The `CreateInvestmentAccountDrawer` would show VR Benefícios, Sodexo, and Alelo alongside XP and BTG — noise for the user
- It's impossible to show only crypto exchanges when the user selects subtype `CRYPTO`
- Brokerages missing from the current seed (NuInvest, Toro, Warren, etc.) cannot be added without context

---

## Categories

```
BANK             — banking license (COMPE/ISPB); includes digital banks and fintechs with bank charter
BROKERAGE        — investment broker/distributor (CVM/ANBIMA regulated); may or may not have bank charter
CRYPTO_EXCHANGE  — virtual asset service provider (VASP); no COMPE/ISPB
PAYMENT          — payment-focused; no full banking license (e.g. voucher issuers, pure payment wallets)
BENEFIT          — meal/transport voucher issuers (VR, Alelo, Sodexo, etc.)
```

**Filter logic in `CreateInvestmentAccountDrawer`:**

| Account subtype | Issuers shown |
|-----------------|---------------|
| `BROKERAGE` | `BANK`, `BROKERAGE` |
| `FIXED_INCOME` | `BANK`, `BROKERAGE` |
| `PENSION` | `BANK`, `BROKERAGE` |
| `CRYPTO` | `CRYPTO_EXCHANGE` |

Banks appear for non-crypto investment subtypes because many (Itaú, Bradesco, BB, BTG, XP, Rico) offer their own investment arms.

---

## Activity 1 — Database migration

**File to create**: `service/kotlin/banking/src/main/resources/db/migration/V0_22__add_issuer_category.sql`

> Check the latest migration number in `banking/src/main/resources/db/migration/` and use the next available number.

```sql
-- ─── Issuer category ──────────────────────────────────────────────────────────
-- Adds a category column to banking.issuers to enable context-based filtering.
-- Classifies existing 37 institutions and inserts new brokerages + crypto exchanges.
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. Add column (nullable first so UPDATE can run)
ALTER TABLE banking.issuers
    ADD COLUMN category TEXT
        CHECK (category IN ('BANK', 'BROKERAGE', 'CRYPTO_EXCHANGE', 'PAYMENT', 'BENEFIT'));

-- 2. Classify existing institutions

-- Banks (full banking license, COMPE-registered)
UPDATE banking.issuers SET category = 'BANK' WHERE name IN (
    'Nubank', 'Mercado Pago', 'Banco Inter', 'PicPay', 'Banco do Brasil',
    'Caixa Econômica Federal', 'Agibank', 'Stone', 'C6 Bank', 'Santander',
    'Itaú', 'BRB', 'Bradesco', 'PagBank', 'BTG Pactual',
    'Safra', 'Citibank Brasil', 'Banco Votorantim (BV)',
    'Neon', 'Next', 'Banco Original', 'Will Bank', 'InfinitePay',
    'Sicoob', 'Sicredi', 'Unicred',
    'Nomad', 'Wise'            -- international, no COMPE but full banking services
);

-- Payment-focused (no full banking license)
UPDATE banking.issuers SET category = 'PAYMENT' WHERE name IN (
    '99Pay'
);

-- Brokerages (investment-focused, CVM/ANBIMA regulated)
UPDATE banking.issuers SET category = 'BROKERAGE' WHERE name IN (
    'XP Investimentos', 'Rico', 'Clear'
);

-- Benefit / voucher issuers
UPDATE banking.issuers SET category = 'BENEFIT' WHERE name IN (
    'VR Benefícios', 'Alelo', 'Sodexo', 'Ticket', 'Caju', 'Flash Benefícios', 'Swile'
);

-- 3. Make not null with default BANK
ALTER TABLE banking.issuers
    ALTER COLUMN category SET NOT NULL,
    ALTER COLUMN category SET DEFAULT 'BANK';

-- 4. Index for filtered queries
CREATE INDEX idx_issuers_category ON banking.issuers(category) WHERE is_active = TRUE;

-- ─── New brokerages ───────────────────────────────────────────────────────────
-- Main Brazilian investment brokerages not in the original seed.
-- compe_code / ispb: populated where publicly known from BCB/B3 registers.
-- NULL means the institution operates as a DTVM (distributor) without own COMPE.

INSERT INTO banking.issuers (name, domain, compe_code, ispb, category) VALUES

-- Nubank's brokerage arm (formerly Easynvest, acquired 2021)
('NuInvest',              'nuinvest.com.br',            '140', '62169875', 'BROKERAGE'),

-- Digital-first brokerages
('Toro Investimentos',    'toroinvestimentos.com.br',   NULL,  NULL,       'BROKERAGE'),
('Warren',                'warren.com.br',              NULL,  NULL,       'BROKERAGE'),
('Órama',                 'orama.com.br',               NULL,  NULL,       'BROKERAGE'),

-- Mid-size brokerages
('Genial Investimentos',  'genialinvestimentos.com.br', NULL,  NULL,       'BROKERAGE'),
('Guide Investimentos',   'guideinvestimentos.com.br',  NULL,  NULL,       'BROKERAGE'),
('Modal Mais',            'modal.com.br',               '394', '04257795', 'BROKERAGE'),
('Ativa Investimentos',   'ativainvestimentos.com.br',  '188', '33775974', 'BROKERAGE'),
('Mirae Asset',           'miraeasset.com.br',          '447', NULL,       'BROKERAGE'),

-- International brokerages accessible to Brazilians
('Avenue',                'avenue.us',                  NULL,  NULL,       'BROKERAGE'),

-- Bank-tied brokerages (institution already in seed as BANK;
-- users of these arms typically use the parent bank's issuer)
-- Agora (Bradesco), Itaú Corretora, Inter Invest → covered by parent bank entry

-- ─── Crypto exchanges ─────────────────────────────────────────────────────────
-- VASP (Virtual Asset Service Providers) regulated by BCB Resolution 96/2022.
-- None have COMPE/ISPB — they are not banks.

INSERT INTO banking.issuers (name, domain, compe_code, ispb, category) VALUES

-- Brazilian-native exchanges
('Mercado Bitcoin',       'mercadobitcoin.com.br',      NULL,  NULL,       'CRYPTO_EXCHANGE'),
('Foxbit',                'foxbit.com.br',              NULL,  NULL,       'CRYPTO_EXCHANGE'),
('Novadax',               'novadax.com',                NULL,  NULL,       'CRYPTO_EXCHANGE'),
('BitPreço',              'bitpreco.com.br',            NULL,  NULL,       'CRYPTO_EXCHANGE'),
('Ripio',                 'ripio.com',                  NULL,  NULL,       'CRYPTO_EXCHANGE'),

-- Global exchanges with significant BR user base
('Binance',               'binance.com',                NULL,  NULL,       'CRYPTO_EXCHANGE'),
('Coinbase',              'coinbase.com',               NULL,  NULL,       'CRYPTO_EXCHANGE'),
('Kraken',                'kraken.com',                 NULL,  NULL,       'CRYPTO_EXCHANGE'),
('Bybit',                 'bybit.com',                  NULL,  NULL,       'CRYPTO_EXCHANGE'),
('OKX',                   'okx.com',                    NULL,  NULL,       'CRYPTO_EXCHANGE');
```

---

## Activity 2 — Update Issuer domain model

**File**: `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/domain/issuer/Issuer.kt`

Add the `category` field:

```kotlin
enum class IssuerCategory {
    BANK,
    BROKERAGE,
    CRYPTO_EXCHANGE,
    PAYMENT,
    BENEFIT,
}

data class Issuer(
    val id: UUID? = null,
    val name: String,
    val domain: String?,
    val compeCode: String?,
    val ispb: String?,
    val logoKey: String?,
    val countryCode: String = "BR",
    val isActive: Boolean = true,
    val category: IssuerCategory = IssuerCategory.BANK,   // ← ADD
)
```

---

## Activity 3 — Update IssuerEntity

**File**: `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/model/issuer/IssuerEntity.kt`

```kotlin
@Enumerated(EnumType.STRING)
@Column(nullable = false, length = 20)
open var category: IssuerCategory = IssuerCategory.BANK,   // ← ADD
```

---

## Activity 4 — Update IssuerRepository

**File**: `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/domain/issuer/IssuerRepository.kt`

```kotlin
interface IssuerRepository {
    fun findAllActive(): List<Issuer>
    fun findActiveByCategories(categories: List<IssuerCategory>): List<Issuer>   // ← ADD
    fun findAllPendingLogoHydration(): List<Issuer>
    fun findById(id: UUID): Issuer?
    fun findByIds(ids: Collection<UUID>): List<Issuer>
    fun updateLogoKey(id: UUID, logoKey: String)
}
```

**File**: `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/repo/DefaultIssuerRepository.kt`

Implement the new method using the JPA entity repository (add `findByIsActiveTrueAndCategoryIn` to `IssuerEntityRepository`).

---

## Activity 5 — Update GraphQL schema and resolver

**File**: `service/kotlin/banking/src/main/resources/graphql/schema.graphqls`

```graphql
enum IssuerCategory {
  BANK
  BROKERAGE
  CRYPTO_EXCHANGE
  PAYMENT
  BENEFIT
}

type Issuer {
  id: ID!
  name: String!
  domain: String
  compeCode: String
  ispb: String
  countryCode: String!
  logoKey: String
  category: IssuerCategory!   # ← ADD
  logoAsset: Asset
}

extend type Query {
  # Add optional filter — when omitted, returns all active issuers (existing behaviour preserved)
  issuers(categories: [IssuerCategory!]): [Issuer!]!
  ...
}
```

**File**: `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/graphql/resolver/IssuerResolver.kt`

```kotlin
fun listIssuers(categories: List<IssuerCategory>?): List<IssuerDTO> {
    val issuers = if (categories.isNullOrEmpty()) {
        issuerRepository.findAllActive()
    } else {
        issuerRepository.findActiveByCategories(categories)
    }
    return issuers.map { it.toDTO() }
}
```

**File**: `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/graphql/BankingWiringFactory.kt`

Update the `issuers` data fetcher to pass the optional `categories` argument:

```kotlin
.dataFetcher(
    "issuers",
    createValidatedDataFetcher { env ->
        env.withPermission(BankingPermissions.BANKING_ISSUER_VIEW) { _ ->
            val categories = env.getArgument<List<String>?>("categories")
                ?.map { IssuerCategory.valueOf(it) }
            issuerResolver.listIssuers(categories)
        }
    },
)
```

**File**: `IssuerDTO.kt` — add `category: IssuerCategory` field and update the mapper.

---

## Activity 6 — Update frontend GraphQL query

**File**: `web/src/features/finance-transactions/api/queries.ts`

Update the `GET_ISSUERS` query to accept the optional `categories` variable and expose the new `category` field:

```typescript
export const GET_ISSUERS = gql`
  query GetIssuers($categories: [IssuerCategory!]) {
    issuers(categories: $categories) {
      id
      name
      domain
      compeCode
      ispb
      countryCode
      logoKey
      category
      logoAsset { id }
    }
  }
`;
```

After editing, run Apollo code generation:
```bash
cd web && npm run codegen
```

This updates `queries.generated.ts` automatically — do not edit it manually.

---

## Activity 7 — Use filtered query in `CreateInvestmentAccountDrawer`

**File**: `web/src/app/personal-finance/investments/components/CreateInvestmentAccountDrawer.tsx`

When fetching issuers, pass the categories filter based on the selected subtype:

```typescript
const investmentCategories: Record<InvestmentSubtype, IssuerCategory[]> = {
  BROKERAGE:    ['BANK', 'BROKERAGE'],
  FIXED_INCOME: ['BANK', 'BROKERAGE'],
  PENSION:      ['BANK', 'BROKERAGE'],
  CRYPTO:       ['CRYPTO_EXCHANGE'],
};

const { data: issuersData } = useGetIssuersQuery({
  variables: {
    categories: selectedSubtype
      ? investmentCategories[selectedSubtype]
      : ['BANK', 'BROKERAGE'],
  },
});
```

The existing `CreateFinancialAccountDrawer` (bank/card flow) is **not changed** — it calls `useGetIssuersQuery()` without `categories`, which still returns all active issuers (backwards compatible because `categories` is optional).

---

## File Change Summary

| File | Change | Notes |
|------|--------|-------|
| `banking/.../db/migration/V0_22__add_issuer_category.sql` | **Create** | Adds column, classifies 37 existing, inserts ~20 new |
| `banking/.../domain/issuer/Issuer.kt` | **Modify** | Add `IssuerCategory` enum + `category` field |
| `banking/.../model/issuer/IssuerEntity.kt` | **Modify** | Add `@Enumerated` `category` field |
| `banking/.../domain/issuer/IssuerRepository.kt` | **Modify** | Add `findActiveByCategories` |
| `banking/.../repo/DefaultIssuerRepository.kt` | **Modify** | Implement new method |
| `banking/.../repo/IssuerEntityRepository.kt` | **Modify** | Add `findByIsActiveTrueAndCategoryIn` |
| `banking/.../graphql/schema.graphqls` | **Modify** | Add `IssuerCategory` enum, `category` field on `Issuer`, `categories` arg on `issuers` query |
| `banking/.../graphql/dto/IssuerDTO.kt` | **Modify** | Add `category` field |
| `banking/.../graphql/resolver/IssuerResolver.kt` | **Modify** | Accept optional `categories` filter |
| `banking/.../graphql/BankingWiringFactory.kt` | **Modify** | Pass `categories` arg to resolver |
| `web/.../features/finance-transactions/api/queries.ts` | **Modify** | Add `$categories` variable + `category` field to `GET_ISSUERS` |
| `web/.../features/finance-transactions/api/queries.generated.ts` | **Auto-generated** | Run `npm run codegen` after editing `queries.ts` |
| `web/.../investments/components/CreateInvestmentAccountDrawer.tsx` | **Modify** | Pass `categories` filter based on selected subtype |

---

## Logo hydration — no code changes needed

The existing `IssuerLogoHydrationJob` automatically handles all new issuers. It runs on every app startup and queries:

```kotlin
// IssuerEntityRepository
findByLogoKeyIsNullAndDomainIsNotNull()
```

Because all new issuers in the migration have `domain` set and `logo_key = NULL` (default), they will be picked up automatically on the next deployment. The job:

- Runs on a **virtual thread** — does not block startup
- Is **idempotent** — already-hydrated issuers are skipped
- Has a **200ms rate-limit delay** between requests (logo.dev free tier: ~5 req/s)
- Handles failures gracefully — logs a warning and continues; `logo_key` stays `NULL`

### Coverage concerns for crypto exchanges

logo.dev fetches logos by domain. Coverage quality varies:

| Exchange | Domain | logo.dev coverage |
|----------|--------|-------------------|
| Binance | binance.com | ✅ Good |
| Coinbase | coinbase.com | ✅ Good |
| Kraken | kraken.com | ✅ Good |
| Bybit | bybit.com | ✅ Good |
| OKX | okx.com | ✅ Good |
| Mercado Bitcoin | mercadobitcoin.com.br | ⚠️ May vary |
| Foxbit | foxbit.com.br | ⚠️ May vary |
| Novadax | novadax.com | ⚠️ May vary |
| BitPreço | bitpreco.com.br | ⚠️ May vary |
| Ripio | ripio.com | ⚠️ May vary |

When logo.dev returns an error for a domain, the job logs a warning and `logo_key` remains `NULL` for that issuer. **This is expected and acceptable** — the frontend `Avatar` component already handles missing logos by falling back to the institution's initials (documented in `shared-components-pattern.md`). No visual breakage occurs.

### No action required

The hydration is fully automatic. After the migration runs and the service restarts, all new issuers with domains will be processed. Failed logos can be re-attempted by clearing `logo_key` and restarting — or by upgrading to a logo.dev paid tier for better coverage.

---

## Acceptance Criteria

- [ ] `banking.issuers` table has `category TEXT NOT NULL DEFAULT 'BANK'` column
- [ ] All 37 existing issuers have a category assigned (none null)
- [ ] ~10 new brokerages inserted with `category = 'BROKERAGE'`
- [ ] ~10 new crypto exchanges inserted with `category = 'CRYPTO_EXCHANGE'`
- [ ] `issuers` GraphQL query accepts optional `categories: [IssuerCategory!]` — no `categories` = returns all (backwards compatible)
- [ ] `Issuer` type exposes `category: IssuerCategory!`
- [ ] `CreateInvestmentAccountDrawer` shows only `BANK + BROKERAGE` issuers for BROKERAGE/FIXED_INCOME/PENSION subtypes
- [ ] `CreateInvestmentAccountDrawer` shows only `CRYPTO_EXCHANGE` issuers for CRYPTO subtype
- [ ] `CreateFinancialAccountDrawer` (bank/card flow) is unaffected — still returns all issuers

---

## Issuer seed reference

### Existing → category assigned by migration

| Institution | Category |
|-------------|----------|
| Nubank, Mercado Pago, Banco Inter, PicPay, Banco do Brasil, Caixa, Agibank, Stone, C6 Bank, Santander, Itaú, BRB, Bradesco, PagBank, BTG Pactual, Safra, Citibank Brasil, Banco Votorantim, Neon, Next, Banco Original, Will Bank, Nomad, Wise, InfinitePay, Sicoob, Sicredi, Unicred | `BANK` |
| XP Investimentos, Rico, Clear | `BROKERAGE` |
| 99Pay | `PAYMENT` |
| VR Benefícios, Alelo, Sodexo, Ticket, Caju, Flash Benefícios, Swile | `BENEFIT` |

### New brokerages inserted

| Institution | Domain | COMPE | Notes |
|-------------|--------|-------|-------|
| NuInvest | nuinvest.com.br | 140 | Formerly Easynvest, acquired by Nubank 2021 |
| Toro Investimentos | toroinvestimentos.com.br | — | DTVM, no own COMPE |
| Warren | warren.com.br | — | DTVM |
| Órama | orama.com.br | — | DTVM |
| Genial Investimentos | genialinvestimentos.com.br | — | DTVM |
| Guide Investimentos | guideinvestimentos.com.br | — | DTVM (BTG infrastructure) |
| Modal Mais | modal.com.br | 394 | Banco Modal |
| Ativa Investimentos | ativainvestimentos.com.br | 188 | |
| Mirae Asset | miraeasset.com.br | 447 | Korean-origin, BR subsidiary |
| Avenue | avenue.us | — | US-regulated (FINRA); no COMPE |

> **Note**: Many Brazilian brokerages operate as DTVM (Distribuidora de Títulos e Valores Mobiliários) without their own COMPE code. Their `compe_code` and `ispb` are `NULL`. This is expected and correct.

### New crypto exchanges inserted

| Exchange | Domain | Notes |
|----------|--------|-------|
| Mercado Bitcoin | mercadobitcoin.com.br | Largest BR-native exchange (GBTech group) |
| Foxbit | foxbit.com.br | BR-native, oldest active exchange |
| Novadax | novadax.com | BR-native, Ant Group investment |
| BitPreço | bitpreco.com.br | BR-native |
| Ripio | ripio.com | LATAM-native (Argentina), strong BR presence |
| Binance | binance.com | World's largest exchange |
| Coinbase | coinbase.com | US-listed, popular globally |
| Kraken | kraken.com | US-based, strong in BR |
| Bybit | bybit.com | Derivatives-focused, growing BR user base |
| OKX | okx.com | Top global exchange |

> All crypto exchanges have `compe_code = NULL` and `ispb = NULL` — they are VASPs regulated by BCB Resolution 96/2022, not banks.
