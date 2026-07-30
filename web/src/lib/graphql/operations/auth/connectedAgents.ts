import { gql } from "@apollo/client";

export const CONNECTED_AGENTS = gql`
  query ConnectedAgents($accountId: ID!, $recentActivityFirst: Int = 3) {
    connectedAgents(accountId: $accountId) {
      connectionId
      clientId
      clientName
      connectedAt
      lastGrantedAt
      scopes {
        name
        description
      }
      recentActivity(first: $recentActivityFirst) {
        id
        occurredAt
        toolName
        outcome
        errorCode
        errorMessage
        latencyMs
      }
    }
  }
`;

export const REVOKE_CONNECTED_AGENT = gql`
  mutation RevokeConnectedAgent($connectionId: ID!) {
    revokeConnectedAgent(connectionId: $connectionId) {
      connectionId
      clientId
      accountId
      revokedAt
    }
  }
`;
