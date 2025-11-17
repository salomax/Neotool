import * as Types from '../../types/__generated__/graphql';

import { gql, MutationOptions } from '@apollo/client';
import * as ApolloReactCommon from '@apollo/client/react';
import * as ApolloReactHooks from '@apollo/client/react';
const defaultOptions = {} as const;

export type ChatMutationVariables = Types.Exact<{
  input: {
    sessionId: string;
    message: string;
  };
}>;

export type ChatMutation = {
  chat: {
    __typename: 'ChatResponse';
    response: string;
  };
};

export const ChatDocument = gql`
  mutation Chat($input: ChatInput!) {
    chat(input: $input) {
      response
    }
  }
`;

/**
 * __useChatMutation__
 *
 * To run a mutation, you first call `useChatMutation` within a React component and pass it any options that fit your needs.
 * When your component renders, `useChatMutation` returns a tuple that includes:
 * - A mutate function that you can call at any time to execute the mutation
 * - An object with fields that represent the current status of the mutation's execution
 *
 * @param baseOptions options that will be passed into the mutation, supported options are listed on: https://www.apollographql.com/docs/react/api/react-hooks/#options-2;
 *
 * @example
 * const [chatMutation, { data, loading, error }] = useChatMutation({
 *   variables: {
 *      input: {
 *        sessionId: "session-123",
 *        message: "Hello"
 *      }
 *   },
 * });
 */
export function useChatMutation(baseOptions?: ApolloReactHooks.MutationHookOptions<ChatMutation, ChatMutationVariables>) {
  const options = {...defaultOptions, ...baseOptions}
  return ApolloReactHooks.useMutation<ChatMutation, ChatMutationVariables>(ChatDocument, options);
}

export type ChatMutationHookResult = ReturnType<typeof useChatMutation>;
export type ChatMutationResult = ApolloReactCommon.MutationResult<ChatMutation>;
export type ChatMutationOptions = MutationOptions<ChatMutation, ChatMutationVariables>;

