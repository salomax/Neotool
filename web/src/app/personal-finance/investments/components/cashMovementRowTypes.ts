import type { InvestmentCashTransactionsQuery } from "@/lib/graphql/operations/banking/queries.generated";

export type CashMovementRowNode =
  InvestmentCashTransactionsQuery["investmentCashTransactions"]["edges"][number]["node"];
