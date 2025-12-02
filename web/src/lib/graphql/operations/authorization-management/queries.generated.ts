import * as Types from '../../types/__generated__/graphql';

import { gql } from '@apollo/client';
import { UserFieldsFragmentDoc, GroupFieldsFragmentDoc, RoleFieldsFragmentDoc, PermissionFieldsFragmentDoc } from '../../fragments/common.generated';
import * as ApolloReactCommon from '@apollo/client/react';
import * as ApolloReactHooks from '@apollo/client/react';
const defaultOptions = {} as const;
export type GetUsersQueryVariables = Types.Exact<{
  first?: Types.InputMaybe<Types.Scalars['Int']['input']>;
  after?: Types.InputMaybe<Types.Scalars['String']['input']>;
  query?: Types.InputMaybe<Types.Scalars['String']['input']>;
}>;


export type GetUsersQuery = { users: { __typename: 'UserConnection', edges: Array<{ __typename: 'UserEdge', cursor: string, node: { __typename: 'User', id: string, email: string, displayName: string | null, enabled: boolean } }>, nodes: Array<{ __typename: 'User', id: string, email: string, displayName: string | null, enabled: boolean }>, pageInfo: { __typename: 'PageInfo', hasNextPage: boolean, hasPreviousPage: boolean, startCursor: string | null, endCursor: string | null } } };

export type GetGroupsQueryVariables = Types.Exact<{
  first?: Types.InputMaybe<Types.Scalars['Int']['input']>;
  after?: Types.InputMaybe<Types.Scalars['String']['input']>;
  query?: Types.InputMaybe<Types.Scalars['String']['input']>;
}>;


export type GetGroupsQuery = { groups: { __typename: 'GroupConnection', edges: Array<{ __typename: 'GroupEdge', cursor: string, node: { __typename: 'Group', id: string, name: string, description: string | null } }>, nodes: Array<{ __typename: 'Group', id: string, name: string, description: string | null }>, pageInfo: { __typename: 'PageInfo', hasNextPage: boolean, hasPreviousPage: boolean, startCursor: string | null, endCursor: string | null } } };

export type GetRolesQueryVariables = Types.Exact<{
  first?: Types.InputMaybe<Types.Scalars['Int']['input']>;
  after?: Types.InputMaybe<Types.Scalars['String']['input']>;
  query?: Types.InputMaybe<Types.Scalars['String']['input']>;
}>;


export type GetRolesQuery = { roles: { __typename: 'RoleConnection', edges: Array<{ __typename: 'RoleEdge', cursor: string, node: { __typename: 'Role', id: string, name: string } }>, nodes: Array<{ __typename: 'Role', id: string, name: string }>, pageInfo: { __typename: 'PageInfo', hasNextPage: boolean, hasPreviousPage: boolean, startCursor: string | null, endCursor: string | null } } };

export type GetPermissionsQueryVariables = Types.Exact<{
  first?: Types.InputMaybe<Types.Scalars['Int']['input']>;
  after?: Types.InputMaybe<Types.Scalars['String']['input']>;
  query?: Types.InputMaybe<Types.Scalars['String']['input']>;
}>;


export type GetPermissionsQuery = { permissions: { __typename: 'PermissionConnection', edges: Array<{ __typename: 'PermissionEdge', cursor: string, node: { __typename: 'Permission', id: string, name: string } }>, nodes: Array<{ __typename: 'Permission', id: string, name: string }>, pageInfo: { __typename: 'PageInfo', hasNextPage: boolean, hasPreviousPage: boolean, startCursor: string | null, endCursor: string | null } } };


export const GetUsersDocument = gql`
    query GetUsers($first: Int, $after: String, $query: String) {
  users(first: $first, after: $after, query: $query) {
    edges {
      node {
        ...UserFields
      }
      cursor
    }
    nodes {
      ...UserFields
    }
    pageInfo {
      hasNextPage
      hasPreviousPage
      startCursor
      endCursor
    }
  }
}
    ${UserFieldsFragmentDoc}`;

/**
 * __useGetUsersQuery__
 *
 * To run a query within a React component, call `useGetUsersQuery` and pass it any options that fit your needs.
 * When your component renders, `useGetUsersQuery` returns an object from Apollo Client that contains loading, error, and data properties
 * you can use to render your UI.
 *
 * @param baseOptions options that will be passed into the query, supported options are listed on: https://www.apollographql.com/docs/react/api/react-hooks/#options;
 *
 * @example
 * const { data, loading, error } = useGetUsersQuery({
 *   variables: {
 *      first: // value for 'first'
 *      after: // value for 'after'
 *      query: // value for 'query'
 *   },
 * });
 */
export function useGetUsersQuery(baseOptions?: ApolloReactHooks.QueryHookOptions<GetUsersQuery, GetUsersQueryVariables>) {
        const options = {...defaultOptions, ...baseOptions}
        return ApolloReactHooks.useQuery<GetUsersQuery, GetUsersQueryVariables>(GetUsersDocument, options);
      }
export function useGetUsersLazyQuery(baseOptions?: ApolloReactHooks.LazyQueryHookOptions<GetUsersQuery, GetUsersQueryVariables>) {
          const options = {...defaultOptions, ...baseOptions}
          return ApolloReactHooks.useLazyQuery<GetUsersQuery, GetUsersQueryVariables>(GetUsersDocument, options);
        }
export function useGetUsersSuspenseQuery(baseOptions: ApolloReactHooks.SkipToken | (ApolloReactHooks.SuspenseQueryHookOptions<GetUsersQuery, GetUsersQueryVariables> & { variables: GetUsersQueryVariables })) {
          const options = baseOptions === ApolloReactHooks.skipToken ? baseOptions : {...defaultOptions, ...baseOptions}
          return ApolloReactHooks.useSuspenseQuery<GetUsersQuery, GetUsersQueryVariables>(GetUsersDocument, options);
        }
export type GetUsersQueryHookResult = ReturnType<typeof useGetUsersQuery>;
export type GetUsersLazyQueryHookResult = ReturnType<typeof useGetUsersLazyQuery>;
export type GetUsersSuspenseQueryHookResult = ReturnType<typeof useGetUsersSuspenseQuery>;
export type GetUsersQueryResult = ApolloReactCommon.QueryResult<GetUsersQuery, GetUsersQueryVariables>;
export const GetGroupsDocument = gql`
    query GetGroups($first: Int, $after: String, $query: String) {
  groups(first: $first, after: $after, query: $query) {
    edges {
      node {
        ...GroupFields
      }
      cursor
    }
    nodes {
      ...GroupFields
    }
    pageInfo {
      hasNextPage
      hasPreviousPage
      startCursor
      endCursor
    }
  }
}
    ${GroupFieldsFragmentDoc}`;

/**
 * __useGetGroupsQuery__
 *
 * To run a query within a React component, call `useGetGroupsQuery` and pass it any options that fit your needs.
 * When your component renders, `useGetGroupsQuery` returns an object from Apollo Client that contains loading, error, and data properties
 * you can use to render your UI.
 *
 * @param baseOptions options that will be passed into the query, supported options are listed on: https://www.apollographql.com/docs/react/api/react-hooks/#options;
 *
 * @example
 * const { data, loading, error } = useGetGroupsQuery({
 *   variables: {
 *      first: // value for 'first'
 *      after: // value for 'after'
 *      query: // value for 'query'
 *   },
 * });
 */
export function useGetGroupsQuery(baseOptions?: ApolloReactHooks.QueryHookOptions<GetGroupsQuery, GetGroupsQueryVariables>) {
        const options = {...defaultOptions, ...baseOptions}
        return ApolloReactHooks.useQuery<GetGroupsQuery, GetGroupsQueryVariables>(GetGroupsDocument, options);
      }
export function useGetGroupsLazyQuery(baseOptions?: ApolloReactHooks.LazyQueryHookOptions<GetGroupsQuery, GetGroupsQueryVariables>) {
          const options = {...defaultOptions, ...baseOptions}
          return ApolloReactHooks.useLazyQuery<GetGroupsQuery, GetGroupsQueryVariables>(GetGroupsDocument, options);
        }
export function useGetGroupsSuspenseQuery(baseOptions: ApolloReactHooks.SkipToken | (ApolloReactHooks.SuspenseQueryHookOptions<GetGroupsQuery, GetGroupsQueryVariables> & { variables: GetGroupsQueryVariables })) {
          const options = baseOptions === ApolloReactHooks.skipToken ? baseOptions : {...defaultOptions, ...baseOptions}
          return ApolloReactHooks.useSuspenseQuery<GetGroupsQuery, GetGroupsQueryVariables>(GetGroupsDocument, options);
        }
export type GetGroupsQueryHookResult = ReturnType<typeof useGetGroupsQuery>;
export type GetGroupsLazyQueryHookResult = ReturnType<typeof useGetGroupsLazyQuery>;
export type GetGroupsSuspenseQueryHookResult = ReturnType<typeof useGetGroupsSuspenseQuery>;
export type GetGroupsQueryResult = ApolloReactCommon.QueryResult<GetGroupsQuery, GetGroupsQueryVariables>;
export const GetRolesDocument = gql`
    query GetRoles($first: Int, $after: String, $query: String) {
  roles(first: $first, after: $after, query: $query) {
    edges {
      node {
        ...RoleFields
      }
      cursor
    }
    nodes {
      ...RoleFields
    }
    pageInfo {
      hasNextPage
      hasPreviousPage
      startCursor
      endCursor
    }
  }
}
    ${RoleFieldsFragmentDoc}`;

/**
 * __useGetRolesQuery__
 *
 * To run a query within a React component, call `useGetRolesQuery` and pass it any options that fit your needs.
 * When your component renders, `useGetRolesQuery` returns an object from Apollo Client that contains loading, error, and data properties
 * you can use to render your UI.
 *
 * @param baseOptions options that will be passed into the query, supported options are listed on: https://www.apollographql.com/docs/react/api/react-hooks/#options;
 *
 * @example
 * const { data, loading, error } = useGetRolesQuery({
 *   variables: {
 *      first: // value for 'first'
 *      after: // value for 'after'
 *      query: // value for 'query'
 *   },
 * });
 */
export function useGetRolesQuery(baseOptions?: ApolloReactHooks.QueryHookOptions<GetRolesQuery, GetRolesQueryVariables>) {
        const options = {...defaultOptions, ...baseOptions}
        return ApolloReactHooks.useQuery<GetRolesQuery, GetRolesQueryVariables>(GetRolesDocument, options);
      }
export function useGetRolesLazyQuery(baseOptions?: ApolloReactHooks.LazyQueryHookOptions<GetRolesQuery, GetRolesQueryVariables>) {
          const options = {...defaultOptions, ...baseOptions}
          return ApolloReactHooks.useLazyQuery<GetRolesQuery, GetRolesQueryVariables>(GetRolesDocument, options);
        }
export function useGetRolesSuspenseQuery(baseOptions: ApolloReactHooks.SkipToken | (ApolloReactHooks.SuspenseQueryHookOptions<GetRolesQuery, GetRolesQueryVariables> & { variables: GetRolesQueryVariables })) {
          const options = baseOptions === ApolloReactHooks.skipToken ? baseOptions : {...defaultOptions, ...baseOptions}
          return ApolloReactHooks.useSuspenseQuery<GetRolesQuery, GetRolesQueryVariables>(GetRolesDocument, options);
        }
export type GetRolesQueryHookResult = ReturnType<typeof useGetRolesQuery>;
export type GetRolesLazyQueryHookResult = ReturnType<typeof useGetRolesLazyQuery>;
export type GetRolesSuspenseQueryHookResult = ReturnType<typeof useGetRolesSuspenseQuery>;
export type GetRolesQueryResult = ApolloReactCommon.QueryResult<GetRolesQuery, GetRolesQueryVariables>;
export const GetPermissionsDocument = gql`
    query GetPermissions($first: Int, $after: String, $query: String) {
  permissions(first: $first, after: $after, query: $query) {
    edges {
      node {
        ...PermissionFields
      }
      cursor
    }
    nodes {
      ...PermissionFields
    }
    pageInfo {
      hasNextPage
      hasPreviousPage
      startCursor
      endCursor
    }
  }
}
    ${PermissionFieldsFragmentDoc}`;

/**
 * __useGetPermissionsQuery__
 *
 * To run a query within a React component, call `useGetPermissionsQuery` and pass it any options that fit your needs.
 * When your component renders, `useGetPermissionsQuery` returns an object from Apollo Client that contains loading, error, and data properties
 * you can use to render your UI.
 *
 * @param baseOptions options that will be passed into the query, supported options are listed on: https://www.apollographql.com/docs/react/api/react-hooks/#options;
 *
 * @example
 * const { data, loading, error } = useGetPermissionsQuery({
 *   variables: {
 *      first: // value for 'first'
 *      after: // value for 'after'
 *      query: // value for 'query'
 *   },
 * });
 */
export function useGetPermissionsQuery(baseOptions?: ApolloReactHooks.QueryHookOptions<GetPermissionsQuery, GetPermissionsQueryVariables>) {
        const options = {...defaultOptions, ...baseOptions}
        return ApolloReactHooks.useQuery<GetPermissionsQuery, GetPermissionsQueryVariables>(GetPermissionsDocument, options);
      }
export function useGetPermissionsLazyQuery(baseOptions?: ApolloReactHooks.LazyQueryHookOptions<GetPermissionsQuery, GetPermissionsQueryVariables>) {
          const options = {...defaultOptions, ...baseOptions}
          return ApolloReactHooks.useLazyQuery<GetPermissionsQuery, GetPermissionsQueryVariables>(GetPermissionsDocument, options);
        }
export function useGetPermissionsSuspenseQuery(baseOptions: ApolloReactHooks.SkipToken | (ApolloReactHooks.SuspenseQueryHookOptions<GetPermissionsQuery, GetPermissionsQueryVariables> & { variables: GetPermissionsQueryVariables })) {
          const options = baseOptions === ApolloReactHooks.skipToken ? baseOptions : {...defaultOptions, ...baseOptions}
          return ApolloReactHooks.useSuspenseQuery<GetPermissionsQuery, GetPermissionsQueryVariables>(GetPermissionsDocument, options);
        }
export type GetPermissionsQueryHookResult = ReturnType<typeof useGetPermissionsQuery>;
export type GetPermissionsLazyQueryHookResult = ReturnType<typeof useGetPermissionsLazyQuery>;
export type GetPermissionsSuspenseQueryHookResult = ReturnType<typeof useGetPermissionsSuspenseQuery>;
export type GetPermissionsQueryResult = ApolloReactCommon.QueryResult<GetPermissionsQuery, GetPermissionsQueryVariables>;