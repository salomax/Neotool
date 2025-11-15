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
      }
    }
  }
`;

