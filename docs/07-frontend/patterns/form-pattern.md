---
title: Form Pattern
type: pattern
category: frontend
status: current
version: 1.0.0
tags: [forms, react-hook-form, zod, validation, drawer, inline, pattern, frontend]
ai_optimized: true
search_keywords: [form, useForm, FormProvider, zodResolver, zod, react-hook-form, drawer form, page form, inline form, FormStack, FormRowStack, FormRow, FormTextField, MoneyInput, DatePickerField, SelectField, NumberField, PercentField, ErrorAlert, Drawer.ActionButtons, validation, size, medium, small]
related:
  - 04-patterns/frontend-patterns/graphql-mutation-pattern.md
  - 04-patterns/frontend-patterns/mutation-pattern.md
  - 04-patterns/frontend-patterns/shared-components-pattern.md
---

# Form Pattern

> **Purpose**: Standardize how forms are built, validated, and structured across all contexts — drawer forms, page forms, and exceptional inline forms.

## Overview

All forms are built with **react-hook-form** + **Zod** for schema validation. Fields use shared controlled components from `@/shared/components/ui/forms/form` that connect to the form context internally via `useFormContext()`.

There are three distinct form contexts in this app. Each has its own structure:

| Context | When to use | Layout wrapper |
|---|---|---|
| **Drawer form** | Most mutations (create/edit inside a Drawer) | `Drawer.Body` + `FormStack` |
| **Page form** | Full-page flows (auth, onboarding) | `FormLayout` |
| **Inline form** | Exceptional table-embedded entry | Local state, no RHF |

---

## 1. Drawer Form (standard)

### Structure

```tsx
// MyDrawer.tsx
<Drawer open={open} onClose={onClose} size="md">
  <Drawer.Header title={t("...")} />
  <Drawer.Body>
    <FormProvider {...methods}>
      <form id={formId} onSubmit={handleSubmit(onSubmit)}>
        <FormStack>
          <ErrorAlert error={mutationError} />

          <FormRowStack>
            <MoneyInput name="amount" label={t("...")} currency={currencyCode} size="medium" />
            <DatePickerField name="date" label={t("...")} size="medium" />
          </FormRowStack>

          <FormTextField name="description" label={t("...")} size="medium" multiline minRows={2} />

          <Divider />

          <Drawer.ActionButtons
            cancelText={t("actions.cancel")}
            submitText={t("actions.save")}
            submittingText={t("actions.saving")}
            onCancel={onClose}
            submitForm={formId}
            cancelDisabled={submitting}
            submitDisabled={submitting}
            submitting={submitting}
            cancelTestId="..."
            submitTestId="..."
          />
        </FormStack>
      </form>
    </FormProvider>
  </Drawer.Body>
</Drawer>
```

### Rules

- `FormProvider` wraps the `<form>` tag, not the other way around.
- The form gets an `id` (e.g., `"edit-cash-form"`). `Drawer.ActionButtons` receives `submitForm={formId}` so the submit button can live outside the `<form>` DOM tree.
- **All fields in drawer forms use `size="medium"` explicitly.** Field components have inconsistent defaults (`MoneyInput` defaults to `"small"`, `TextField` defaults to `"medium"`) — always pass `size="medium"` to avoid visual inconsistency.
- Use `FormStack` (not raw MUI `Stack`) for the vertical layout — it sets `spacing=1.5` by default.
- Use `FormRowStack` for horizontal field groups — it is responsive (`direction={{ xs: "column", sm: "row" }}`).
- Mutation errors go at the **top** of the form via `<ErrorAlert error={mutationError} />`. Do not use `FormErrorBanner` — it is a redundant wrapper around `ErrorAlert`. Use `ErrorAlert` directly.
- A `<Divider />` before `Drawer.ActionButtons` provides visual separation from the fields.

### Form setup

```tsx
const schema = React.useMemo(
  () =>
    z.object({
      amount: z.number().positive(),
      date: z.date(),
      description: z.string().optional(),
    }).superRefine((data, ctx) => {
      // custom cross-field validation
      if (dayjs(data.date).isAfter(dayjs())) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: t("validation.dateNotInFuture"),
          path: ["date"],
        });
      }
    }),
  [t],  // include locale separators if the schema parses raw strings
);

const methods = useForm<MyFormValues>({
  resolver: zodResolver(schema),
  defaultValues: {
    amount: initialAmount,
    date: dayjs(initialDate).toDate(),
    description: initialDescription ?? "",
  },
});
```

- Define the schema inside `React.useMemo` when it depends on `t()` (translations) or locale separators (to avoid stale closures).
- `defaultValues` must always be declared explicitly. Do not rely on schema defaults for form initialization.
- To reset on prop change: `useEffect(() => { reset({ ... }) }, [transactionId, resetSignal, reset])`.

### Dates

- Store as `Date` objects in the form (via `dayjs(...).toDate()`).
- Submit as ISO string: `dayjs(data.date).format("YYYY-MM-DD")`.
- Use `maxDate={dayjs().startOf("day").toDate()}` to prevent future dates.
- Use `getFinanceMinAllowedDate()` for the finance transaction window constraint.

---

## 2. Page Form (auth / full-page)

```tsx
<FormProvider {...methods}>
  <Box component="form" onSubmit={methods.handleSubmit(onSubmit)} noValidate>
    <Stack gap={3}>
      <ErrorAlert error={localError} />
      ...fields...
      <Button type="submit" variant="contained" loading={isSubmitting}>
        {t("submit")}
      </Button>
    </Stack>
  </Box>
</FormProvider>
```

- Use `<Box component="form">` (not a separate `<form>` tag).
- Add `noValidate` to disable native browser validation.
- Error state is typically `useState<string | null>(null)` set inside the submit handler's `catch` block.
- Use `FormLayout` for pages that need max-width centering.

---

## 3. Inline Form (exceptional)

`InlineTransactionForm` is the canonical example. It embeds inside a `<TableRow>` and requires a compact, borderless appearance that is incompatible with standard drawer form aesthetics.

**Use local state instead of react-hook-form** when:
- The form is embedded in a table or other dense layout.
- Fields need `variant="standard"` + `disableUnderline` styling.
- Keyboard UX requires custom Enter-key handling.

```tsx
const [amount, setAmount] = useState<number | undefined>();
const [description, setDescription] = useState("");
const [date, setDate] = useState<dayjs.Dayjs | null>(dayjs());

// Use BaseMoneyInput (uncontrolled) instead of MoneyInput (requires FormProvider)
<BaseMoneyInput
  value={amount}
  onChange={(val) => setAmount(val as number | undefined)}
  variant="standard"
  InputProps={{ disableUnderline: true, sx: inputBottomBorderSx }}
/>

// MUI TextField directly (not FormTextField)
<TextField
  value={description}
  onChange={(e) => setDescription(e.target.value)}
  variant="standard"
  InputProps={{ disableUnderline: true }}
/>
```

- `BaseMoneyInput` is the version of `MoneyInput` that does not require `FormProvider`.
- All inputs use `variant="standard"` with `disableUnderline: true`.
- Bottom border styling (`inputBottomBorderSx`) simulates underline focus state.
- Validation is imperative inside the save handler (`if (!amount || !description || !date) return`).

---

## Field Reference

All fields below must be inside `<FormProvider>`. Import from `@/shared/components/ui/forms/form`.

| Field | Use for | Key props |
|---|---|---|
| `FormTextField` | Text, multiline text | `name`, `label`, `size`, `multiline`, `minRows` |
| `MoneyInput` | Currency amounts | `name`, `currency`, `size`, `allowNegative` |
| `NumberField` | Generic numbers | `name`, `fractionDigits`, `min`, `max` |
| `PercentField` | Percentages (0–100) | `name`, `fractionDigits`, `ratio` (for 0–1 storage) |
| `DatePickerField` | Date selection | `name`, `size`, `minDate`, `maxDate`, `disableFuture` |
| `DateRangeField` | Date range (start + end) | `nameStart`, `nameEnd`, `size` |
| `SelectField` | Enum / option list | `name`, `options: {label, value, icon?}[]`, `size` |
| `AutocompleteField` | Searchable list | `name`, `options`, `getOptionLabel` |
| `CheckboxField` | Boolean toggle | `name`, `label` |
| `ToggleField` | Segmented control | `name`, `options` |
| `PercentField` | Rate / percentage | `name`, `fractionDigits`, `ratio` |
| `MaskedField` | Masked input (phone, etc.) | `name`, `mask` |

Brazilian-specific fields are in `@/shared/components/ui/forms/form/br`: `CPFField`, `CNPJField`, `CEPField`.

---

## Layout Primitives

Import from `@/shared/components/ui/forms/components`.

| Component | Use for |
|---|---|
| `FormStack` | Vertical field stack inside a form (`spacing=1.5` default) |
| `FormRowStack` | Horizontal field groups, responsive (`column` on xs, `row` on sm+) |
| `FormRow` | Equal-column grid layout via MUI Grid (use for complex multi-column layouts) |
| `FormLayout` | Full-page form centering with max-width |
| `ErrorAlert` | Mutation or submit errors (from `@/shared/components/ui/feedback`) |

Do **not** use raw MUI `Stack` inside forms — use `FormStack` / `FormRowStack` so spacing is consistent.

---

## Error Handling

```tsx
// Mutation error — always at the top of the form
<ErrorAlert error={mutationError} />

// Local (non-mutation) error — page forms
const [error, setError] = useState<string | null>(null);
// in submit catch:
setError(extractErrorMessage(err, t("errors.unknown")));
<ErrorAlert error={error} />
```

Do not use `FormErrorBanner`. It is a thin wrapper over `ErrorAlert` that adds no value. Use `ErrorAlert` directly.

---

## Validation Rules

- Always use `zodResolver` — no manual `validate` functions in `register()`.
- Define schema in `React.useMemo` when the schema closure references `t()` or locale state.
- Use `.superRefine()` for cross-field rules (e.g., date cannot be in the future, amount must be positive when non-null).
- Numeric fields (`MoneyInput`, `NumberField`, `PercentField`) perform locale-aware string-to-number preprocessing in the schema. Include `localeDecimal` / `localeGroup` in the `useMemo` deps array when needed.
- Error messages must come from `t()` — no hardcoded strings.

---

## Anti-Patterns

| Anti-pattern | Correct approach |
|---|---|
| Raw `<Stack spacing={3}>` in a form | Use `<FormStack>` |
| `<FormErrorBanner message={mutationError.message} />` | `<ErrorAlert error={mutationError} />` |
| `size="small"` on drawer form fields | Always `size="medium"` in drawers |
| Schema defined outside `useMemo` when using `t()` | Wrap in `React.useMemo([t, ...locale])` |
| `MoneyInput` / `DatePickerField` without `<FormProvider>` | These require `FormProvider`; use `BaseMoneyInput` for uncontrolled contexts |
| Using the `Form` component from `form/Form.tsx` | Too limited for real use cases; use `useForm` + `FormProvider` directly |
