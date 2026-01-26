"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { Container, Box, Page } from "@/shared/components/ui/layout";
import { SearchableAutocomplete } from "@/shared/components/ui/forms/SearchableAutocomplete";
import { CardGrid } from "@/shared/components/ui/layout/CardGrid";
import { BacenInstitutionCard, type BacenInstitution } from "@/features/financial_data/components/BacenInstitutionCard";
import { useTranslation } from "@/shared/i18n";
import { financialDataTranslations } from "@/features/financial_data/i18n";
import { usePageTitle } from "@/shared/hooks/ui/usePageTitle";
import { encodeId } from "@/shared/utils/idObfuscation";
import {
  useBacenInstitutionsAutocompleteQuery,
  useBacenInstitutionsSearch,
} from "@/features/financial_data/hooks/useBacenInstitutionsSearch";

export default function BacenInstitutionsPage() {
  const { t } = useTranslation(financialDataTranslations);
  usePageTitle(t("pageTitle"));
  const router = useRouter();

  const {
    institutions,
    loading,
    error,
    refetch,
    pageInfo,
    loadNextPage,
    selectedInstitution,
    handleSelectionChange,
    handleAutocompleteSearch,
  } = useBacenInstitutionsSearch();

  const handleCardClick = React.useCallback(
    (institution: BacenInstitution) => {
      router.push(`/financial-data/bacen-institutions/${encodeId(institution.id)}`);
    },
    [router]
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
              useQuery={useBacenInstitutionsAutocompleteQuery}
              getQueryVariables={(query) => ({ query })}
              extractData={(data: any) => data?.searchBacenInstitutionsAutocomplete?.suggestions || []}
              transformOption={(item: any) => ({
                id: item.name, // Use name as id since autocomplete only returns names
                name: item.name,
              })}
              selectedItems={selectedInstitution ? [selectedInstitution] : []}
              onChange={handleSelectionChange}
              onSearch={handleAutocompleteSearch}
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
          loading={loading}
          error={error}
          onRetry={() => refetch()}
          emptyMessage={t("messages.noInstitutionsFound")}
          errorMessage={t("messages.unableToLoadInstitutions")}
        />
      </Container>
    </Page>
  );
}
