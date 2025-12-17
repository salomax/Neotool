import { gql } from '@apollo/client';

// Create new product
export const CREATE_PRODUCT = gql`
  mutation CreateProduct($input: ProductInput!) {
    createProduct(input: $input) {
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

// Update existing product
export const UPDATE_PRODUCT = gql`
  mutation UpdateProduct($id: ID!, $input: ProductInput!) {
    updateProduct(id: $id, input: $input) {
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

// Delete product
export const DELETE_PRODUCT = gql`
  mutation DeleteProduct($id: ID!) {
    deleteProduct(id: $id)
  }
`;
