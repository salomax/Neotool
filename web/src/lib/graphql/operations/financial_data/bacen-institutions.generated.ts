import * as Types from '../../types/__generated__/graphql';

import { gql } from '@apollo/client';
import * as ApolloReactCommon from '@apollo/client/react';
import * as ApolloReactHooks from '@apollo/client/react';
const defaultOptions = {} as const;
export type SearchBacenInstitutionsAutocompleteQueryVariables = Types.Exact<{
  query: Types.Scalars['String']['input'];
}>;


export type SearchBacenInstitutionsAutocompleteQuery = { searchBacenInstitutionsAutocomplete: { __typename: 'BacenInstitutionAutocompleteConnection', suggestions: Array<{ __typename: 'BacenInstitutionAutocompleteItem', name: string }> } };

export type BacenInstitutionsQueryVariables = Types.Exact<{
  query?: Types.InputMaybe<Types.Scalars['String']['input']>;
  first?: Types.InputMaybe<Types.Scalars['Int']['input']>;
  after?: Types.InputMaybe<Types.Scalars['String']['input']>;
}>;


export type BacenInstitutionsQuery = { bacenInstitutions: { __typename: 'BacenInstitutionConnection', totalCount: number | null, edges: Array<{ __typename: 'BacenInstitutionEdge', cursor: string, node: { __typename: 'BacenInstitutionSummaryItem', id: string, name: string, profit: number | null, equity: number | null, yoyEquityGrowth: number | null, basileiaIndex: number | null, institutionType: Types.BacenInstitutionType | null, logoAsset: { __typename: 'Asset', publicUrl: string | null } | null } }>, pageInfo: { __typename: 'PageInfo', hasNextPage: boolean, hasPreviousPage: boolean, startCursor: string | null, endCursor: string | null } } };


export const SearchBacenInstitutionsAutocompleteDocument = gql`
    query SearchBacenInstitutionsAutocomplete($query: String!) {
  searchBacenInstitutionsAutocomplete(query: $query) {
    suggestions {
      name
    }
  }
}
    `;

/**
 * __useSearchBacenInstitutionsAutocompleteQuery__
 *
 * To run a query within a React component, call `useSearchBacenInstitutionsAutocompleteQuery` and pass it any options that fit your needs.
 * When your component renders, `useSearchBacenInstitutionsAutocompleteQuery` returns an object from Apollo Client that contains loading, error, and data properties
 * you can use to render your UI.
 *
 * @param baseOptions options that will be passed into the query, supported options are listed on: https://www.apollographql.com/docs/react/api/react-hooks/#options;
 *
 * @example
 * const { data, loading, error } = useSearchBacenInstitutionsAutocompleteQuery({
 *   variables: {
 *      query: // value for 'query'
 *   },
 * });
 */
export function useSearchBacenInstitutionsAutocompleteQuery(baseOptions: ApolloReactHooks.QueryHookOptions<SearchBacenInstitutionsAutocompleteQuery, SearchBacenInstitutionsAutocompleteQueryVariables> & ({ variables: SearchBacenInstitutionsAutocompleteQueryVariables; skip?: boolean; } | { skip: boolean; }) ) {
        const options = {...defaultOptions, ...baseOptions}
        return ApolloReactHooks.useQuery<SearchBacenInstitutionsAutocompleteQuery, SearchBacenInstitutionsAutocompleteQueryVariables>(SearchBacenInstitutionsAutocompleteDocument, options);
      }
export function useSearchBacenInstitutionsAutocompleteLazyQuery(baseOptions?: ApolloReactHooks.LazyQueryHookOptions<SearchBacenInstitutionsAutocompleteQuery, SearchBacenInstitutionsAutocompleteQueryVariables>) {
          const options = {...defaultOptions, ...baseOptions}
          return ApolloReactHooks.useLazyQuery<SearchBacenInstitutionsAutocompleteQuery, SearchBacenInstitutionsAutocompleteQueryVariables>(SearchBacenInstitutionsAutocompleteDocument, options);
        }
export function useSearchBacenInstitutionsAutocompleteSuspenseQuery(baseOptions?: ApolloReactHooks.SkipToken | ApolloReactHooks.SuspenseQueryHookOptions<SearchBacenInstitutionsAutocompleteQuery, SearchBacenInstitutionsAutocompleteQueryVariables>) {
          const options = baseOptions === ApolloReactHooks.skipToken ? baseOptions : {...defaultOptions, ...baseOptions}
          return ApolloReactHooks.useSuspenseQuery<SearchBacenInstitutionsAutocompleteQuery, SearchBacenInstitutionsAutocompleteQueryVariables>(SearchBacenInstitutionsAutocompleteDocument, options as ApolloReactHooks.SkipToken | ApolloReactHooks.useSuspenseQuery.Options<SearchBacenInstitutionsAutocompleteQueryVariables>);
        }
export type SearchBacenInstitutionsAutocompleteQueryHookResult = ReturnType<typeof useSearchBacenInstitutionsAutocompleteQuery>;
export type SearchBacenInstitutionsAutocompleteLazyQueryHookResult = ReturnType<typeof useSearchBacenInstitutionsAutocompleteLazyQuery>;
export type SearchBacenInstitutionsAutocompleteSuspenseQueryHookResult = ReturnType<typeof useSearchBacenInstitutionsAutocompleteSuspenseQuery>;
export type SearchBacenInstitutionsAutocompleteQueryResult = ApolloReactCommon.QueryResult<SearchBacenInstitutionsAutocompleteQuery, SearchBacenInstitutionsAutocompleteQueryVariables>;
export const BacenInstitutionsDocument = gql`
    query BacenInstitutions($query: String, $first: Int, $after: String) {
  bacenInstitutions(query: $query, first: $first, after: $after) {
    edges {
      node {
        id
        name
        logoAsset {
          publicUrl
        }
        profit
        equity
        yoyEquityGrowth
        basileiaIndex
        institutionType
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
 * __useBacenInstitutionsQuery__
 *
 * To run a query within a React component, call `useBacenInstitutionsQuery` and pass it any options that fit your needs.
 * When your component renders, `useBacenInstitutionsQuery` returns an object from Apollo Client that contains loading, error, and data properties
 * you can use to render your UI.
 *
 * @param baseOptions options that will be passed into the query, supported options are listed on: https://www.apollographql.com/docs/react/api/react-hooks/#options;
 *
 * @example
 * const { data, loading, error } = useBacenInstitutionsQuery({
 *   variables: {
 *      query: // value for 'query'
 *      first: // value for 'first'
 *      after: // value for 'after'
 *   },
 * });
 */
export function useBacenInstitutionsQuery(baseOptions?: ApolloReactHooks.QueryHookOptions<BacenInstitutionsQuery, BacenInstitutionsQueryVariables>) {
        const options = {...defaultOptions, ...baseOptions}
        return ApolloReactHooks.useQuery<BacenInstitutionsQuery, BacenInstitutionsQueryVariables>(BacenInstitutionsDocument, options);
      }
export function useBacenInstitutionsLazyQuery(baseOptions?: ApolloReactHooks.LazyQueryHookOptions<BacenInstitutionsQuery, BacenInstitutionsQueryVariables>) {
          const options = {...defaultOptions, ...baseOptions}
          return ApolloReactHooks.useLazyQuery<BacenInstitutionsQuery, BacenInstitutionsQueryVariables>(BacenInstitutionsDocument, options);
        }
export function useBacenInstitutionsSuspenseQuery(baseOptions?: ApolloReactHooks.SkipToken | ApolloReactHooks.SuspenseQueryHookOptions<BacenInstitutionsQuery, BacenInstitutionsQueryVariables>) {
          const options = baseOptions === ApolloReactHooks.skipToken ? baseOptions : {...defaultOptions, ...baseOptions}
          return ApolloReactHooks.useSuspenseQuery<BacenInstitutionsQuery, BacenInstitutionsQueryVariables>(BacenInstitutionsDocument, options);
        }
export type BacenInstitutionsQueryHookResult = ReturnType<typeof useBacenInstitutionsQuery>;
export type BacenInstitutionsLazyQueryHookResult = ReturnType<typeof useBacenInstitutionsLazyQuery>;
export type BacenInstitutionsSuspenseQueryHookResult = ReturnType<typeof useBacenInstitutionsSuspenseQuery>;
export type BacenInstitutionsQueryResult = ApolloReactCommon.QueryResult<BacenInstitutionsQuery, BacenInstitutionsQueryVariables>;