import { CreditDebitType } from '@/lib/graphql/types/__generated__/graphql';

/**
 * Returns true when a category/tag row should be dimmed because the active
 * credit/debit type filter targets the opposite section.
 *
 * e.g. when the user has filtered to "income" (Credit), expense rows are dimmed.
 */
export function isDimmedByTypeFilter(
  section: 'expenses' | 'income' | undefined,
  filter: CreditDebitType | null | undefined,
): boolean {
  if (!section || !filter) return false;
  return (
    (filter === CreditDebitType.Credit && section === 'expenses') ||
    (filter === CreditDebitType.Debit && section === 'income')
  );
}
