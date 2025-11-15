import { gql } from '@apollo/client';
import { PRODUCT_FIELDS } from '../../fragments/common';

// Get all products
export const GET_PRODUCTS = gql`
  ${PRODUCT_FIELDS}
  query GetProducts {
    products {
      ...ProductFields
    }
  }
`;

// Get single product by ID
export const GET_PRODUCT = gql`
  ${PRODUCT_FIELDS}
  query GetProduct($id: ID!) {
    product(id: $id) {
      ...ProductFields
    }
  }
`;
