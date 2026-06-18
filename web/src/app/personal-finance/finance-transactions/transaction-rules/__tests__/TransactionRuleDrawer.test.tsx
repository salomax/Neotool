'use client';

import React from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import { CategoryApplicableTo, PatternType } from '@/lib/graphql/types/__generated__/graphql';
import type { TransactionRule } from '../types';
import { TransactionRuleDrawer } from '../TransactionRuleDrawer';

const mockCreateRule = vi.fn();
const mockUpdateRule = vi.fn();
const mockUseQuery = vi.fn();
const mockPreviewRule = vi.fn();
const toast = {
  success: vi.fn(),
  error: vi.fn(),
  info: vi.fn(),
  warning: vi.fn(),
};

let lastCategoryPickerProps: any = null;

vi.mock('@mui/material', async () => {
  const actual = await vi.importActual<typeof import('@mui/material')>('@mui/material');

  return {
    ...actual,
    Select: ({ children, value = '', onChange, label, displayEmpty: _displayEmpty, inputProps, renderValue: _renderValue, ...props }: any) => (
      <select
        aria-label={inputProps?.['aria-label'] ?? label}
        value={value ?? ''}
        onChange={(event) => onChange?.({ target: { value: event.target.value } })}
        {...props}
      >
        {children}
      </select>
    ),
    MenuItem: ({ children, value, ...props }: any) => (
      <option value={value ?? ''} {...props}>
        {children}
      </option>
    ),
  };
});

vi.mock('@apollo/client/react', () => ({
  useQuery: (query: unknown, options?: unknown) => mockUseQuery(query, options),
  useLazyQuery: vi.fn(() => [mockPreviewRule, { loading: false, data: null, error: null }]),
  useMutation: vi.fn((mutation: any, options?: { onCompleted?: (data: any) => void }) => {
    const operationName = mutation?.definitions?.[0]?.name?.value;

    if (operationName === 'CreateTransactionRule') {
      return [
        (args: unknown) => {
          mockCreateRule(args);
          options?.onCompleted?.({
            createTransactionRule: {
              id: 'rule-new',
            },
          });
          return Promise.resolve({ data: { createTransactionRule: { id: 'rule-new' } } });
        },
        { loading: false, error: null },
      ];
    }

    if (operationName === 'UpdateTransactionRule') {
      return [
        (args: unknown) => {
          mockUpdateRule(args);
          options?.onCompleted?.({
            updateTransactionRule: {
              id: 'rule-1',
            },
          });
          return Promise.resolve({ data: { updateTransactionRule: { id: 'rule-1' } } });
        },
        { loading: false, error: null },
      ];
    }

    return [vi.fn(), { loading: false, error: null }];
  }),
}));

vi.mock('@/shared/providers', () => ({
  useToast: () => toast,
}));

vi.mock('@/shared/i18n', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
  }),
  i18n: { language: 'pt-BR' },
}));

vi.mock('@/shared/components/ui/layout/Drawer', () => {
  const Drawer = ({ open, children }: { open: boolean; children: React.ReactNode }) =>
    open ? <div>{children}</div> : null;

  const DrawerHeader = ({ title }: { title: React.ReactNode }) => <h2>{title}</h2>;
  const DrawerBody = ({ children }: { children: React.ReactNode }) => <div>{children}</div>;
  const DrawerActionButtons = ({
    cancelText,
    submitText,
    onCancel,
    onSubmit,
    submitDisabled,
  }: {
    cancelText: string;
    submitText: string;
    onCancel: () => void;
    onSubmit: () => void;
    submitDisabled?: boolean;
  }) => (
    <div>
      <button type="button" onClick={onCancel}>
        {cancelText}
      </button>
      <button type="button" onClick={onSubmit} disabled={submitDisabled}>
        {submitText}
      </button>
    </div>
  );

  Drawer.Header = DrawerHeader;
  Drawer.Body = DrawerBody;
  Drawer.ActionButtons = DrawerActionButtons;

  return { Drawer };
});

vi.mock('@/shared/components/ui/forms/form/TagsInput', () => ({
  TagsInput: () => null,
}));

vi.mock('../../category/CategoryPicker', () => ({
  CategoryPicker: (props: any) => {
    lastCategoryPickerProps = props;
    return null;
  },
}));

vi.mock('../../transaction/MerchantSelect', () => ({
  MerchantSelect: () => null,
}));

const theme = createTheme();

const baseRule: TransactionRule = {
  id: 'rule-1',
  pattern: 'SALARIO',
  patternType: PatternType.Contains,
  applicableTo: CategoryApplicableTo.Expense,
  typeOverride: CategoryApplicableTo.Income,
  priority: 10,
  isSystem: false,
  isDisabledForAccount: false,
  isImplicit: false,
  matchCount: 0,
  createdAt: new Date().toISOString(),
  descriptionOverride: 'Salario',
  tagsOverride: null,
  categoryId: null,
  category: null,
  merchantId: null,
  merchant: null,
};

function renderDrawer(rule: TransactionRule | null = null) {
  const onSaved = vi.fn();

  render(
    <ThemeProvider theme={theme}>
      <TransactionRuleDrawer open rule={rule} onClose={vi.fn()} onSaved={onSaved} />
    </ThemeProvider>,
  );

  return { onSaved };
}

describe.sequential('TransactionRuleDrawer', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    lastCategoryPickerProps = null;
    mockUseQuery.mockImplementation((query: any) => {
      const operationName = query?.definitions?.[0]?.name?.value;

      if (operationName === 'TransactionCategories') {
        return {
          data: {
            transactionCategories: [],
          },
          loading: false,
          error: null,
        };
      }

      if (operationName === 'TransactionTags') {
        return {
          data: {
            transactionTags: [],
          },
          loading: false,
          error: null,
        };
      }

      return { data: undefined, loading: false, error: null };
    });
  });

  it('creates a rule with optional typeOverride and excludes ANY from destination options', async () => {
    const user = userEvent.setup();
    renderDrawer();

    await user.type(
      screen.getByRole('textbox', {
        name: /Texto comparativo/i,
      }),
      'PIX recebido',
    );

    const typeOverrideSelect = screen.getByRole('combobox', {
      name: 'Tipo de reclassificacao',
    });

    expect(
      within(typeOverrideSelect).queryByRole('option', {
        name: 'Qualquer',
      }),
    ).not.toBeInTheDocument();

    await user.selectOptions(typeOverrideSelect, CategoryApplicableTo.Expense);

    expect(lastCategoryPickerProps?.creditDebitType).toBe('DEBIT');

    const saveButton = screen.getByRole('button', { name: 'Salvar Regra' });
    await waitFor(() => expect(saveButton).toBeEnabled());

    await user.click(saveButton);

    await waitFor(() => {
      expect(mockCreateRule).toHaveBeenCalledWith(
        expect.objectContaining({
          variables: {
            input: expect.objectContaining({
              pattern: 'PIX recebido',
              applicableTo: CategoryApplicableTo.Any,
              typeOverride: CategoryApplicableTo.Expense,
            }),
          },
        }),
      );
    });
  });

  it('loads an existing typeOverride and clears it as null on update', async () => {
    const user = userEvent.setup();
    renderDrawer(baseRule);

    const typeOverrideSelect = screen.getByRole('combobox', {
      name: 'Tipo de reclassificacao',
    });

    expect(typeOverrideSelect).toHaveValue(CategoryApplicableTo.Income);
    expect(lastCategoryPickerProps?.creditDebitType).toBe('CREDIT');

    await user.selectOptions(typeOverrideSelect, '');

    expect(lastCategoryPickerProps?.creditDebitType).toBe('DEBIT');

    await user.click(screen.getByRole('button', { name: 'Salvar Regra' }));

    await waitFor(() => {
      expect(mockUpdateRule).toHaveBeenCalledWith(
        expect.objectContaining({
          variables: {
            id: 'rule-1',
            input: expect.objectContaining({
              applicableTo: CategoryApplicableTo.Expense,
              typeOverride: null,
            }),
          },
        }),
      );
    });
  });
});
