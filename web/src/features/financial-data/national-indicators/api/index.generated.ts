import * as Types from '../../../../lib/graphql/types/__generated__/graphql';

import { TypedDocumentNode as DocumentNode } from '@graphql-typed-document-node/core';
export type GetEconomicIndicatorsQueryVariables = Types.Exact<{ [key: string]: never; }>;


export type GetEconomicIndicatorsQuery = { economicIndicators: Array<{ __typename: 'EconomicIndicator', id: string, label: string, description: string | null, unit: string, frequency: string, variations: Array<{ __typename: 'EconomicIndicatorVariation', id: string, label: string, isDefault: boolean }> }> };

export type IndicatorsLatestQueryVariables = Types.Exact<{ [key: string]: never; }>;


export type IndicatorsLatestQuery = { indicatorsLatest: Array<{ __typename: 'EconomicIndicatorLatest', indicator: string, variation: string, unit: string, latest: { __typename: 'EconomicIndicatorPoint', referenceDate: unknown, value: number } | null }> };


export const GetEconomicIndicatorsDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"query","name":{"kind":"Name","value":"GetEconomicIndicators"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"economicIndicators"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"id"}},{"kind":"Field","name":{"kind":"Name","value":"label"}},{"kind":"Field","name":{"kind":"Name","value":"description"}},{"kind":"Field","name":{"kind":"Name","value":"unit"}},{"kind":"Field","name":{"kind":"Name","value":"frequency"}},{"kind":"Field","name":{"kind":"Name","value":"variations"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"id"}},{"kind":"Field","name":{"kind":"Name","value":"label"}},{"kind":"Field","name":{"kind":"Name","value":"isDefault"}}]}}]}}]}}]} as unknown as DocumentNode<GetEconomicIndicatorsQuery, GetEconomicIndicatorsQueryVariables>;
export const IndicatorsLatestDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"query","name":{"kind":"Name","value":"IndicatorsLatest"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"indicatorsLatest"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"indicator"}},{"kind":"Field","name":{"kind":"Name","value":"variation"}},{"kind":"Field","name":{"kind":"Name","value":"unit"}},{"kind":"Field","name":{"kind":"Name","value":"latest"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"referenceDate"}},{"kind":"Field","name":{"kind":"Name","value":"value"}}]}}]}}]}}]} as unknown as DocumentNode<IndicatorsLatestQuery, IndicatorsLatestQueryVariables>;