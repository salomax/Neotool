import type { InvestmentCashTransactionsQuery } from "@/lib/graphql/operations/banking/queries.generated";

export type CashTransactionRowNode =
  InvestmentCashTransactionsQuery["investmentCashTransactions"]["edges"][number]["node"];
