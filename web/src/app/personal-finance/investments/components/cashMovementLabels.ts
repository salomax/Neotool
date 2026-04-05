import { CreditDebitType, TransactionType } from "@/lib/graphql/types/__generated__/graphql";

export function formatCashLedgerMovementLabel(
  type: TransactionType,
  creditDebitType: CreditDebitType,
  t: (key: string) => string,
): string {
  switch (type) {
    case TransactionType.BalanceAdjustment:
      return t("investments.cash.movements.kind.adjustment");
    case TransactionType.InvestmentReturn:
      return t("investments.cash.movements.kind.return");
    case TransactionType.Transfer:
      return creditDebitType === CreditDebitType.Credit
        ? t("investments.cash.movements.kind.transferIn")
        : t("investments.cash.movements.kind.transferOut");
    default:
      return String(type);
  }
}
