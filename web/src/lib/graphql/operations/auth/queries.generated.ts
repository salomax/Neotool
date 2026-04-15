import * as Types from '../../types/__generated__/graphql';

import { TypedDocumentNode as DocumentNode } from '@graphql-typed-document-node/core';
export type CurrentUserQueryVariables = Types.Exact<{ [key: string]: never; }>;


export type CurrentUserQuery = { currentUser: { __typename: 'User', id: string, email: string, displayName: string | null, avatarUrl: string | null, avatarProvider: string | null, roles: Array<{ __typename: 'Role', id: string, name: string }>, permissions: Array<{ __typename: 'Permission', id: string, name: string }> } | null };

export type UserPreferencesQueryVariables = Types.Exact<{ [key: string]: never; }>;


export type UserPreferencesQuery = { userPreferences: { __typename: 'UserPreferences', language: Types.Language, theme: Types.Theme, blurBalances: boolean, firstDayOfWeek: number, keepMeConnected: boolean } };

export type MyAccountsQueryVariables = Types.Exact<{ [key: string]: never; }>;


export type MyAccountsQuery = { myAccounts: Array<{ __typename: 'Account', id: string, accountName: string, accountType: string, accountStatus: string, isOwner: boolean }> };


export const CurrentUserDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"query","name":{"kind":"Name","value":"CurrentUser"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"currentUser"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"id"}},{"kind":"Field","name":{"kind":"Name","value":"email"}},{"kind":"Field","name":{"kind":"Name","value":"displayName"}},{"kind":"Field","name":{"kind":"Name","value":"avatarUrl"}},{"kind":"Field","name":{"kind":"Name","value":"avatarProvider"}},{"kind":"Field","name":{"kind":"Name","value":"roles"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"id"}},{"kind":"Field","name":{"kind":"Name","value":"name"}}]}},{"kind":"Field","name":{"kind":"Name","value":"permissions"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"id"}},{"kind":"Field","name":{"kind":"Name","value":"name"}}]}}]}}]}}]} as unknown as DocumentNode<CurrentUserQuery, CurrentUserQueryVariables>;
export const UserPreferencesDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"query","name":{"kind":"Name","value":"UserPreferences"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"userPreferences"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"language"}},{"kind":"Field","name":{"kind":"Name","value":"theme"}},{"kind":"Field","name":{"kind":"Name","value":"blurBalances"}},{"kind":"Field","name":{"kind":"Name","value":"firstDayOfWeek"}},{"kind":"Field","name":{"kind":"Name","value":"keepMeConnected"}}]}}]}}]} as unknown as DocumentNode<UserPreferencesQuery, UserPreferencesQueryVariables>;
export const MyAccountsDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"query","name":{"kind":"Name","value":"MyAccounts"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"myAccounts"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"id"}},{"kind":"Field","name":{"kind":"Name","value":"accountName"}},{"kind":"Field","name":{"kind":"Name","value":"accountType"}},{"kind":"Field","name":{"kind":"Name","value":"accountStatus"}},{"kind":"Field","name":{"kind":"Name","value":"isOwner"}}]}}]}}]} as unknown as DocumentNode<MyAccountsQuery, MyAccountsQueryVariables>;