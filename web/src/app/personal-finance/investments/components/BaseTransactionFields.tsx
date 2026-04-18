"use client";

import * as React from "react";
import { Box } from "@mui/material";
import type { UseFormReturn } from "react-hook-form";
import { InvestmentTransactionDirection } from "@/lib/graphql/types/__generated__/graphql";
import { FormStack, FormRowStack } from "@/shared/components/ui/forms/components";
import { DatePickerField, MoneyInput } from "@/shared/components/ui/forms/form";
import { SegmentedControl } from "@/shared/components/ui/inputs/SegmentedControl";
import { Label } from "@/shared/components/ui/primitives/Label";

export interface BaseTransactionFormData {
  tradeDate?: Date;
  grossValue?: number;
  netValue?: number;
  incomeTax?: number;
  iof?: number;
}

interface BaseTransactionFieldsProps {
  form: UseFormReturn<BaseTransactionFormData>;
  direction: InvestmentTransactionDirection;
  onDirectionChange: (d: InvestmentTransactionDirection) => void;
  directionOptions: { inflow: string; outflow: string };
  minDate?: Date;
  showNetValue?: boolean;
  showTaxFields?: boolean;
}

export function BaseTransactionFields({
  form,
  direction,
  onDirectionChange,
  directionOptions,
  minDate,
  showNetValue = true,
  showTaxFields = true,
}: BaseTransactionFieldsProps) {
  const submitCount = form.formState.submitCount;

  return (
    <FormStack data-submit-count={submitCount}>
      <FormStack spacing={0.5}>
        <Label sx={{ mb: 0 }}>Tipo</Label>
        <SegmentedControl
          fullWidth
          value={direction}
          onChange={(value) => onDirectionChange(value as InvestmentTransactionDirection)}
          options={[
            { value: InvestmentTransactionDirection.Inflow, label: directionOptions.inflow },
            { value: InvestmentTransactionDirection.Outflow, label: directionOptions.outflow },
          ]}
        />
      </FormStack>

      <FormRowStack alignItems="flex-start">
        <Box sx={{ flex: 1, minWidth: 0 }}>
          <DatePickerField
            name="tradeDate"
            label="Data"
            size="medium"
            minDate={minDate}
          />
        </Box>
        <Box sx={{ flex: 1, minWidth: 0 }}>
          <MoneyInput
            name="grossValue"
            label="Valor bruto"
            currency="BRL"
            size="medium"
          />
        </Box>
      </FormRowStack>

      {showNetValue ? (
        <MoneyInput
          name="netValue"
          label="Valor líquido"
          currency="BRL"
          size="medium"
        />
      ) : null}

      {showTaxFields ? (
        <FormRowStack alignItems="flex-start">
          <Box sx={{ flex: 1, minWidth: 0 }}>
            <MoneyInput
              name="incomeTax"
              label="Imposto de renda"
              currency="BRL"
              size="medium"
            />
          </Box>
          <Box sx={{ flex: 1, minWidth: 0 }}>
            <MoneyInput
              name="iof"
              label="IOF"
              currency="BRL"
              size="medium"
            />
          </Box>
        </FormRowStack>
      ) : null}
    </FormStack>
  );
}

export type { BaseTransactionFieldsProps };
