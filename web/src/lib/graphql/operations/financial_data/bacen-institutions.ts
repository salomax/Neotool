import { gql } from "@apollo/client";

/**
 * GraphQL query for searching Bacen institutions with autocomplete
 */
export const SEARCH_BACEN_INSTITUTIONS_AUTOCOMPLETE = gql`
  query SearchBacenInstitutionsAutocomplete($query: String!) {
    searchBacenInstitutionsAutocomplete(query: $query) {
      suggestions {
        id
        name
        cnpj
        logoUrl
      }
    }
  }
`;

/**
 * GraphQL query for fetching paginated Bacen institutions
 */
export const BACEN_INSTITUTIONS = gql`
  query BacenInstitutions($query: String, $first: Int, $after: String) {
    bacenInstitutions(query: $query, first: $first, after: $after) {
      edges {
        node {
          id
          name
          logoUrl
          profit
          equity
          yoyEquityGrowth
          basileiaIndex
          institutionType
        }
        cursor
      }
      pageInfo {
        hasNextPage
        hasPreviousPage
        startCursor
        endCursor
      }
      totalCount
    }
  }
`;

