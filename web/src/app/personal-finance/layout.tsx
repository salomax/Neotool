"use client";

import React from "react";
import { useTranslation } from "@/shared/i18n";
import { personalFinanceTranslations } from "./i18n";
import { useBreadcrumbLabel } from "@/shared/hooks/ui/useBreadcrumbLabel";

export default function PersonalFinanceLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const { t } = useTranslation(personalFinanceTranslations);
  
  // Register the label for the 'personal-finance' segment
  useBreadcrumbLabel("personal-finance", t("title"));

  return <>{children}</>;
}
