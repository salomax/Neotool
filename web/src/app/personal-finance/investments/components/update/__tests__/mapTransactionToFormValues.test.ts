import { describe, expect, it } from 'vitest';
import { InvestmentTransactionDirection } from '@/lib/graphql/types/__generated__/graphql';
import { mapTransactionToFormValues } from '../mapTransactionToFormValues';

describe('mapTransactionToFormValues', () => {
  it('maps fixed-income outflow values with parsed dates', () => {
    const result = mapTransactionToFormValues({
      direction: InvestmentTransactionDirection.Outflow,
      tradeDate: '2026-04-10',
      settlementDate: '2026-04-11',
      grossValue: 1000,
      netValue: 900,
      quantity: 3,
      unitPrice: null,
      incomeTax: 100,
      iof: 0,
    });

    expect(result.tradeDate).toBeInstanceOf(Date);
    expect(result.settlementDate).toBeInstanceOf(Date);
    expect(result.tradeDate?.getFullYear()).toBe(2026);
    expect(result.tradeDate?.getMonth()).toBe(3);
    expect(result.tradeDate?.getDate()).toBe(10);
    expect(result.grossValue).toBe(1000);
    expect(result.netValue).toBe(900);
    expect(result.incomeTax).toBe(100);
    expect(result.iof).toBe(0);
    expect(result.totalFees).toBe(100);
  });

  it('maps fund inflow values and defaults taxes to zero when missing', () => {
    const result = mapTransactionToFormValues({
      direction: InvestmentTransactionDirection.Inflow,
      tradeDate: '2026-03-22',
      settlementDate: null,
      grossValue: 2500,
      netValue: null,
      quantity: 12.5,
      unitPrice: 200,
      incomeTax: null,
      iof: null,
    });

    expect(result.tradeDate).toBeInstanceOf(Date);
    expect(result.settlementDate).toBeUndefined();
    expect(result.grossValue).toBe(2500);
    expect(result.quantity).toBe(12.5);
    expect(result.unitPrice).toBe(200);
    expect(result.incomeTax).toBe(0);
    expect(result.iof).toBe(0);
  });

  it('maps equity transaction values', () => {
    const result = mapTransactionToFormValues({
      direction: InvestmentTransactionDirection.Outflow,
      tradeDate: '2026-02-05',
      settlementDate: '2026-02-07',
      grossValue: 3200,
      netValue: 3190,
      quantity: 20,
      unitPrice: 160,
      incomeTax: 10,
      iof: null,
    });

    expect(result.tradeDate).toBeInstanceOf(Date);
    expect(result.settlementDate).toBeInstanceOf(Date);
    expect(result.quantity).toBe(20);
    expect(result.unitPrice).toBe(160);
    expect(result.totalFees).toBe(10);
  });

  it('maps crypto transaction values', () => {
    const result = mapTransactionToFormValues({
      direction: InvestmentTransactionDirection.Inflow,
      tradeDate: '2026-01-15',
      settlementDate: null,
      grossValue: 1200,
      netValue: null,
      quantity: 0.12345678,
      unitPrice: 9720,
      incomeTax: null,
      iof: null,
    });

    expect(result.tradeDate).toBeInstanceOf(Date);
    expect(result.quantity).toBe(0.12345678);
    expect(result.unitPrice).toBe(9720);
  });
});
