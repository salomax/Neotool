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
      }
    }
  }
`;

