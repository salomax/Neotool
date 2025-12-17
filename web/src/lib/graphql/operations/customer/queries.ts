import { gql } from '@apollo/client';

// Get all customers
export const GET_CUSTOMERS = gql`
  query GetCustomers {
    customers {
      id
      name
      email
      status
      createdAt
      updatedAt
    }
  }
`;

// Get single customer by ID
export const GET_CUSTOMER = gql`
  query GetCustomer($id: ID!) {
    customer(id: $id) {
      id
      name
      email
      status
      createdAt
      updatedAt
    }
  }
`;
