"use client";

import React from "react";
import { useTranslation } from "@/shared/i18n";
import { personalFinanceTranslations } from "../../i18n";
import type { IssuerSelectionAccount } from "./IssuerSelection";
import { InvestmentRecordDrawer } from "./InvestmentRecordDrawer";
import { RecordCashForm } from "./RecordCashForm";

export interface RecordCashDrawerProps {
  open: boolean;
  onClose: () => void;
  financialAccountId?: string | null;
  financialAccounts?: IssuerSelectionAccount[];
  bankAccounts?: IssuerSelectionAccount[];
  /** Matches `InvestmentTable` cash query filter (sidebar selection); drives refetch after save. */
  listContextAccountId?: string | null;
  onRecorded?: () => void | Promise<void>;
}

export function RecordCashDrawer({
  open,
  onClose,
  financialAccountId,
  financialAccounts = [],
  bankAccounts = [],
  listContextAccountId,
  onRecorded,
}: RecordCashDrawerProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const [formResetSignal, setFormResetSignal] = React.useState(0);

  React.useEffect(() => {
    if (open) setFormResetSignal((n) => n + 1);
  }, [open]);

  return (
    <InvestmentRecordDrawer
      open={open}
      onClose={onClose}
      title={t("investments.cash.drawer.title")}
      initialFinancialAccountId={financialAccountId || null}
      financialAccounts={financialAccounts}
      issuerSelection={{
        description: t("investments.cash.drawer.selectAccount.description"),
        emptyState: t("investments.cash.drawer.selectAccount.emptyState"),
        dataTestIdPrefix: "record-cash-select-account-",
      }}
    >
      {({ resolvedFinancialAccountId: fid }) =>
        fid ? (
          <RecordCashForm
            key={fid}
            financialAccountId={fid}
            bankAccounts={bankAccounts}
            investmentAccounts={financialAccounts}
            listContextAccountId={listContextAccountId}
            resetSignal={formResetSignal}
            formId="record-cash-form"
            onSuccess={onClose}
            onRecorded={onRecorded}
            onCancel={onClose}
            cancelText={t("actions.cancel")}
            submitText={t("investments.cash.actions.submit")}
            submittingText={t("investments.cash.actions.submitting")}
            cancelTestId="record-cash-cancel-btn"
            submitTestId="record-cash-submit-btn"
          />
        ) : null}
    </InvestmentRecordDrawer>
  );
}
