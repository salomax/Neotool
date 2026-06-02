import { gql } from "@apollo/client";

export const UPDATE_INVESTMENT_CASH_TRANSACTION = gql`
  mutation UpdateInvestmentCashTransaction($id: ID!, $input: UpdateInvestmentCashTransactionInput!) {
    updateInvestmentCashTransaction(id: $id, input: $input) {
      id
      financialAccountId
      creditDebitType
      type
      amount
      date
      description
    }
  }
`;

export const DELETE_INVESTMENT_CASH_TRANSACTION = gql`
  mutation DeleteInvestmentCashTransaction($id: ID!) {
    deleteInvestmentCashTransaction(id: $id)
  }
`;
