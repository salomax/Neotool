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

export const INVESTMENT_FUND_NAV = gql`
  query InvestmentFundNav($id: ID!, $to: Date!) {
    investmentFund(id: $id) {
      id
      latestNav {
        date
        closePrice
      }
      priceHistory(to: $to, last: 1) {
        date
        closePrice
      }
    }
  }
`;

/**
 * Autocomplete search for B3 equities and FIIs (by ticker / name).
 * Used by the equity holding drawer.
 */
export const SEARCH_INVESTMENT_ASSETS_AUTOCOMPLETE = gql`
  query SearchInvestmentAssetsAutocomplete($query: String!, $assetType: B3AssetType) {
    searchInvestmentAssetsAutocomplete(query: $query, assetType: $assetType) {
      suggestions {
        id
        ticker
        name
        assetType
        isActive
      }
    }
  }
`;

/**
 * Fetches the most recent close price for a B3 asset up to a given date.
 * Used to pre-fill unit price in the equity holding drawer.
 */
export const INVESTMENT_ASSET_PRICE = gql`
  query InvestmentAssetPrice($ticker: String!, $to: Date!) {
    investmentAssetByTicker(ticker: $ticker) {
      id
      ticker
      priceHistory(to: $to, last: 1) {
        date
        closePrice
      }
    }
  }
`;
