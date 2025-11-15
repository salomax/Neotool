"use client";

import * as React from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

let _client: QueryClient | null = null;
function getClient() {
  if (!_client) {
    _client = new QueryClient({
      defaultOptions: {
        queries: {
          staleTime: 30_000,
          refetchOnWindowFocus: false,
          retry: 1,
        },
      },
    });
  }
  return _client;
}

export const AppQueryProvider: React.FC<React.PropsWithChildren> = ({
  children,
}) => {
  const client = React.useMemo(() => getClient(), []);
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
};
