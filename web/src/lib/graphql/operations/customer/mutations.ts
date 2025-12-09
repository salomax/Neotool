import { gql } from '@apollo/client';
import { CustomerFieldsFragmentDoc } from '../../fragments/common.generated';

// Create new customer
export const CREATE_CUSTOMER = gql`
  mutation CreateCustomer($input: CustomerInput!) {
    createCustomer(input: $input) {
      ...CustomerFields
    }
  }
  ${CustomerFieldsFragmentDoc}
`;

// Update existing customer
export const UPDATE_CUSTOMER = gql`
  mutation UpdateCustomer($id: ID!, $input: CustomerInput!) {
    updateCustomer(id: $id, input: $input) {
      ...CustomerFields
    }
  }
  ${CustomerFieldsFragmentDoc}
`;

// Delete customer
export const DELETE_CUSTOMER = gql`
  mutation DeleteCustomer($id: ID!) {
    deleteCustomer(id: $id)
  }
`;
