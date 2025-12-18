"use client";

import * as React from "react";
import { useQuery } from "@tanstack/react-query";

export type PageQuery = {
  page: number; // zero-based
  pageSize: number; // items per page
  sort?: string; // e.g., "name:asc,id:desc"
  filter?: Record<string, any>;
};

export type PageResult<T> = {
  rows: T[];
  total: number;
};

export type UseDataTableQueryOptions<T> = {
  key: string; // query key namespace
  fetcher: (_q: PageQuery) => Promise<PageResult<T>>;
  initialPage?: number;
  initialPageSize?: number;
  initialSort?: string;
  initialFilter?: Record<string, any>;
};

export function useDataTableQuery<T>(opts: UseDataTableQueryOptions<T>) {
  const [page, setPage] = React.useState(opts.initialPage ?? 0);
  const [pageSize, setPageSize] = React.useState(opts.initialPageSize ?? 25);
  const [sort, setSort] = React.useState<string | undefined>(opts.initialSort);
  const [filter, setFilter] = React.useState<Record<string, any> | undefined>(
    opts.initialFilter,
  );

  const query = useQuery({
    queryKey: [opts.key, page, pageSize, sort, filter],
    queryFn: () => {
      const queryParams: PageQuery = { page, pageSize };
      if (sort) queryParams.sort = sort;
      if (filter) queryParams.filter = filter;
      return opts.fetcher(queryParams);
    },
    placeholderData: (previousData) => previousData,
  });

  const onPageChange = (p: number, ps?: number) => {
    setPage(p);
    if (ps && ps !== pageSize) setPageSize(ps);
  };

  return {
    ...query,
    page,
    pageSize,
    sort,
    filter,
    setPage,
    setPageSize,
    setSort,
    setFilter,
    onPageChange,
  };
}
