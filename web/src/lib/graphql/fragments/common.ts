import { gql } from '@apollo/client';

// Customer fields fragment
export const CUSTOMER_FIELDS = gql`
  fragment CustomerFields on Customer {
    id
    name
    email
    status
    createdAt
    updatedAt
  }
`;

// Product fields fragment
export const PRODUCT_FIELDS = gql`
  fragment ProductFields on Product {
    id
    name
    sku
    priceCents
    stock
    createdAt
    updatedAt
  }
`;

// Note: The actual schema returns objects directly or boolean values
// No common response wrapper fragments needed for current schema
