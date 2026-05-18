import { gql } from "@apollo/client";

export const RECURRING_PAYMENT_FIELDS = gql`
  fragment RecurringPaymentFields on RecurringPayment {
    id
    financialAccountId
    name
    creditDebitType
    type
    expectedAmount
    currencyCode
    frequency
    dayOfMonth
    intervalDays
    remindDaysBefore
    status
    nextDueDate
    lastPaidDate
    notes
    category {
      id
      name
      color
      icon
    }
    merchant {
      id
      name
      logoAsset {
        publicUrl
      }
    }
  }
`;

export const RECURRING_PAYMENTS = gql`
  query RecurringPayments($financialAccountId: ID) {
    recurringPayments(financialAccountId: $financialAccountId) {
      ...RecurringPaymentFields
    }
  }
  ${RECURRING_PAYMENT_FIELDS}
`;

export const CREATE_RECURRING_PAYMENT = gql`
  mutation CreateRecurringPayment($input: CreateRecurringPaymentInput!) {
    createRecurringPayment(input: $input) {
      ...RecurringPaymentFields
    }
  }
  ${RECURRING_PAYMENT_FIELDS}
`;

export const UPDATE_RECURRING_PAYMENT = gql`
  mutation UpdateRecurringPayment($id: ID!, $input: UpdateRecurringPaymentInput!) {
    updateRecurringPayment(id: $id, input: $input) {
      ...RecurringPaymentFields
    }
  }
  ${RECURRING_PAYMENT_FIELDS}
`;

export const PAUSE_RECURRING_PAYMENT = gql`
  mutation PauseRecurringPayment($id: ID!) {
    pauseRecurringPayment(id: $id) {
      id
      status
    }
  }
`;

export const RESUME_RECURRING_PAYMENT = gql`
  mutation ResumeRecurringPayment($id: ID!) {
    resumeRecurringPayment(id: $id) {
      id
      status
    }
  }
`;

export const DELETE_RECURRING_PAYMENT = gql`
  mutation DeleteRecurringPayment($id: ID!) {
    deleteRecurringPayment(id: $id)
  }
`;

export const CONFIRM_RECURRING_PAYMENT = gql`
  mutation ConfirmRecurringPayment($id: ID!, $paymentDate: Date!) {
    confirmRecurringPayment(id: $id, paymentDate: $paymentDate) {
      id
      status
      nextDueDate
      lastPaidDate
    }
  }
`;

export const CREATE_RECURRING_PAYMENT_FROM_TRANSACTION = gql`
  mutation CreateRecurringPaymentFromTransaction($transactionId: ID!, $input: CreateRecurringPaymentFromTransactionInput!) {
    createRecurringPaymentFromTransaction(transactionId: $transactionId, input: $input) {
      ...RecurringPaymentFields
    }
  }
  ${RECURRING_PAYMENT_FIELDS}
`;

export const SUGGEST_RECURRING_FOR_TRANSACTION = gql`
  query SuggestRecurringForTransaction($transactionId: ID!) {
    suggestRecurringForTransaction(transactionId: $transactionId) {
      id
      name
      nextDueDate
      expectedAmount
      currencyCode
    }
  }
`;

export const LINK_TRANSACTION_TO_RECURRING = gql`
  mutation LinkTransactionToRecurring($transactionId: ID!, $recurringPaymentId: ID!) {
    linkTransactionToRecurring(transactionId: $transactionId, recurringPaymentId: $recurringPaymentId) {
      id
    }
  }
`;
