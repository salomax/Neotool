"use client";

import React from "react";
import { useTranslation } from "@/shared/i18n";
import { personalFinanceTranslations } from "../i18n";
import { useBreadcrumbLabel } from "@/shared/hooks/ui/useBreadcrumbLabel";

export default function FinanceTransactionsLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const { t } = useTranslation(personalFinanceTranslations);
  
  // Register the label for the 'finance-transactions' segment
  useBreadcrumbLabel("finance-transactions", t("financeTransactions.title"));

  return <>{children}</>;
}
