# Task: Create Investment Account

> **Goal**: Allow users to create an investment custody account (corretora/broker) from the investments page empty state.
> **Entry point**: "Criar conta de investimento" (primary CTA) on `/personal-finance/investments`
> **Backend**: Already fully implemented — `createFinancialAccount` mutation supports `type: INVESTMENT`
> **Effort**: Medium

---

## Architectural decision

The investment account creation flow is **completely separate** from the existing bank/card account creation flow (`CreateFinancialAccountDrawer`).

**Do NOT modify:**
- `CreateFinancialAccountDrawer.tsx` — belongs to the finance-transactions domain (bank + card only)
- `FinancialAccountTypeSelection.tsx` — belongs to the finance-transactions domain

**Why**: Mixing banking and investment concerns in a shared component violates separation of concerns. When a user is in the investments area, they are always creating an investment account — there is no type selection step, no bank fields, no card fields. A dedicated component is simpler, cleaner, and easier to evolve independently.

**Solution**: Create a new, purpose-built `CreateInvestmentAccountDrawer` that lives inside the investments feature. It calls the same `createFinancialAccount` mutation but is fully focused on investment accounts.

---

## Mandatory: Load context before starting

| Doc | Why |
|-----|-----|
| `docs/07-frontend/patterns/i18n-pattern.md` | i18n co-location rules |
| `docs/07-frontend/patterns/styling-pattern.md` | **No hardcoded colors** rule |
| `docs/07-frontend/patterns/graphql-mutation-pattern.md` | `refetchQueries` with all current variables |
| `docs/07-frontend/patterns/testing-pattern.md` | `data-testid`, `describe.sequential`, `@testing-library/user-event` |
| `docs/07-frontend/patterns/shared-components-pattern.md` | Shared form components reference |
| `docs/94-validation/feature-checklist.md` | Run before marking done |

---

## Specification (Gherkin)

```gherkin
Feature: Create Investment Account
  As a user
  I want to create an investment custody account
  So that I can start tracking my investment portfolio

  Background:
    Given I am authenticated
    And I am on the /personal-finance/investments page
    And my portfolio is empty

  Scenario: Open the investment account drawer from the empty state
    When I click "Criar conta de investimento"
    Then a drawer opens on the right side
    And the form shows directly — no type selection step

  Scenario: Create a brokerage account with a known issuer
    Given the investment account drawer is open
    When I select subtype "Corretora de Valores"
    And I select issuer "XP Investimentos" from the autocomplete
    And I leave the account name blank
    And I click "Criar"
    Then the mutation createFinancialAccount is called with:
      | type     | INVESTMENT |
      | subtype  | BROKERAGE  |
      | issuerId | <XP UUID>  |
    And the drawer closes
    And the page refreshes

  Scenario: Create a brokerage account with a custom broker name
    Given the investment account drawer is open
    When I select subtype "Corretora de Valores"
    And I select "Outro / Personalizado" in the issuer field
    And I type "Rico Investimentos" in the institution name field
    And I type "Minha Carteira Rico" in the account name field
    And I click "Criar"
    Then the mutation createFinancialAccount is called with:
      | type       | INVESTMENT          |
      | subtype    | BROKERAGE           |
      | issuerName | Rico Investimentos  |
      | name       | Minha Carteira Rico |

  Scenario: Validation — subtype is required
    Given the investment account drawer is open
    When I select issuer "XP Investimentos"
    And I click "Criar" without selecting a subtype
    Then a validation error appears below the subtype field
    And the mutation is NOT called

  Scenario: Validation — issuer or custom name is required
    Given the investment account drawer is open
    When I select subtype "Corretora de Valores"
    And I leave both issuer and custom name blank
    And I click "Criar"
    Then a validation error appears below the issuer field
    And the mutation is NOT called

  Scenario: The form never shows bank or card fields
    Given the investment account drawer is open
    Then I do NOT see: branch code, account number, card network,
                       credit limit, closing day, due day, initial balance
```

---

## Context & Existing Infrastructure

Read these files before starting:

| File | Purpose |
|------|---------|
| `web/src/features/finance-transactions/api/queries.ts` | Source of `CREATE_FINANCIAL_ACCOUNT` mutation and `GET_FINANCIAL_ACCOUNTS` query — **do not modify** |
| `web/src/features/finance-transactions/api/queries.generated.ts` | Auto-generated types — **do not edit manually** |
| `web/src/app/personal-finance/finance-transactions/components/CreateFinancialAccountDrawer.tsx` | Read to understand the existing drawer pattern and form structure — **do not modify** |
| `web/src/app/personal-finance/investments/page.tsx` | The investments empty state page — wire the CTA here |
| `web/src/app/personal-finance/i18n/locales/pt.json` | Module-level i18n — add investment account creation keys here |
| `web/src/app/personal-finance/i18n/locales/en.json` | English i18n |
| `web/src/shared/components/ui/forms/` | Shared form components: `TextField`, `SelectField`, `Autocomplete` |
| `web/src/shared/components/ui/layout/Drawer.tsx` | Composable drawer: `Drawer.Header`, `Drawer.Body`, `Drawer.Footer` |

### Key patterns to follow

- **No hardcoded colors** — always `theme.palette.*` tokens (styling-pattern.md)
- **No GraphQL fragments** — inline all fields in the mutation selection set (fragments are forbidden)
- **Validation**: `react-hook-form` + `zod`
- **Mutation**: `useMutation(CREATE_FINANCIAL_ACCOUNT)` with `refetchQueries: [GET_FINANCIAL_ACCOUNTS]` — include all current variables
- **Drawer**: `<Drawer anchor="right" size="md">` with composable `Drawer.Header` / `Drawer.Body` / `Drawer.Footer`

### GraphQL (no backend or query changes needed)

```graphql
# Already exists in finance-transactions/api/queries.ts — do not duplicate
mutation CreateFinancialAccount($input: CreateFinancialAccountInput!) {
  createFinancialAccount(input: $input) {
    id
    type
    subtype
    name
    currencyCode
    includeInNetWorth
    includeInDashboard
    issuerName
    issuer { id name domain logoKey logoAsset { id } }
  }
}
```

**Input for investment accounts:**
```typescript
{
  type: "INVESTMENT",
  subtype: "BROKERAGE" | "FIXED_INCOME" | "PENSION" | "CRYPTO",
  name?: string,          // optional — defaults to issuer name when blank
  issuerId?: string,      // UUID — mutually exclusive with issuerName
  issuerName?: string,    // custom broker — mutually exclusive with issuerId
  currencyCode: "BRL",
  includeInNetWorth: true,
  includeInDashboard: true,
  // bankData: omitted
  // cardData: omitted
  // initialBalance: omitted — investment positions start at R$0 via transactions
}
```

### Investment subtypes

| Subtype | PT label | EN label | Use case |
|---------|----------|----------|----------|
| `BROKERAGE` | Corretora de Valores | Brokerage | Stocks, FIIs, ETFs, BDRs, Debentures |
| `FIXED_INCOME` | Renda Fixa | Fixed Income | CDB, LCI, LCA, CRI, CRA, Tesouro |
| `PENSION` | Previdência Privada | Pension | PGBL, VGBL |
| `CRYPTO` | Criptomoedas | Crypto | Bitcoin, ETH, etc. |

---

## Activities

### Activity 1 — Create `CreateInvestmentAccountDrawer`

**File to create**: `web/src/app/personal-finance/investments/components/CreateInvestmentAccountDrawer.tsx`

This is a new, dedicated component. It always creates an `INVESTMENT` account — no type selection step.

**Form layout** (single step):
```
┌────────────────────────────────────────────────────┐
│  Criar conta de investimento               [×]     │  ← Drawer.Header
├────────────────────────────────────────────────────┤
│                                                    │
│  Tipo de conta *                                   │
│  [ Corretora de Valores          ▾ ]               │  ← SelectField, data-testid="investment-subtype-select"
│                                                    │
│  Corretora / Instituição *                         │
│  [ Buscar por nome...            ▾ ]               │  ← existing Autocomplete (logos + search + custom)
│                                                    │
│  Nome da conta  (opcional)                         │
│  [ Ex: Minha Carteira XP           ]               │  ← TextField, data-testid="investment-account-name-input"
│                                                    │
├────────────────────────────────────────────────────┤
│  [Cancelar]                     [Criar conta]      │  ← Drawer.Footer
└────────────────────────────────────────────────────┘
```

**Zod schema:**
```typescript
const schema = z.object({
  subtype: z.enum(['BROKERAGE', 'FIXED_INCOME', 'PENSION', 'CRYPTO'], {
    required_error: t('investments.createAccount.validation.subtypeRequired'),
  }),
  issuerId: z.string().optional(),
  issuerName: z.string().optional(),
  name: z.string().optional(),
}).refine(
  (data) => !!data.issuerId || (!!data.issuerName && data.issuerName.trim().length > 0),
  {
    message: t('investments.createAccount.validation.issuerRequired'),
    path: ['issuerId'],
  }
);
```

**Component props:**
```typescript
interface CreateInvestmentAccountDrawerProps {
  open: boolean;
  onClose: () => void;
  onSuccess?: () => void;
}
```

**onSubmit** builds:
```typescript
const input: CreateFinancialAccountInput = {
  type: FinancialAccountType.Investment,   // always INVESTMENT
  subtype: data.subtype as FinancialAccountSubtype,
  name: data.name?.trim() || undefined,
  issuerId: data.issuerId || undefined,
  issuerName: !data.issuerId ? data.issuerName?.trim() : undefined,
  currencyCode: 'BRL',
  includeInNetWorth: true,
  includeInDashboard: true,
  // No bankData, cardData, initialBalance
};
```

**Reuse the issuer Autocomplete** from `CreateFinancialAccountDrawer`. If it is not extracted into a shared component yet, extract it first into `web/src/shared/components/ui/forms/IssuerAutocomplete.tsx` and use it in both places. This avoids duplication.

---

### Activity 2 — Wire the CTA on the investments page

**File**: `web/src/app/personal-finance/investments/page.tsx`

```tsx
const [createAccountOpen, setCreateAccountOpen] = useState(false);

// CTA button:
<Button
  variant="contained"
  startIcon={<AddchartIcon />}
  onClick={() => setCreateAccountOpen(true)}
  data-testid="create-investment-account-btn"
>
  {t('investments.emptyState.primaryAction')}
</Button>

// Drawer (at the end of the JSX):
<CreateInvestmentAccountDrawer
  open={createAccountOpen}
  onClose={() => setCreateAccountOpen(false)}
  onSuccess={() => {
    setCreateAccountOpen(false);
    router.refresh();
  }}
/>
```

---

### Activity 3 — Add i18n keys

**Files**: `web/src/app/personal-finance/i18n/locales/pt.json` and `en.json`

The investment account creation keys live under `investments.createAccount` — inside the `investments` namespace that already exists in this file, keeping investment concerns together.

#### Additions to `pt.json`

```json
{
  "investments": {
    "createAccount": {
      "title": "Criar conta de investimento",
      "fields": {
        "subtypeLabel": "Tipo de conta",
        "issuerLabel": "Corretora / Instituição",
        "issuerPlaceholder": "Buscar por nome...",
        "customIssuerLabel": "Nome da instituição",
        "accountNameLabel": "Nome da conta",
        "accountNamePlaceholder": "Ex: Minha Carteira XP"
      },
      "subtypes": {
        "BROKERAGE": "Corretora de Valores",
        "FIXED_INCOME": "Renda Fixa",
        "PENSION": "Previdência Privada",
        "CRYPTO": "Criptomoedas"
      },
      "validation": {
        "subtypeRequired": "Tipo de conta é obrigatório",
        "issuerRequired": "Informe a corretora ou nome da instituição"
      },
      "actions": {
        "submit": "Criar conta",
        "submitting": "Criando...",
        "cancel": "Cancelar"
      },
      "success": "Conta de investimento criada com sucesso"
    }
  }
}
```

#### Additions to `en.json`

```json
{
  "investments": {
    "createAccount": {
      "title": "Create investment account",
      "fields": {
        "subtypeLabel": "Account type",
        "issuerLabel": "Broker / Institution",
        "issuerPlaceholder": "Search by name...",
        "customIssuerLabel": "Institution name",
        "accountNameLabel": "Account name",
        "accountNamePlaceholder": "e.g. My XP Portfolio"
      },
      "subtypes": {
        "BROKERAGE": "Brokerage",
        "FIXED_INCOME": "Fixed Income",
        "PENSION": "Pension",
        "CRYPTO": "Crypto"
      },
      "validation": {
        "subtypeRequired": "Account type is required",
        "issuerRequired": "Please enter a broker or institution name"
      },
      "actions": {
        "submit": "Create account",
        "submitting": "Creating...",
        "cancel": "Cancel"
      },
      "success": "Investment account created successfully"
    }
  }
}
```

---

### Activity 4 — Unit tests

**File to create**: `web/src/app/personal-finance/investments/components/CreateInvestmentAccountDrawer.test.tsx`

Follow the project testing pattern: `describe.sequential`, `cleanup()` in `afterEach`, `@testing-library/user-event` (not `fireEvent`), `data-testid` for element selection.

```typescript
import { describe, it, expect, afterEach, vi } from 'vitest';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { CreateInvestmentAccountDrawer } from './CreateInvestmentAccountDrawer';

describe.sequential('CreateInvestmentAccountDrawer', () => {
  afterEach(() => { cleanup(); });

  it('renders the form directly — no type selection step', () => {
    render(<CreateInvestmentAccountDrawer open onClose={vi.fn()} />, { wrapper });
    expect(screen.getByTestId('investment-subtype-select')).toBeInTheDocument();
    // No type selection cards
    expect(screen.queryByText(/banco/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/cartão/i)).not.toBeInTheDocument();
  });

  it('shows subtype validation error when submitting without subtype', async () => {
    const user = userEvent.setup();
    render(<CreateInvestmentAccountDrawer open onClose={vi.fn()} />, { wrapper });
    await selectIssuer(user, 'XP Investimentos');
    await user.click(screen.getByRole('button', { name: /criar conta/i }));
    await waitFor(() => {
      expect(screen.getByText(/tipo de conta é obrigatório/i)).toBeInTheDocument();
    });
  });

  it('shows issuer validation error when neither issuer nor custom name is provided', async () => {
    const user = userEvent.setup();
    render(<CreateInvestmentAccountDrawer open onClose={vi.fn()} />, { wrapper });
    await selectSubtype(user, 'Corretora de Valores');
    await user.click(screen.getByRole('button', { name: /criar conta/i }));
    await waitFor(() => {
      expect(screen.getByText(/informe a corretora/i)).toBeInTheDocument();
    });
  });

  it('does NOT render bank or card fields', () => {
    render(<CreateInvestmentAccountDrawer open onClose={vi.fn()} />, { wrapper });
    expect(screen.queryByLabelText(/agência/i)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/limite de crédito/i)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/saldo inicial/i)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/dia de fechamento/i)).not.toBeInTheDocument();
  });

  it('calls createFinancialAccount with type=INVESTMENT and correct subtype on valid submit', async () => {
    const mutationSpy = mockCreateMutation({ type: 'INVESTMENT', subtype: 'BROKERAGE' });
    const user = userEvent.setup();
    render(<CreateInvestmentAccountDrawer open onClose={vi.fn()} />, { wrapper: withMock(mutationSpy) });

    await selectSubtype(user, 'Corretora de Valores');
    await selectIssuer(user, 'XP Investimentos');
    await user.click(screen.getByRole('button', { name: /criar conta/i }));

    await waitFor(() => { expect(mutationSpy).toHaveBeenCalledWith(
      expect.objectContaining({ variables: { input: expect.objectContaining({ type: 'INVESTMENT' }) } })
    ); });
  });

  it('calls onSuccess and closes after successful creation', async () => {
    const onSuccess = vi.fn();
    const onClose = vi.fn();
    // ... setup with resolving mutation mock ...
    // ... submit valid form ...
    await waitFor(() => {
      expect(onSuccess).toHaveBeenCalled();
      expect(onClose).toHaveBeenCalled();
    });
  });
});
```

---

## File Change Summary

| File | Change | Notes |
|------|--------|-------|
| `investments/components/CreateInvestmentAccountDrawer.tsx` | **Create** | New dedicated drawer for investment accounts only |
| `investments/components/CreateInvestmentAccountDrawer.test.tsx` | **Create** | Unit tests |
| `app/personal-finance/investments/page.tsx` | **Modify** | Wire open state + render `<CreateInvestmentAccountDrawer>`; add `data-testid` to CTA |
| `app/personal-finance/i18n/locales/pt.json` | **Modify** | Add keys under `investments.createAccount` |
| `app/personal-finance/i18n/locales/en.json` | **Modify** | Add keys under `investments.createAccount` |
| `shared/components/ui/forms/IssuerAutocomplete.tsx` | **Create (if needed)** | Extract issuer autocomplete if not already shared |
| `finance-transactions/components/CreateFinancialAccountDrawer.tsx` | **Do not modify** | |
| `finance-transactions/components/FinancialAccountTypeSelection.tsx` | **Do not modify** | |

---

## Acceptance Criteria

- [ ] Clicking "Criar conta de investimento" opens `CreateInvestmentAccountDrawer` — not the finance-transactions drawer
- [ ] The form has no type selection step — shows investment form immediately
- [ ] Form fields: subtype selector, issuer autocomplete, account name (optional)
- [ ] Subtype options: Corretora de Valores, Renda Fixa, Previdência Privada, Criptomoedas
- [ ] No bank fields, no card fields, no initial balance field
- [ ] Mutation called with `type: INVESTMENT` + correct subtype; no `bankData` or `cardData`
- [ ] Drawer closes and `router.refresh()` called on success
- [ ] All strings use i18n keys — no hardcoded text
- [ ] All colors use `theme.palette.*` — no hardcoded hex values
- [ ] `data-testid` on: CTA button, subtype select, account name input
- [ ] All Gherkin scenarios pass
- [ ] Unit tests pass with no console errors
- [ ] `CreateFinancialAccountDrawer` and `FinancialAccountTypeSelection` are unchanged

---

## Out of scope

- Displaying the new account in the sidebar broker selector (requires Holdings list task)
- Editing or deleting investment accounts
- Open Finance / broker API sync
- The "Registrar primeiro ativo" CTA — separate task
