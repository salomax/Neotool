'use client';

import React from 'react';
import { AssetType, InvestmentTransaction } from '@/lib/graphql/types/__generated__/graphql';
import { RecordEquityHoldingDrawer } from './RecordEquityHoldingDrawer';
import { RecordFixedIncomeDrawer } from './RecordFixedIncomeDrawer';
import { RecordFundHoldingDrawer } from './RecordFundHoldingDrawer';
import type { IssuerSelectionAccount } from './IssuerSelection';
import type { HoldingKind, HoldingRow } from './table/investmentTableHelpers';

interface InvestmentEditDrawersProps {
  holding: HoldingRow | null;
  transaction: InvestmentTransaction | null;
  kind: HoldingKind;
  financialAccountId: string | null;
  financialAccounts: IssuerSelectionAccount[];
  onClose: () => void;
  onRecorded: () => Promise<void>;
}

export const InvestmentEditDrawers = React.memo(function InvestmentEditDrawers({
  holding,
  transaction,
  kind,
  financialAccountId,
  financialAccounts,
  onClose,
  onRecorded,
}: InvestmentEditDrawersProps) {
  const isOpen = Boolean(holding && transaction);

  return (
    <>
      <RecordFixedIncomeDrawer
        open={isOpen && kind === 'fixedIncome'}
        onClose={onClose}
        financialAccountId={financialAccountId}
        financialAccounts={financialAccounts}
        editHolding={
          holding
            ? {
                id: holding.id,
                asset: {
                  id: holding.asset.id,
                  name: holding.asset.name,
                  assetType: holding.asset.assetType,
                  issuerName: holding.asset.issuerName ?? null,
                },
                indexer: holding.indexer ?? null,
                indexerPercentage: holding.indexerPercentage ?? null,
                fixedAnnualRate: holding.fixedAnnualRate ?? null,
                maturityDate: holding.maturityDate ?? null,
                isTaxExempt: holding.isTaxExempt ?? false,
              }
            : null
        }
        editTransaction={transaction}
        onRecorded={onRecorded}
      />

      <RecordFundHoldingDrawer
        open={isOpen && kind === 'fund'}
        onClose={onClose}
        financialAccountId={financialAccountId}
        financialAccounts={financialAccounts}
        editHolding={
          holding
            ? {
                id: holding.id,
                pricingMode: holding.pricingMode ?? null,
                valuationReferenceDate: holding.valuationReferenceDate ?? null,
                grossValue: holding.grossValue ?? null,
                netValue: holding.netValue ?? null,
                asset: {
                  id: holding.asset.id,
                  name: holding.asset.name,
                  assetType: holding.asset.assetType as AssetType,
                },
              }
            : null
        }
        editTransaction={transaction}
        onRecorded={onRecorded}
      />

      <RecordEquityHoldingDrawer
        open={isOpen && kind === 'equity'}
        onClose={onClose}
        financialAccountId={financialAccountId}
        financialAccounts={financialAccounts}
        editHolding={
          holding
            ? {
                id: holding.id,
                asset: {
                  id: holding.asset.id,
                  ticker: holding.asset.ticker ?? null,
                  name: holding.asset.name,
                  assetType: holding.asset.assetType as AssetType,
                },
              }
            : null
        }
        editTransaction={transaction}
        onRecorded={onRecorded}
      />
    </>
  );
});
