/**
 * @deprecated This file is deprecated. Fragments are now defined in common.graphql
 * and generated FragmentDoc exports are available from common.generated.ts
 * 
 * This file is kept temporarily for backward compatibility but will be removed.
 * Please use imports from './common.generated' instead.
 */

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

// User fields fragment
export const USER_FIELDS = gql`
  fragment UserFields on User {
    id
    email
    displayName
    enabled
  }
`;

// Group fields fragment
export const GROUP_FIELDS = gql`
  fragment GroupFields on Group {
    id
    name
    description
    members {
      ...UserFields
    }
  }
`;

// Role fields fragment
export const ROLE_FIELDS = gql`
  fragment RoleFields on Role {
    id
    name
  }
`;

// Permission fields fragment
export const PERMISSION_FIELDS = gql`
  fragment PermissionFields on Permission {
    id
    name
  }
`;

// Note: The actual schema returns objects directly or boolean values
// No common response wrapper fragments needed for current schema
