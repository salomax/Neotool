import { gql } from '@apollo/client';

export const SUBMIT_INVESTMENT_IMPORT = gql`
  mutation SubmitInvestmentImport($input: SubmitInvestmentImportInput!) {
    submitInvestmentImport(input: $input) {
      id
      status
      fileName
      createdAt
      errorMessage
      errorCode
      errorParameters
      reviewPayload {
        source
        institutions {
          reviewInstitutionId
          institutionName
          matchedFinancialAccountId
          matchAmbiguous
          matchCandidates {
            financialAccountId
            accountName
          }
          rows {
            rowIndex
            productName
            assetTypeCode
            issuerName
            providerCode
            indexerLabel
            issueDate
            maturityDate
            quantity
            principal
            currentValue
            requiresPrincipal
            conflictingHoldingId
            conflictDiff {
              existingQuantity
              fileQuantity
              existingCurrentValue
              fileCurrentValue
              existingMaturityDate
              fileMaturityDate
            }
          }
        }
      }
    }
  }
`;

export const CONFIRM_INVESTMENT_IMPORT = gql`
  mutation ConfirmInvestmentImport($input: ConfirmInvestmentImportInput!) {
    confirmInvestmentImport(input: $input) {
      id
      status
      errorMessage
      errorCode
      errorParameters
    }
  }
`;

export const INVESTMENT_IMPORT_JOB = gql`
  query InvestmentImportJob($id: ID!) {
    investmentImportJob(id: $id) {
      id
      status
      errorMessage
      errorCode
    }
  }
`;
