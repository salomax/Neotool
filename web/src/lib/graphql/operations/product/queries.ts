import { gql } from '@apollo/client';

// Get all products
export const GET_PRODUCTS = gql`
  query GetProducts {
    products {
      id
      name
      sku
      priceCents
      stock
      createdAt
      updatedAt
    }
  }
`;

// Get single product by ID
export const GET_PRODUCT = gql`
  query GetProduct($id: ID!) {
    product(id: $id) {
      id
      name
      sku
      priceCents
      stock
      createdAt
      updatedAt
    }
  }
`;
