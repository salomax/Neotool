import { useCallback, useMemo, useRef, useState } from 'react';
import { Transaction } from '@/lib/graphql/types/__generated__/graphql';
import { TransactionsQuery } from '@/lib/graphql/operations/banking/queries.generated';

type FetchMoreFn = (options: {
  variables: { after: string };
  updateQuery: (
    prev: TransactionsQuery,
    opts: { fetchMoreResult: TransactionsQuery | undefined },
  ) => TransactionsQuery;
}) => Promise<unknown>;

interface UsePaginatedTransactionsResult {
  transactions: Transaction[];
  tableLoading: boolean;
  hasMore: boolean;
  onLoadMore: () => void;
}

export function usePaginatedTransactions(
  data: TransactionsQuery | undefined,
  loading: boolean,
  fetchMore: FetchMoreFn,
): UsePaginatedTransactionsResult {
  const [isFetchingMore, setIsFetchingMore] = useState(false);
  const isFetchingMoreRef = useRef(false);

  const transactions = useMemo(
    () => (data?.transactions?.edges?.map((edge) => edge.node) || []) as Transaction[],
    [data?.transactions?.edges],
  );

  const hasMore = data?.transactions?.pageInfo?.hasNextPage || false;
  const endCursor = data?.transactions?.pageInfo?.endCursor ?? null;

  const onLoadMore = useCallback(() => {
    if (!hasMore || !endCursor) return;
    if (loading || isFetchingMoreRef.current) return;

    isFetchingMoreRef.current = true;
    setIsFetchingMore(true);

    fetchMore({
      variables: { after: endCursor },
      updateQuery: (previousQueryResult, { fetchMoreResult }) => {
        if (!fetchMoreResult?.transactions) return previousQueryResult;

        const previousEdges = previousQueryResult.transactions?.edges ?? [];
        const incomingEdges = fetchMoreResult.transactions.edges ?? [];

        const seenIds = new Set<string>();
        const mergedEdges = [...previousEdges, ...incomingEdges].filter((edge) => {
          const id = edge?.node?.id;
          if (!id) return false;
          if (seenIds.has(id)) return false;
          seenIds.add(id);
          return true;
        });

        return {
          ...previousQueryResult,
          transactions: {
            ...previousQueryResult.transactions,
            ...fetchMoreResult.transactions,
            edges: mergedEdges,
            pageInfo: fetchMoreResult.transactions.pageInfo,
          },
        };
      },
    }).finally(() => {
      isFetchingMoreRef.current = false;
      setIsFetchingMore(false);
    });
  }, [endCursor, fetchMore, hasMore, loading]);

  return {
    transactions,
    tableLoading: loading || isFetchingMore,
    hasMore,
    onLoadMore,
  };
}
