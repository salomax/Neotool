/**
 * Investment fixtures for E2E tests.
 * Built on top of personal-finance fixtures to reuse authenticated worker user/state.
 */

import { test as base } from './personal-finance';
import {
  createFinancialAccount,
} from '../helpers/banking-graphql';
import type { FinancialAccount } from '../helpers/banking-graphql';

export interface InvestmentFixtures {
  brokerageAccount: FinancialAccount;
  secondaryBrokerageAccount: FinancialAccount;
  cryptoAccount: FinancialAccount;
}

function uniqueName(prefix: string): string {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
}

export const test = base.extend<InvestmentFixtures>({
  brokerageAccount: async ({ pfUser }, provideFixture) => {
    const account = await createFinancialAccount(
      {
        name: uniqueName('Corretora E2E A'),
        type: 'INVESTMENT',
        subtype: 'BROKERAGE',
      },
      pfUser.token,
    );

    await provideFixture(account);
  },

  secondaryBrokerageAccount: async ({ pfUser }, provideFixture) => {
    const account = await createFinancialAccount(
      {
        name: uniqueName('Corretora E2E B'),
        type: 'INVESTMENT',
        subtype: 'BROKERAGE',
      },
      pfUser.token,
    );

    await provideFixture(account);
  },

  cryptoAccount: async ({ pfUser }, provideFixture) => {
    const account = await createFinancialAccount(
      {
        name: uniqueName('Exchange Cripto E2E'),
        type: 'INVESTMENT',
        subtype: 'CRYPTO',
      },
      pfUser.token,
    );

    await provideFixture(account);
  },
});

export { expect } from '@playwright/test';
