import { PendingActionType } from "@/lib/graphql/types/__generated__/graphql";
import type { PendingActionsQuery } from "@/lib/graphql/operations/banking/pendingActionOperations.generated";
import { toStartOfDayLocal, formatDateOnlyLocale } from "@/shared/utils/date/dateOnly";

export type PendingActionRowData = PendingActionsQuery["pendingActions"][number];

type PendingActionPayload = {
  operation?: string;
  assetName?: string;
  recurringPaymentName?: string;
  financialAccountName?: string;
  maturityDate?: string;
  couponDate?: string;
  nextDueDate?: string;
  expectedAmount?: string | number | null;
  currencyCode?: string;
  ledgerBalance?: string | number;
  variableAmount?: boolean;
  transactionId?: string;
  categoryId?: string;
  ruleId?: string;
};

export function pendingActionPayload(row: PendingActionRowData): PendingActionPayload {
  return (row.payload ?? {}) as PendingActionPayload;
}

export function pendingActionTitle(row: PendingActionRowData): string {
  const payload = pendingActionPayload(row);
  if (row.type === PendingActionType.AgentWriteConfirmation) {
    switch (payload.operation) {
      case "delete_transaction":
        return "Transaction deletion";
      case "bulk_categorize":
        return "Bulk categorization";
      case "apply_rule_to_historical":
        return "Rule replay";
      default:
        return "Agent confirmation";
    }
  }
  return (
    row.relatedHolding?.asset?.name ??
    payload.recurringPaymentName ??
    payload.financialAccountName ??
    payload.assetName ??
    "—"
  );
}

export function pendingActionAmount(
  row: PendingActionRowData,
): { value: number; currency: string } | null {
  const payload = pendingActionPayload(row);
  const type = row.type as PendingActionType;

  if (type === PendingActionType.RecurringPaymentDue) {
    if (payload.variableAmount || payload.expectedAmount == null) return null;
    const value = Number(payload.expectedAmount);
    if (!Number.isFinite(value)) return null;
    return { value, currency: payload.currencyCode ?? "BRL" };
  }

  if (type === PendingActionType.BankAccountNegativeBalance) {
    if (payload.ledgerBalance == null) return null;
    const value = Number(payload.ledgerBalance);
    if (!Number.isFinite(value)) return null;
    return { value, currency: payload.currencyCode ?? "BRL" };
  }

  const holdingValue = row.relatedHolding?.grossValue ?? row.relatedHolding?.netValue;
  if (holdingValue != null && Number.isFinite(holdingValue)) {
    return { value: holdingValue, currency: "BRL" };
  }

  return null;
}

export function daysSinceCreated(createdAt: unknown): number {
  const created =
    typeof createdAt === "string" || createdAt instanceof Date
      ? toStartOfDayLocal(new Date(createdAt))
      : null;
  if (!created || Number.isNaN(created.getTime())) return 0;

  const today = toStartOfDayLocal(new Date());
  return Math.max(0, Math.round((today.getTime() - created.getTime()) / 86_400_000));
}

export function pendingActionAgeLabelKey(days: number): "today" | "yesterday" | "days" {
  if (days <= 0) return "today";
  if (days === 1) return "yesterday";
  return "days";
}

export function pendingActionDescription(
  row: PendingActionRowData,
  t: (key: string, options?: Record<string, unknown>) => string,
  locale: string,
): string {
  const payload = pendingActionPayload(row);
  const type = row.type as PendingActionType;

  if (type === PendingActionType.InvestmentMatured) {
    const maturityDate =
      payload.maturityDate ??
      (row.relatedHolding?.maturityDate ? String(row.relatedHolding.maturityDate) : null);
    if (maturityDate) {
      return t("investments.pendingActions.descriptions.maturityOn", {
        date: formatDateOnlyLocale(maturityDate, locale),
      });
    }
    return t("investments.pendingActions.descriptions.investmentMatured");
  }

  if (type === PendingActionType.CouponPaymentDue) {
    if (payload.couponDate) {
      return t("investments.pendingActions.descriptions.couponOn", {
        date: formatDateOnlyLocale(payload.couponDate, locale),
      });
    }
    return t("investments.pendingActions.descriptions.couponDue");
  }

  if (type === PendingActionType.RecurringPaymentDue) {
    if (payload.nextDueDate) {
      return t("investments.pendingActions.descriptions.recurringDueOn", {
        date: formatDateOnlyLocale(payload.nextDueDate, locale),
      });
    }
    return t("recurringPayments.pendingActions.paymentDue");
  }

  if (type === PendingActionType.BankAccountNegativeBalance) {
    return t("investments.pendingActions.descriptions.negativeBalance");
  }

  if (type === PendingActionType.AgentWriteConfirmation) {
    switch (payload.operation) {
      case "delete_transaction":
        return t("investments.pendingActions.descriptions.agentWriteDelete");
      case "bulk_categorize":
        return t("investments.pendingActions.descriptions.agentWriteBulkCategorize");
      case "apply_rule_to_historical":
        return t("investments.pendingActions.descriptions.agentWriteApplyRule");
      default:
        return t("investments.pendingActions.descriptions.agentWriteConfirmation");
    }
  }

  return "";
}
