"use client";

import React from "react";
import { useTranslation } from "@/shared/i18n";
import { personalFinanceTranslations } from "../i18n";
import { useBreadcrumbLabel } from "@/shared/hooks/ui/useBreadcrumbLabel";

export default function PersonalFinanceInvestmentsLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const { t } = useTranslation(personalFinanceTranslations);

  useBreadcrumbLabel("investments", t("dashboard.actions.investmentsPortfolio.title"));

  return <>{children}</>;
}

