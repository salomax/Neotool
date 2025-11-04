import { gql } from '@apollo/client';
import { CUSTOMER_FIELDS } from '../../fragments/common';

// Get all customers
export const GET_CUSTOMERS = gql`
  ${CUSTOMER_FIELDS}
  query GetCustomers {
    customers {
      ...CustomerFields
    }
  }
`;

// Get single customer by ID
export const GET_CUSTOMER = gql`
  ${CUSTOMER_FIELDS}
  query GetCustomer($id: ID!) {
    customer(id: $id) {
      ...CustomerFields
    }
  }
`;
