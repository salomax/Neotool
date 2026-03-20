"use client";

import React from "react";
import { useQuery } from "@apollo/client/react";
import { Autocomplete, Box, Chip, CircularProgress, TextField, Typography } from "@mui/material";
import { Controller, useFormContext } from "react-hook-form";
import { useDebounce } from "@/shared/hooks/ui/useDebounce";
import { useTranslation } from "@/shared/i18n";
import { personalFinanceTranslations } from "../../i18n";
import { SEARCH_INVESTMENT_FUNDS_AUTOCOMPLETE } from "@/lib/graphql/operations/financial-data/queries";
import {
  SearchInvestmentFundsAutocompleteQuery,
  SearchInvestmentFundsAutocompleteQueryVariables,
} from "@/lib/graphql/operations/financial-data/queries.generated";
import { formatCnpj } from "@/lib/utils/formatCnpj";
import { formatFundAssetType } from "@/lib/utils/mapCvmFundAssetType";

export type FundAutocompleteValue = {
  id: string;
  name: string;
  cnpj: string;
  assetType: string;
  isActive: boolean;
};

interface FundAutocompleteProps {
  name: string;
  label?: string;
  disabled?: boolean;
  "data-testid"?: string;
}

export function FundAutocomplete({ name, label, disabled, "data-testid": dataTestId }: FundAutocompleteProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const { control } = useFormContext();
  const [inputValue, setInputValue] = React.useState("");
  const debounced = useDebounce(inputValue, 300);
  const trimmed = debounced.trim();

  const { data, loading } = useQuery<SearchInvestmentFundsAutocompleteQuery, SearchInvestmentFundsAutocompleteQueryVariables>(
    SEARCH_INVESTMENT_FUNDS_AUTOCOMPLETE,
    {
      variables: { query: trimmed },
      skip: trimmed.length === 0,
      fetchPolicy: "network-only",
      errorPolicy: "all",
    },
  );

  const options: FundAutocompleteValue[] = React.useMemo(() => {
    const suggestions = data?.searchInvestmentFundsAutocomplete.suggestions ?? [];
    return suggestions.map((s) => ({
      id: s.id,
      name: s.name,
      cnpj: s.cnpj,
      assetType: s.assetType,
      isActive: s.isActive,
    }));
  }, [data?.searchInvestmentFundsAutocomplete.suggestions]);

  return (
    <Controller
      name={name}
      control={control}
      render={({ field, fieldState }) => (
        <Autocomplete
          options={options}
          value={(field.value as FundAutocompleteValue | null) ?? null}
          onChange={(_, nextValue) => {
            field.onChange(nextValue);
          }}
          inputValue={inputValue}
          onInputChange={(_, nextInputValue, reason) => {
            setInputValue(nextInputValue);
            if (reason === "clear" || nextInputValue.trim().length === 0) {
              field.onChange(null);
            }
          }}
          getOptionLabel={(opt) => opt?.name ?? ""}
          isOptionEqualToValue={(opt, val) => opt.id === val.id}
          loading={loading}
          disabled={disabled}
          noOptionsText={trimmed.length === 0 ? t("search.placeholder") : t("cardGrid.noResultsFound")}
          loadingText={t("actions.loading")}
          renderOption={(props, option) => (
            <Box
              component="li"
              {...props}
              sx={{
                display: "flex",
                alignItems: "center",
                justifyContent: "space-between",
                gap: 1,
                py: 1,
              }}
            >
              <Box sx={{ minWidth: 0 }}>
                <Typography variant="body2" fontWeight={700} noWrap>
                  {option.name}
                </Typography>
                <Typography variant="caption" color="text.secondary" noWrap>
                  {formatCnpj(option.cnpj)}
                </Typography>
              </Box>
              <Chip size="small" variant="outlined" label={formatFundAssetType(option.assetType, t)} sx={{ flex: "0 0 auto" }} />
            </Box>
          )}
          renderInput={(params) => (
            <TextField
              {...params}
              label={label ?? t("investments.recordFund.fields.fund")}
              error={!!fieldState.error}
              helperText={fieldState.error?.message}
              data-testid={dataTestId}
              InputProps={{
                ...params.InputProps,
                endAdornment: (
                  <>
                    {loading ? <CircularProgress size={16} /> : null}
                    {params.InputProps.endAdornment}
                  </>
                ),
              }}
            />
          )}
        />
      )}
    />
  );
}
