import * as Types from '../../types/__generated__/graphql';

import { gql } from '@apollo/client';
import * as ApolloReactCommon from '@apollo/client/react';
import * as ApolloReactHooks from '@apollo/client/react';
const defaultOptions = {} as const;
export type SearchBacenInstitutionsAutocompleteQueryVariables = Types.Exact<{
  query: Types.Scalars['String']['input'];
}>;


export type SearchBacenInstitutionsAutocompleteQuery = { searchBacenInstitutionsAutocomplete: { __typename: 'BacenInstitutionAutocompleteConnection', suggestions: Array<{ __typename: 'BacenInstitutionAutocompleteItem', name: string }> } };

export type BacenInstitutionReportDetailsQueryVariables = Types.Exact<{
  id: Types.Scalars['ID']['input'];
  reportId: Types.Scalars['ID']['input'];
  range: Types.QuarterRangeInput;
}>;


export type BacenInstitutionReportDetailsQuery = { bacenFinanceInstitution: { __typename: 'BacenFinanceInstitution', id: string, reportDetails: { __typename: 'ReportDetails', reportId: string, name: string | null, accounts: Array<{ __typename: 'ReportAccount', group: string | null, accountNumber: string | null, columnName: string | null, valuesByQuarter: Array<{ __typename: 'QuarterValue', quarter: string, value: number | null }> }> } } | null };

export type BacenInstitutionsQueryVariables = Types.Exact<{
  query?: Types.InputMaybe<Types.Scalars['String']['input']>;
  first?: Types.InputMaybe<Types.Scalars['Int']['input']>;
  after?: Types.InputMaybe<Types.Scalars['String']['input']>;
}>;


export type BacenInstitutionsQuery = { bacenInstitutions: { __typename: 'BacenInstitutionConnection', totalCount: number | null, edges: Array<{ __typename: 'BacenInstitutionEdge', cursor: string, node: { __typename: 'BacenInstitutionSummaryItem', id: string, name: string, profit: number | null, equity: number | null, yoyEquityGrowth: number | null, basileiaIndex: number | null, institutionType: Types.BacenInstitutionType | null, logoAsset: { __typename: 'Asset', publicUrl: string | null } | null } }>, pageInfo: { __typename: 'PageInfo', hasNextPage: boolean, hasPreviousPage: boolean, startCursor: string | null, endCursor: string | null } } };

export type BacenInstitutionQueryVariables = Types.Exact<{
  id: Types.Scalars['ID']['input'];
  anoMes?: Types.InputMaybe<Types.Scalars['String']['input']>;
}>;


export type BacenInstitutionQuery = { bacenFinanceInstitution: { __typename: 'BacenFinanceInstitution', id: string, quarter: string | null, name: string, legalName: string | null, tradeName: string | null, institutionType: Types.BacenInstitutionType | null, controlType: Types.BacenControlType | null, institutionBankType: Types.BacenInstitutionBankType | null, tipoConsolidadoBancario: Types.BacenInstitutionBankType | null, website: string | null, socialNetworks: Array<string> | null, mainActivity: string | null, address: string | null, founded: string | null, rwaReferenceEquity: number | null, logoAsset: { __typename: 'Asset', id: string, publicUrl: string | null } | null, asset: { __typename: 'Asset', publicUrl: string | null } | null } | null };

export type BacenInstitutionReportsQueryVariables = Types.Exact<{
  id: Types.Scalars['ID']['input'];
  anoMes?: Types.InputMaybe<Types.Scalars['String']['input']>;
  range: Types.QuarterRangeInput;
}>;


export type BacenInstitutionReportsQuery = { bacenFinanceInstitution: { __typename: 'BacenFinanceInstitution', id: string, name: string, reportsSummary: { __typename: 'ReportsAvailability', quarters: Array<{ __typename: 'QuarterReports', quarter: string, reports: Array<{ __typename: 'ReportDescriptor', reportId: string, name: string | null }> }> } } | null };


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
export const BacenInstitutionReportDetailsDocument = gql`
    query BacenInstitutionReportDetails($id: ID!, $reportId: ID!, $range: QuarterRangeInput!) {
  bacenFinanceInstitution(id: $id) {
    id
    reportDetails(reportId: $reportId, range: $range) {
      reportId
      name
      accounts {
        group
        accountNumber
        columnName
        valuesByQuarter {
          quarter
          value
        }
      }
    }
  }
}
    `;

/**
 * __useBacenInstitutionReportDetailsQuery__
 *
 * To run a query within a React component, call `useBacenInstitutionReportDetailsQuery` and pass it any options that fit your needs.
 * When your component renders, `useBacenInstitutionReportDetailsQuery` returns an object from Apollo Client that contains loading, error, and data properties
 * you can use to render your UI.
 *
 * @param baseOptions options that will be passed into the query, supported options are listed on: https://www.apollographql.com/docs/react/api/react-hooks/#options;
 *
 * @example
 * const { data, loading, error } = useBacenInstitutionReportDetailsQuery({
 *   variables: {
 *      id: // value for 'id'
 *      reportId: // value for 'reportId'
 *      range: // value for 'range'
 *   },
 * });
 */
export function useBacenInstitutionReportDetailsQuery(baseOptions: ApolloReactHooks.QueryHookOptions<BacenInstitutionReportDetailsQuery, BacenInstitutionReportDetailsQueryVariables> & ({ variables: BacenInstitutionReportDetailsQueryVariables; skip?: boolean; } | { skip: boolean; }) ) {
        const options = {...defaultOptions, ...baseOptions}
        return ApolloReactHooks.useQuery<BacenInstitutionReportDetailsQuery, BacenInstitutionReportDetailsQueryVariables>(BacenInstitutionReportDetailsDocument, options);
      }
export function useBacenInstitutionReportDetailsLazyQuery(baseOptions?: ApolloReactHooks.LazyQueryHookOptions<BacenInstitutionReportDetailsQuery, BacenInstitutionReportDetailsQueryVariables>) {
          const options = {...defaultOptions, ...baseOptions}
          return ApolloReactHooks.useLazyQuery<BacenInstitutionReportDetailsQuery, BacenInstitutionReportDetailsQueryVariables>(BacenInstitutionReportDetailsDocument, options);
        }
export function useBacenInstitutionReportDetailsSuspenseQuery(baseOptions?: ApolloReactHooks.SkipToken | ApolloReactHooks.SuspenseQueryHookOptions<BacenInstitutionReportDetailsQuery, BacenInstitutionReportDetailsQueryVariables>) {
          const options = baseOptions === ApolloReactHooks.skipToken ? baseOptions : {...defaultOptions, ...baseOptions}
          return ApolloReactHooks.useSuspenseQuery<BacenInstitutionReportDetailsQuery, BacenInstitutionReportDetailsQueryVariables>(BacenInstitutionReportDetailsDocument, options as ApolloReactHooks.SkipToken | ApolloReactHooks.useSuspenseQuery.Options<BacenInstitutionReportDetailsQueryVariables>);
        }
export type BacenInstitutionReportDetailsQueryHookResult = ReturnType<typeof useBacenInstitutionReportDetailsQuery>;
export type BacenInstitutionReportDetailsLazyQueryHookResult = ReturnType<typeof useBacenInstitutionReportDetailsLazyQuery>;
export type BacenInstitutionReportDetailsSuspenseQueryHookResult = ReturnType<typeof useBacenInstitutionReportDetailsSuspenseQuery>;
export type BacenInstitutionReportDetailsQueryResult = ApolloReactCommon.QueryResult<BacenInstitutionReportDetailsQuery, BacenInstitutionReportDetailsQueryVariables>;
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
export const BacenInstitutionDocument = gql`
    query BacenInstitution($id: ID!, $anoMes: String) {
  bacenFinanceInstitution(id: $id, anoMes: $anoMes) {
    id
    quarter
    name
    legalName
    tradeName
    logoAsset {
      id
      publicUrl
    }
    asset {
      publicUrl
    }
    institutionType
    controlType
    institutionBankType
    tipoConsolidadoBancario
    website
    socialNetworks
    mainActivity
    address
    founded
    rwaReferenceEquity
  }
}
    `;

/**
 * __useBacenInstitutionQuery__
 *
 * To run a query within a React component, call `useBacenInstitutionQuery` and pass it any options that fit your needs.
 * When your component renders, `useBacenInstitutionQuery` returns an object from Apollo Client that contains loading, error, and data properties
 * you can use to render your UI.
 *
 * @param baseOptions options that will be passed into the query, supported options are listed on: https://www.apollographql.com/docs/react/api/react-hooks/#options;
 *
 * @example
 * const { data, loading, error } = useBacenInstitutionQuery({
 *   variables: {
 *      id: // value for 'id'
 *      anoMes: // value for 'anoMes'
 *   },
 * });
 */
export function useBacenInstitutionQuery(baseOptions: ApolloReactHooks.QueryHookOptions<BacenInstitutionQuery, BacenInstitutionQueryVariables> & ({ variables: BacenInstitutionQueryVariables; skip?: boolean; } | { skip: boolean; }) ) {
        const options = {...defaultOptions, ...baseOptions}
        return ApolloReactHooks.useQuery<BacenInstitutionQuery, BacenInstitutionQueryVariables>(BacenInstitutionDocument, options);
      }
export function useBacenInstitutionLazyQuery(baseOptions?: ApolloReactHooks.LazyQueryHookOptions<BacenInstitutionQuery, BacenInstitutionQueryVariables>) {
          const options = {...defaultOptions, ...baseOptions}
          return ApolloReactHooks.useLazyQuery<BacenInstitutionQuery, BacenInstitutionQueryVariables>(BacenInstitutionDocument, options);
        }
export function useBacenInstitutionSuspenseQuery(baseOptions?: ApolloReactHooks.SkipToken | ApolloReactHooks.SuspenseQueryHookOptions<BacenInstitutionQuery, BacenInstitutionQueryVariables>) {
          const options = baseOptions === ApolloReactHooks.skipToken ? baseOptions : {...defaultOptions, ...baseOptions}
          return ApolloReactHooks.useSuspenseQuery<BacenInstitutionQuery, BacenInstitutionQueryVariables>(BacenInstitutionDocument, options as ApolloReactHooks.SkipToken | ApolloReactHooks.useSuspenseQuery.Options<BacenInstitutionQueryVariables>);
        }
export type BacenInstitutionQueryHookResult = ReturnType<typeof useBacenInstitutionQuery>;
export type BacenInstitutionLazyQueryHookResult = ReturnType<typeof useBacenInstitutionLazyQuery>;
export type BacenInstitutionSuspenseQueryHookResult = ReturnType<typeof useBacenInstitutionSuspenseQuery>;
export type BacenInstitutionQueryResult = ApolloReactCommon.QueryResult<BacenInstitutionQuery, BacenInstitutionQueryVariables>;
export const BacenInstitutionReportsDocument = gql`
    query BacenInstitutionReports($id: ID!, $anoMes: String, $range: QuarterRangeInput!) {
  bacenFinanceInstitution(id: $id, anoMes: $anoMes) {
    id
    name
    reportsSummary(range: $range) {
      quarters {
        quarter
        reports {
          reportId
          name
        }
      }
    }
  }
}
    `;

/**
 * __useBacenInstitutionReportsQuery__
 *
 * To run a query within a React component, call `useBacenInstitutionReportsQuery` and pass it any options that fit your needs.
 * When your component renders, `useBacenInstitutionReportsQuery` returns an object from Apollo Client that contains loading, error, and data properties
 * you can use to render your UI.
 *
 * @param baseOptions options that will be passed into the query, supported options are listed on: https://www.apollographql.com/docs/react/api/react-hooks/#options;
 *
 * @example
 * const { data, loading, error } = useBacenInstitutionReportsQuery({
 *   variables: {
 *      id: // value for 'id'
 *      anoMes: // value for 'anoMes'
 *      range: // value for 'range'
 *   },
 * });
 */
export function useBacenInstitutionReportsQuery(baseOptions: ApolloReactHooks.QueryHookOptions<BacenInstitutionReportsQuery, BacenInstitutionReportsQueryVariables> & ({ variables: BacenInstitutionReportsQueryVariables; skip?: boolean; } | { skip: boolean; }) ) {
        const options = {...defaultOptions, ...baseOptions}
        return ApolloReactHooks.useQuery<BacenInstitutionReportsQuery, BacenInstitutionReportsQueryVariables>(BacenInstitutionReportsDocument, options);
      }
export function useBacenInstitutionReportsLazyQuery(baseOptions?: ApolloReactHooks.LazyQueryHookOptions<BacenInstitutionReportsQuery, BacenInstitutionReportsQueryVariables>) {
          const options = {...defaultOptions, ...baseOptions}
          return ApolloReactHooks.useLazyQuery<BacenInstitutionReportsQuery, BacenInstitutionReportsQueryVariables>(BacenInstitutionReportsDocument, options);
        }
export function useBacenInstitutionReportsSuspenseQuery(baseOptions?: ApolloReactHooks.SkipToken | ApolloReactHooks.SuspenseQueryHookOptions<BacenInstitutionReportsQuery, BacenInstitutionReportsQueryVariables>) {
          const options = baseOptions === ApolloReactHooks.skipToken ? baseOptions : {...defaultOptions, ...baseOptions}
          return ApolloReactHooks.useSuspenseQuery<BacenInstitutionReportsQuery, BacenInstitutionReportsQueryVariables>(BacenInstitutionReportsDocument, options as ApolloReactHooks.SkipToken | ApolloReactHooks.useSuspenseQuery.Options<BacenInstitutionReportsQueryVariables>);
        }
export type BacenInstitutionReportsQueryHookResult = ReturnType<typeof useBacenInstitutionReportsQuery>;
export type BacenInstitutionReportsLazyQueryHookResult = ReturnType<typeof useBacenInstitutionReportsLazyQuery>;
export type BacenInstitutionReportsSuspenseQueryHookResult = ReturnType<typeof useBacenInstitutionReportsSuspenseQuery>;
export type BacenInstitutionReportsQueryResult = ApolloReactCommon.QueryResult<BacenInstitutionReportsQuery, BacenInstitutionReportsQueryVariables>;