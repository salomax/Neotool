import * as Types from '../types/__generated__/graphql';

import { gql } from '@apollo/client';
export type CustomerFieldsFragment = { __typename: 'Customer', id: string, name: string, email: string, status: string, createdAt: string | null, updatedAt: string | null };

export type ProductFieldsFragment = { __typename: 'Product', id: string, name: string, sku: string, priceCents: number, stock: number, createdAt: string | null, updatedAt: string | null };

export type UserFieldsFragment = { __typename: 'User', id: string, email: string, displayName: string | null, enabled: boolean };

export type GroupFieldsFragment = { __typename: 'Group', id: string, name: string, description: string | null };

export type RoleFieldsFragment = { __typename: 'Role', id: string, name: string };

export type PermissionFieldsFragment = { __typename: 'Permission', id: string, name: string };

export const CustomerFieldsFragmentDoc = gql`
    fragment CustomerFields on Customer {
  id
  name
  email
  status
  createdAt
  updatedAt
}
    `;
export const ProductFieldsFragmentDoc = gql`
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
export const UserFieldsFragmentDoc = gql`
    fragment UserFields on User {
  id
  email
  displayName
  enabled
}
    `;
export const GroupFieldsFragmentDoc = gql`
    fragment GroupFields on Group {
  id
  name
  description
}
    `;
export const RoleFieldsFragmentDoc = gql`
    fragment RoleFields on Role {
  id
  name
}
    `;
export const PermissionFieldsFragmentDoc = gql`
    fragment PermissionFields on Permission {
  id
  name
}
    `;