'use client';

import type { InvestmentSource, ValuationSource } from '@/lib/graphql/types/__generated__/graphql';

export type ValueOriginLabel =
  | 'calculado_app'
  | 'estimado_app'
  | 'mercado_app'
  | 'manual'
  | 'sincronizado_provider'
  | 'importado'
  | 'externo'
  | 'indisponivel';

export function getHoldingValueOrigin(holding: {
  valuationSource: ValuationSource;
  source: InvestmentSource;
}): ValueOriginLabel {
  switch (holding.valuationSource) {
    case 'CALCULATED':
      return 'calculado_app';
    case 'ESTIMATED':
      return 'estimado_app';
    case 'MARKET_DATA':
      return 'mercado_app';
    case 'MANUAL':
      return 'manual';
    case 'UNAVAILABLE':
      return 'indisponivel';
    case 'PROVIDER':
      if (holding.source === 'OPEN_FINANCE') return 'sincronizado_provider';
      if (holding.source === 'FILE_IMPORT') return 'importado';
      return 'externo';
  }

  throw new Error(`Unknown valuationSource: ${String(holding.valuationSource)}`);
}
