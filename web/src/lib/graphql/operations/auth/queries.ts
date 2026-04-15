import { gql } from '@apollo/client';

// Get current user query
export const CURRENT_USER = gql`
  query CurrentUser {
    currentUser {
      id
      email
      displayName
      avatarUrl
      avatarProvider
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

export const USER_PREFERENCES = gql`
  query UserPreferences {
    userPreferences {
      language
      theme
      blurBalances
      firstDayOfWeek
      keepMeConnected
    }
  }
`;

export const MY_ACCOUNTS = gql`
  query MyAccounts {
    myAccounts {
      id
      accountName
      accountType
      accountStatus
      isOwner
    }
  }
`;
