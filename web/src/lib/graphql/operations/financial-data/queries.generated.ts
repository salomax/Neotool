import * as Types from '../../types/__generated__/graphql';

import { TypedDocumentNode as DocumentNode } from '@graphql-typed-document-node/core';
export type SearchInvestmentFundsAutocompleteQueryVariables = Types.Exact<{
  query: Types.Scalars['String']['input'];
}>;


export type SearchInvestmentFundsAutocompleteQuery = { searchInvestmentFundsAutocomplete: { __typename: 'InvestmentFundAutocompleteConnection', suggestions: Array<{ __typename: 'InvestmentFundAutocompleteItem', id: string, name: string, cnpj: string, assetType: string, isActive: boolean }> } };


export const SearchInvestmentFundsAutocompleteDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"query","name":{"kind":"Name","value":"SearchInvestmentFundsAutocomplete"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"query"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"searchInvestmentFundsAutocomplete"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"query"},"value":{"kind":"Variable","name":{"kind":"Name","value":"query"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"suggestions"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"id"}},{"kind":"Field","name":{"kind":"Name","value":"name"}},{"kind":"Field","name":{"kind":"Name","value":"cnpj"}},{"kind":"Field","name":{"kind":"Name","value":"assetType"}},{"kind":"Field","name":{"kind":"Name","value":"isActive"}}]}}]}}]}}]} as unknown as DocumentNode<SearchInvestmentFundsAutocompleteQuery, SearchInvestmentFundsAutocompleteQueryVariables>;