import { graphql } from "../generated";

export const SignInDocument = graphql(`
  mutation SignIn($input: SignInInput!) {
    signIn(input: $input) {
      token
      refreshToken
      user {
        id
        email
        displayName
        avatarUrl
        emailVerified
        roles {
          id
          name
        }
      }
    }
  }
`);

export const SignUpDocument = graphql(`
  mutation SignUp($input: SignUpInput!) {
    signUp(input: $input) {
      token
      refreshToken
      requiresVerification
      user {
        id
        email
        displayName
        avatarUrl
        emailVerified
        roles {
          id
          name
        }
      }
    }
  }
`);

export const RefreshAccessTokenDocument = graphql(`
  mutation RefreshAccessToken($input: RefreshAccessTokenInput!) {
    refreshAccessToken(input: $input) {
      token
      refreshToken
      user {
        id
        email
        displayName
        avatarUrl
        emailVerified
        roles {
          id
          name
        }
      }
    }
  }
`);

export const RequestPasswordResetDocument = graphql(`
  mutation RequestPasswordReset($input: RequestPasswordResetInput!) {
    requestPasswordReset(input: $input) {
      success
      message
    }
  }
`);

export const ResetPasswordDocument = graphql(`
  mutation ResetPassword($input: ResetPasswordInput!) {
    resetPassword(input: $input) {
      success
      message
    }
  }
`);
