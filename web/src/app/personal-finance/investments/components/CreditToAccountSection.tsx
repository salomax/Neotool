"use client";

import React from "react";
import { Box, Typography } from "@mui/material";
import { DatePickerField, MoneyInput, ToggleField } from "@/shared/components/ui/forms/form";
import { FormRowStack } from "@/shared/components/ui/forms/components";
import {
  FinancialAccountIdentity,
  type FinancialAccountIdentityAccount,
} from "@/shared/components/ui/data-display/FinancialAccountIdentity";

export interface CreditToAccountSectionProps {
  showToggle?: boolean;
  toggleName: string;
  toggleLabel: string;
  enabled: boolean;
  netValueName: string;
  netValueLabel: string;
  creditDateName: string;
  creditDateLabel: string;
  destinationLabel: string;
  destinationAccount: FinancialAccountIdentityAccount | null | undefined;
  destinationNameTitle?: string;
  minCreditDate?: Date;
  maxCreditDate?: Date;
  toggleTestId?: string;
  destinationTestId?: string;
}

/**
 * Reusable form block for optional crediting to a financial account.
 * Works with react-hook-form via field names passed through props.
 */
export function CreditToAccountSection({
  showToggle = true,
  toggleName,
  toggleLabel,
  enabled,
  netValueName,
  netValueLabel,
  creditDateName,
  creditDateLabel,
  destinationLabel,
  destinationAccount,
  destinationNameTitle,
  minCreditDate,
  maxCreditDate,
  toggleTestId = "credit-to-account-toggle",
  destinationTestId = "credit-to-account-destination",
}: CreditToAccountSectionProps) {
  return (
    <>
      {showToggle ? (
        <ToggleField
          name={toggleName}
          label={toggleLabel}
          size="medium"
          data-testid={toggleTestId}
        />
      ) : null}

      {enabled ? (
        <>
          <FormRowStack alignItems="flex-start">
            <Box sx={{ flex: 1, minWidth: 0 }}>
              <MoneyInput
                name={netValueName}
                label={netValueLabel}
                currency="BRL"
                size="medium"
              />
            </Box>
            <Box sx={{ flex: 1, minWidth: 0 }}>
              <DatePickerField
                name={creditDateName}
                label={creditDateLabel}
                size="medium"
                minDate={minCreditDate}
                maxDate={maxCreditDate}
              />
            </Box>
          </FormRowStack>

          <Box
            sx={(theme) => ({
              position: "relative",
              border: `1px solid ${theme.palette.divider}`,
              borderRadius: 1,
              px: 1.5,
              pt: 2,
              pb: 1.5,
            })}
            data-testid={`${destinationTestId}-box`}
          >
            <Typography
              component="span"
              variant="caption"
              sx={(theme) => ({
                position: "absolute",
                left: 10,
                top: 0,
                transform: "translateY(-50%)",
                px: 0.75,
                bgcolor: theme.palette.background.paper,
                color: theme.palette.text.secondary,
                lineHeight: 1.2,
                pointerEvents: "none",
              })}
            >
              {destinationLabel}
            </Typography>
            <FinancialAccountIdentity
              account={destinationAccount}
              justify="flex-start"
              nameTitle={destinationNameTitle}
              data-testid={destinationTestId}
            />
          </Box>
        </>
      ) : null}
    </>
  );
}

