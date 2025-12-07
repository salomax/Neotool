import { gql } from '@apollo/client';

// Check permission query
export const CHECK_PERMISSION = gql`
  query CheckPermission($userId: ID!, $permission: String!, $resourceId: ID) {
    checkPermission(userId: $userId, permission: $permission, resourceId: $resourceId) {
      allowed
      reason
    }
  }
`;
