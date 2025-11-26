import * as Types from '../../types/__generated__/graphql';

import { gql } from '@apollo/client';
import { CustomerFieldsFragmentDoc } from '../../fragments/common.generated';
import * as ApolloReactCommon from '@apollo/client/react';
import * as ApolloReactHooks from '@apollo/client/react';
const defaultOptions = {} as const;
export type CreateCustomerMutationVariables = Types.Exact<{
  input: Types.CustomerInput;
}>;


export type CreateCustomerMutation = { createCustomer: { __typename: 'Customer', id: string, name: string, email: string, status: string, createdAt: string | null, updatedAt: string | null } };

export type UpdateCustomerMutationVariables = Types.Exact<{
  id: Types.Scalars['ID']['input'];
  input: Types.CustomerInput;
}>;


export type UpdateCustomerMutation = { updateCustomer: { __typename: 'Customer', id: string, name: string, email: string, status: string, createdAt: string | null, updatedAt: string | null } };

export type DeleteCustomerMutationVariables = Types.Exact<{
  id: Types.Scalars['ID']['input'];
}>;


export type DeleteCustomerMutation = { deleteCustomer: boolean };


export const CreateCustomerDocument = gql`
    mutation CreateCustomer($input: CustomerInput!) {
  createCustomer(input: $input) {
    ...CustomerFields
  }
}
    ${CustomerFieldsFragmentDoc}`;

/**
 * __useCreateCustomerMutation__
 *
 * To run a mutation, you first call `useCreateCustomerMutation` within a React component and pass it any options that fit your needs.
 * When your component renders, `useCreateCustomerMutation` returns a tuple that includes:
 * - A mutate function that you can call at any time to execute the mutation
 * - An object with fields that represent the current status of the mutation's execution
 *
 * @param baseOptions options that will be passed into the mutation, supported options are listed on: https://www.apollographql.com/docs/react/api/react-hooks/#options-2;
 *
 * @example
 * const [createCustomerMutation, { data, loading, error }] = useCreateCustomerMutation({
 *   variables: {
 *      input: // value for 'input'
 *   },
 * });
 */
export function useCreateCustomerMutation(baseOptions?: ApolloReactHooks.MutationHookOptions<CreateCustomerMutation, CreateCustomerMutationVariables>) {
        const options = {...defaultOptions, ...baseOptions}
        return ApolloReactHooks.useMutation<CreateCustomerMutation, CreateCustomerMutationVariables>(CreateCustomerDocument, options);
      }
export type CreateCustomerMutationHookResult = ReturnType<typeof useCreateCustomerMutation>;
export type CreateCustomerMutationResult = ApolloReactCommon.MutationResult<CreateCustomerMutation>;
export const UpdateCustomerDocument = gql`
    mutation UpdateCustomer($id: ID!, $input: CustomerInput!) {
  updateCustomer(id: $id, input: $input) {
    ...CustomerFields
  }
}
    ${CustomerFieldsFragmentDoc}`;

/**
 * __useUpdateCustomerMutation__
 *
 * To run a mutation, you first call `useUpdateCustomerMutation` within a React component and pass it any options that fit your needs.
 * When your component renders, `useUpdateCustomerMutation` returns a tuple that includes:
 * - A mutate function that you can call at any time to execute the mutation
 * - An object with fields that represent the current status of the mutation's execution
 *
 * @param baseOptions options that will be passed into the mutation, supported options are listed on: https://www.apollographql.com/docs/react/api/react-hooks/#options-2;
 *
 * @example
 * const [updateCustomerMutation, { data, loading, error }] = useUpdateCustomerMutation({
 *   variables: {
 *      id: // value for 'id'
 *      input: // value for 'input'
 *   },
 * });
 */
export function useUpdateCustomerMutation(baseOptions?: ApolloReactHooks.MutationHookOptions<UpdateCustomerMutation, UpdateCustomerMutationVariables>) {
        const options = {...defaultOptions, ...baseOptions}
        return ApolloReactHooks.useMutation<UpdateCustomerMutation, UpdateCustomerMutationVariables>(UpdateCustomerDocument, options);
      }
export type UpdateCustomerMutationHookResult = ReturnType<typeof useUpdateCustomerMutation>;
export type UpdateCustomerMutationResult = ApolloReactCommon.MutationResult<UpdateCustomerMutation>;
export const DeleteCustomerDocument = gql`
    mutation DeleteCustomer($id: ID!) {
  deleteCustomer(id: $id)
}
    `;

/**
 * __useDeleteCustomerMutation__
 *
 * To run a mutation, you first call `useDeleteCustomerMutation` within a React component and pass it any options that fit your needs.
 * When your component renders, `useDeleteCustomerMutation` returns a tuple that includes:
 * - A mutate function that you can call at any time to execute the mutation
 * - An object with fields that represent the current status of the mutation's execution
 *
 * @param baseOptions options that will be passed into the mutation, supported options are listed on: https://www.apollographql.com/docs/react/api/react-hooks/#options-2;
 *
 * @example
 * const [deleteCustomerMutation, { data, loading, error }] = useDeleteCustomerMutation({
 *   variables: {
 *      id: // value for 'id'
 *   },
 * });
 */
export function useDeleteCustomerMutation(baseOptions?: ApolloReactHooks.MutationHookOptions<DeleteCustomerMutation, DeleteCustomerMutationVariables>) {
        const options = {...defaultOptions, ...baseOptions}
        return ApolloReactHooks.useMutation<DeleteCustomerMutation, DeleteCustomerMutationVariables>(DeleteCustomerDocument, options);
      }
export type DeleteCustomerMutationHookResult = ReturnType<typeof useDeleteCustomerMutation>;
export type DeleteCustomerMutationResult = ApolloReactCommon.MutationResult<DeleteCustomerMutation>;
