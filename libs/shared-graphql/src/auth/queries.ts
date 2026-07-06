import { graphql } from '../generated';

export const CurrentUserDocument = graphql(`
  query CurrentUser {
    currentUser {
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
`);
