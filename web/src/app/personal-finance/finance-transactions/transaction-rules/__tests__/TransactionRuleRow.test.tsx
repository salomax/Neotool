import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Table, TableBody } from '@mui/material';
import { CategoryApplicableTo, PatternType } from '@/lib/graphql/types/__generated__/graphql';
import { TransactionRuleRow } from '../TransactionRuleRow';
import type { TransactionRule } from '../types';

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
  it('shows disabled visual badge for disabled automatic rules', () => {
    render(
      <Table>
        <TableBody>
          <TransactionRuleRow
            rule={baseRule}
            onEdit={vi.fn()}
            onDelete={vi.fn()}
            renderAs="table"
            showActions
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

    await user.click(screen.getByRole('button', { name: /Ações da regra/i }));
    await user.click(screen.getByRole('menuitem', { name: /Habilitar regra automática/i }));

    expect(onToggle).toHaveBeenCalledWith(baseRule, true);
  });
});
