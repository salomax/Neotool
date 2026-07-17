import { gql } from "@apollo/client";

export const ACCOUNT_MEMBERS = gql`
  query AccountMembers($accountId: ID!) {
    accountMembers(accountId: $accountId) {
      membershipId
      accountId
      userId
      email
      displayName
      avatarUrl
      role
      status
      isDefault
      isCurrentUser
    }
  }
`;

export const INVITE_MEMBER = gql`
  mutation InviteMember($input: InviteMemberInput!) {
    inviteMember(input: $input) {
      membershipId
      accountId
      userId
      email
      displayName
      avatarUrl
      role
      status
      isDefault
      isCurrentUser
    }
  }
`;

export const CANCEL_INVITATION = gql`
  mutation CancelInvitation($accountId: ID!, $membershipId: ID!) {
    cancelInvitation(accountId: $accountId, membershipId: $membershipId)
  }
`;

export const ACCEPT_INVITATION = gql`
  mutation AcceptInvitation($token: String!) {
    acceptInvitation(token: $token) {
      membershipId
      accountId
      accountName
    }
  }
`;

export const DECLINE_INVITATION = gql`
  mutation DeclineInvitation($token: String!) {
    declineInvitation(token: $token)
  }
`;

export const REMOVE_MEMBER = gql`
  mutation RemoveMember($accountId: ID!, $membershipId: ID!) {
    removeMember(accountId: $accountId, membershipId: $membershipId)
  }
`;

export const CHANGE_MEMBER_ROLE = gql`
  mutation ChangeMemberRole($accountId: ID!, $membershipId: ID!, $role: AccountRole!) {
    changeMemberRole(accountId: $accountId, membershipId: $membershipId, role: $role) {
      membershipId
      accountId
      userId
      email
      displayName
      avatarUrl
      role
      status
      isDefault
      isCurrentUser
    }
  }
`;

export const LEAVE_ACCOUNT = gql`
  mutation LeaveAccount($accountId: ID!) {
    leaveAccount(accountId: $accountId)
  }
`;
