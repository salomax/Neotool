import { gql } from "@apollo/client";

/**
 * Autocomplete search for CVM investment funds (by name/CNPJ).
 *
 * Used by the investments fund holding drawer.
 */
export const SEARCH_INVESTMENT_FUNDS_AUTOCOMPLETE = gql`
  query SearchInvestmentFundsAutocomplete($query: String!) {
    searchInvestmentFundsAutocomplete(query: $query) {
      suggestions {
        id
        name
        cnpj
        assetType
        isActive
      }
    }
  }
`;

