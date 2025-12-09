import { gql } from '@apollo/client';
import { ProductFieldsFragmentDoc } from '../../fragments/common.generated';

// Create new product
export const CREATE_PRODUCT = gql`
  mutation CreateProduct($input: ProductInput!) {
    createProduct(input: $input) {
      ...ProductFields
    }
  }
  ${ProductFieldsFragmentDoc}
`;

// Update existing product
export const UPDATE_PRODUCT = gql`
  mutation UpdateProduct($id: ID!, $input: ProductInput!) {
    updateProduct(id: $id, input: $input) {
      ...ProductFields
    }
  }
  ${ProductFieldsFragmentDoc}
`;

// Delete product
export const DELETE_PRODUCT = gql`
  mutation DeleteProduct($id: ID!) {
    deleteProduct(id: $id)
  }
`;
