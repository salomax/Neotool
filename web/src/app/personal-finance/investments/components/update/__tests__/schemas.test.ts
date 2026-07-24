import dayjs from 'dayjs';
import { describe, expect, it } from 'vitest';
import { InvestmentTransactionDirection } from '@/lib/graphql/types/__generated__/graphql';
import { buildUpdateHoldingSchema } from '../schemas';

const t = (key: string) => key;

describe('buildUpdateHoldingSchema — fund', () => {
  it('requires unitPrice on fund inflow even for market-data pricing', () => {
    const schema = buildUpdateHoldingSchema({
      assetClass: 'fund',
      direction: InvestmentTransactionDirection.Inflow,
      maxQuantity: 100,
      minAllowedDate: dayjs().subtract(5, 'year'),
      t,
    });

    const result = schema.safeParse({
      tradeDate: new Date(),
      settlementDate: new Date(),
      grossValue: 1000,
      quantity: 10,
    });

    expect(result.success).toBe(false);
    if (result.success) return;
    expect(result.error.issues.some((issue) => issue.path[0] === 'unitPrice')).toBe(true);
  });

  it('requires unitPrice on fund outflow', () => {
    const schema = buildUpdateHoldingSchema({
      assetClass: 'fund',
      direction: InvestmentTransactionDirection.Outflow,
      maxQuantity: 100,
      minAllowedDate: dayjs().subtract(5, 'year'),
      t,
    });

    const result = schema.safeParse({
      tradeDate: new Date(),
      settlementDate: new Date(),
      grossValue: 500,
      netValue: 480,
      quantity: 5,
    });

    expect(result.success).toBe(false);
    if (result.success) return;
    expect(result.error.issues.some((issue) => issue.path[0] === 'unitPrice')).toBe(true);
  });

  it('accepts fund inflow when unitPrice is present', () => {
    const schema = buildUpdateHoldingSchema({
      assetClass: 'fund',
      direction: InvestmentTransactionDirection.Inflow,
      maxQuantity: 100,
      minAllowedDate: dayjs().subtract(5, 'year'),
      t,
    });

    const byAmount = schema.safeParse({
      tradeDate: new Date(),
      settlementDate: new Date(),
      grossValue: 1000,
      unitPrice: 100,
    });
    expect(byAmount.success).toBe(true);

    const byQuantity = schema.safeParse({
      tradeDate: new Date(),
      settlementDate: new Date(),
      quantity: 10,
      unitPrice: 100,
    });
    expect(byQuantity.success).toBe(true);
  });
});
