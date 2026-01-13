import { gql } from "@apollo/client";

/**
 * GraphQL query for searching Bacen institutions with autocomplete
 */
export const SEARCH_BACEN_INSTITUTIONS_AUTOCOMPLETE = gql`
  query SearchBacenInstitutionsAutocomplete($query: String!) {
    searchBacenInstitutionsAutocomplete(query: $query) {
      suggestions {
        name
      }
    }
  }
`;

/**
 * GraphQL query for fetching Bacen institution report details
 */
export const BACEN_INSTITUTION_REPORT_DETAILS = gql`
  query BacenInstitutionReportDetails($id: ID!, $reportId: ID!, $range: QuarterRangeInput!) {
    bacenFinanceInstitution(id: $id) {
      id
      reportDetails(reportId: $reportId, range: $range) {
        reportId
        name
        accounts {
          group
          accountNumber
          columnName
          valuesByQuarter {
            quarter
            value
          }
        }
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
          logoAsset {
            publicUrl
          }
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

/**
 * GraphQL query for fetching a single Bacen institution by ID
 */
export const BACEN_INSTITUTION = gql`
  query BacenInstitution($id: ID!, $anoMes: String) {
    bacenFinanceInstitution(id: $id, anoMes: $anoMes) {
      id
      quarter
      name
      legalName
      tradeName
      logoAsset {
        id
        publicUrl
      }
      asset {
        publicUrl
      }
      institutionType
      controlType
      institutionBankType
      tipoConsolidadoBancario
      website
      socialNetworks
      mainActivity
      address
      founded
      rwaReferenceEquity
    }
  }
`;

/**
 * GraphQL query for fetching Bacen institution report catalog by trimester
 */
export const BACEN_INSTITUTION_REPORTS = gql`
  query BacenInstitutionReports($id: ID!, $anoMes: String, $range: QuarterRangeInput!) {
    bacenFinanceInstitution(id: $id, anoMes: $anoMes) {
      id
      name
      reportsSummary(range: $range) {
        quarters {
          quarter
          reports {
            reportId
            name
          }
        }
      }
    }
  }
`;

