import { gql } from '@apollo/client';
import { ProductFieldsFragmentDoc } from '../../fragments/common.generated';

// Get all products
export const GET_PRODUCTS = gql`
  query GetProducts {
    products {
      ...ProductFields
    }
  }
  ${ProductFieldsFragmentDoc}
`;

// Get single product by ID
export const GET_PRODUCT = gql`
  query GetProduct($id: ID!) {
    product(id: $id) {
      ...ProductFields
    }
  }
  ${ProductFieldsFragmentDoc}
`;
