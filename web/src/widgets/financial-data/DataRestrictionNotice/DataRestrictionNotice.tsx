"use client";

import React from "react";
import { WarningAlert } from "@/shared/components/ui/feedback/alerts";

/** GraphQL enum value for data restriction reason */
export type DataRestrictionReason = "INSTITUTION_REQUEST";

const RESTRICTION_MESSAGES: Record<DataRestrictionReason, string> = {
  INSTITUTION_REQUEST:
    "Dado não disponível. Razão: A instituição solicitou a remoção dos seus dados",
};

export interface DataRestrictionNoticeProps {
  /** Restriction reason from API; when present, the notice is shown */
  reason: DataRestrictionReason | null | undefined;
}

/**
 * Displays a notice when an institution's financial data is restricted.
 * Message is shown in PT-BR per product spec.
 */
export const DataRestrictionNotice: React.FC<DataRestrictionNoticeProps> = ({
  reason,
}) => {
  if (!reason) {
    return null;
  }

  const message = RESTRICTION_MESSAGES[reason] ?? RESTRICTION_MESSAGES.INSTITUTION_REQUEST;
  return <WarningAlert message={message} />;
};

export default DataRestrictionNotice;
