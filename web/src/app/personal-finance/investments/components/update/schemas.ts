'use client';

import dayjs from 'dayjs';
import { z } from 'zod';
import { FINANCE_MOVING_WINDOW_YEARS } from '../../../finance-transactions/constants/dateWindow';
import { InvestmentTransactionDirection } from '@/lib/graphql/types/__generated__/graphql';
import { validateInvestmentSettlementDates } from '@/shared/utils/investment/settlementDatePolicy';
import type { UpdateHoldingAssetClass, UpdateHoldingFormValues } from './types';

interface BuildSchemaParams {
  assetClass: UpdateHoldingAssetClass;
  direction: InvestmentTransactionDirection;
  maxQuantity: number;
  minAllowedDate: dayjs.Dayjs;
  t: (key: string, options?: Record<string, unknown>) => string;
}

function decimalPlaces(value: number): number {
  const parts = value.toString().split('.');
  return parts[1]?.length ?? 0;
}

export function buildUpdateHoldingSchema(params: BuildSchemaParams): z.ZodType<UpdateHoldingFormValues, z.ZodTypeDef, unknown> {
  const { assetClass, direction, maxQuantity, minAllowedDate, t } = params;

  return z
    .object({
      tradeDate: z.preprocess(
        (value) => (value === '' || value == null ? undefined : value),
        z.date({
          required_error: t('investments.updateHolding.validation.tradeDateRequired'),
          invalid_type_error: t('investments.updateHolding.validation.tradeDateRequired'),
        }),
      ),
      settlementDate: z.preprocess(
        (value) => (value === '' || value == null ? undefined : value),
        z.date({
          required_error: t('investments.updateHolding.validation.settlementDateRequired'),
          invalid_type_error: t('investments.updateHolding.validation.settlementDateRequired'),
        }),
      ),
      grossValue: z.number().positive().optional(),
      netValue: z.number().positive().optional(),
      quantity: z.number().positive().optional(),
      unitPrice: z.number().positive().optional(),
      incomeTax: z.number().min(0).optional(),
      iof: z.number().min(0).optional(),
      totalFees: z.number().min(0).optional(),
      valuedAt: z.preprocess(
        (value) => (value === '' || value == null ? undefined : value),
        z.date().optional(),
      ),
    })
    .superRefine((data, ctx) => {
      if (dayjs(data.tradeDate).isBefore(minAllowedDate, 'day')) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: t('financeTransactions.validation.dateTooOld', { years: FINANCE_MOVING_WINDOW_YEARS }),
          path: ['tradeDate'],
        });
      }

      validateInvestmentSettlementDates(data, ctx, t);

      if (assetClass === 'fixedIncome') {
        if (data.grossValue == null) {
          ctx.addIssue({
            code: z.ZodIssueCode.custom,
            message: t('investments.updateHolding.validation.grossValueRequired'),
            path: ['grossValue'],
          });
        }

        if (direction === InvestmentTransactionDirection.Outflow) {
          if (data.netValue == null) {
            ctx.addIssue({
              code: z.ZodIssueCode.custom,
              message: t('investments.updateHolding.validation.netValueRequired'),
              path: ['netValue'],
            });
          }
          if (data.quantity != null && data.quantity > maxQuantity) {
            ctx.addIssue({
              code: z.ZodIssueCode.custom,
              message: t('investments.updateHolding.quantityExceeds', {
                qty: maxQuantity,
                unit: t('investments.updateHolding.units.securities'),
              }),
              path: ['quantity'],
            });
          }
        }
      }

      if (assetClass === 'fund') {
        if (direction === InvestmentTransactionDirection.Inflow) {
          if (data.unitPrice == null) {
            ctx.addIssue({
              code: z.ZodIssueCode.custom,
              message: t('investments.updateHolding.validation.unitPriceRequired'),
              path: ['unitPrice'],
            });
          }

          const hasGross = data.grossValue != null;
          const hasQuantity = data.quantity != null;
          if (!hasGross && !hasQuantity) {
            ctx.addIssue({
              code: z.ZodIssueCode.custom,
              message: t('investments.updateHolding.validation.grossValueRequired'),
              path: ['grossValue'],
            });
            ctx.addIssue({
              code: z.ZodIssueCode.custom,
              message: t('investments.updateHolding.validation.quantityRequired'),
              path: ['quantity'],
            });
          }
        }

        if (direction === InvestmentTransactionDirection.Outflow) {
          if (data.netValue == null) {
            ctx.addIssue({
              code: z.ZodIssueCode.custom,
              message: t('investments.updateHolding.validation.netValueRequired'),
              path: ['netValue'],
            });
          }
          if (data.unitPrice == null) {
            ctx.addIssue({
              code: z.ZodIssueCode.custom,
              message: t('investments.updateHolding.validation.unitPriceRequired'),
              path: ['unitPrice'],
            });
          }
          const hasGross = data.grossValue != null;
          const hasQuantity = data.quantity != null;
          if (!hasGross && !hasQuantity) {
            ctx.addIssue({
              code: z.ZodIssueCode.custom,
              message: t('investments.updateHolding.validation.grossValueRequired'),
              path: ['grossValue'],
            });
          }
          if (data.quantity != null && data.quantity > maxQuantity) {
            ctx.addIssue({
              code: z.ZodIssueCode.custom,
              message: t('investments.updateHolding.quantityExceeds', {
                qty: maxQuantity,
                unit: t('investments.updateHolding.units.quotas'),
              }),
              path: ['quantity'],
            });
          }
        }
      }

      if (assetClass === 'equity') {
        if (data.quantity == null) {
          ctx.addIssue({
            code: z.ZodIssueCode.custom,
            message: t('investments.updateHolding.validation.quantityRequired'),
            path: ['quantity'],
          });
        }
        if (data.quantity != null && !Number.isInteger(data.quantity)) {
          ctx.addIssue({
            code: z.ZodIssueCode.custom,
            message: t('investments.updateHolding.validation.integerQuantityRequired'),
            path: ['quantity'],
          });
        }
        if (data.unitPrice == null) {
          ctx.addIssue({
            code: z.ZodIssueCode.custom,
            message: t('investments.updateHolding.validation.unitPriceRequired'),
            path: ['unitPrice'],
          });
        }
        if (direction === InvestmentTransactionDirection.Outflow && data.quantity != null && data.quantity > maxQuantity) {
          ctx.addIssue({
            code: z.ZodIssueCode.custom,
            message: t('investments.updateHolding.quantityExceeds', {
              qty: maxQuantity,
              unit: t('investments.updateHolding.units.stocks'),
            }),
            path: ['quantity'],
          });
        }
      }

      if (assetClass === 'crypto') {
        if (data.quantity == null) {
          ctx.addIssue({
            code: z.ZodIssueCode.custom,
            message: t('investments.updateHolding.validation.quantityRequired'),
            path: ['quantity'],
          });
        }
        if (data.quantity != null && decimalPlaces(data.quantity) > 8) {
          ctx.addIssue({
            code: z.ZodIssueCode.custom,
            message: t('investments.updateHolding.validation.cryptoPrecision'),
            path: ['quantity'],
          });
        }
        if (data.unitPrice == null) {
          ctx.addIssue({
            code: z.ZodIssueCode.custom,
            message: t('investments.updateHolding.validation.unitPriceRequired'),
            path: ['unitPrice'],
          });
        }
        if (direction === InvestmentTransactionDirection.Outflow && data.quantity != null && data.quantity > maxQuantity) {
          ctx.addIssue({
            code: z.ZodIssueCode.custom,
            message: t('investments.updateHolding.quantityExceeds', {
              qty: maxQuantity,
              unit: t('investments.updateHolding.units.units'),
            }),
            path: ['quantity'],
          });
        }
      }
    });
}
