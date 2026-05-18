'use client';

import * as React from 'react';
import dayjs from 'dayjs';
import { Box, CircularProgress } from '@mui/material';
import { FormProvider } from 'react-hook-form';
import { ErrorAlert } from '@/shared/components/ui/feedback';
import { Drawer } from '@/shared/components/ui/layout/Drawer';
import { useTranslation } from '@/shared/i18n';
import { formatDateOnly } from '@/shared/utils/date/dateOnly';
import {
  coerceInvestmentSettlementDate,
  type InvestmentSettlementAssetKind,
} from '@/shared/utils/investment/settlementDatePolicy';
import { getFinanceMinAllowedDate } from '../../../finance-transactions/constants/dateWindow';
import { personalFinanceTranslations } from '../../../i18n';
import {
  InvestmentTransactionDirection,
  InvestmentTransactionType,
  type RecordInvestmentTransactionInput,
} from '@/lib/graphql/types/__generated__/graphql';
import { buildHoldingDetailHeaderModelFromRow } from '../holdingDetailHeaderPresenter';
import { useHolding } from '../../hooks/useHolding';
import { useUpdateHoldingForm } from '../../hooks/useUpdateHoldingForm';
import { useSyncDefaultSettlementDate } from '../../hooks/useSyncDefaultSettlementDate';
import { buildUpdateHoldingSchema } from './schemas';
import { FixedIncomeUpdateFields } from './FixedIncomeUpdateFields';
import { FundUpdateFields } from './FundUpdateFields';
import { EquityUpdateFields } from './EquityUpdateFields';
import { CryptoUpdateFields } from './CryptoUpdateFields';
import { HoldingUpdateDrawerShell } from './HoldingUpdateDrawerShell';
import { mapTransactionToFormValues } from './mapTransactionToFormValues';
import { getHoldingPositionValue } from './positionSummaryCalculations';
import {
  getUpdateHoldingAssetClass,
  isFundManualPricingMode,
  type UpdateHoldingEditTransaction,
  type UpdateHolding,
  type UpdateHoldingAssetClass,
  type UpdateHoldingFormValues,
} from './types';

interface UpdateHoldingDrawerProps {
  holdingId: string | null;
  open: boolean;
  onClose: () => void;
  initialDirection?: InvestmentTransactionDirection;
  editTransaction?: UpdateHoldingEditTransaction | null;
  onRecorded?: () => void;
}

function defaultDirection(initialDirection?: InvestmentTransactionDirection): InvestmentTransactionDirection {
  return initialDirection ?? InvestmentTransactionDirection.Inflow;
}

function coerceFormSettlementDate(
  assetClass: UpdateHoldingAssetClass,
  tradeDate: Date,
  settlementDate: Date | undefined,
): Date {
  return coerceInvestmentSettlementDate(assetClass as InvestmentSettlementAssetKind, tradeDate, settlementDate);
}

function getDirectionLabels(assetClass: UpdateHoldingAssetClass, t: (key: string, options?: Record<string, unknown>) => string) {
  if (assetClass === 'equity' || assetClass === 'crypto') {
    return {
      inflow: t('investments.updateHolding.direction.inflowEquity'),
      outflow: t('investments.updateHolding.direction.outflowEquity'),
    };
  }

  return {
    inflow: t('investments.updateHolding.direction.inflow'),
    outflow: t('investments.updateHolding.direction.outflow'),
  };
}

function getQuantityUnit(assetClass: UpdateHoldingAssetClass, t: (key: string, options?: Record<string, unknown>) => string): string {
  if (assetClass === 'fixedIncome') return t('investments.updateHolding.units.securities');
  if (assetClass === 'fund') return t('investments.updateHolding.units.quotas');
  if (assetClass === 'equity') return t('investments.updateHolding.units.stocks');
  return t('investments.updateHolding.units.units');
}

function toNumber(value: unknown): number | undefined {
  if (typeof value !== 'number') return undefined;
  if (!Number.isFinite(value)) return undefined;
  return value;
}

function compactInput(input: RecordInvestmentTransactionInput): RecordInvestmentTransactionInput {
  const entries = Object.entries(input).filter(([, value]) => value !== undefined);
  return Object.fromEntries(entries) as RecordInvestmentTransactionInput;
}

function buildRecordInput(
  data: UpdateHoldingFormValues,
  context: {
    holding: UpdateHolding;
    direction: InvestmentTransactionDirection;
    assetClass: UpdateHoldingAssetClass;
  },
): RecordInvestmentTransactionInput {
  const { holding, direction, assetClass } = context;
  const tradeDate = formatDateOnly(data.tradeDate ?? new Date());
  const settlementIso = formatDateOnly(
    coerceFormSettlementDate(assetClass, data.tradeDate ?? new Date(), data.settlementDate),
  );

  if (assetClass === 'fixedIncome') {
    const grossValue = toNumber(data.grossValue) ?? toNumber(data.netValue) ?? 0;
    const netValue = toNumber(data.netValue);
    const incomeTax =
      toNumber(data.incomeTax) ??
      (direction === InvestmentTransactionDirection.Outflow && netValue != null ? Math.max(grossValue - netValue, 0) : undefined);

    if (direction === InvestmentTransactionDirection.Inflow) {
      const qtyParsed = toNumber(data.quantity);
      const quantity =
        typeof qtyParsed === 'number' && Number.isFinite(qtyParsed) && qtyParsed >= 0 ? qtyParsed : 1;
      const unitPrice = quantity > 0 ? grossValue / quantity : grossValue;
      return compactInput({
        assetId: holding.asset.id,
        financialAccountId: holding.financialAccountId,
        direction,
        type: InvestmentTransactionType.Purchase,
        tradeDate,
        settlementDate: settlementIso,
        grossValue,
        netValue,
        quantity,
        unitPrice,
        incomeTax,
        iof: toNumber(data.iof),
      });
    }

    return compactInput({
      assetId: holding.asset.id,
      financialAccountId: holding.financialAccountId,
      direction,
      type: InvestmentTransactionType.Sale,
      tradeDate,
      settlementDate: settlementIso,
      grossValue,
      netValue,
      quantity: toNumber(data.quantity),
      incomeTax,
      iof: toNumber(data.iof),
    });
  }

  if (assetClass === 'fund') {
    const grossValue = toNumber(data.grossValue) ?? toNumber(data.netValue) ?? 0;
    const unitPrice = toNumber(data.unitPrice);
    const quantity = toNumber(data.quantity);

    return compactInput({
      assetId: holding.asset.id,
      financialAccountId: holding.financialAccountId,
      direction,
      type: direction === InvestmentTransactionDirection.Inflow ? InvestmentTransactionType.Subscription : InvestmentTransactionType.Sale,
      tradeDate,
      settlementDate: settlementIso,
      grossValue,
      netValue: toNumber(data.netValue),
      quantity,
      unitPrice,
      incomeTax: toNumber(data.incomeTax),
      manualValuation:
        direction === InvestmentTransactionDirection.Inflow && data.valuedAt && toNumber(data.grossValue) != null
          ? {
              grossValue: toNumber(data.grossValue) as number,
              valuedAt: formatDateOnly(data.valuedAt),
            }
          : undefined,
    });
  }

  if (assetClass === 'equity') {
    const quantity = toNumber(data.quantity) ?? 0;
    const unitPrice = toNumber(data.unitPrice) ?? 0;
    const grossValue = quantity * unitPrice;
    const fees = toNumber(data.totalFees) ?? 0;
    const netValue = direction === InvestmentTransactionDirection.Inflow ? grossValue + fees : grossValue - fees;

    return compactInput({
      assetId: holding.asset.id,
      financialAccountId: holding.financialAccountId,
      direction,
      type: direction === InvestmentTransactionDirection.Inflow ? InvestmentTransactionType.Purchase : InvestmentTransactionType.Sale,
      tradeDate,
      settlementDate: settlementIso,
      quantity,
      unitPrice,
      grossValue,
      netValue,
      incomeTax: toNumber(data.incomeTax),
    });
  }

  const quantity = toNumber(data.quantity) ?? 0;
  const unitPrice = toNumber(data.unitPrice) ?? 0;
  const grossValue = quantity * unitPrice;

  return compactInput({
    assetId: holding.asset.id,
    financialAccountId: holding.financialAccountId,
    direction,
    type: direction === InvestmentTransactionDirection.Inflow ? InvestmentTransactionType.Purchase : InvestmentTransactionType.Sale,
    tradeDate,
    settlementDate: settlementIso,
    quantity,
    unitPrice,
    grossValue,
  });
}

function getDrawerTitle(
  asset: { name: string; ticker: string | null; assetType: string },
  assetClass: UpdateHoldingAssetClass,
  direction: InvestmentTransactionDirection,
  editTransaction: UpdateHoldingEditTransaction | null | undefined,
  t: (key: string, options?: Record<string, unknown>) => string,
): string {
  const isEquityLike = assetClass === 'equity' || assetClass === 'crypto';
  const targetLabel = isEquityLike ? asset.ticker || asset.name : asset.name;

  if (editTransaction) {
    return t('investments.updateHolding.titles.edit', { asset: targetLabel });
  }

  if (direction === InvestmentTransactionDirection.Inflow) {
    return isEquityLike
      ? t('investments.updateHolding.titles.buy', { asset: targetLabel })
      : t('investments.updateHolding.titles.contribute', { asset: targetLabel });
  }

  return isEquityLike
    ? t('investments.updateHolding.titles.sell', { asset: targetLabel })
    : t('investments.updateHolding.titles.redeem', { asset: targetLabel });
}

function buildDefaultValues(assetClass: UpdateHoldingAssetClass): Partial<UpdateHoldingFormValues> {
  const today = dayjs().startOf('day').toDate();

  return {
    tradeDate: today,
    settlementDate: coerceFormSettlementDate(assetClass, today, undefined),
    ...(assetClass === 'equity' ? { quantity: 0 } : {}),
    incomeTax: 0,
    iof: 0,
    totalFees: 0,
    valuedAt: today,
  };
}

function UpdateHoldingDrawerForm({
  open,
  holding,
  onClose,
  direction,
  onDirectionChange,
  editTransaction,
  onRecorded,
}: {
  open: boolean;
  holding: UpdateHolding;
  onClose: () => void;
  direction: InvestmentTransactionDirection;
  onDirectionChange: (direction: InvestmentTransactionDirection) => void;
  editTransaction?: UpdateHoldingEditTransaction | null;
  onRecorded?: () => void;
}) {
  const { t } = useTranslation(personalFinanceTranslations);
  const isEditMode = Boolean(editTransaction);
  const locale = React.useMemo(() => Intl.DateTimeFormat().resolvedOptions().locale || 'pt-BR', []);
  const assetClass = React.useMemo(() => getUpdateHoldingAssetClass(holding.asset.assetType), [holding.asset.assetType]);
  const minDate = React.useMemo(() => getFinanceMinAllowedDate().toDate(), []);
  const maxDate = React.useMemo(() => dayjs().startOf('day').toDate(), []);
  const formId = React.useMemo(() => `update-holding-form-${holding.id}`, [holding.id]);
  const quantityUnit = React.useMemo(() => getQuantityUnit(assetClass, t), [assetClass, t]);
  const quantityFieldLabel = React.useMemo(
    () => t('investments.updateHolding.fields.quantityOf', { unit: quantityUnit }),
    [quantityUnit, t],
  );

  const directionLabels = React.useMemo(() => getDirectionLabels(assetClass, t), [assetClass, t]);
  const headerModel = React.useMemo(
    () => buildHoldingDetailHeaderModelFromRow(holding, t, locale),
    [holding, t, locale],
  );
  const currentPositionValue = React.useMemo(() => getHoldingPositionValue(holding), [holding]);

  const schema = React.useMemo(
    () =>
      buildUpdateHoldingSchema({
        assetClass,
        direction,
        maxQuantity: holding.quantity,
        minAllowedDate: getFinanceMinAllowedDate(),
        isFundManualPricingMode: isFundManualPricingMode(holding.pricingMode),
        t,
      }),
    [assetClass, direction, holding.pricingMode, holding.quantity, t],
  );

  const defaultValues = React.useMemo<Partial<UpdateHoldingFormValues>>(() => {
    const merged: Partial<UpdateHoldingFormValues> = {
      ...buildDefaultValues(assetClass),
      ...(editTransaction ? mapTransactionToFormValues(editTransaction) : {}),
    };
    if (merged.tradeDate) {
      merged.settlementDate = coerceFormSettlementDate(assetClass, merged.tradeDate, merged.settlementDate);
    }
    return merged;
  }, [assetClass, editTransaction]);

  const formController = useUpdateHoldingForm<UpdateHoldingFormValues>({
    holding,
    direction,
    schema,
    defaultValues,
    onBuildInput: (data, context) => buildRecordInput(data, { ...context, assetClass }),
    editTransaction,
    successToastMessage: isEditMode ? t('investments.updateHolding.successEdit') : t('investments.updateHolding.success'),
    onSuccess: () => {
      onRecorded?.();
      onClose();
    },
  });

  const watchedTradeDate = formController.form.watch('tradeDate');

  useSyncDefaultSettlementDate<UpdateHoldingFormValues>({
    enabled: open,
    assetKind: assetClass,
    tradeDate: watchedTradeDate,
    setValue: formController.form.setValue,
  });

  const title = React.useMemo(
    () => getDrawerTitle(holding.asset, assetClass, direction, editTransaction, t),
    [assetClass, direction, editTransaction, holding.asset, t],
  );
  const holdingDetailPath = React.useMemo(
    () => `/personal-finance/investments/${holding.id}`,
    [holding.id],
  );

  return (
    <FormProvider {...formController.form}>
      <HoldingUpdateDrawerShell
        open={open}
        onClose={onClose}
        headerModel={headerModel}
        holdingDetailPath={holdingDetailPath}
        openHoldingDetailAriaLabel={t('investments.updateHolding.openDetailAriaLabel')}
        title={title}
        formId={formId}
        direction={direction}
        directionLocked={isEditMode}
        inflowLabel={directionLabels.inflow}
        outflowLabel={directionLabels.outflow}
        onDirectionChange={onDirectionChange}
        onSubmit={formController.onSubmit}
        mutationError={formController.mutationError}
        isSubmitting={formController.isLoading}
        cancelText={t('actions.cancel')}
        submitText={isEditMode ? t('investments.updateHolding.saveChanges') : t('actions.save')}
        submittingText={t('investments.updateHolding.submitting')}
      >
        {assetClass === 'fixedIncome' ? (
          <FixedIncomeUpdateFields
            hideSummaryStrip={isEditMode}
            direction={direction}
            currentValue={currentPositionValue}
            currentQuantity={holding.quantity}
            currencyCode={holding.asset.currencyCode || 'BRL'}
            quantityUnit={quantityUnit}
            locale={locale}
            minDate={minDate}
            maxDate={maxDate}
            tradeDateLabel={
              direction === InvestmentTransactionDirection.Inflow
                ? t('investments.updateHolding.fields.tradeDateInflow')
                : t('investments.updateHolding.fields.tradeDateOutflow')
            }
            settlementDateLabel={t('investments.updateHolding.fields.settlementDate')}
            grossValueLabel={
              direction === InvestmentTransactionDirection.Inflow
                ? t('investments.updateHolding.fields.grossValueApplied')
                : t('investments.updateHolding.fields.grossValueRedeem')
            }
            netValueLabel={t('investments.updateHolding.fields.netValue')}
            quantityLabel={quantityFieldLabel}
            incomeTaxLabel={t('investments.updateHolding.fields.incomeTax')}
            iofLabel={t('investments.updateHolding.fields.iof')}
          />
        ) : null}

        {assetClass === 'fund' ? (
          <FundUpdateFields
            hideSummaryStrip={isEditMode}
            direction={direction}
            currentValue={currentPositionValue}
            currentQuantity={holding.quantity}
            currencyCode={holding.asset.currencyCode || 'BRL'}
            quantityUnit={quantityUnit}
            locale={locale}
            minDate={minDate}
            maxDate={maxDate}
            isManualPricingMode={isFundManualPricingMode(holding.pricingMode)}
            tradeDateLabel={
              direction === InvestmentTransactionDirection.Inflow
                ? t('investments.updateHolding.fields.tradeDateApply')
                : t('investments.updateHolding.fields.tradeDateOutflow')
            }
            settlementDateLabel={t('investments.updateHolding.fields.settlementDate')}
            grossValueLabel={
              direction === InvestmentTransactionDirection.Inflow
                ? t('investments.updateHolding.fields.grossValueApplied')
                : t('investments.updateHolding.fields.grossValueRedeem')
            }
            netValueLabel={t('investments.updateHolding.fields.netValue')}
            quantityLabel={quantityFieldLabel}
            unitPriceLabel={t('investments.updateHolding.fields.unitPrice')}
            valuedAtLabel={t('investments.updateHolding.fields.valuedAt')}
            incomeTaxLabel={t('investments.updateHolding.fields.incomeTax')}
            iofLabel={t('investments.updateHolding.fields.iof')}
          />
        ) : null}

        {assetClass === 'equity' ? (
          <EquityUpdateFields
            hideSummaryStrip={isEditMode}
            direction={direction}
            currentValue={currentPositionValue}
            currentQuantity={holding.quantity}
            currencyCode={holding.asset.currencyCode || 'BRL'}
            quantityUnit={quantityUnit}
            locale={locale}
            minDate={minDate}
            maxDate={maxDate}
            tradeDateLabel={t('investments.updateHolding.fields.tradeDate')}
            settlementDateLabel={t('investments.updateHolding.fields.settlementDate')}
            quantityLabel={quantityFieldLabel}
            unitPriceLabel={
              direction === InvestmentTransactionDirection.Inflow
                ? t('investments.updateHolding.fields.unitPrice')
                : t('investments.updateHolding.fields.salePrice')
            }
            totalFeesLabel={t('investments.recordEquity.fees.totalFees')}
            feesSectionLabel={
              direction === InvestmentTransactionDirection.Inflow
                ? t('investments.updateHolding.sections.purchaseFees')
                : t('investments.updateHolding.sections.saleFees')
            }
          />
        ) : null}

        {assetClass === 'crypto' ? (
          <CryptoUpdateFields
            hideSummaryStrip={isEditMode}
            direction={direction}
            currentValue={currentPositionValue}
            currentQuantity={holding.quantity}
            currencyCode={holding.asset.currencyCode || 'BRL'}
            quantityUnit={quantityUnit}
            locale={locale}
            minDate={minDate}
            maxDate={maxDate}
            tradeDateLabel={t('investments.updateHolding.fields.tradeDate')}
            settlementDateLabel={t('investments.updateHolding.fields.settlementDate')}
            quantityLabel={quantityFieldLabel}
            unitPriceLabel={
              direction === InvestmentTransactionDirection.Inflow
                ? t('investments.updateHolding.fields.unitPrice')
                : t('investments.updateHolding.fields.salePrice')
            }
          />
        ) : null}
      </HoldingUpdateDrawerShell>
    </FormProvider>
  );
}

export function UpdateHoldingDrawer({
  holdingId,
  open,
  onClose,
  initialDirection,
  editTransaction,
  onRecorded,
}: UpdateHoldingDrawerProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const [direction, setDirection] = React.useState<InvestmentTransactionDirection>(defaultDirection(initialDirection));
  const { holding, loading, error } = useHolding(open ? holdingId : null);

  React.useEffect(() => {
    if (!open) return;
    setDirection(editTransaction?.direction ?? defaultDirection(initialDirection));
  }, [editTransaction?.direction, initialDirection, holdingId, open]);

  if (!open) return null;

  if (loading && !holding) {
    return (
      <Drawer open={open} onClose={onClose} anchor="right" size="md">
        <Drawer.Header title={t('investments.updateHolding.loadingTitle')} />
        <Drawer.Body>
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
            <CircularProgress size={24} />
          </Box>
        </Drawer.Body>
      </Drawer>
    );
  }

  if (error || !holding) {
    return (
      <Drawer open={open} onClose={onClose} anchor="right" size="md">
        <Drawer.Header title={t('investments.updateHolding.errorTitle')} />
        <Drawer.Body>
          <ErrorAlert error={error ?? t('investments.updateHolding.errorHoldingNotFound')} />
        </Drawer.Body>
      </Drawer>
    );
  }

  return (
    <UpdateHoldingDrawerForm
      key={`${holding.id}-${editTransaction?.id ?? 'create'}`}
      open={open}
      holding={holding}
      onClose={onClose}
      direction={direction}
      onDirectionChange={(nextDirection) => {
        if (editTransaction) return;
        setDirection(nextDirection);
      }}
      editTransaction={editTransaction}
      onRecorded={onRecorded}
    />
  );
}

export default UpdateHoldingDrawer;
