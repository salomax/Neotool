import { gql } from '@apollo/client';

// Sign in mutation
export const SIGN_IN = gql`
  mutation SignIn($input: SignInInput!) {
    signIn(input: $input) {
      token
      refreshToken
      user {
        id
        email
        displayName
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
  }
`;

// Sign in with OAuth mutation
export const SIGN_IN_WITH_OAUTH = gql`
  mutation SignInWithOAuth($input: SignInWithOAuthInput!) {
    signInWithOAuth(input: $input) {
      token
      refreshToken
      user {
        id
        email
        displayName
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
  }
`;

// Sign up mutation
export const SIGN_UP = gql`
  mutation SignUp($input: SignUpInput!) {
    signUp(input: $input) {
      token
      refreshToken
      user {
        id
        email
        displayName
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
  }
`;

// Request password reset mutation
export const REQUEST_PASSWORD_RESET = gql`
  mutation RequestPasswordReset($input: RequestPasswordResetInput!) {
    requestPasswordReset(input: $input) {
      success
      message
    }
  }
`;

// Reset password mutation
export const RESET_PASSWORD = gql`
  mutation ResetPassword($input: ResetPasswordInput!) {
    resetPassword(input: $input) {
      success
      message
    }
  }
`;

// Refresh access token mutation
export const REFRESH_ACCESS_TOKEN = gql`
  mutation RefreshAccessToken($input: RefreshAccessTokenInput!) {
    refreshAccessToken(input: $input) {
      token
      user {
        id
        email
        displayName
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
  }
`;
