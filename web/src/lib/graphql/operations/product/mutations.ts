import { gql } from '@apollo/client';
import { PRODUCT_FIELDS } from '../../fragments/common';

// Create new product
export const CREATE_PRODUCT = gql`
  ${PRODUCT_FIELDS}
  mutation CreateProduct($input: ProductInput!) {
    createProduct(input: $input) {
      ...ProductFields
    }
  }
`;

// Update existing product
export const UPDATE_PRODUCT = gql`
  ${PRODUCT_FIELDS}
  mutation UpdateProduct($id: ID!, $input: ProductInput!) {
    updateProduct(id: $id, input: $input) {
      ...ProductFields
    }
  }
`;

// Delete product
export const DELETE_PRODUCT = gql`
  mutation DeleteProduct($id: ID!) {
    deleteProduct(id: $id)
  }
`;
