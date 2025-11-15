import * as Types from '../types/__generated__/graphql';

import { gql } from '@apollo/client';
export type CustomerFieldsFragment = { __typename: 'Customer', id: string, name: string, email: string, status: string, createdAt: string | null, updatedAt: string | null };

export type ProductFieldsFragment = { __typename: 'Product', id: string, name: string, sku: string, priceCents: number, stock: number, createdAt: string | null, updatedAt: string | null };

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