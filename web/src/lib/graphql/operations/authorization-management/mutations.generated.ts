import * as Types from '../../types/__generated__/graphql';

import { gql } from '@apollo/client';
import { UserFieldsFragmentDoc, GroupFieldsFragmentDoc, RoleFieldsFragmentDoc } from '../../fragments/common.generated';
import * as ApolloReactCommon from '@apollo/client/react';
import * as ApolloReactHooks from '@apollo/client/react';
const defaultOptions = {} as const;
export type EnableUserMutationVariables = Types.Exact<{
  userId: Types.Scalars['ID']['input'];
}>;


export type EnableUserMutation = { enableUser: { __typename: 'User', id: string, email: string, displayName: string | null, enabled: boolean } };

export type DisableUserMutationVariables = Types.Exact<{
  userId: Types.Scalars['ID']['input'];
}>;


export type DisableUserMutation = { disableUser: { __typename: 'User', id: string, email: string, displayName: string | null, enabled: boolean } };

export type CreateGroupMutationVariables = Types.Exact<{
  input: Types.CreateGroupInput;
}>;


export type CreateGroupMutation = { createGroup: { __typename: 'Group', id: string, name: string, description: string | null } };

export type UpdateGroupMutationVariables = Types.Exact<{
  groupId: Types.Scalars['ID']['input'];
  input: Types.UpdateGroupInput;
}>;


export type UpdateGroupMutation = { updateGroup: { __typename: 'Group', id: string, name: string, description: string | null } };

export type DeleteGroupMutationVariables = Types.Exact<{
  groupId: Types.Scalars['ID']['input'];
}>;


export type DeleteGroupMutation = { deleteGroup: boolean };

export type CreateRoleMutationVariables = Types.Exact<{
  input: Types.CreateRoleInput;
}>;


export type CreateRoleMutation = { createRole: { __typename: 'Role', id: string, name: string } };

export type UpdateRoleMutationVariables = Types.Exact<{
  roleId: Types.Scalars['ID']['input'];
  input: Types.UpdateRoleInput;
}>;


export type UpdateRoleMutation = { updateRole: { __typename: 'Role', id: string, name: string } };

export type DeleteRoleMutationVariables = Types.Exact<{
  roleId: Types.Scalars['ID']['input'];
}>;


export type DeleteRoleMutation = { deleteRole: boolean };

export type AssignPermissionToRoleMutationVariables = Types.Exact<{
  roleId: Types.Scalars['ID']['input'];
  permissionId: Types.Scalars['ID']['input'];
}>;


export type AssignPermissionToRoleMutation = { assignPermissionToRole: { __typename: 'Role', id: string, name: string } };

export type RemovePermissionFromRoleMutationVariables = Types.Exact<{
  roleId: Types.Scalars['ID']['input'];
  permissionId: Types.Scalars['ID']['input'];
}>;


export type RemovePermissionFromRoleMutation = { removePermissionFromRole: { __typename: 'Role', id: string, name: string } };


export const EnableUserDocument = gql`
    mutation EnableUser($userId: ID!) {
  enableUser(userId: $userId) {
    ...UserFields
  }
}
    ${UserFieldsFragmentDoc}`;

/**
 * __useEnableUserMutation__
 *
 * To run a mutation, you first call `useEnableUserMutation` within a React component and pass it any options that fit your needs.
 * When your component renders, `useEnableUserMutation` returns a tuple that includes:
 * - A mutate function that you can call at any time to execute the mutation
 * - An object with fields that represent the current status of the mutation's execution
 *
 * @param baseOptions options that will be passed into the mutation, supported options are listed on: https://www.apollographql.com/docs/react/api/react-hooks/#options-2;
 *
 * @example
 * const [enableUserMutation, { data, loading, error }] = useEnableUserMutation({
 *   variables: {
 *      userId: // value for 'userId'
 *   },
 * });
 */
export function useEnableUserMutation(baseOptions?: ApolloReactHooks.MutationHookOptions<EnableUserMutation, EnableUserMutationVariables>) {
        const options = {...defaultOptions, ...baseOptions}
        return ApolloReactHooks.useMutation<EnableUserMutation, EnableUserMutationVariables>(EnableUserDocument, options);
      }
export type EnableUserMutationHookResult = ReturnType<typeof useEnableUserMutation>;
export type EnableUserMutationResult = ApolloReactCommon.MutationResult<EnableUserMutation>;
export const DisableUserDocument = gql`
    mutation DisableUser($userId: ID!) {
  disableUser(userId: $userId) {
    ...UserFields
  }
}
    ${UserFieldsFragmentDoc}`;

/**
 * __useDisableUserMutation__
 *
 * To run a mutation, you first call `useDisableUserMutation` within a React component and pass it any options that fit your needs.
 * When your component renders, `useDisableUserMutation` returns a tuple that includes:
 * - A mutate function that you can call at any time to execute the mutation
 * - An object with fields that represent the current status of the mutation's execution
 *
 * @param baseOptions options that will be passed into the mutation, supported options are listed on: https://www.apollographql.com/docs/react/api/react-hooks/#options-2;
 *
 * @example
 * const [disableUserMutation, { data, loading, error }] = useDisableUserMutation({
 *   variables: {
 *      userId: // value for 'userId'
 *   },
 * });
 */
export function useDisableUserMutation(baseOptions?: ApolloReactHooks.MutationHookOptions<DisableUserMutation, DisableUserMutationVariables>) {
        const options = {...defaultOptions, ...baseOptions}
        return ApolloReactHooks.useMutation<DisableUserMutation, DisableUserMutationVariables>(DisableUserDocument, options);
      }
export type DisableUserMutationHookResult = ReturnType<typeof useDisableUserMutation>;
export type DisableUserMutationResult = ApolloReactCommon.MutationResult<DisableUserMutation>;
export const CreateGroupDocument = gql`
    mutation CreateGroup($input: CreateGroupInput!) {
  createGroup(input: $input) {
    ...GroupFields
  }
}
    ${GroupFieldsFragmentDoc}`;

/**
 * __useCreateGroupMutation__
 *
 * To run a mutation, you first call `useCreateGroupMutation` within a React component and pass it any options that fit your needs.
 * When your component renders, `useCreateGroupMutation` returns a tuple that includes:
 * - A mutate function that you can call at any time to execute the mutation
 * - An object with fields that represent the current status of the mutation's execution
 *
 * @param baseOptions options that will be passed into the mutation, supported options are listed on: https://www.apollographql.com/docs/react/api/react-hooks/#options-2;
 *
 * @example
 * const [createGroupMutation, { data, loading, error }] = useCreateGroupMutation({
 *   variables: {
 *      input: // value for 'input'
 *   },
 * });
 */
export function useCreateGroupMutation(baseOptions?: ApolloReactHooks.MutationHookOptions<CreateGroupMutation, CreateGroupMutationVariables>) {
        const options = {...defaultOptions, ...baseOptions}
        return ApolloReactHooks.useMutation<CreateGroupMutation, CreateGroupMutationVariables>(CreateGroupDocument, options);
      }
export type CreateGroupMutationHookResult = ReturnType<typeof useCreateGroupMutation>;
export type CreateGroupMutationResult = ApolloReactCommon.MutationResult<CreateGroupMutation>;
export const UpdateGroupDocument = gql`
    mutation UpdateGroup($groupId: ID!, $input: UpdateGroupInput!) {
  updateGroup(groupId: $groupId, input: $input) {
    ...GroupFields
  }
}
    ${GroupFieldsFragmentDoc}`;

/**
 * __useUpdateGroupMutation__
 *
 * To run a mutation, you first call `useUpdateGroupMutation` within a React component and pass it any options that fit your needs.
 * When your component renders, `useUpdateGroupMutation` returns a tuple that includes:
 * - A mutate function that you can call at any time to execute the mutation
 * - An object with fields that represent the current status of the mutation's execution
 *
 * @param baseOptions options that will be passed into the mutation, supported options are listed on: https://www.apollographql.com/docs/react/api/react-hooks/#options-2;
 *
 * @example
 * const [updateGroupMutation, { data, loading, error }] = useUpdateGroupMutation({
 *   variables: {
 *      groupId: // value for 'groupId'
 *      input: // value for 'input'
 *   },
 * });
 */
export function useUpdateGroupMutation(baseOptions?: ApolloReactHooks.MutationHookOptions<UpdateGroupMutation, UpdateGroupMutationVariables>) {
        const options = {...defaultOptions, ...baseOptions}
        return ApolloReactHooks.useMutation<UpdateGroupMutation, UpdateGroupMutationVariables>(UpdateGroupDocument, options);
      }
export type UpdateGroupMutationHookResult = ReturnType<typeof useUpdateGroupMutation>;
export type UpdateGroupMutationResult = ApolloReactCommon.MutationResult<UpdateGroupMutation>;
export const DeleteGroupDocument = gql`
    mutation DeleteGroup($groupId: ID!) {
  deleteGroup(groupId: $groupId)
}
    `;

/**
 * __useDeleteGroupMutation__
 *
 * To run a mutation, you first call `useDeleteGroupMutation` within a React component and pass it any options that fit your needs.
 * When your component renders, `useDeleteGroupMutation` returns a tuple that includes:
 * - A mutate function that you can call at any time to execute the mutation
 * - An object with fields that represent the current status of the mutation's execution
 *
 * @param baseOptions options that will be passed into the mutation, supported options are listed on: https://www.apollographql.com/docs/react/api/react-hooks/#options-2;
 *
 * @example
 * const [deleteGroupMutation, { data, loading, error }] = useDeleteGroupMutation({
 *   variables: {
 *      groupId: // value for 'groupId'
 *   },
 * });
 */
export function useDeleteGroupMutation(baseOptions?: ApolloReactHooks.MutationHookOptions<DeleteGroupMutation, DeleteGroupMutationVariables>) {
        const options = {...defaultOptions, ...baseOptions}
        return ApolloReactHooks.useMutation<DeleteGroupMutation, DeleteGroupMutationVariables>(DeleteGroupDocument, options);
      }
export type DeleteGroupMutationHookResult = ReturnType<typeof useDeleteGroupMutation>;
export type DeleteGroupMutationResult = ApolloReactCommon.MutationResult<DeleteGroupMutation>;
export const CreateRoleDocument = gql`
    mutation CreateRole($input: CreateRoleInput!) {
  createRole(input: $input) {
    ...RoleFields
  }
}
    ${RoleFieldsFragmentDoc}`;

/**
 * __useCreateRoleMutation__
 *
 * To run a mutation, you first call `useCreateRoleMutation` within a React component and pass it any options that fit your needs.
 * When your component renders, `useCreateRoleMutation` returns a tuple that includes:
 * - A mutate function that you can call at any time to execute the mutation
 * - An object with fields that represent the current status of the mutation's execution
 *
 * @param baseOptions options that will be passed into the mutation, supported options are listed on: https://www.apollographql.com/docs/react/api/react-hooks/#options-2;
 *
 * @example
 * const [createRoleMutation, { data, loading, error }] = useCreateRoleMutation({
 *   variables: {
 *      input: // value for 'input'
 *   },
 * });
 */
export function useCreateRoleMutation(baseOptions?: ApolloReactHooks.MutationHookOptions<CreateRoleMutation, CreateRoleMutationVariables>) {
        const options = {...defaultOptions, ...baseOptions}
        return ApolloReactHooks.useMutation<CreateRoleMutation, CreateRoleMutationVariables>(CreateRoleDocument, options);
      }
export type CreateRoleMutationHookResult = ReturnType<typeof useCreateRoleMutation>;
export type CreateRoleMutationResult = ApolloReactCommon.MutationResult<CreateRoleMutation>;
export const UpdateRoleDocument = gql`
    mutation UpdateRole($roleId: ID!, $input: UpdateRoleInput!) {
  updateRole(roleId: $roleId, input: $input) {
    ...RoleFields
  }
}
    ${RoleFieldsFragmentDoc}`;

/**
 * __useUpdateRoleMutation__
 *
 * To run a mutation, you first call `useUpdateRoleMutation` within a React component and pass it any options that fit your needs.
 * When your component renders, `useUpdateRoleMutation` returns a tuple that includes:
 * - A mutate function that you can call at any time to execute the mutation
 * - An object with fields that represent the current status of the mutation's execution
 *
 * @param baseOptions options that will be passed into the mutation, supported options are listed on: https://www.apollographql.com/docs/react/api/react-hooks/#options-2;
 *
 * @example
 * const [updateRoleMutation, { data, loading, error }] = useUpdateRoleMutation({
 *   variables: {
 *      roleId: // value for 'roleId'
 *      input: // value for 'input'
 *   },
 * });
 */
export function useUpdateRoleMutation(baseOptions?: ApolloReactHooks.MutationHookOptions<UpdateRoleMutation, UpdateRoleMutationVariables>) {
        const options = {...defaultOptions, ...baseOptions}
        return ApolloReactHooks.useMutation<UpdateRoleMutation, UpdateRoleMutationVariables>(UpdateRoleDocument, options);
      }
export type UpdateRoleMutationHookResult = ReturnType<typeof useUpdateRoleMutation>;
export type UpdateRoleMutationResult = ApolloReactCommon.MutationResult<UpdateRoleMutation>;
export const DeleteRoleDocument = gql`
    mutation DeleteRole($roleId: ID!) {
  deleteRole(roleId: $roleId)
}
    `;

/**
 * __useDeleteRoleMutation__
 *
 * To run a mutation, you first call `useDeleteRoleMutation` within a React component and pass it any options that fit your needs.
 * When your component renders, `useDeleteRoleMutation` returns a tuple that includes:
 * - A mutate function that you can call at any time to execute the mutation
 * - An object with fields that represent the current status of the mutation's execution
 *
 * @param baseOptions options that will be passed into the mutation, supported options are listed on: https://www.apollographql.com/docs/react/api/react-hooks/#options-2;
 *
 * @example
 * const [deleteRoleMutation, { data, loading, error }] = useDeleteRoleMutation({
 *   variables: {
 *      roleId: // value for 'roleId'
 *   },
 * });
 */
export function useDeleteRoleMutation(baseOptions?: ApolloReactHooks.MutationHookOptions<DeleteRoleMutation, DeleteRoleMutationVariables>) {
        const options = {...defaultOptions, ...baseOptions}
        return ApolloReactHooks.useMutation<DeleteRoleMutation, DeleteRoleMutationVariables>(DeleteRoleDocument, options);
      }
export type DeleteRoleMutationHookResult = ReturnType<typeof useDeleteRoleMutation>;
export type DeleteRoleMutationResult = ApolloReactCommon.MutationResult<DeleteRoleMutation>;
export const AssignPermissionToRoleDocument = gql`
    mutation AssignPermissionToRole($roleId: ID!, $permissionId: ID!) {
  assignPermissionToRole(roleId: $roleId, permissionId: $permissionId) {
    ...RoleFields
  }
}
    ${RoleFieldsFragmentDoc}`;

/**
 * __useAssignPermissionToRoleMutation__
 *
 * To run a mutation, you first call `useAssignPermissionToRoleMutation` within a React component and pass it any options that fit your needs.
 * When your component renders, `useAssignPermissionToRoleMutation` returns a tuple that includes:
 * - A mutate function that you can call at any time to execute the mutation
 * - An object with fields that represent the current status of the mutation's execution
 *
 * @param baseOptions options that will be passed into the mutation, supported options are listed on: https://www.apollographql.com/docs/react/api/react-hooks/#options-2;
 *
 * @example
 * const [assignPermissionToRoleMutation, { data, loading, error }] = useAssignPermissionToRoleMutation({
 *   variables: {
 *      roleId: // value for 'roleId'
 *      permissionId: // value for 'permissionId'
 *   },
 * });
 */
export function useAssignPermissionToRoleMutation(baseOptions?: ApolloReactHooks.MutationHookOptions<AssignPermissionToRoleMutation, AssignPermissionToRoleMutationVariables>) {
        const options = {...defaultOptions, ...baseOptions}
        return ApolloReactHooks.useMutation<AssignPermissionToRoleMutation, AssignPermissionToRoleMutationVariables>(AssignPermissionToRoleDocument, options);
      }
export type AssignPermissionToRoleMutationHookResult = ReturnType<typeof useAssignPermissionToRoleMutation>;
export type AssignPermissionToRoleMutationResult = ApolloReactCommon.MutationResult<AssignPermissionToRoleMutation>;
export const RemovePermissionFromRoleDocument = gql`
    mutation RemovePermissionFromRole($roleId: ID!, $permissionId: ID!) {
  removePermissionFromRole(roleId: $roleId, permissionId: $permissionId) {
    ...RoleFields
  }
}
    ${RoleFieldsFragmentDoc}`;

/**
 * __useRemovePermissionFromRoleMutation__
 *
 * To run a mutation, you first call `useRemovePermissionFromRoleMutation` within a React component and pass it any options that fit your needs.
 * When your component renders, `useRemovePermissionFromRoleMutation` returns a tuple that includes:
 * - A mutate function that you can call at any time to execute the mutation
 * - An object with fields that represent the current status of the mutation's execution
 *
 * @param baseOptions options that will be passed into the mutation, supported options are listed on: https://www.apollographql.com/docs/react/api/react-hooks/#options-2;
 *
 * @example
 * const [removePermissionFromRoleMutation, { data, loading, error }] = useRemovePermissionFromRoleMutation({
 *   variables: {
 *      roleId: // value for 'roleId'
 *      permissionId: // value for 'permissionId'
 *   },
 * });
 */
export function useRemovePermissionFromRoleMutation(baseOptions?: ApolloReactHooks.MutationHookOptions<RemovePermissionFromRoleMutation, RemovePermissionFromRoleMutationVariables>) {
        const options = {...defaultOptions, ...baseOptions}
        return ApolloReactHooks.useMutation<RemovePermissionFromRoleMutation, RemovePermissionFromRoleMutationVariables>(RemovePermissionFromRoleDocument, options);
      }
export type RemovePermissionFromRoleMutationHookResult = ReturnType<typeof useRemovePermissionFromRoleMutation>;
export type RemovePermissionFromRoleMutationResult = ApolloReactCommon.MutationResult<RemovePermissionFromRoleMutation>;
