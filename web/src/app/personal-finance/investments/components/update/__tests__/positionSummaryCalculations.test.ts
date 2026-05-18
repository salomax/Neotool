import { InvestmentTransactionDirection } from '@/lib/graphql/types/__generated__/graphql';
import {
  getFundFixedIncomeProjectedValue,
  getHoldingPositionValue,
  getProjectedPositionValue,
  getProjectedQuantity,
  getTradeGrossValue,
  resolveDisplayedProjectedQuantity,
  resolveDisplayedProjectedValue,
} from '../positionSummaryCalculations';

describe('positionSummaryCalculations', () => {
  it('derives trade value from quantity using implied unit price', () => {
    expect(getTradeGrossValue(10, undefined, 1000, 100)).toBe(100);
    expect(getTradeGrossValue(10, 12, 1000, 100)).toBe(120);
  });

  it('projects position value and quantity when quantity is set', () => {
    expect(
      getProjectedPositionValue(1000, 100, InvestmentTransactionDirection.Inflow),
    ).toBe(1100);
    expect(getProjectedQuantity(10, 100, InvestmentTransactionDirection.Inflow)).toBe(110);
  });

  it('resolves holding position value and initial projected display', () => {
    expect(
      getHoldingPositionValue({
        grossValue: null,
        netValue: 500,
        quantity: 10,
        averageCost: 40,
      }),
    ).toBe(500);

    expect(resolveDisplayedProjectedValue(null, 1000)).toBe(1000);
    expect(resolveDisplayedProjectedQuantity(null, 25)).toBe(25);
  });

  it('projects fund and fixed income values from quantity when amounts are empty', () => {
    expect(
      getFundFixedIncomeProjectedValue({
        currentValue: 1000,
        currentQuantity: 100,
        direction: InvestmentTransactionDirection.Inflow,
        quantity: 10,
        grossValue: undefined,
      }),
    ).toBe(1100);

    expect(
      getFundFixedIncomeProjectedValue({
        currentValue: 1000,
        currentQuantity: 100,
        direction: InvestmentTransactionDirection.Outflow,
        quantity: 10,
        grossValue: undefined,
        netValue: undefined,
      }),
    ).toBe(900);
  });
});
