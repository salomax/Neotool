"use client";

import React from "react";
import { useTranslation } from "@/shared/i18n";
import { financialDataTranslations } from "@/shared/i18n/domains/financial-data";
import { useBreadcrumbLabel } from "@/shared/hooks/ui/useBreadcrumbLabel";

export default function BacenInstitutionsLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const { t } = useTranslation(financialDataTranslations);
  
  useBreadcrumbLabel("bacen-institutions", t("routes.bacenInstitutions"));

  return <>{children}</>;
}
