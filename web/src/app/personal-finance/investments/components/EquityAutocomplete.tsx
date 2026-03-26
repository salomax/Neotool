"use client";

import React from "react";
import { useQuery } from "@apollo/client/react";
import { Autocomplete, Box, CircularProgress, TextField, Typography } from "@mui/material";
import { Controller, useFormContext } from "react-hook-form";
import { useDebounce } from "@/shared/hooks/ui/useDebounce";
import { useTranslation } from "@/shared/i18n";
import { personalFinanceTranslations } from "../../i18n";
import { SEARCH_INVESTMENT_ASSETS_AUTOCOMPLETE } from "@/lib/graphql/operations/financial-data/queries";
import { mapB3AssetType } from "@/lib/utils/mapB3AssetType";
import { AssetTypeChip } from "./AssetTypeChip";

export type EquityAutocompleteValue = {
  id: string;
  ticker: string;
  name: string;
  assetType: string;
  isActive: boolean;
};

interface EquityAutocompleteProps {
  name: string;
  label?: string;
  disabled?: boolean;
  "data-testid"?: string;
}

export function EquityAutocomplete({ name, label, disabled, "data-testid": dataTestId }: EquityAutocompleteProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const { control } = useFormContext();
  const [inputValue, setInputValue] = React.useState("");
  const debounced = useDebounce(inputValue, 300);
  const trimmed = debounced.trim();

  const { data, loading } = useQuery<
    {
      searchInvestmentAssetsAutocomplete: {
        suggestions: Array<{ id: string; ticker: string; name: string; assetType: string; isActive: boolean }>;
      };
    },
    { query: string }
  >(SEARCH_INVESTMENT_ASSETS_AUTOCOMPLETE, {
    variables: { query: trimmed },
    skip: trimmed.length === 0,
    fetchPolicy: "network-only",
    errorPolicy: "all",
  });

  const options: EquityAutocompleteValue[] = React.useMemo(() => {
    const suggestions = data?.searchInvestmentAssetsAutocomplete.suggestions ?? [];
    return suggestions.map((s) => ({
      id: s.id,
      ticker: s.ticker,
      name: s.name,
      assetType: s.assetType,
      isActive: s.isActive,
    }));
  }, [data?.searchInvestmentAssetsAutocomplete.suggestions]);

  return (
    <Controller
      name={name}
      control={control}
      render={({ field, fieldState }) => (
        <Autocomplete
          options={options}
          value={(field.value as EquityAutocompleteValue | null) ?? null}
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
          getOptionLabel={(opt) => opt?.ticker ?? ""}
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
                  {option.ticker}
                </Typography>
                <Typography variant="caption" color="text.secondary" noWrap>
                  {option.name}
                </Typography>
              </Box>
              <AssetTypeChip assetType={mapB3AssetType(option.assetType)} size="small" />
            </Box>
          )}
          renderInput={(params) => (
            <TextField
              {...params}
              label={label ?? t("investments.recordEquity.fields.asset")}
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
