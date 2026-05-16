"use client";

import React from "react";
import { Page } from "@/shared/components/ui/layout";
import { usePageTitle } from "@/shared/hooks/ui/usePageTitle";
import { useTranslation } from "@/shared/i18n";
import { personalFinanceTranslations } from "../i18n";
import { RecurringPaymentsList } from "./RecurringPaymentsList";

export default function RecurringPaymentsPage() {
  const { tCommon } = useTranslation(personalFinanceTranslations);
  usePageTitle(tCommon('routes.recurringPayments'));

  return <RecurringPaymentsList />;
}
