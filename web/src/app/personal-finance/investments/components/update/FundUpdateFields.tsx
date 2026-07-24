'use client';

import * as React from 'react';
import { Box, Tab } from '@mui/material';
import { useQuery } from '@apollo/client/react';
import { useFormContext, useWatch } from 'react-hook-form';
import dayjs from 'dayjs';
import { useTranslation } from '@/shared/i18n';
import { FormRowStack, FormStack } from '@/shared/components/ui/forms/components';
import { FormTabs } from '@/shared/components/ui/forms/components/FormTabs';
import { DatePickerField, MoneyInput, NumberField } from '@/shared/components/ui/forms/form';
import { InvestmentTransactionDirection } from '@/lib/graphql/types/__generated__/graphql';
import {
  FundNavSeriesDocument,
  InvestmentFundNavDocument,
  SearchInvestmentFundsAutocompleteDocument,
} from '@/lib/graphql/operations/financial-data/queries.generated';
import { formatDateOnly, isValidDate } from '@/shared/utils/date/dateOnly';
import { personalFinanceTranslations } from '../../../i18n';
import { deriveFundOutflowNetValue, type UpdateHoldingFormValues } from './types';
import { PositionSummaryStrip } from './PositionSummaryStrip';
import {
  getFundFixedIncomeProjectedValue,
  getProjectedQuantity,
} from './positionSummaryCalculations';
import { UpdateFormSectionLabel } from './UpdateFormSectionLabel';
import { InvestmentTradeAndSettlementDateRow } from '../InvestmentTradeAndSettlementDateRow';

type UnitPriceMode = 'AUTO' | 'MANUAL';
type ValueFlow = 'BY_AMOUNT' | 'BY_QUANTITY';

interface FundUpdateFieldsProps {
  hideSummaryStrip?: boolean;
  direction: InvestmentTransactionDirection;
  currentValue: number | null;
  currentQuantity: number;
  currencyCode: string;
  quantityUnit: string;
  locale: string;
  minDate: Date;
  maxDate: Date;
  isManualPricingMode: boolean;
  /** CVM fund CNPJ when available — enables NAV suggestion by trade date. */
  fundCnpj?: string | null;
  /** Fund display name — used to resolve CVM fund when CNPJ is missing. */
  fundName?: string | null;
  /** When editing an existing transaction, do not auto-overwrite the seeded unit price. */
  disableAutoUnitPrice?: boolean;
  tradeDateLabel: string;
  settlementDateLabel: string;
  grossValueLabel: string;
  netValueLabel: string;
  quantityLabel: string;
  unitPriceLabel: string;
  valuedAtLabel: string;
  incomeTaxLabel: string;
  iofLabel: string;
}

function toPositiveNumber(value: unknown): number | null {
  if (typeof value !== 'number' || !Number.isFinite(value) || value <= 0) return null;
  return value;
}

export function FundUpdateFields({
  hideSummaryStrip = false,
  direction,
  currentValue,
  currentQuantity,
  currencyCode,
  quantityUnit,
  locale,
  minDate,
  maxDate,
  isManualPricingMode,
  fundCnpj,
  fundName,
  disableAutoUnitPrice = false,
  tradeDateLabel,
  settlementDateLabel,
  grossValueLabel,
  netValueLabel,
  quantityLabel,
  unitPriceLabel,
  valuedAtLabel,
  incomeTaxLabel,
  iofLabel,
}: FundUpdateFieldsProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const { control, setValue } = useFormContext<UpdateHoldingFormValues>();
  const grossValue = useWatch({ control, name: 'grossValue' });
  const netValue = useWatch({ control, name: 'netValue' });
  const quantity = useWatch({ control, name: 'quantity' });
  const unitPrice = useWatch({ control, name: 'unitPrice' });
  const tradeDate = useWatch({ control, name: 'tradeDate' });
  const incomeTax = useWatch({ control, name: 'incomeTax' });
  const iof = useWatch({ control, name: 'iof' });

  const [unitPriceMode, setUnitPriceMode] = React.useState<UnitPriceMode>(
    disableAutoUnitPrice ? 'MANUAL' : 'AUTO',
  );
  const [valueFlow, setValueFlow] = React.useState<ValueFlow>(() => {
    const hasQuantity = toPositiveNumber(quantity) != null;
    const hasUnitPrice = toPositiveNumber(unitPrice) != null;
    return hasQuantity && hasUnitPrice ? 'BY_QUANTITY' : 'BY_AMOUNT';
  });

  const halfRowFieldSx = React.useMemo(
    () => ({ flex: 1, minWidth: 0, maxWidth: { xs: '100%', sm: '50%' } }),
    [],
  );

  const tradeDateIso = React.useMemo(
    () => (isValidDate(tradeDate) ? formatDateOnly(tradeDate) : null),
    [tradeDate],
  );
  const cnpjDigits = React.useMemo(() => (fundCnpj ?? '').replace(/\D/g, ''), [fundCnpj]);
  const lookupQuery = React.useMemo(() => {
    if (cnpjDigits.length === 14) return cnpjDigits;
    const trimmedName = fundName?.trim() ?? '';
    return trimmedName.length >= 3 ? trimmedName : '';
  }, [cnpjDigits, fundName]);
  const navFromIso = React.useMemo(
    () => (tradeDateIso ? dayjs(tradeDateIso).subtract(120, 'day').format('YYYY-MM-DD') : null),
    [tradeDateIso],
  );

  // Same idea as create (CVM): suggest NAV whenever we can resolve the fund + trade date.
  // Holding pricingMode (manual vs market) only controls valuedAt for position valuation, not trade quote suggestion.
  const canLookupNav = !disableAutoUnitPrice && Boolean(lookupQuery) && Boolean(tradeDateIso);

  const fundLookupQuery = useQuery(SearchInvestmentFundsAutocompleteDocument, {
    variables: { query: lookupQuery },
    skip: !canLookupNav,
    fetchPolicy: 'cache-first',
    errorPolicy: 'all',
  });

  const resolvedFund = React.useMemo(() => {
    if (!canLookupNav) return null;
    const suggestions = fundLookupQuery.data?.searchInvestmentFundsAutocomplete?.suggestions ?? [];
    if (suggestions.length === 0) return null;
    if (cnpjDigits.length === 14) {
      return suggestions.find((item) => item.cnpj.replace(/\D/g, '') === cnpjDigits) ?? null;
    }
    const normalizedName = (fundName ?? '').trim().toLowerCase();
    return (
      suggestions.find((item) => item.name.trim().toLowerCase() === normalizedName) ??
      suggestions[0] ??
      null
    );
  }, [
    canLookupNav,
    cnpjDigits,
    fundLookupQuery.data?.searchInvestmentFundsAutocomplete?.suggestions,
    fundName,
  ]);

  const resolvedFundId = resolvedFund?.id ?? null;
  const resolvedCnpjDigits = React.useMemo(() => {
    if (cnpjDigits.length === 14) return cnpjDigits;
    return (resolvedFund?.cnpj ?? '').replace(/\D/g, '');
  }, [cnpjDigits, resolvedFund?.cnpj]);

  // Primary path: same query as RecordFundHoldingDrawer (priceHistory up to trade date).
  const fundNavByIdQuery = useQuery(InvestmentFundNavDocument, {
    variables: { id: resolvedFundId ?? '', to: tradeDateIso ?? '' },
    skip: !canLookupNav || !resolvedFundId || !tradeDateIso,
    fetchPolicy: 'network-only',
    errorPolicy: 'all',
  });

  // Fallback when autocomplete cannot resolve the CVM fund id.
  const fundNavSeriesQuery = useQuery(FundNavSeriesDocument, {
    variables: {
      cnpj: resolvedCnpjDigits,
      from: navFromIso ?? '',
      to: tradeDateIso ?? '',
    },
    skip:
      !canLookupNav ||
      Boolean(resolvedFundId) ||
      resolvedCnpjDigits.length !== 14 ||
      !tradeDateIso ||
      !navFromIso,
    fetchPolicy: 'network-only',
    errorPolicy: 'all',
  });

  const suggestedUnitPrice = React.useMemo(() => {
    if (!canLookupNav) return null;
    const byId = fundNavByIdQuery.data?.investmentFund?.priceHistory?.[0]?.closePrice;
    if (typeof byId === 'number' && Number.isFinite(byId) && byId > 0) return byId;

    const series = fundNavSeriesQuery.data?.fundNavSeries ?? [];
    if (series.length === 0) return null;
    const closePrice = series[series.length - 1]?.closePrice;
    return typeof closePrice === 'number' && Number.isFinite(closePrice) && closePrice > 0 ? closePrice : null;
  }, [canLookupNav, fundNavByIdQuery.data?.investmentFund?.priceHistory, fundNavSeriesQuery.data?.fundNavSeries]);

  const lastAppliedRef = React.useRef<{ tradeDateIso: string | null; price: number | null }>({
    tradeDateIso: null,
    price: null,
  });

  // Changing the application date should re-enter AUTO and pick the NAV for that date.
  React.useEffect(() => {
    if (disableAutoUnitPrice) {
      setUnitPriceMode('MANUAL');
      return;
    }
    setUnitPriceMode('AUTO');
    lastAppliedRef.current = { tradeDateIso: null, price: null };
  }, [disableAutoUnitPrice, tradeDateIso]);

  React.useEffect(() => {
    if (!canLookupNav || unitPriceMode !== 'AUTO') return;
    if (!suggestedUnitPrice || !tradeDateIso) return;
    if (
      lastAppliedRef.current.tradeDateIso === tradeDateIso &&
      lastAppliedRef.current.price === suggestedUnitPrice
    ) {
      return;
    }
    lastAppliedRef.current = { tradeDateIso, price: suggestedUnitPrice };
    setValue('unitPrice', suggestedUnitPrice, { shouldDirty: false, shouldTouch: true, shouldValidate: true });
  }, [canLookupNav, setValue, suggestedUnitPrice, tradeDateIso, unitPriceMode]);

  // Auto-complete triad: amount ↔ quantity ↔ unit price (same model as create fund form).
  React.useEffect(() => {
    const price = toPositiveNumber(unitPrice);
    if (!price) return;

    if (valueFlow === 'BY_AMOUNT') {
      const amount = toPositiveNumber(grossValue);
      if (!amount) return;
      const nextQuantity = amount / price;
      if (!Number.isFinite(nextQuantity) || nextQuantity <= 0) return;
      if (typeof quantity === 'number' && Math.abs(quantity - nextQuantity) < 1e-12) return;
      setValue('quantity', nextQuantity, { shouldDirty: false, shouldValidate: true });
      return;
    }

    const qty = toPositiveNumber(quantity);
    if (!qty) return;
    const nextGross = qty * price;
    if (!Number.isFinite(nextGross) || nextGross <= 0) return;
    if (typeof grossValue === 'number' && Math.abs(grossValue - nextGross) < 1e-8) return;
    setValue('grossValue', nextGross, { shouldDirty: false, shouldValidate: true });
  }, [grossValue, quantity, setValue, unitPrice, valueFlow]);

  // Outflow net value is derived: gross − IR − IOF (read-only in the form).
  React.useEffect(() => {
    if (direction !== InvestmentTransactionDirection.Outflow) return;
    const nextNet = deriveFundOutflowNetValue({
      grossValue: typeof grossValue === 'number' ? grossValue : undefined,
      incomeTax: typeof incomeTax === 'number' ? incomeTax : undefined,
      iof: typeof iof === 'number' ? iof : undefined,
    });
    if (nextNet == null) {
      if (netValue != null) {
        setValue('netValue', undefined, { shouldDirty: false, shouldValidate: true });
      }
      return;
    }
    if (typeof netValue === 'number' && Math.abs(netValue - nextNet) < 1e-8) return;
    setValue('netValue', nextNet, { shouldDirty: false, shouldValidate: true });
  }, [direction, grossValue, incomeTax, iof, netValue, setValue]);

  const projectedValue = React.useMemo(
    () =>
      getFundFixedIncomeProjectedValue({
        currentValue,
        currentQuantity,
        direction,
        quantity,
        unitPrice,
        grossValue,
        netValue,
      }),
    [currentQuantity, currentValue, direction, grossValue, netValue, quantity, unitPrice],
  );

  const projectedQuantity = React.useMemo(
    () => getProjectedQuantity(quantity, currentQuantity, direction),
    [currentQuantity, direction, quantity],
  );

  const unitPriceField = (
    <MoneyInput
      name="unitPrice"
      label={unitPriceLabel}
      currency="BRL"
      size="medium"
      fractionDigits={8}
      fixedDecimalScale={false}
      data-testid="fund-update-unit-price"
      onNumericValueChange={(value) => {
        if (disableAutoUnitPrice) {
          setUnitPriceMode('MANUAL');
          return;
        }
        setUnitPriceMode(value == null ? 'AUTO' : 'MANUAL');
      }}
    />
  );

  return (
    <FormStack>
      {!hideSummaryStrip ? (
        <PositionSummaryStrip
          currentValue={currentValue}
          currentQuantity={currentQuantity}
          projectedValue={projectedValue}
          projectedQuantity={projectedQuantity}
          currencyCode={currencyCode}
          quantityUnit={quantityUnit}
          locale={locale}
        />
      ) : null}

      <UpdateFormSectionLabel title={t('investments.updateHolding.sections.dates')} />
      <InvestmentTradeAndSettlementDateRow<UpdateHoldingFormValues>
        tradeDateLabel={tradeDateLabel}
        settlementDateLabel={settlementDateLabel}
        minDate={minDate}
        tradeMaxDate={maxDate}
        size="medium"
      />

      <FormTabs
        value={valueFlow}
        onChange={(_, next: ValueFlow) => {
          setValueFlow(next);
        }}
        data-testid="fund-update-value-flow-tabs"
      >
        <Tab value="BY_AMOUNT" label={t('investments.recordFund.flows.byAmount')} />
        <Tab value="BY_QUANTITY" label={t('investments.recordFund.flows.byQuantity')} />
      </FormTabs>

      {valueFlow === 'BY_AMOUNT' ? (
        <FormRowStack alignItems="flex-start" data-testid="fund-update-by-amount-row">
          <Box sx={{ flex: 1, minWidth: 0 }}>
            <MoneyInput
              name="grossValue"
              label={grossValueLabel}
              currency="BRL"
              size="medium"
              data-testid="fund-update-gross-value"
            />
          </Box>
          <Box sx={{ flex: 1, minWidth: 0 }}>{unitPriceField}</Box>
        </FormRowStack>
      ) : (
        <Box
          data-testid="fund-update-quotas-row"
          sx={{
            display: 'grid',
            gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' },
            gap: 2,
          }}
        >
          <NumberField
            name="quantity"
            label={quantityLabel}
            locale={locale}
            fractionDigits={6}
            fixedDecimalScale={false}
            size="medium"
            data-testid="fund-update-quantity"
          />
          {unitPriceField}
        </Box>
      )}

      {direction === InvestmentTransactionDirection.Inflow && isManualPricingMode ? (
        <>
          <UpdateFormSectionLabel title={t('investments.updateHolding.sections.pricing')} />
          <FormRowStack>
            <Box sx={halfRowFieldSx}>
              <DatePickerField
                name="valuedAt"
                label={valuedAtLabel}
                minDate={minDate}
                maxDate={maxDate}
                size="medium"
              />
            </Box>
          </FormRowStack>
        </>
      ) : null}

      {direction === InvestmentTransactionDirection.Outflow ? (
        <>
          <UpdateFormSectionLabel title={t('investments.updateHolding.sections.taxes')} />
          <FormRowStack>
            <Box sx={{ flex: 1, minWidth: 0 }}>
              <MoneyInput name="incomeTax" label={incomeTaxLabel} currency="BRL" size="medium" />
            </Box>
            <Box sx={{ flex: 1, minWidth: 0 }}>
              <MoneyInput name="iof" label={iofLabel} currency="BRL" size="medium" />
            </Box>
          </FormRowStack>
          <UpdateFormSectionLabel title={t('investments.updateHolding.sections.totals')} />
          <FormRowStack>
            <Box sx={halfRowFieldSx}>
              <MoneyInput
                name="netValue"
                label={netValueLabel}
                currency="BRL"
                size="medium"
                disabled
                data-testid="fund-update-net-value"
              />
            </Box>
          </FormRowStack>
        </>
      ) : null}
    </FormStack>
  );
}
