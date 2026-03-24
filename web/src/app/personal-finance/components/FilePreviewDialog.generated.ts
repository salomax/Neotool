import * as Types from '../../../lib/graphql/types/__generated__/graphql';

import { TypedDocumentNode as DocumentNode } from '@graphql-typed-document-node/core';
export type AssetPreviewQueryVariables = Types.Exact<{
  id: Types.Scalars['ID']['input'];
  ttlSeconds?: Types.InputMaybe<Types.Scalars['Int']['input']>;
}>;


export type AssetPreviewQuery = { asset: { __typename: 'Asset', id: string, status: Types.AssetStatus, originalFilename: string | null, mimeType: string, sizeBytes: unknown | null, downloadUrl: string | null } | null };


export const AssetPreviewDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"query","name":{"kind":"Name","value":"AssetPreview"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"id"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"ID"}}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"ttlSeconds"}},"type":{"kind":"NamedType","name":{"kind":"Name","value":"Int"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"asset"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"id"},"value":{"kind":"Variable","name":{"kind":"Name","value":"id"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"id"}},{"kind":"Field","name":{"kind":"Name","value":"status"}},{"kind":"Field","name":{"kind":"Name","value":"originalFilename"}},{"kind":"Field","name":{"kind":"Name","value":"mimeType"}},{"kind":"Field","name":{"kind":"Name","value":"sizeBytes"}},{"kind":"Field","name":{"kind":"Name","value":"downloadUrl"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"ttlSeconds"},"value":{"kind":"Variable","name":{"kind":"Name","value":"ttlSeconds"}}}]}]}}]}}]} as unknown as DocumentNode<AssetPreviewQuery, AssetPreviewQueryVariables>;