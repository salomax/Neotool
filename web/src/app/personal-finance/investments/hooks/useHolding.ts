'use client';

import * as React from 'react';
import { useQuery } from '@apollo/client/react';
import {
  InvestmentHoldingsDocument,
  type InvestmentHoldingsQuery,
  type InvestmentHoldingsQueryVariables,
} from '@/lib/graphql/operations/banking/queries.generated';
import type { ErrorLike as ApolloError } from '@apollo/client';
import type { UpdateHolding } from '../components/update/types';

interface UseHoldingReturn {
  holding: UpdateHolding | null;
  loading: boolean;
  error: ApolloError | undefined;
  refetch: () => Promise<unknown>;
}

export function useHolding(holdingId: string | null): UseHoldingReturn {
  const query = useQuery<InvestmentHoldingsQuery, InvestmentHoldingsQueryVariables>(InvestmentHoldingsDocument, {
    skip: !holdingId,
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
    notifyOnNetworkStatusChange: true,
  });

  const holding = React.useMemo(() => {
    if (!holdingId) return null;
    return query.data?.investmentHoldings.find((item) => item.id === holdingId) ?? null;
  }, [holdingId, query.data?.investmentHoldings]);

  return {
    holding,
    loading: query.loading,
    error: query.error,
    refetch: query.refetch,
  };
}
