import { expect } from '@playwright/test';
import {
  getInvestmentPortfolioSummary,
  PerformancePeriod,
  triggerPortfolioRecalculation,
} from './investment-graphql';
import { isoDaysAgo } from './fixed-income-e2e';
import { E2E_RECALC_REPLAY_PAUSE_MS, E2E_RECALC_TIMEOUT_MS } from './timeouts';

export { E2E_RECALC_TIMEOUT_MS };

/**
 * Waits until portfolio TWR recalculation is idle for the scoped summary query.
 * Under load, Kafka-backed jobs can lag; a one-time manual trigger nudges stuck PENDING jobs.
 */
export async function waitForRecalculation(
  token: string,
  financialAccountId?: string,
  options: { timeoutMs?: number; period?: PerformancePeriod } = {},
): Promise<void> {
  const timeoutMs = options.timeoutMs ?? E2E_RECALC_TIMEOUT_MS;
  const period = options.period ?? PerformancePeriod.All;
  const started = Date.now();
  let nudged = false;

  const isStillCalculating = async (): Promise<boolean> => {
    const summary = await getInvestmentPortfolioSummary(token, {
      financialAccountId,
      period,
    });
    const status = summary.recalculationStatus;
    if (status.errorCode) {
      throw new Error(
        `Portfolio recalculation failed (${status.errorCode}): ${status.message ?? 'no message'}`,
      );
    }
    return status.isCalculating;
  };

  while (Date.now() - started < timeoutMs) {
    if (!(await isStillCalculating())) {
      return;
    }

    if (!nudged && Date.now() - started > timeoutMs / 2) {
      nudged = true;
      await triggerPortfolioRecalculation(token, { financialAccountId }).catch(() => {});
      await new Promise((resolve) => setTimeout(resolve, 2000));
      if (!(await isStillCalculating())) {
        return;
      }
    }

    await new Promise((resolve) => setTimeout(resolve, 1000));
  }

  if (await isStillCalculating()) {
    throw new Error(`Timed out after ${timeoutMs}ms waiting for portfolio recalculation`);
  }
}

/** Polls portfolio summary until `performance.twr` is populated and close to `expected`. */
export async function expectPortfolioTwrCloseTo(
  token: string,
  expected: number,
  options: {
    financialAccountId?: string;
    precision?: number;
    timeoutMs?: number;
  } = {},
): Promise<void> {
  const precision = options.precision ?? 1;
  const timeoutMs = options.timeoutMs ?? E2E_RECALC_TIMEOUT_MS;

  await expect
    .poll(
      async () => {
        const summary = await getInvestmentPortfolioSummary(token, {
          financialAccountId: options.financialAccountId,
          period: PerformancePeriod.All,
        });
        const twr = summary.performance.twr;
        return twr != null ? twr : Number.NaN;
      },
      { timeout: timeoutMs },
    )
    .toBeCloseTo(expected, precision);
}

/** Clears persisted investment grid class tab selection between scenarios. */
export async function clearInvestmentGridClassSelection(page: import('@playwright/test').Page): Promise<void> {
  await page.evaluate(() => {
    for (const key of Object.keys(sessionStorage)) {
      if (key.startsWith('investments.grid.selectedAssetClassKey')) {
        sessionStorage.removeItem(key);
      }
    }
  });
}

async function waitAllScopes(
  token: string,
  brokerageAccountId: string,
  cryptoAccountId: string,
): Promise<void> {
  await waitForRecalculation(token, brokerageAccountId);
  await waitForRecalculation(token, cryptoAccountId);
  await waitForRecalculation(token);
}

/**
 * Runs repeated forced recalculations and waits for idle — used for idempotency tests.
 * Compare snapshots **after** two rounds, not before the first round vs after.
 */
export async function replayRecalculationRound(
  token: string,
  brokerageAccountId: string,
  cryptoAccountId: string,
  options: { recalculateFromDaysAgo?: number; innerIterations?: number } = {},
): Promise<void> {
  const recalculateFrom = isoDaysAgo(options.recalculateFromDaysAgo ?? 130);
  const innerIterations = options.innerIterations ?? 2;

  for (let i = 0; i < innerIterations; i += 1) {
    await triggerPortfolioRecalculation(token, {
      financialAccountId: brokerageAccountId,
      recalculateFrom,
    });
    await triggerPortfolioRecalculation(token, {
      financialAccountId: cryptoAccountId,
      recalculateFrom,
    });
    await waitAllScopes(token, brokerageAccountId, cryptoAccountId);
    await new Promise((resolve) => setTimeout(resolve, E2E_RECALC_REPLAY_PAUSE_MS));
  }
  await waitAllScopes(token, brokerageAccountId, cryptoAccountId);
}
