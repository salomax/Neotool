import { gql } from "@apollo/client";

/** Minimal fields for header badge. accountId is implicit from the auth token. */
export const GET_PENDING_ACTIONS_COUNT = gql`
  query PendingActionsCount($status: PendingActionStatus) {
    pendingActions(status: $status) {
      id
      severity
      __typename
    }
  }
`;

export const GET_PENDING_ACTIONS = gql`
  query PendingActions($status: PendingActionStatus) {
    pendingActions(status: $status) {
      id
      type
      severity
      status
      entityType
      entityId
      createdAt
      expiresAt
      payload
      relatedHolding {
        id
        financialAccountId
        quantity
        averageCost
        couponFrequency
        issueDate
        maturityDate
        isTaxExempt
        grossValue
        netValue
        incomeTax
        iof
        accruedInterest
        asset {
          id
          name
          assetType
          issuerName
          logoAsset {
            publicUrl
          }
        }
      }
    }
  }
`;

export const CONFIRM_HOLDING_MATURITY = gql`
  mutation ConfirmHoldingMaturity($command: ConfirmHoldingMaturityInput!) {
    confirmHoldingMaturity(command: $command) {
      maturityTransaction {
        id
        type
        grossValue
        tradeDate
      }
      bankingTransaction {
        id
        amount
        date
        type
      }
      pendingAction {
        id
        status
      }
    }
  }
`;

export const CONFIRM_COUPON_PAYMENT = gql`
  mutation ConfirmCouponPayment($input: ConfirmCouponPaymentInput!) {
    confirmCouponPayment(input: $input) {
      couponTransaction {
        id
        type
        grossValue
        netValue
        incomeTax
        tradeDate
      }
      bankingTransaction {
        id
        amount
        date
        type
      }
      pendingAction {
        id
        status
      }
    }
  }
`;

export const DISMISS_PENDING_ACTION = gql`
  mutation DismissPendingAction($id: ID!) {
    dismissPendingAction(id: $id) {
      id
      status
    }
  }
`;

export const CONFIRM_PENDING_ACTION = gql`
  mutation ConfirmPendingAction($id: ID!) {
    confirmPendingAction(id: $id) {
      pendingAction {
        id
        status
      }
    }
  }
`;
