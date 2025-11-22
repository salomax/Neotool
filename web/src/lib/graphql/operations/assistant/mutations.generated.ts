// Temporary stub file - Assistant service not yet integrated into supergraph
// This file will be auto-generated once the assistant subgraph is added to the supergraph
// TODO: Remove this stub and regenerate when assistant service is integrated

import * as Types from '../../types/__generated__/graphql';
import { gql } from '@apollo/client';
import * as ApolloReactCommon from '@apollo/client/react';
import * as ApolloReactHooks from '@apollo/client/react';

// Stub types - will be replaced by codegen
export type ChatInput = {
  sessionId: string;
  message: string;
};

export type ChatResponse = {
  __typename: 'ChatResponse';
  sessionId: string;
  response: string;
};

export type ChatMutationVariables = Types.Exact<{
  input: ChatInput;
}>;

export type ChatMutation = {
  __typename: 'Mutation';
  chat: ChatResponse;
};

// Stub document - will be replaced by codegen
export const ChatDocument = gql`
  mutation Chat($input: ChatInput!) {
    chat(input: $input) {
      sessionId
      response
    }
  }
`;

// Stub hook - will be replaced by codegen
export function useChatMutation(
  baseOptions?: ApolloReactHooks.MutationHookOptions<ChatMutation, ChatMutationVariables>
) {
  return ApolloReactHooks.useMutation<ChatMutation, ChatMutationVariables>(ChatDocument, baseOptions);
}

export type ChatMutationHookResult = ReturnType<typeof useChatMutation>;
export type ChatMutationResult = ApolloReactCommon.MutationResult<ChatMutation>;

