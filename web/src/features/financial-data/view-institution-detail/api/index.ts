import { gql } from "@apollo/client";

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
      dataRestriction {
        isRestricted
        reason
      }
      ratings {
        id
        name
        label
        ratingDate
        issuer {
          id
          code
          name
          website
          logoAssetId
        }
        updatedAt
      }
      ratingHistory(limit: 10) {
        id
        name
        label
        ratingDate
        issuerCode
        changedAt
      }
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
      dataRestriction {
        isRestricted
        reason
      }
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

/**
 * GraphQL query for fetching Bacen institution report details
 */
export const BACEN_INSTITUTION_REPORT_DETAILS = gql`
  query BacenInstitutionReportDetails($id: ID!, $reportId: ID!, $range: QuarterRangeInput!) {
    bacenFinanceInstitution(id: $id) {
      id
      dataRestriction {
        isRestricted
        reason
      }
      reportDetails(reportId: $reportId, range: $range) {
        reportId
        name
        accounts {
          group
          id
          name
          valuesByQuarter {
            quarter
            value
          }
        }
      }
    }
  }
`;
