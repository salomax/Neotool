"use client";

import React from "react";
import { useQuery } from "@apollo/client/react";
import ClearIcon from "@mui/icons-material/Clear";
import SearchIcon from "@mui/icons-material/Search";
import {
  Autocomplete,
  Box,
  CircularProgress,
  IconButton,
  InputAdornment,
  TextField,
  Typography,
} from "@mui/material";
import type { TextFieldProps } from "@mui/material";
import { Controller, useFormContext, useWatch } from "react-hook-form";
import { useDebounce } from "@/shared/hooks/ui/useDebounce";
import { useTranslation } from "@/shared/i18n";
import { personalFinanceTranslations } from "../../i18n";
import { SEARCH_INVESTMENT_FUNDS_AUTOCOMPLETE } from "@/lib/graphql/operations/financial-data/queries";
import {
  SearchInvestmentFundsAutocompleteQuery,
  SearchInvestmentFundsAutocompleteQueryVariables,
} from "@/lib/graphql/operations/financial-data/queries.generated";
import { formatCnpj } from "@/lib/utils/formatCnpj";
import { FundTypeChip } from "./FundTypeChip";

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
  size?: TextFieldProps["size"];
  disabled?: boolean;
  /** Focus the search input on mount (e.g. new-holding step 1). */
  autoFocus?: boolean;
  "data-testid"?: string;
}

export function FundAutocomplete({
  name,
  label,
  size = "medium",
  disabled,
  autoFocus = false,
  "data-testid": dataTestId,
}: FundAutocompleteProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const { control } = useFormContext();
  const [inputValue, setInputValue] = React.useState("");
  const selectedFund = useWatch({ control, name }) as FundAutocompleteValue | null | undefined;
  const lastSyncedFundId = React.useRef<string | undefined>(undefined);

  React.useEffect(() => {
    const fund = selectedFund;
    if (fund?.id) {
      if (lastSyncedFundId.current !== fund.id) {
        setInputValue(fund.name);
        lastSyncedFundId.current = fund.id;
      }
    } else {
      lastSyncedFundId.current = undefined;
    }
  }, [selectedFund]);
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
                gap: 1,
                py: 1,
              }}
            >
              <Box sx={{ minWidth: 0, flex: 1 }}>
                <Typography variant="body2" fontWeight={700} noWrap>
                  {option.name}
                </Typography>
                <Typography variant="caption" color="text.secondary" noWrap>
                  {formatCnpj(option.cnpj)}
                </Typography>
              </Box>
              <Box sx={{ ml: "auto", flexShrink: 0 }}>
                <FundTypeChip assetType={option.assetType} size="small" />
              </Box>
            </Box>
          )}
          renderInput={(params) => (
            <TextField
              {...params}
              label={label ?? t("investments.recordFund.fields.fund")}
              size={size}
              autoFocus={autoFocus}
              error={!!fieldState.error}
              helperText={fieldState.error?.message}
              data-testid={dataTestId}
              slotProps={{
                input: {
                  ...params.InputProps,
                  startAdornment: (
                    <>
                      <InputAdornment position="start">
                        <SearchIcon fontSize="small" sx={{ color: "text.secondary" }} />
                      </InputAdornment>
                      {params.InputProps.startAdornment}
                    </>
                  ),
                  endAdornment: (
                    <>
                      {loading ? <CircularProgress size={16} sx={{ mr: 0.5 }} /> : null}
                      {inputValue.trim() ? (
                        <InputAdornment position="end">
                          <IconButton
                            size="small"
                            edge="end"
                            aria-label={t("investments.table.actions.clearSearchAriaLabel")}
                            onMouseDown={(e) => e.preventDefault()}
                            onClick={() => {
                              setInputValue("");
                              field.onChange(null);
                            }}
                          >
                            <ClearIcon fontSize="small" />
                          </IconButton>
                        </InputAdornment>
                      ) : null}
                      {params.InputProps.endAdornment}
                    </>
                  ),
                },
              }}
              sx={{
                "& .MuiOutlinedInput-root": {
                  backgroundColor: "background.paper",
                },
              }}
            />
          )}
        />
      )}
    />
  );
}
