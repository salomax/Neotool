import * as React from "react";
import { useQuery } from "@apollo/client/react";
import { useRelayPagination } from "@/shared/hooks/pagination";
import {
  BACEN_INSTITUTIONS,
  SEARCH_BACEN_INSTITUTIONS_AUTOCOMPLETE,
} from "../api";
import type { BacenInstitution } from "@/widgets/financial-data";

export interface BacenInstitutionSearchOption {
  id: string;
  name: string;
}

export function useBacenInstitutionsAutocompleteQuery(options?: {
  variables?: { query: string };
  skip?: boolean;
  fetchPolicy?: "cache-first" | "network-only" | "cache-only" | "no-cache" | "standby";
  notifyOnNetworkStatusChange?: boolean;
}) {
  return useQuery(SEARCH_BACEN_INSTITUTIONS_AUTOCOMPLETE, {
    variables: options?.variables || { query: "" },
    skip: options?.skip ?? false,
    fetchPolicy: options?.fetchPolicy || "network-only",
    notifyOnNetworkStatusChange: options?.notifyOnNetworkStatusChange ?? true,
  });
}

export function useBacenInstitutionsSearch() {
  const [searchQuery, setSearchQuery] = React.useState("");
  const [after, setAfter] = React.useState<string | null>(null);
  const [selectedInstitution, setSelectedInstitution] =
    React.useState<BacenInstitutionSearchOption | null>(null);
  const [searchHistory, setSearchHistory] = React.useState<string[]>([]);

  // Load search history from localStorage on mount
  React.useEffect(() => {
    try {
      const stored = localStorage.getItem("bacen_search_history");
      if (stored) {
        const parsed = JSON.parse(stored);
        // Ensure unique items, trim strings, and limit to 5
        const items = Array.isArray(parsed) ? parsed : [];
        const uniqueHistory = Array.from(new Set(items.map((s: any) => String(s).trim()).filter(Boolean))).slice(0, 5);
        setSearchHistory(uniqueHistory);
      }
    } catch (e) {
      console.error("Failed to load search history", e);
    }
  }, []);

  const addToHistory = React.useCallback((term: string) => {
    if (!term || !term.trim()) return;
    
    const cleanTerm = term.trim();
    
    setSearchHistory(prev => {
      // Use Set to remove duplicates, keep new term at start
      const newHistory = Array.from(new Set([cleanTerm, ...prev])).slice(0, 5);
      try {
        localStorage.setItem("bacen_search_history", JSON.stringify(newHistory));
      } catch (e) {
        console.error("Failed to save search history", e);
      }
      return newHistory;
    });
  }, []);

  const queryVariables = React.useMemo(
    () => ({
      first: 50,
      ...(searchQuery?.trim() && { query: searchQuery.trim() }),
      ...(after && { after }),
    }),
    [searchQuery, after]
  );

  const shouldSkipQuery = !searchQuery?.trim();

  const { data, loading, error, refetch } = useQuery(BACEN_INSTITUTIONS, {
    variables: queryVariables,
    fetchPolicy: "network-only",
    notifyOnNetworkStatusChange: true,
    skip: shouldSkipQuery,
  });

  const institutions = React.useMemo(() => {
    if (shouldSkipQuery || !data) {
      return [];
    }

    const response = data as any;
    const edges = response?.bacenInstitutions?.edges || [];

    return edges
      .map((edge: any) => edge?.node)
      .filter((node: any) => node != null) as BacenInstitution[];
  }, [data, shouldSkipQuery]);

  const pageInfo = React.useMemo(() => {
    if (shouldSkipQuery) return null;
    return (data as any)?.bacenInstitutions?.pageInfo || null;
  }, [data, shouldSkipQuery]);

  const totalCount = React.useMemo(() => {
    if (shouldSkipQuery) return null;
    return (data as any)?.bacenInstitutions?.totalCount || null;
  }, [data, shouldSkipQuery]);

  const { loadNextPage } = useRelayPagination(
    institutions,
    pageInfo,
    totalCount,
    searchQuery,
    after,
    setAfter,
    {
      initialAfter: null,
      initialSearchQuery: "",
    }
  );

  const handleSearchChange = React.useCallback((query: string) => {
    React.startTransition(() => {
      setSearchQuery(query);
      setAfter(null);
    });
    // Add to history if query is not empty
    if (query && query.trim()) {
      addToHistory(query.trim());
    }
  }, [addToHistory]);

  const handleSelectionChange = React.useCallback(
    (items: BacenInstitutionSearchOption[]) => {
      const first = items[0];
      if (first) {
        setSelectedInstitution(first);
        handleSearchChange(first.name);
      } else {
        setSelectedInstitution(null);
        handleSearchChange("");
      }
    },
    [handleSearchChange]
  );

  const handleAutocompleteSearch = React.useCallback(
    (query: string) => {
      if (selectedInstitution && selectedInstitution.name !== query) {
        setSelectedInstitution(null);
      }
      handleSearchChange(query);
    },
    [handleSearchChange, selectedInstitution]
  );

  return {
    institutions,
    loading: loading && !shouldSkipQuery,
    error,
    refetch,
    pageInfo,
    loadNextPage,
    selectedInstitution,
    handleSelectionChange,
    handleAutocompleteSearch,
    shouldSkipQuery,
    searchHistory,
  };
}
