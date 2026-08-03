/**
 * Banking GraphQL enums from app codegen — use these instead of raw strings for
 * transaction type, credit/debit, status, and category applicability so typos fail
 * at compile time and stay aligned with the schema.
 *
 * @see spec/features/personal-finance/requirements/02-transactions.md
 */
export {
  CategoryApplicableTo,
  CreditDebitType,
  TransactionStatus,
  TransactionType,
} from '../../../src/lib/graphql/types/__generated__/graphql';
