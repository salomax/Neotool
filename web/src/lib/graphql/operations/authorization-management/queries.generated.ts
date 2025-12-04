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
  orderBy?: Types.InputMaybe<Array<Types.UserOrderByInput> | Types.UserOrderByInput>;
}>;


export type GetUsersQuery = { users: { __typename: 'UserConnection', totalCount: number | null, edges: Array<{ __typename: 'UserEdge', cursor: string, node: { __typename: 'User', id: string, email: string, displayName: string | null, enabled: boolean } }>, nodes: Array<{ __typename: 'User', id: string, email: string, displayName: string | null, enabled: boolean }>, pageInfo: { __typename: 'PageInfo', hasNextPage: boolean, hasPreviousPage: boolean, startCursor: string | null, endCursor: string | null } } };

export type GetGroupsQueryVariables = Types.Exact<{
  first?: Types.InputMaybe<Types.Scalars['Int']['input']>;
  after?: Types.InputMaybe<Types.Scalars['String']['input']>;
  query?: Types.InputMaybe<Types.Scalars['String']['input']>;
}>;


export type GetGroupsQuery = { groups: { __typename: 'GroupConnection', totalCount: number | null, edges: Array<{ __typename: 'GroupEdge', cursor: string, node: { __typename: 'Group', id: string, name: string, description: string | null, members: Array<{ __typename: 'User', id: string, email: string, displayName: string | null, enabled: boolean }> } }>, nodes: Array<{ __typename: 'Group', id: string, name: string, description: string | null, members: Array<{ __typename: 'User', id: string, email: string, displayName: string | null, enabled: boolean }> }>, pageInfo: { __typename: 'PageInfo', hasNextPage: boolean, hasPreviousPage: boolean, startCursor: string | null, endCursor: string | null } } };

export type GetRolesQueryVariables = Types.Exact<{
  first?: Types.InputMaybe<Types.Scalars['Int']['input']>;
  after?: Types.InputMaybe<Types.Scalars['String']['input']>;
  query?: Types.InputMaybe<Types.Scalars['String']['input']>;
}>;


export type GetRolesQuery = { roles: { __typename: 'RoleConnection', totalCount: number | null, edges: Array<{ __typename: 'RoleEdge', cursor: string, node: { __typename: 'Role', id: string, name: string } }>, nodes: Array<{ __typename: 'Role', id: string, name: string }>, pageInfo: { __typename: 'PageInfo', hasNextPage: boolean, hasPreviousPage: boolean, startCursor: string | null, endCursor: string | null } } };

export type GetPermissionsQueryVariables = Types.Exact<{
  first?: Types.InputMaybe<Types.Scalars['Int']['input']>;
  after?: Types.InputMaybe<Types.Scalars['String']['input']>;
  query?: Types.InputMaybe<Types.Scalars['String']['input']>;
}>;


export type GetPermissionsQuery = { permissions: { __typename: 'PermissionConnection', edges: Array<{ __typename: 'PermissionEdge', cursor: string, node: { __typename: 'Permission', id: string, name: string } }>, nodes: Array<{ __typename: 'Permission', id: string, name: string }>, pageInfo: { __typename: 'PageInfo', hasNextPage: boolean, hasPreviousPage: boolean, startCursor: string | null, endCursor: string | null } } };

export type GetUserWithRelationshipsQueryVariables = Types.Exact<{
  id: Types.Scalars['ID']['input'];
}>;


export type GetUserWithRelationshipsQuery = { user: { __typename: 'User', id: string, email: string, displayName: string | null, enabled: boolean, groups: Array<{ __typename: 'Group', id: string, name: string, description: string | null, members: Array<{ __typename: 'User', id: string, email: string, displayName: string | null, enabled: boolean }> }>, roles: Array<{ __typename: 'Role', id: string, name: string }>, permissions: Array<{ __typename: 'Permission', id: string, name: string }> } | null };

export type GetRolesWithPermissionsQueryVariables = Types.Exact<{ [key: string]: never; }>;


export type GetRolesWithPermissionsQuery = { roles: { __typename: 'RoleConnection', nodes: Array<{ __typename: 'Role', id: string, name: string, permissions: Array<{ __typename: 'Permission', id: string, name: string }> }> } };

export type GetRoleWithUsersAndGroupsQueryVariables = Types.Exact<{ [key: string]: never; }>;


export type GetRoleWithUsersAndGroupsQuery = { users: { __typename: 'UserConnection', nodes: Array<{ __typename: 'User', id: string, email: string, displayName: string | null, enabled: boolean, roles: Array<{ __typename: 'Role', id: string, name: string }> }> }, groups: { __typename: 'GroupConnection', nodes: Array<{ __typename: 'Group', id: string, name: string, description: string | null, roles: Array<{ __typename: 'Role', id: string, name: string }>, members: Array<{ __typename: 'User', id: string, email: string, displayName: string | null, enabled: boolean }> }> } };


export const GetUsersDocument = gql`
    query GetUsers($first: Int, $after: String, $query: String, $orderBy: [UserOrderByInput!]) {
  users(first: $first, after: $after, query: $query, orderBy: $orderBy) {
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
    totalCount
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
 *      orderBy: // value for 'orderBy'
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
    totalCount
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
    totalCount
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
export const GetUserWithRelationshipsDocument = gql`
    query GetUserWithRelationships($id: ID!) {
  user(id: $id) {
    ...UserFields
    groups {
      ...GroupFields
    }
    roles {
      ...RoleFields
    }
    permissions {
      ...PermissionFields
    }
  }
}
    ${UserFieldsFragmentDoc}
${GroupFieldsFragmentDoc}
${RoleFieldsFragmentDoc}
${PermissionFieldsFragmentDoc}`;

/**
 * __useGetUserWithRelationshipsQuery__
 *
 * To run a query within a React component, call `useGetUserWithRelationshipsQuery` and pass it any options that fit your needs.
 * When your component renders, `useGetUserWithRelationshipsQuery` returns an object from Apollo Client that contains loading, error, and data properties
 * you can use to render your UI.
 *
 * @param baseOptions options that will be passed into the query, supported options are listed on: https://www.apollographql.com/docs/react/api/react-hooks/#options;
 *
 * @example
 * const { data, loading, error } = useGetUserWithRelationshipsQuery({
 *   variables: {
 *      id: // value for 'id'
 *   },
 * });
 */
export function useGetUserWithRelationshipsQuery(baseOptions: ApolloReactHooks.QueryHookOptions<GetUserWithRelationshipsQuery, GetUserWithRelationshipsQueryVariables> & ({ variables: GetUserWithRelationshipsQueryVariables; skip?: boolean; } | { skip: boolean; }) ) {
        const options = {...defaultOptions, ...baseOptions}
        return ApolloReactHooks.useQuery<GetUserWithRelationshipsQuery, GetUserWithRelationshipsQueryVariables>(GetUserWithRelationshipsDocument, options);
      }
export function useGetUserWithRelationshipsLazyQuery(baseOptions?: ApolloReactHooks.LazyQueryHookOptions<GetUserWithRelationshipsQuery, GetUserWithRelationshipsQueryVariables>) {
          const options = {...defaultOptions, ...baseOptions}
          return ApolloReactHooks.useLazyQuery<GetUserWithRelationshipsQuery, GetUserWithRelationshipsQueryVariables>(GetUserWithRelationshipsDocument, options);
        }
export function useGetUserWithRelationshipsSuspenseQuery(baseOptions: ApolloReactHooks.SkipToken | (ApolloReactHooks.SuspenseQueryHookOptions<GetUserWithRelationshipsQuery, GetUserWithRelationshipsQueryVariables> & { variables: GetUserWithRelationshipsQueryVariables })) {
          const options = baseOptions === ApolloReactHooks.skipToken ? baseOptions : {...defaultOptions, ...baseOptions}
          return ApolloReactHooks.useSuspenseQuery<GetUserWithRelationshipsQuery, GetUserWithRelationshipsQueryVariables>(GetUserWithRelationshipsDocument, options);
        }
export type GetUserWithRelationshipsQueryHookResult = ReturnType<typeof useGetUserWithRelationshipsQuery>;
export type GetUserWithRelationshipsLazyQueryHookResult = ReturnType<typeof useGetUserWithRelationshipsLazyQuery>;
export type GetUserWithRelationshipsSuspenseQueryHookResult = ReturnType<typeof useGetUserWithRelationshipsSuspenseQuery>;
export type GetUserWithRelationshipsQueryResult = ApolloReactCommon.QueryResult<GetUserWithRelationshipsQuery, GetUserWithRelationshipsQueryVariables>;
export const GetRolesWithPermissionsDocument = gql`
    query GetRolesWithPermissions {
  roles(first: 1000) {
    nodes {
      ...RoleFields
      permissions {
        ...PermissionFields
      }
    }
  }
}
    ${RoleFieldsFragmentDoc}
${PermissionFieldsFragmentDoc}`;

/**
 * __useGetRolesWithPermissionsQuery__
 *
 * To run a query within a React component, call `useGetRolesWithPermissionsQuery` and pass it any options that fit your needs.
 * When your component renders, `useGetRolesWithPermissionsQuery` returns an object from Apollo Client that contains loading, error, and data properties
 * you can use to render your UI.
 *
 * @param baseOptions options that will be passed into the query, supported options are listed on: https://www.apollographql.com/docs/react/api/react-hooks/#options;
 *
 * @example
 * const { data, loading, error } = useGetRolesWithPermissionsQuery({
 *   variables: {
 *   },
 * });
 */
export function useGetRolesWithPermissionsQuery(baseOptions?: ApolloReactHooks.QueryHookOptions<GetRolesWithPermissionsQuery, GetRolesWithPermissionsQueryVariables>) {
        const options = {...defaultOptions, ...baseOptions}
        return ApolloReactHooks.useQuery<GetRolesWithPermissionsQuery, GetRolesWithPermissionsQueryVariables>(GetRolesWithPermissionsDocument, options);
      }
export function useGetRolesWithPermissionsLazyQuery(baseOptions?: ApolloReactHooks.LazyQueryHookOptions<GetRolesWithPermissionsQuery, GetRolesWithPermissionsQueryVariables>) {
          const options = {...defaultOptions, ...baseOptions}
          return ApolloReactHooks.useLazyQuery<GetRolesWithPermissionsQuery, GetRolesWithPermissionsQueryVariables>(GetRolesWithPermissionsDocument, options);
        }
export function useGetRolesWithPermissionsSuspenseQuery(baseOptions: ApolloReactHooks.SkipToken | (ApolloReactHooks.SuspenseQueryHookOptions<GetRolesWithPermissionsQuery, GetRolesWithPermissionsQueryVariables> & { variables: GetRolesWithPermissionsQueryVariables })) {
          const options = baseOptions === ApolloReactHooks.skipToken ? baseOptions : {...defaultOptions, ...baseOptions}
          return ApolloReactHooks.useSuspenseQuery<GetRolesWithPermissionsQuery, GetRolesWithPermissionsQueryVariables>(GetRolesWithPermissionsDocument, options);
        }
export type GetRolesWithPermissionsQueryHookResult = ReturnType<typeof useGetRolesWithPermissionsQuery>;
export type GetRolesWithPermissionsLazyQueryHookResult = ReturnType<typeof useGetRolesWithPermissionsLazyQuery>;
export type GetRolesWithPermissionsSuspenseQueryHookResult = ReturnType<typeof useGetRolesWithPermissionsSuspenseQuery>;
export type GetRolesWithPermissionsQueryResult = ApolloReactCommon.QueryResult<GetRolesWithPermissionsQuery, GetRolesWithPermissionsQueryVariables>;
export const GetRoleWithUsersAndGroupsDocument = gql`
    query GetRoleWithUsersAndGroups {
  users(first: 1000) {
    nodes {
      ...UserFields
      roles {
        ...RoleFields
      }
    }
  }
  groups(first: 1000) {
    nodes {
      ...GroupFields
      roles {
        ...RoleFields
      }
    }
  }
}
    ${UserFieldsFragmentDoc}
${RoleFieldsFragmentDoc}
${GroupFieldsFragmentDoc}`;

/**
 * __useGetRoleWithUsersAndGroupsQuery__
 *
 * To run a query within a React component, call `useGetRoleWithUsersAndGroupsQuery` and pass it any options that fit your needs.
 * When your component renders, `useGetRoleWithUsersAndGroupsQuery` returns an object from Apollo Client that contains loading, error, and data properties
 * you can use to render your UI.
 *
 * @param baseOptions options that will be passed into the query, supported options are listed on: https://www.apollographql.com/docs/react/api/react-hooks/#options;
 *
 * @example
 * const { data, loading, error } = useGetRoleWithUsersAndGroupsQuery({
 *   variables: {
 *   },
 * });
 */
export function useGetRoleWithUsersAndGroupsQuery(baseOptions?: ApolloReactHooks.QueryHookOptions<GetRoleWithUsersAndGroupsQuery, GetRoleWithUsersAndGroupsQueryVariables>) {
        const options = {...defaultOptions, ...baseOptions}
        return ApolloReactHooks.useQuery<GetRoleWithUsersAndGroupsQuery, GetRoleWithUsersAndGroupsQueryVariables>(GetRoleWithUsersAndGroupsDocument, options);
      }
export function useGetRoleWithUsersAndGroupsLazyQuery(baseOptions?: ApolloReactHooks.LazyQueryHookOptions<GetRoleWithUsersAndGroupsQuery, GetRoleWithUsersAndGroupsQueryVariables>) {
          const options = {...defaultOptions, ...baseOptions}
          return ApolloReactHooks.useLazyQuery<GetRoleWithUsersAndGroupsQuery, GetRoleWithUsersAndGroupsQueryVariables>(GetRoleWithUsersAndGroupsDocument, options);
        }
export function useGetRoleWithUsersAndGroupsSuspenseQuery(baseOptions: ApolloReactHooks.SkipToken | (ApolloReactHooks.SuspenseQueryHookOptions<GetRoleWithUsersAndGroupsQuery, GetRoleWithUsersAndGroupsQueryVariables> & { variables: GetRoleWithUsersAndGroupsQueryVariables })) {
          const options = baseOptions === ApolloReactHooks.skipToken ? baseOptions : {...defaultOptions, ...baseOptions}
          return ApolloReactHooks.useSuspenseQuery<GetRoleWithUsersAndGroupsQuery, GetRoleWithUsersAndGroupsQueryVariables>(GetRoleWithUsersAndGroupsDocument, options);
        }
export type GetRoleWithUsersAndGroupsQueryHookResult = ReturnType<typeof useGetRoleWithUsersAndGroupsQuery>;
export type GetRoleWithUsersAndGroupsLazyQueryHookResult = ReturnType<typeof useGetRoleWithUsersAndGroupsLazyQuery>;
export type GetRoleWithUsersAndGroupsSuspenseQueryHookResult = ReturnType<typeof useGetRoleWithUsersAndGroupsSuspenseQuery>;
export type GetRoleWithUsersAndGroupsQueryResult = ApolloReactCommon.QueryResult<GetRoleWithUsersAndGroupsQuery, GetRoleWithUsersAndGroupsQueryVariables>;