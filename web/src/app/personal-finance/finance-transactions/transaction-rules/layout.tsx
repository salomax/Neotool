"use client";

import React from "react";
import { useBreadcrumbLabel } from "@/shared/hooks/ui/useBreadcrumbLabel";

export default function TransactionRulesLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  useBreadcrumbLabel("transaction-rules", "Regras de Movimentação");
  return <>{children}</>;
}
