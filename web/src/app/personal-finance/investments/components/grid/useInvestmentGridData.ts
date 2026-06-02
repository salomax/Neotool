"use client";

import { useApolloClient, useQuery } from "@apollo/client/react";
import { useCallback } from "react";
import type { InvestmentGridDataQuery } from "@/lib/graphql/operations/banking/queries.generated";
import { INVESTMENT_GRID_DATA } from "@/lib/graphql/operations/banking/queries";

export function useInvestmentGridData(financialAccountId: string | null) {
  const apolloClient = useApolloClient();
  const queryResult = useQuery<InvestmentGridDataQuery>(INVESTMENT_GRID_DATA, {
    variables: { financialAccountId: financialAccountId ?? undefined, includeProjection: true },
    errorPolicy: "all",
  });

  const refreshGridQueries = useCallback(async () => {
    await apolloClient.refetchQueries({ include: [INVESTMENT_GRID_DATA] });
  }, [apolloClient]);

  return { ...queryResult, refreshGridQueries };
}
