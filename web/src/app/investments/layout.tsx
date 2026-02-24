"use client";

import React from "react";
import { useTranslation } from "@/shared/i18n";
import { investmentsTranslations } from "@/shared/i18n/domains/investments";
import { useBreadcrumbLabel } from "@/shared/hooks/ui/useBreadcrumbLabel";

export default function InvestmentsLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const { t } = useTranslation(investmentsTranslations);

  useBreadcrumbLabel("investments", t("routes.investments"));

  return <>{children}</>;
}
