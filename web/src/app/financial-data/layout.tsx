"use client";

import React from "react";
import { useTranslation } from "@/shared/i18n";
import { financialDataTranslations } from "@/shared/i18n/domains/financial-data";
import { useBreadcrumbLabel } from "@/shared/hooks/ui/useBreadcrumbLabel";

export default function FinancialDataLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const { t } = useTranslation(financialDataTranslations);
  
  // Register the label for the 'financial-data' segment
  // This ensures it is translated even when visiting child routes like /financial-data/national-indicators
  useBreadcrumbLabel("financial-data", t("routes.financialData"));

  return <>{children}</>;
}
