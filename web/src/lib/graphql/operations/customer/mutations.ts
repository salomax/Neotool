import { gql } from '@apollo/client';
import { CUSTOMER_FIELDS } from '../../fragments/common';

// Create new customer
export const CREATE_CUSTOMER = gql`
  ${CUSTOMER_FIELDS}
  mutation CreateCustomer($input: CustomerInput!) {
    createCustomer(input: $input) {
      ...CustomerFields
    }
  }
`;

// Update existing customer
export const UPDATE_CUSTOMER = gql`
  ${CUSTOMER_FIELDS}
  mutation UpdateCustomer($id: ID!, $input: CustomerInput!) {
    updateCustomer(id: $id, input: $input) {
      ...CustomerFields
    }
  }
`;

// Delete customer
export const DELETE_CUSTOMER = gql`
  mutation DeleteCustomer($id: ID!) {
    deleteCustomer(id: $id)
  }
`;
