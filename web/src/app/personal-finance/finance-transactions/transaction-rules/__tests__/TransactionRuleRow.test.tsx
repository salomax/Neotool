import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen, cleanup, fireEvent, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Table, TableBody } from '@mui/material';
import { CategoryApplicableTo, PatternType } from '@/lib/graphql/types/__generated__/graphql';
import { TransactionRuleRow } from '../TransactionRuleRow';
import type { TransactionRule } from '../types';

vi.mock('@mui/material', async () => {
  const actual = await vi.importActual<typeof import('@mui/material')>('@mui/material');

  return {
    ...actual,
    Menu: ({
      children,
      anchorEl,
      anchorOrigin,
      transformOrigin,
      MenuListProps,
      open,
      onClose,
      ...props
    }: any) => (
      <div role="menu" data-open={open ? 'true' : 'false'} {...props}>
        {children}
      </div>
    ),
  };
});

const baseRule: TransactionRule = {
  id: 'rule-1',
  pattern: 'netflix',
  patternType: PatternType.Contains,
  applicableTo: CategoryApplicableTo.Expense,
  priority: 10,
  isSystem: true,
  isDisabledForAccount: true,
  isImplicit: false,
  matchCount: 0,
  createdAt: new Date().toISOString(),
  descriptionOverride: null,
  tagsOverride: null,
  categoryId: null,
  category: null,
  merchantId: null,
  merchant: null,
};

describe('TransactionRuleRow', () => {
  afterEach(() => {
    cleanup();
  });

  it('shows disabled visual badge for disabled automatic rules', () => {
    render(
      <Table>
        <TableBody>
          <TransactionRuleRow
            rule={baseRule}
            onEdit={vi.fn()}
            onDelete={vi.fn()}
            renderAs="table"
            disabledChipLabel="Desabilitada"
          />
        </TableBody>
      </Table>,
    );

    expect(screen.getByText('Desabilitada')).toBeInTheDocument();
  });

  it('calls toggle callback from actions menu', async () => {
    const user = userEvent.setup();
    const onToggle = vi.fn();

    render(
      <Table>
        <TableBody>
          <TransactionRuleRow
            rule={baseRule}
            onEdit={vi.fn()}
            onDelete={vi.fn()}
            renderAs="table"
            showActions
            onToggleSystemRuleEnabled={onToggle}
            enableSystemRuleLabel="Habilitar regra automática"
          />
        </TableBody>
      </Table>,
    );

    const toggleMenuItem = screen.getByRole('menuitem', { name: /Habilitar regra automática/i });
    fireEvent.click(toggleMenuItem);

    await waitFor(() => {
      expect(onToggle).toHaveBeenCalledWith(baseRule, true);
    });
  });
});
