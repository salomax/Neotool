"use client";

import React from "react";
import { useQuery } from "@apollo/client/react";
import { Autocomplete, Box, CircularProgress, TextField, Typography, type TextFieldProps } from "@mui/material";
import { Controller, useFormContext } from "react-hook-form";
import { useDebounce } from "@/shared/hooks/ui/useDebounce";
import { useTranslation } from "@/shared/i18n";
import { personalFinanceTranslations } from "../../i18n";
import { SEARCH_INVESTMENT_ASSETS_AUTOCOMPLETE } from "@/lib/graphql/operations/financial-data/queries";
import { B3AssetType } from "@/lib/graphql/types/__generated__/graphql";
import { mapB3AssetType } from "@/lib/utils/mapB3AssetType";
import { AssetTypeChip } from "./AssetTypeChip";
import { Logo } from "@/shared/components/ui/primitives";
import { getInvestmentIconKindFromAssetType, InvestmentAssetIcon } from "./InvestmentAssetIcon";

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
  /** When set, restricts B3 autocomplete to this asset class (e.g. stocks vs FIIs). */
  assetTypeFilter?: B3AssetType | null;
  disabled?: boolean;
  size?: TextFieldProps["size"];
  "data-testid"?: string;
}

export function EquityAutocomplete({
  name,
  label,
  assetTypeFilter,
  disabled,
  size = "medium",
  "data-testid": dataTestId,
}: EquityAutocompleteProps) {
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
    { query: string; assetType?: B3AssetType }
  >(SEARCH_INVESTMENT_ASSETS_AUTOCOMPLETE, {
    variables: {
      query: trimmed,
      ...(assetTypeFilter != null ? { assetType: assetTypeFilter } : {}),
    },
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
                gap: 1,
                py: 1,
              }}
            >
              <Box sx={{ display: "flex", alignItems: "center", gap: 1, minWidth: 0, flex: "1 1 auto" }}>
                <Logo
                  size="xs"
                  sx={{
                    bgcolor: "action.hover",
                    color: "text.secondary",
                    flexShrink: 0,
                  }}
                >
                  <InvestmentAssetIcon
                    kind={getInvestmentIconKindFromAssetType(mapB3AssetType(option.assetType))}
                    sx={{ fontSize: 16 }}
                  />
                </Logo>
                <Box sx={{ minWidth: 0 }}>
                  <Typography variant="body2" fontWeight={700} noWrap>
                    {option.ticker}
                  </Typography>
                  <Typography variant="caption" color="text.secondary" noWrap>
                    {option.name}
                  </Typography>
                </Box>
              </Box>
              <AssetTypeChip sx={{ ml: "auto", flex: "0 0 auto" }} assetType={mapB3AssetType(option.assetType)} size="small" />
            </Box>
          )}
          renderInput={(params) => (
            <TextField
              {...params}
              label={label ?? t("investments.recordEquity.fields.asset")}
              size={size}
              error={!!fieldState.error}
              helperText={fieldState.error?.message}
              data-testid={dataTestId}
              InputProps={{
                ...params.InputProps,
                startAdornment:
                  field.value != null ? (
                    <>
                      <Box component="span" sx={{ display: "inline-flex", alignItems: "center", mr: 1 }}>
                        <Logo
                          size="xs"
                          sx={{
                            bgcolor: "action.hover",
                            color: "text.secondary",
                          }}
                        >
                          <InvestmentAssetIcon
                            kind={getInvestmentIconKindFromAssetType(
                              mapB3AssetType((field.value as EquityAutocompleteValue).assetType),
                            )}
                            sx={{ fontSize: 16 }}
                          />
                        </Logo>
                      </Box>
                      {params.InputProps.startAdornment}
                    </>
                  ) : (
                    params.InputProps.startAdornment
                  ),
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
