import { describe, expect, it } from 'vitest';
import {
  HoldingPricingMode,
  InvestmentTransactionDirection,
} from '@/lib/graphql/types/__generated__/graphql';
import { buildFundManualValuationInput, deriveFundOutflowNetValue, deriveFundTradeLegs } from '../types';

describe('deriveFundTradeLegs', () => {
  it('derives quantity from grossValue × unitPrice', () => {
    expect(
      deriveFundTradeLegs({
        grossValue: 1000,
        unitPrice: 10,
      }),
    ).toEqual({ quantity: 100, grossValue: 1000, unitPrice: 10 });
  });

  it('derives grossValue from quantity × unitPrice', () => {
    expect(
      deriveFundTradeLegs({
        quantity: 50,
        unitPrice: 12.5,
      }),
    ).toEqual({ quantity: 50, grossValue: 625, unitPrice: 12.5 });
  });

  it('keeps explicit legs when both are present', () => {
    expect(
      deriveFundTradeLegs({
        grossValue: 1000,
        quantity: 80,
        unitPrice: 10,
      }),
    ).toEqual({ quantity: 80, grossValue: 1000, unitPrice: 10 });
  });

  it('falls back to netValue when gross is missing and quantity cannot be derived', () => {
    expect(
      deriveFundTradeLegs({
        netValueFallback: 900,
        unitPrice: 10,
      }),
    ).toEqual({ quantity: undefined, grossValue: 900, unitPrice: 10 });
  });
});

describe('buildFundManualValuationInput', () => {
  const valuedAt = new Date('2026-07-20T12:00:00');

  it('returns manualValuation only for MANUAL-priced fund inflows', () => {
    expect(
      buildFundManualValuationInput({
        direction: InvestmentTransactionDirection.Inflow,
        pricingMode: HoldingPricingMode.Manual,
        valuedAt,
        grossValue: 1000,
      }),
    ).toEqual({ grossValue: 1000, valuedAt: '2026-07-20' });
  });

  it('omits manualValuation for market-data / non-manual pricing even with valuedAt seeded', () => {
    expect(
      buildFundManualValuationInput({
        direction: InvestmentTransactionDirection.Inflow,
        pricingMode: HoldingPricingMode.MarketData,
        valuedAt,
        grossValue: 1000,
      }),
    ).toBeUndefined();

    expect(
      buildFundManualValuationInput({
        direction: InvestmentTransactionDirection.Inflow,
        pricingMode: null,
        valuedAt,
        grossValue: 1000,
      }),
    ).toBeUndefined();
  });

  it('omits manualValuation on outflows', () => {
    expect(
      buildFundManualValuationInput({
        direction: InvestmentTransactionDirection.Outflow,
        pricingMode: HoldingPricingMode.Manual,
        valuedAt,
        grossValue: 1000,
      }),
    ).toBeUndefined();
  });

  it('omits manualValuation when valuedAt is missing', () => {
    expect(
      buildFundManualValuationInput({
        direction: InvestmentTransactionDirection.Inflow,
        pricingMode: HoldingPricingMode.Manual,
        grossValue: 1000,
      }),
    ).toBeUndefined();
  });
});

describe('deriveFundOutflowNetValue', () => {
  it('computes gross − IR − IOF', () => {
    expect(
      deriveFundOutflowNetValue({
        grossValue: 1000,
        incomeTax: 150,
        iof: 20,
      }),
    ).toBe(830);
  });

  it('treats missing taxes as zero and floors at zero', () => {
    expect(deriveFundOutflowNetValue({ grossValue: 1000 })).toBe(1000);
    expect(deriveFundOutflowNetValue({ grossValue: 100, incomeTax: 120 })).toBe(0);
    expect(deriveFundOutflowNetValue({})).toBeUndefined();
  });
});
