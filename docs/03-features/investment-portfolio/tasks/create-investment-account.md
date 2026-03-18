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

### Key patterns to follow — from `CreateFinancialAccountDrawer` (the reference)

> Read `CreateFinancialAccountDrawer.tsx` in full before starting. The investment drawer must match it exactly in structure and feel.

- **No hardcoded colors** — always `theme.palette.*` tokens
- **No GraphQL fragments** — inline all fields
- **`Drawer`** — `anchor="right" size="md"`, title in `Drawer.Header`, submit button inside `Drawer.Body` after `<Divider>` (NOT `Drawer.Footer` — the existing drawer does not use it)
- **`FormProvider`** wraps the `<form>` for `react-hook-form` context sharing
- **`FormErrorBanner`** at the top of the form for mutation errors
- **`useToast()`** success notification after creation
- **`skip: !open`** on the `ISSUERS` query — do not fetch when drawer is closed
- **Import paths**: queries and mutations come from `@/lib/graphql/operations/banking/` (not from `features/`)

### GraphQL imports

```typescript
// Queries — already exist, do NOT duplicate or modify
import { ISSUERS, FINANCIAL_ACCOUNTS } from '@/lib/graphql/operations/banking/queries';
// Mutation — already exists
import { CREATE_FINANCIAL_ACCOUNT } from '@/lib/graphql/operations/banking/mutations';
```

The `ISSUERS` query now accepts `categories: [IssuerCategory!]` (added in Task 0). Use it to filter issuers by selected subtype:

```typescript
const issuerCategories = selectedSubtype === 'CRYPTO'
  ? ['CRYPTO_EXCHANGE']
  : ['BANK', 'BROKERAGE'];           // for BROKERAGE, FIXED_INCOME, PENSION

const { data: issuersData, loading: issuersLoading } = useQuery(ISSUERS, {
  variables: { categories: issuerCategories },
  skip: !open,
});
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

Single-step form. Always creates `type: INVESTMENT`. No type selection step.

---

#### 1a — Props

```typescript
interface CreateInvestmentAccountDrawerProps {
  open: boolean;
  onClose: () => void;
  /** Called after successful creation. Matches the onCreated pattern from CreateFinancialAccountDrawer. */
  onCreated?: (account: { id: string; name: string }) => void;
}
```

---

#### 1b — Form data type & Zod schema

```typescript
// Exact same pattern as CreateFinancialAccountDrawer — z.object() + superRefine for cross-field
type CreateInvestmentAccountFormData = {
  subtype: 'BROKERAGE' | 'FIXED_INCOME' | 'PENSION' | 'CRYPTO';
  issuerId?: string;
  issuerName?: string;  // used when issuerId === 'custom'
  name: string;
};

const schema = React.useMemo(() => z.object({
  subtype: z.enum(['BROKERAGE', 'FIXED_INCOME', 'PENSION', 'CRYPTO'], {
    required_error: t('investments.createAccount.validation.subtypeRequired'),
  }),
  issuerId: z.string().optional(),
  issuerName: z.string().optional(),
  name: z.string().default(''),
}).superRefine((data, ctx) => {
  if (!data.issuerId && !data.issuerName) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      message: t('investments.createAccount.validation.issuerRequired'),
      path: ['issuerId'],
    });
  }
}), [t]);
```

---

#### 1c — Hooks setup

```typescript
const { success } = useToast();              // same as CreateFinancialAccountDrawer
const theme = useTheme();
const { t } = useTranslation(personalFinanceTranslations);

const methods = useForm<CreateInvestmentAccountFormData>({
  resolver: zodResolver(schema),
  defaultValues: { subtype: 'BROKERAGE', name: '', issuerId: undefined, issuerName: '' },
});
const { watch, setValue, reset, control } = methods;

const watchedSubtype  = watch('subtype');
const watchedIssuerId = watch('issuerId');

// Reset when drawer closes — same pattern as CreateFinancialAccountDrawer
useEffect(() => {
  if (!open) reset();
}, [open, reset]);
```

---

#### 1d — ISSUERS query with category filter

```typescript
// Re-compute categories when subtype changes
const issuerCategories = React.useMemo(() =>
  watchedSubtype === 'CRYPTO' ? ['CRYPTO_EXCHANGE'] : ['BANK', 'BROKERAGE'],
  [watchedSubtype]
);

const { data: issuersData, loading: issuersLoading } = useQuery<{ issuers: Issuer[] }>(ISSUERS, {
  variables: { categories: issuerCategories },
  skip: !open,   // ← same pattern as CreateFinancialAccountDrawer
});

// Build options including 'custom' — identical pattern to CreateFinancialAccountDrawer
const allIssuerOptions = React.useMemo(() => {
  const options = (issuersData?.issuers || []).map(i => ({
    label: i.name,
    value: i.id,
    logo: i.logoAsset?.publicUrl,
    compe: i.compeCode,
  }));
  return [
    ...options,
    { label: t('financeTransactions.createAccount.step2.fields.customIssuer'), value: 'custom', logo: null, compe: null },
  ];
}, [issuersData, t]);
```

---

#### 1e — Auto-fill name when issuer selected

This is the same smart behaviour from `CreateFinancialAccountDrawer`: when the user selects a known issuer, the account name is auto-filled. When "custom" is selected, name is cleared.

```typescript
useEffect(() => {
  if (!watchedIssuerId || !issuersData?.issuers) return;
  if (watchedIssuerId === 'custom') {
    setValue('name', '');
    setValue('issuerName', '');
  } else {
    const issuer = issuersData.issuers.find(i => i.id === watchedIssuerId);
    if (issuer) {
      const subtypeLabel = t(`investments.createAccount.subtypes.${watchedSubtype}`);
      setValue('name', `${subtypeLabel} - ${issuer.name}`);
      // e.g. "Corretora de Valores - XP Investimentos"
      setValue('issuerName', issuer.name);
    }
  }
}, [watchedIssuerId, issuersData, watchedSubtype, setValue, t]);
```

---

#### 1f — Mutation

```typescript
// Same pattern as CreateFinancialAccountDrawer: refetchQueries + onCompleted
const [createAccount, { loading: creating, error: createError }] = useMutation<{
  createFinancialAccount: { id: string; name: string };
}>(CREATE_FINANCIAL_ACCOUNT, {
  refetchQueries: [{ query: FINANCIAL_ACCOUNTS }],
  onCompleted: (data) => {
    success(t('investments.createAccount.success'));
    onCreated?.({ id: data.createFinancialAccount.id, name: data.createFinancialAccount.name });
    onClose();
  },
});

const onSubmit = (data: CreateInvestmentAccountFormData) => {
  createAccount({
    variables: {
      input: {
        type: 'INVESTMENT',
        subtype: data.subtype,
        name: data.name || undefined,
        currencyCode: 'BRL',
        includeInNetWorth: true,
        includeInDashboard: true,
        ...(data.issuerId === 'custom'
          ? { issuerName: data.issuerName }
          : { issuerId: data.issuerId }),
        // No bankData, cardData, initialBalance
      },
    },
  });
};
```

---

#### 1g — Drawer structure

> This replicates the exact DOM structure of `CreateFinancialAccountDrawer` step 2. The submit button is inside `Drawer.Body` after a `<Divider>` — the existing drawer does NOT use `Drawer.Footer`.

```tsx
return (
  <Drawer open={open} onClose={onClose} anchor="right" size="md">
    <Drawer.Header
      title={
        watchedSubtype
          ? t(`investments.createAccount.subtypes.${watchedSubtype}`)
          : t('investments.createAccount.title')
      }
      // No back button — this drawer always starts at the form directly.
      // The [×] close button is provided by Drawer.Header by default.
    />
    <Drawer.Body>
      <FormProvider {...methods}>
        <form onSubmit={methods.handleSubmit(onSubmit)}>
          <Stack spacing={3}>
            {createError && <FormErrorBanner message={createError.message} />}

            {/* 1. Subtype — first so issuer list filters correctly */}
            <SelectField
              name="subtype"
              label={t('investments.createAccount.fields.subtypeLabel')}
              options={investmentSubtypes}   // built with useMemo from subtype enum + t()
              data-testid="investment-subtype-select"
            />

            {/* 2. Issuer autocomplete — EXACT same implementation as CreateFinancialAccountDrawer */}
            <Controller
              name="issuerId"
              control={control}
              render={({ field: { onChange, value }, fieldState: { error } }) => (
                <Autocomplete
                  options={allIssuerOptions}
                  loading={issuersLoading}
                  value={allIssuerOptions.find(o => o.value === value) || null}
                  onChange={(_, newValue) => onChange(newValue ? newValue.value : null)}
                  isOptionEqualToValue={(option, value) => option.value === value.value}
                  noOptionsText={t('financeTransactions.createAccount.step2.fields.noIssuersFound')}
                  getOptionLabel={(option) => option.label || ''}
                  renderOption={(props, option) => {
                    const { key, ...otherProps } = props;
                    return (
                      <Box component="li" key={key} {...otherProps} sx={{ display: 'flex', alignItems: 'center', gap: 2, py: 1.5 }}>
                        {option.logo ? (
                          <Avatar src={option.logo} sx={{ width: 40, height: 40, borderRadius: 1.5 }} />
                        ) : (
                          <Avatar sx={{ width: 40, height: 40, borderRadius: 1.5, bgcolor: 'action.disabledBackground' }}>
                            <BankIcon sx={{ fontSize: 24 }} />
                          </Avatar>
                        )}
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                          <Typography variant="body1" fontWeight="600">{option.label}</Typography>
                          {option.compe && (
                            <Typography variant="body2" color="text.secondary">({option.compe})</Typography>
                          )}
                        </Box>
                      </Box>
                    );
                  }}
                  renderInput={(params) => (
                    <MuiTextField
                      {...params}
                      label={t('investments.createAccount.fields.issuerLabel')}
                      error={!!error}
                      helperText={error?.message}
                      InputProps={{
                        ...params.InputProps,
                        endAdornment: (
                          <React.Fragment>
                            {issuersLoading ? <CircularProgress color="inherit" size={20} /> : null}
                            {params.InputProps.endAdornment}
                          </React.Fragment>
                        ),
                      }}
                    />
                  )}
                />
              )}
            />

            {/* 3. Custom issuer name — only when 'custom' selected, same as bank/card flow */}
            {watchedIssuerId === 'custom' && (
              <TextField
                name="issuerName"
                label={t('investments.createAccount.fields.customIssuerLabel')}
                required
              />
            )}

            {/* 4. Account name — auto-filled, editable */}
            <TextField
              name="name"
              label={t('investments.createAccount.fields.accountNameLabel')}
              placeholder={t('investments.createAccount.fields.accountNamePlaceholder')}
              data-testid="investment-account-name-input"
            />
          </Stack>
        </form>
      </FormProvider>

      {/* Submit — inside Drawer.Body after Divider, same structure as CreateFinancialAccountDrawer */}
      <Divider sx={{ marginY: 3 }} />
      <Box sx={{ display: 'flex', justifyContent: 'flex-end' }}>
        <Button
          variant="contained"
          onClick={methods.handleSubmit(onSubmit)}
          disabled={creating}
          startIcon={creating ? <CircularProgress size={20} color="inherit" /> : null}
        >
          {creating
            ? t('investments.createAccount.actions.submitting')
            : t('investments.createAccount.actions.submit')}
        </Button>
      </Box>
    </Drawer.Body>
  </Drawer>
);
```

---

#### 1h — Form layout (visual reference)

```
┌────────────────────────────────────────────────────┐
│  ← [back]  Corretora de Valores            [×]    │  ← Drawer.Header — title = selected subtype label
├────────────────────────────────────────────────────┤
│                                                    │
│  Tipo de conta *                                   │
│  [ Corretora de Valores               ▾ ]          │  ← SelectField (subtype)
│                                                    │
│  Corretora / Instituição *                         │
│  [🏦 Buscar por nome...               ▾ ]          │  ← Autocomplete (logos + custom option)
│                                                    │
│  ─── (only if 'custom' selected) ───────────────── │
│  Nome da instituição *                             │
│  [ XP Investimentos                   ]            │
│                                                    │
│  Nome da conta                                     │
│  [ Corretora de Valores - XP Invest…  ]            │  ← auto-filled, editable
│                                                    │
│  ────────────────────────────────────────────────  │  ← <Divider sx={{ marginY: 3 }} />
│                                      [Criar conta] │  ← Button inside Drawer.Body (no Drawer.Footer)
└────────────────────────────────────────────────────┘
```

> **Note**: `Drawer.Footer` is NOT used. This matches the exact rendering pattern from `CreateFinancialAccountDrawer`.

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
  onCreated={() => {
    // onCreated fires from inside the drawer after mutation succeeds + toast shown
    // No need to close here — the drawer calls onClose() internally in onCompleted
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
