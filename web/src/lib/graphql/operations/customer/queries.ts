import { gql } from '@apollo/client';
import { CustomerFieldsFragmentDoc } from '../../fragments/common.generated';

// Get all customers
export const GET_CUSTOMERS = gql`
  query GetCustomers {
    customers {
      ...CustomerFields
    }
  }
  ${CustomerFieldsFragmentDoc}
`;

// Get single customer by ID
export const GET_CUSTOMER = gql`
  query GetCustomer($id: ID!) {
    customer(id: $id) {
      ...CustomerFields
    }
  }
  ${CustomerFieldsFragmentDoc}
`;
