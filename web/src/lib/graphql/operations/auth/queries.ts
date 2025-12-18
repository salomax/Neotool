import { gql } from '@apollo/client';

// Get current user query
export const CURRENT_USER = gql`
  query CurrentUser {
    currentUser {
      id
      email
      displayName
      avatarUrl
      roles {
        id
        name
      }
      permissions {
        id
        name
      }
    }
  }
`;

