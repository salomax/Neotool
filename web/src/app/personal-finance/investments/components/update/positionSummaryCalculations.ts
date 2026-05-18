import { InvestmentTransactionDirection } from '@/lib/graphql/types/__generated__/graphql';

export function getHoldingPositionValue(holding: {
  grossValue?: number | null;
  netValue?: number | null;
  quantity?: number;
  averageCost?: number;
}): number | null {
  if (typeof holding.grossValue === 'number' && Number.isFinite(holding.grossValue)) {
    return holding.grossValue;
  }
  if (typeof holding.netValue === 'number' && Number.isFinite(holding.netValue)) {
    return holding.netValue;
  }
  if (
    typeof holding.quantity === 'number' &&
    Number.isFinite(holding.quantity) &&
    typeof holding.averageCost === 'number' &&
    Number.isFinite(holding.averageCost)
  ) {
    const costBasis = holding.quantity * holding.averageCost;
    return Number.isFinite(costBasis) ? costBasis : null;
  }
  return null;
}

/** Before the user edits the form, show the current position in the "after" column. */
export function resolveDisplayedProjectedValue(
  projectedValue: number | null,
  currentValue: number | null,
): number | null {
  if (projectedValue != null && Number.isFinite(projectedValue)) return projectedValue;
  if (currentValue != null && Number.isFinite(currentValue)) return currentValue;
  return null;
}

export function resolveDisplayedProjectedQuantity(
  projectedQuantity: number | null,
  currentQuantity: number,
): number | null {
  if (projectedQuantity != null && Number.isFinite(projectedQuantity)) return projectedQuantity;
  if (Number.isFinite(currentQuantity)) return currentQuantity;
  return null;
}

export function getImpliedUnitPrice(
  currentValue: number | null,
  currentQuantity: number,
): number | null {
  if (currentValue == null || !Number.isFinite(currentValue)) return null;
  if (!Number.isFinite(currentQuantity) || currentQuantity <= 0) return null;
  return currentValue / currentQuantity;
}

/** Gross trade value from explicit unit price, or position-implied price when only quantity is set. */
export function getTradeGrossValue(
  quantity: unknown,
  unitPrice: unknown,
  currentValue: number | null,
  currentQuantity: number,
): number | null {
  if (typeof quantity !== 'number' || !Number.isFinite(quantity)) return null;

  if (typeof unitPrice === 'number' && Number.isFinite(unitPrice)) {
    return quantity * unitPrice;
  }

  const impliedUnitPrice = getImpliedUnitPrice(currentValue, currentQuantity);
  if (impliedUnitPrice == null) return null;
  return quantity * impliedUnitPrice;
}

export function getProjectedQuantity(
  quantity: unknown,
  currentQuantity: number,
  direction: InvestmentTransactionDirection,
): number | null {
  if (typeof quantity !== 'number' || !Number.isFinite(quantity)) return null;
  return direction === InvestmentTransactionDirection.Inflow
    ? currentQuantity + quantity
    : currentQuantity - quantity;
}

export function getProjectedPositionValue(
  currentValue: number | null,
  tradeGrossValue: number | null,
  direction: InvestmentTransactionDirection,
): number | null {
  if (currentValue == null || !Number.isFinite(currentValue)) return null;
  if (tradeGrossValue == null) return null;
  return direction === InvestmentTransactionDirection.Inflow
    ? currentValue + tradeGrossValue
    : currentValue - tradeGrossValue;
}

function isFiniteNumber(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value);
}

/** Funds and fixed income: explicit gross/net amounts, or quantity-based implied trade value. */
export function getFundFixedIncomeProjectedValue(params: {
  currentValue: number | null;
  currentQuantity: number;
  direction: InvestmentTransactionDirection;
  quantity: unknown;
  unitPrice?: unknown;
  grossValue?: unknown;
  netValue?: unknown;
}): number | null {
  const { currentValue, currentQuantity, direction, quantity, unitPrice, grossValue, netValue } =
    params;

  const quantityTradeValue = getTradeGrossValue(quantity, unitPrice, currentValue, currentQuantity);

  const tradeAmount =
    direction === InvestmentTransactionDirection.Inflow
      ? isFiniteNumber(grossValue)
        ? grossValue
        : quantityTradeValue
      : isFiniteNumber(netValue)
        ? netValue
        : isFiniteNumber(grossValue)
          ? grossValue
          : quantityTradeValue;

  return getProjectedPositionValue(currentValue, tradeAmount, direction);
}
