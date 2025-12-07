import * as Types from '../../types/__generated__/graphql';

import { gql } from '@apollo/client';
import * as ApolloReactCommon from '@apollo/client/react';
import * as ApolloReactHooks from '@apollo/client/react';
const defaultOptions = {} as const;
export type CheckPermissionQueryVariables = Types.Exact<{
  userId: Types.Scalars['ID']['input'];
  permission: Types.Scalars['String']['input'];
  resourceId?: Types.InputMaybe<Types.Scalars['ID']['input']>;
}>;


export type CheckPermissionQuery = { checkPermission: { __typename: 'AuthorizationResult', allowed: boolean, reason: string } };


export const CheckPermissionDocument = gql`
    query CheckPermission($userId: ID!, $permission: String!, $resourceId: ID) {
  checkPermission(
    userId: $userId
    permission: $permission
    resourceId: $resourceId
  ) {
    allowed
    reason
  }
}
    `;

/**
 * __useCheckPermissionQuery__
 *
 * To run a query within a React component, call `useCheckPermissionQuery` and pass it any options that fit your needs.
 * When your component renders, `useCheckPermissionQuery` returns an object from Apollo Client that contains loading, error, and data properties
 * you can use to render your UI.
 *
 * @param baseOptions options that will be passed into the query, supported options are listed on: https://www.apollographql.com/docs/react/api/react-hooks/#options;
 *
 * @example
 * const { data, loading, error } = useCheckPermissionQuery({
 *   variables: {
 *      userId: // value for 'userId'
 *      permission: // value for 'permission'
 *      resourceId: // value for 'resourceId'
 *   },
 * });
 */
export function useCheckPermissionQuery(baseOptions: ApolloReactHooks.QueryHookOptions<CheckPermissionQuery, CheckPermissionQueryVariables> & ({ variables: CheckPermissionQueryVariables; skip?: boolean; } | { skip: boolean; }) ) {
        const options = {...defaultOptions, ...baseOptions}
        return ApolloReactHooks.useQuery<CheckPermissionQuery, CheckPermissionQueryVariables>(CheckPermissionDocument, options);
      }
export function useCheckPermissionLazyQuery(baseOptions?: ApolloReactHooks.LazyQueryHookOptions<CheckPermissionQuery, CheckPermissionQueryVariables>) {
          const options = {...defaultOptions, ...baseOptions}
          return ApolloReactHooks.useLazyQuery<CheckPermissionQuery, CheckPermissionQueryVariables>(CheckPermissionDocument, options);
        }
export function useCheckPermissionSuspenseQuery(baseOptions?: ApolloReactHooks.SkipToken | ApolloReactHooks.SuspenseQueryHookOptions<CheckPermissionQuery, CheckPermissionQueryVariables>) {
          const options = baseOptions === ApolloReactHooks.skipToken ? baseOptions : {...defaultOptions, ...baseOptions}
          return ApolloReactHooks.useSuspenseQuery<CheckPermissionQuery, CheckPermissionQueryVariables>(CheckPermissionDocument, options);
        }
export type CheckPermissionQueryHookResult = ReturnType<typeof useCheckPermissionQuery>;
export type CheckPermissionLazyQueryHookResult = ReturnType<typeof useCheckPermissionLazyQuery>;
export type CheckPermissionSuspenseQueryHookResult = ReturnType<typeof useCheckPermissionSuspenseQuery>;
export type CheckPermissionQueryResult = ApolloReactCommon.QueryResult<CheckPermissionQuery, CheckPermissionQueryVariables>;