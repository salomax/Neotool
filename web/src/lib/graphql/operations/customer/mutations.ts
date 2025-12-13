import { gql } from '@apollo/client';

// Create new customer
export const CREATE_CUSTOMER = gql`
  mutation CreateCustomer($input: CustomerInput!) {
    createCustomer(input: $input) {
      id
      name
      email
      status
      createdAt
      updatedAt
    }
  }
`;

// Update existing customer
export const UPDATE_CUSTOMER = gql`
  mutation UpdateCustomer($id: ID!, $input: CustomerInput!) {
    updateCustomer(id: $id, input: $input) {
      id
      name
      email
      status
      createdAt
      updatedAt
    }
  }
`;

// Delete customer
export const DELETE_CUSTOMER = gql`
  mutation DeleteCustomer($id: ID!) {
    deleteCustomer(id: $id)
  }
`;
