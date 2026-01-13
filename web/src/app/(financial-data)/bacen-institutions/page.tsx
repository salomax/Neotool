"use client";

import * as React from "react";
import { useQuery } from "@apollo/client/react";
import { useRouter } from "next/navigation";
import { Container, Box, Page } from "@/shared/components/ui/layout";
import { SearchableAutocomplete } from "@/shared/components/ui/forms/SearchableAutocomplete";
import { CardGrid } from "@/shared/components/ui/layout/CardGrid";
import { BacenInstitutionCard, type BacenInstitution } from "@/features/financial_data/components/BacenInstitutionCard";
import { useRelayPagination } from "@/shared/hooks/pagination";
import { BACEN_INSTITUTIONS, SEARCH_BACEN_INSTITUTIONS_AUTOCOMPLETE } from "@/lib/graphql/operations/financial_data/bacen-institutions";
import { useState, useMemo, useCallback } from "react";
import { startTransition } from "react";
import { useTranslation } from "@/shared/i18n";
import { financialDataTranslations } from "@/features/financial_data/i18n";
import { usePageTitle } from "@/shared/hooks/ui/usePageTitle";
import { encodeId } from "@/shared/utils/idObfuscation";

export default function BacenInstitutionsPage() {
  const { t } = useTranslation(financialDataTranslations);
  usePageTitle(t("pageTitle"));
  const router = useRouter();
  const [searchQuery, setSearchQuery] = useState("");
  const [after, setAfter] = useState<string | null>(null);
  const [selectedInstitution, setSelectedInstitution] = useState<{
    name: string;
  } | null>(null);

  const queryVariables = useMemo(
    () => ({
      first: 50,
      ...(searchQuery?.trim() && { query: searchQuery.trim() }),
      ...(after && { after }),
    }),
    [searchQuery, after]
  );

  const shouldSkipQuery = !searchQuery?.trim();
  
  const {
    data,
    loading,
    error,
    refetch,
  } = useQuery(BACEN_INSTITUTIONS, {
    variables: queryVariables,
    fetchPolicy: "network-only",
    notifyOnNetworkStatusChange: true,
    skip: shouldSkipQuery,
  });

  const institutions = useMemo(() => {
    if (shouldSkipQuery || !data) {
      return [];
    }

    const response = data as any;
    const edges = response?.bacenInstitutions?.edges || [];

    return edges
      .map((edge: any) => edge?.node)
      .filter((node: any) => node != null);
  }, [data, shouldSkipQuery]);

  const pageInfo = useMemo(() => {
    if (shouldSkipQuery) return null;
    return (data as any)?.bacenInstitutions?.pageInfo || null;
  }, [data, shouldSkipQuery]);

  const totalCount = useMemo(() => {
    if (shouldSkipQuery) return null;
    return (data as any)?.bacenInstitutions?.totalCount || null;
  }, [data, shouldSkipQuery]);

  // Pagination hook
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

  const handleSearchChange = useCallback((query: string) => {
    startTransition(() => {
      setSearchQuery(query);
      setAfter(null);
    });
  }, []);

  const handleCardClick = useCallback(
    (institution: BacenInstitution) => {
      router.push(`/bacen-institutions/${encodeId(institution.id)}`);
    },
    [router]
  );

  const useAutocompleteQuery = React.useCallback(
    (options?: {
      variables?: { query: string };
      skip?: boolean;
      fetchPolicy?: "cache-first" | "network-only" | "cache-only" | "no-cache" | "standby";
      notifyOnNetworkStatusChange?: boolean;
    }) => {
      // eslint-disable-next-line react-hooks/rules-of-hooks
      return useQuery(SEARCH_BACEN_INSTITUTIONS_AUTOCOMPLETE, {
        variables: options?.variables || { query: "" },
        skip: options?.skip ?? false,
        fetchPolicy: options?.fetchPolicy || "network-only",
        notifyOnNetworkStatusChange: options?.notifyOnNetworkStatusChange ?? true,
      });
    },
    []
  );

  return (
    <Page>
      <Box
        component="header"
        sx={{
          position: "sticky",
          top: -24, // Negative value to account for AppShell's padding (3 * 8px = 24px)
          zIndex: 10,
          bgcolor: "background.default",
          py: 2,
          pt: 4, // Add top padding to compensate
          borderBottom: "1px solid",
          borderColor: "divider",
        }}
      >
        <Container maxWidth="xl">
          <Box sx={{ maxWidth: 600, mx: "auto", width: "100%" }}>
            <SearchableAutocomplete
              useQuery={useAutocompleteQuery}
              getQueryVariables={(query) => ({ query })}
              extractData={(data: any) => data?.searchBacenInstitutionsAutocomplete?.suggestions || []}
              transformOption={(item: any) => ({
                id: item.name, // Use name as id since autocomplete only returns names
                name: item.name,
              })}
              selectedItems={selectedInstitution ? [selectedInstitution] : []}
              onChange={(items) => {
                if (items.length > 0) {
                  setSelectedInstitution(items[0] as any);
                  // Trigger search with the selected name
                  handleSearchChange(items[0].name);
                } else {
                  setSelectedInstitution(null);
                  handleSearchChange("");
                }
              }}
              onSearch={(query) => {
                if (selectedInstitution && selectedInstitution.name !== query) {
                  setSelectedInstitution(null);
                }
                handleSearchChange(query);
              }}
              getOptionId={(opt) => opt.id}
              getOptionLabel={(opt) => opt.name}
              placeholder={t("search.placeholder")}
              label={t("search.label")}
              loadMode="lazy"
            />
          </Box>
        </Container>
      </Box>

      <Container maxWidth="xl" sx={{ py: 4 }}>
        <CardGrid
          items={institutions}
          columns={4}
          renderItem={(institution: BacenInstitution) => (
            <BacenInstitutionCard
              institution={institution}
              onClick={() => handleCardClick(institution)}
            />
          )}
          onLoadMore={loadNextPage}
          hasNextPage={pageInfo?.hasNextPage || false}
          loading={loading && !shouldSkipQuery}
          error={error}
          onRetry={() => refetch()}
          emptyMessage={t("messages.noInstitutionsFound")}
          errorMessage={t("messages.unableToLoadInstitutions")}
        />
      </Container>
    </Page>
  );
}
