import * as Types from '../../types/__generated__/graphql';

import { gql } from '@apollo/client';
import * as ApolloReactCommon from '@apollo/client/react';
import * as ApolloReactHooks from '@apollo/client/react';
const defaultOptions = {} as const;
export type GetUsersQueryVariables = Types.Exact<{
  first?: Types.InputMaybe<Types.Scalars['Int']['input']>;
  after?: Types.InputMaybe<Types.Scalars['String']['input']>;
  query?: Types.InputMaybe<Types.Scalars['String']['input']>;
  orderBy?: Types.InputMaybe<Array<Types.UserOrderByInput> | Types.UserOrderByInput>;
}>;


export type GetUsersQuery = { users: { __typename: 'UserConnection', totalCount: number | null, edges: Array<{ __typename: 'UserEdge', cursor: string, node: { __typename: 'User', id: string, email: string, displayName: string | null, avatarUrl: string | null, enabled: boolean } }>, pageInfo: { __typename: 'PageInfo', hasNextPage: boolean, hasPreviousPage: boolean, startCursor: string | null, endCursor: string | null } } };

export type GetGroupsQueryVariables = Types.Exact<{
  first?: Types.InputMaybe<Types.Scalars['Int']['input']>;
  after?: Types.InputMaybe<Types.Scalars['String']['input']>;
  query?: Types.InputMaybe<Types.Scalars['String']['input']>;
  orderBy?: Types.InputMaybe<Array<Types.GroupOrderByInput> | Types.GroupOrderByInput>;
}>;


export type GetGroupsQuery = { groups: { __typename: 'GroupConnection', totalCount: number | null, edges: Array<{ __typename: 'GroupEdge', cursor: string, node: { __typename: 'Group', id: string, name: string, description: string | null, members: Array<{ __typename: 'User', id: string, email: string, displayName: string | null, enabled: boolean }> } }>, pageInfo: { __typename: 'PageInfo', hasNextPage: boolean, hasPreviousPage: boolean, startCursor: string | null, endCursor: string | null } } };

export type GetRolesQueryVariables = Types.Exact<{
  first?: Types.InputMaybe<Types.Scalars['Int']['input']>;
  after?: Types.InputMaybe<Types.Scalars['String']['input']>;
  query?: Types.InputMaybe<Types.Scalars['String']['input']>;
  orderBy?: Types.InputMaybe<Array<Types.RoleOrderByInput> | Types.RoleOrderByInput>;
}>;


export type GetRolesQuery = { roles: { __typename: 'RoleConnection', totalCount: number | null, edges: Array<{ __typename: 'RoleEdge', cursor: string, node: { __typename: 'Role', id: string, name: string } }>, pageInfo: { __typename: 'PageInfo', hasNextPage: boolean, hasPreviousPage: boolean, startCursor: string | null, endCursor: string | null } } };

export type GetPermissionsQueryVariables = Types.Exact<{
  first?: Types.InputMaybe<Types.Scalars['Int']['input']>;
  after?: Types.InputMaybe<Types.Scalars['String']['input']>;
  query?: Types.InputMaybe<Types.Scalars['String']['input']>;
}>;


export type GetPermissionsQuery = { permissions: { __typename: 'PermissionConnection', edges: Array<{ __typename: 'PermissionEdge', cursor: string, node: { __typename: 'Permission', id: string, name: string } }>, pageInfo: { __typename: 'PageInfo', hasNextPage: boolean, hasPreviousPage: boolean, startCursor: string | null, endCursor: string | null } } };

export type GetUserWithRelationshipsQueryVariables = Types.Exact<{
  id: Types.Scalars['ID']['input'];
}>;


export type GetUserWithRelationshipsQuery = { user: { __typename: 'User', id: string, email: string, displayName: string | null, avatarUrl: string | null, enabled: boolean, createdAt: string, updatedAt: string, groups: Array<{ __typename: 'Group', id: string, name: string, description: string | null }>, roles: Array<{ __typename: 'Role', id: string, name: string }>, permissions: Array<{ __typename: 'Permission', id: string, name: string }> } | null };

export type GetRoleWithRelationshipsQueryVariables = Types.Exact<{
  id: Types.Scalars['ID']['input'];
}>;


export type GetRoleWithRelationshipsQuery = { role: { __typename: 'Role', id: string, name: string, createdAt: string, updatedAt: string, groups: Array<{ __typename: 'Group', id: string, name: string, description: string | null }>, permissions: Array<{ __typename: 'Permission', id: string, name: string }> } | null };

export type GetGroupWithRelationshipsQueryVariables = Types.Exact<{
  id: Types.Scalars['ID']['input'];
}>;


export type GetGroupWithRelationshipsQuery = { group: { __typename: 'Group', id: string, name: string, description: string | null, createdAt: string, updatedAt: string, members: Array<{ __typename: 'User', id: string, email: string, displayName: string | null, enabled: boolean }>, roles: Array<{ __typename: 'Role', id: string, name: string }> } | null };

export type GetRolesWithPermissionsQueryVariables = Types.Exact<{ [key: string]: never; }>;


export type GetRolesWithPermissionsQuery = { roles: { __typename: 'RoleConnection', edges: Array<{ __typename: 'RoleEdge', node: { __typename: 'Role', id: string, name: string, permissions: Array<{ __typename: 'Permission', id: string, name: string }> } }> } };

export type GetRoleWithUsersAndGroupsQueryVariables = Types.Exact<{ [key: string]: never; }>;


export type GetRoleWithUsersAndGroupsQuery = { users: { __typename: 'UserConnection', edges: Array<{ __typename: 'UserEdge', node: { __typename: 'User', id: string, email: string, displayName: string | null, avatarUrl: string | null, enabled: boolean, roles: Array<{ __typename: 'Role', id: string, name: string }> } }> }, groups: { __typename: 'GroupConnection', edges: Array<{ __typename: 'GroupEdge', node: { __typename: 'Group', id: string, name: string, description: string | null, members: Array<{ __typename: 'User', id: string, email: string, displayName: string | null, enabled: boolean }>, roles: Array<{ __typename: 'Role', id: string, name: string }> } }> } };


export const GetUsersDocument = gql`
    query GetUsers($first: Int, $after: String, $query: String, $orderBy: [UserOrderByInput!]) {
  users(first: $first, after: $after, query: $query, orderBy: $orderBy) {
    edges {
      node {
        id
        email
        displayName
        avatarUrl
        enabled
      }
      cursor
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
    `;

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
export function useGetUsersSuspenseQuery(baseOptions?: ApolloReactHooks.SkipToken | ApolloReactHooks.SuspenseQueryHookOptions<GetUsersQuery, GetUsersQueryVariables>) {
          const options = baseOptions === ApolloReactHooks.skipToken ? baseOptions : {...defaultOptions, ...baseOptions}
          return ApolloReactHooks.useSuspenseQuery<GetUsersQuery, GetUsersQueryVariables>(GetUsersDocument, options);
        }
export type GetUsersQueryHookResult = ReturnType<typeof useGetUsersQuery>;
export type GetUsersLazyQueryHookResult = ReturnType<typeof useGetUsersLazyQuery>;
export type GetUsersSuspenseQueryHookResult = ReturnType<typeof useGetUsersSuspenseQuery>;
export type GetUsersQueryResult = ApolloReactCommon.QueryResult<GetUsersQuery, GetUsersQueryVariables>;
export const GetGroupsDocument = gql`
    query GetGroups($first: Int, $after: String, $query: String, $orderBy: [GroupOrderByInput!]) {
  groups(first: $first, after: $after, query: $query, orderBy: $orderBy) {
    edges {
      node {
        id
        name
        description
        members {
          id
          email
          displayName
          enabled
        }
      }
      cursor
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
    `;

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
 *      orderBy: // value for 'orderBy'
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
export function useGetGroupsSuspenseQuery(baseOptions?: ApolloReactHooks.SkipToken | ApolloReactHooks.SuspenseQueryHookOptions<GetGroupsQuery, GetGroupsQueryVariables>) {
          const options = baseOptions === ApolloReactHooks.skipToken ? baseOptions : {...defaultOptions, ...baseOptions}
          return ApolloReactHooks.useSuspenseQuery<GetGroupsQuery, GetGroupsQueryVariables>(GetGroupsDocument, options);
        }
export type GetGroupsQueryHookResult = ReturnType<typeof useGetGroupsQuery>;
export type GetGroupsLazyQueryHookResult = ReturnType<typeof useGetGroupsLazyQuery>;
export type GetGroupsSuspenseQueryHookResult = ReturnType<typeof useGetGroupsSuspenseQuery>;
export type GetGroupsQueryResult = ApolloReactCommon.QueryResult<GetGroupsQuery, GetGroupsQueryVariables>;
export const GetRolesDocument = gql`
    query GetRoles($first: Int, $after: String, $query: String, $orderBy: [RoleOrderByInput!]) {
  roles(first: $first, after: $after, query: $query, orderBy: $orderBy) {
    edges {
      node {
        id
        name
      }
      cursor
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
    `;

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
 *      orderBy: // value for 'orderBy'
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
export function useGetRolesSuspenseQuery(baseOptions?: ApolloReactHooks.SkipToken | ApolloReactHooks.SuspenseQueryHookOptions<GetRolesQuery, GetRolesQueryVariables>) {
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
        id
        name
      }
      cursor
    }
    pageInfo {
      hasNextPage
      hasPreviousPage
      startCursor
      endCursor
    }
  }
}
    `;

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
export function useGetPermissionsSuspenseQuery(baseOptions?: ApolloReactHooks.SkipToken | ApolloReactHooks.SuspenseQueryHookOptions<GetPermissionsQuery, GetPermissionsQueryVariables>) {
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
    id
    email
    displayName
    avatarUrl
    enabled
    createdAt
    updatedAt
    groups {
      id
      name
      description
    }
    roles {
      id
      name
    }
    permissions {
      id
      name
    }
  }
}
    `;

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
export function useGetUserWithRelationshipsSuspenseQuery(baseOptions?: ApolloReactHooks.SkipToken | ApolloReactHooks.SuspenseQueryHookOptions<GetUserWithRelationshipsQuery, GetUserWithRelationshipsQueryVariables>) {
          const options = baseOptions === ApolloReactHooks.skipToken ? baseOptions : {...defaultOptions, ...baseOptions}
          return ApolloReactHooks.useSuspenseQuery<GetUserWithRelationshipsQuery, GetUserWithRelationshipsQueryVariables>(GetUserWithRelationshipsDocument, options as ApolloReactHooks.SkipToken | ApolloReactHooks.useSuspenseQuery.Options<GetUserWithRelationshipsQueryVariables>);
        }
export type GetUserWithRelationshipsQueryHookResult = ReturnType<typeof useGetUserWithRelationshipsQuery>;
export type GetUserWithRelationshipsLazyQueryHookResult = ReturnType<typeof useGetUserWithRelationshipsLazyQuery>;
export type GetUserWithRelationshipsSuspenseQueryHookResult = ReturnType<typeof useGetUserWithRelationshipsSuspenseQuery>;
export type GetUserWithRelationshipsQueryResult = ApolloReactCommon.QueryResult<GetUserWithRelationshipsQuery, GetUserWithRelationshipsQueryVariables>;
export const GetRoleWithRelationshipsDocument = gql`
    query GetRoleWithRelationships($id: ID!) {
  role(id: $id) {
    id
    name
    createdAt
    updatedAt
    groups {
      id
      name
      description
    }
    permissions {
      id
      name
    }
  }
}
    `;

/**
 * __useGetRoleWithRelationshipsQuery__
 *
 * To run a query within a React component, call `useGetRoleWithRelationshipsQuery` and pass it any options that fit your needs.
 * When your component renders, `useGetRoleWithRelationshipsQuery` returns an object from Apollo Client that contains loading, error, and data properties
 * you can use to render your UI.
 *
 * @param baseOptions options that will be passed into the query, supported options are listed on: https://www.apollographql.com/docs/react/api/react-hooks/#options;
 *
 * @example
 * const { data, loading, error } = useGetRoleWithRelationshipsQuery({
 *   variables: {
 *      id: // value for 'id'
 *   },
 * });
 */
export function useGetRoleWithRelationshipsQuery(baseOptions: ApolloReactHooks.QueryHookOptions<GetRoleWithRelationshipsQuery, GetRoleWithRelationshipsQueryVariables> & ({ variables: GetRoleWithRelationshipsQueryVariables; skip?: boolean; } | { skip: boolean; }) ) {
        const options = {...defaultOptions, ...baseOptions}
        return ApolloReactHooks.useQuery<GetRoleWithRelationshipsQuery, GetRoleWithRelationshipsQueryVariables>(GetRoleWithRelationshipsDocument, options);
      }
export function useGetRoleWithRelationshipsLazyQuery(baseOptions?: ApolloReactHooks.LazyQueryHookOptions<GetRoleWithRelationshipsQuery, GetRoleWithRelationshipsQueryVariables>) {
          const options = {...defaultOptions, ...baseOptions}
          return ApolloReactHooks.useLazyQuery<GetRoleWithRelationshipsQuery, GetRoleWithRelationshipsQueryVariables>(GetRoleWithRelationshipsDocument, options);
        }
export function useGetRoleWithRelationshipsSuspenseQuery(baseOptions?: ApolloReactHooks.SkipToken | ApolloReactHooks.SuspenseQueryHookOptions<GetRoleWithRelationshipsQuery, GetRoleWithRelationshipsQueryVariables>) {
          const options = baseOptions === ApolloReactHooks.skipToken ? baseOptions : {...defaultOptions, ...baseOptions}
          return ApolloReactHooks.useSuspenseQuery<GetRoleWithRelationshipsQuery, GetRoleWithRelationshipsQueryVariables>(GetRoleWithRelationshipsDocument, options as ApolloReactHooks.SkipToken | ApolloReactHooks.useSuspenseQuery.Options<GetRoleWithRelationshipsQueryVariables>);
        }
export type GetRoleWithRelationshipsQueryHookResult = ReturnType<typeof useGetRoleWithRelationshipsQuery>;
export type GetRoleWithRelationshipsLazyQueryHookResult = ReturnType<typeof useGetRoleWithRelationshipsLazyQuery>;
export type GetRoleWithRelationshipsSuspenseQueryHookResult = ReturnType<typeof useGetRoleWithRelationshipsSuspenseQuery>;
export type GetRoleWithRelationshipsQueryResult = ApolloReactCommon.QueryResult<GetRoleWithRelationshipsQuery, GetRoleWithRelationshipsQueryVariables>;
export const GetGroupWithRelationshipsDocument = gql`
    query GetGroupWithRelationships($id: ID!) {
  group(id: $id) {
    id
    name
    description
    createdAt
    updatedAt
    members {
      id
      email
      displayName
      enabled
    }
    roles {
      id
      name
    }
  }
}
    `;

/**
 * __useGetGroupWithRelationshipsQuery__
 *
 * To run a query within a React component, call `useGetGroupWithRelationshipsQuery` and pass it any options that fit your needs.
 * When your component renders, `useGetGroupWithRelationshipsQuery` returns an object from Apollo Client that contains loading, error, and data properties
 * you can use to render your UI.
 *
 * @param baseOptions options that will be passed into the query, supported options are listed on: https://www.apollographql.com/docs/react/api/react-hooks/#options;
 *
 * @example
 * const { data, loading, error } = useGetGroupWithRelationshipsQuery({
 *   variables: {
 *      id: // value for 'id'
 *   },
 * });
 */
export function useGetGroupWithRelationshipsQuery(baseOptions: ApolloReactHooks.QueryHookOptions<GetGroupWithRelationshipsQuery, GetGroupWithRelationshipsQueryVariables> & ({ variables: GetGroupWithRelationshipsQueryVariables; skip?: boolean; } | { skip: boolean; }) ) {
        const options = {...defaultOptions, ...baseOptions}
        return ApolloReactHooks.useQuery<GetGroupWithRelationshipsQuery, GetGroupWithRelationshipsQueryVariables>(GetGroupWithRelationshipsDocument, options);
      }
export function useGetGroupWithRelationshipsLazyQuery(baseOptions?: ApolloReactHooks.LazyQueryHookOptions<GetGroupWithRelationshipsQuery, GetGroupWithRelationshipsQueryVariables>) {
          const options = {...defaultOptions, ...baseOptions}
          return ApolloReactHooks.useLazyQuery<GetGroupWithRelationshipsQuery, GetGroupWithRelationshipsQueryVariables>(GetGroupWithRelationshipsDocument, options);
        }
export function useGetGroupWithRelationshipsSuspenseQuery(baseOptions?: ApolloReactHooks.SkipToken | ApolloReactHooks.SuspenseQueryHookOptions<GetGroupWithRelationshipsQuery, GetGroupWithRelationshipsQueryVariables>) {
          const options = baseOptions === ApolloReactHooks.skipToken ? baseOptions : {...defaultOptions, ...baseOptions}
          return ApolloReactHooks.useSuspenseQuery<GetGroupWithRelationshipsQuery, GetGroupWithRelationshipsQueryVariables>(GetGroupWithRelationshipsDocument, options as ApolloReactHooks.SkipToken | ApolloReactHooks.useSuspenseQuery.Options<GetGroupWithRelationshipsQueryVariables>);
        }
export type GetGroupWithRelationshipsQueryHookResult = ReturnType<typeof useGetGroupWithRelationshipsQuery>;
export type GetGroupWithRelationshipsLazyQueryHookResult = ReturnType<typeof useGetGroupWithRelationshipsLazyQuery>;
export type GetGroupWithRelationshipsSuspenseQueryHookResult = ReturnType<typeof useGetGroupWithRelationshipsSuspenseQuery>;
export type GetGroupWithRelationshipsQueryResult = ApolloReactCommon.QueryResult<GetGroupWithRelationshipsQuery, GetGroupWithRelationshipsQueryVariables>;
export const GetRolesWithPermissionsDocument = gql`
    query GetRolesWithPermissions {
  roles(first: 100) {
    edges {
      node {
        id
        name
        permissions {
          id
          name
        }
      }
    }
  }
}
    `;

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
export function useGetRolesWithPermissionsSuspenseQuery(baseOptions?: ApolloReactHooks.SkipToken | ApolloReactHooks.SuspenseQueryHookOptions<GetRolesWithPermissionsQuery, GetRolesWithPermissionsQueryVariables>) {
          const options = baseOptions === ApolloReactHooks.skipToken ? baseOptions : {...defaultOptions, ...baseOptions}
          return ApolloReactHooks.useSuspenseQuery<GetRolesWithPermissionsQuery, GetRolesWithPermissionsQueryVariables>(GetRolesWithPermissionsDocument, options);
        }
export type GetRolesWithPermissionsQueryHookResult = ReturnType<typeof useGetRolesWithPermissionsQuery>;
export type GetRolesWithPermissionsLazyQueryHookResult = ReturnType<typeof useGetRolesWithPermissionsLazyQuery>;
export type GetRolesWithPermissionsSuspenseQueryHookResult = ReturnType<typeof useGetRolesWithPermissionsSuspenseQuery>;
export type GetRolesWithPermissionsQueryResult = ApolloReactCommon.QueryResult<GetRolesWithPermissionsQuery, GetRolesWithPermissionsQueryVariables>;
export const GetRoleWithUsersAndGroupsDocument = gql`
    query GetRoleWithUsersAndGroups {
  users(first: 100) {
    edges {
      node {
        id
        email
        displayName
        avatarUrl
        enabled
        roles {
          id
          name
        }
      }
    }
  }
  groups(first: 100) {
    edges {
      node {
        id
        name
        description
        members {
          id
          email
          displayName
          enabled
        }
        roles {
          id
          name
        }
      }
    }
  }
}
    `;

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
export function useGetRoleWithUsersAndGroupsSuspenseQuery(baseOptions?: ApolloReactHooks.SkipToken | ApolloReactHooks.SuspenseQueryHookOptions<GetRoleWithUsersAndGroupsQuery, GetRoleWithUsersAndGroupsQueryVariables>) {
          const options = baseOptions === ApolloReactHooks.skipToken ? baseOptions : {...defaultOptions, ...baseOptions}
          return ApolloReactHooks.useSuspenseQuery<GetRoleWithUsersAndGroupsQuery, GetRoleWithUsersAndGroupsQueryVariables>(GetRoleWithUsersAndGroupsDocument, options);
        }
export type GetRoleWithUsersAndGroupsQueryHookResult = ReturnType<typeof useGetRoleWithUsersAndGroupsQuery>;
export type GetRoleWithUsersAndGroupsLazyQueryHookResult = ReturnType<typeof useGetRoleWithUsersAndGroupsLazyQuery>;
export type GetRoleWithUsersAndGroupsSuspenseQueryHookResult = ReturnType<typeof useGetRoleWithUsersAndGroupsSuspenseQuery>;
export type GetRoleWithUsersAndGroupsQueryResult = ApolloReactCommon.QueryResult<GetRoleWithUsersAndGroupsQuery, GetRoleWithUsersAndGroupsQueryVariables>;