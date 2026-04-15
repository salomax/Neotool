import { CategorySource } from '@/lib/graphql/types/__generated__/graphql';

// ── Constants ────────────────────────────────────────────────────────────────

export const MAX_FILE_BYTES = 3 * 1024 * 1024; // 3 MB
export const ACCEPTED_EXTENSIONS = ['.ofx', '.csv', '.xlsx', '.xls', '.pdf'] as const;
export const ACCEPTED_TYPES =
  'application/x-ofx,application/ofx,.ofx,text/csv,.csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,.xlsx,application/vnd.ms-excel,.xls,application/pdf,.pdf';
export const POLL_INTERVAL_MS = 2000;

export const CardSubtypeValues = [
  'CREDIT_CARD',
  'CHARGE_CARD',
  'PREPAID_CARD',
  'VOUCHER_CARD',
] as const;

export const CardNetworkValues = [
  'VISA',
  'MASTERCARD',
  'ELO',
  'AMERICAN_EXPRESS',
  'HIPERCARD',
  'DINERS_CLUB',
  'BANDEIRA_PROPRIA',
  'OUTRAS',
] as const;

export const CardLevelValues = [
  'STANDARD',
  'GOLD',
  'PLATINUM',
  'INFINITE',
  'BLACK',
] as const;

export const INITIAL_SHARED_CARD_TEMPLATE: SharedCardTemplate = {
  issuerId: '',
  issuerName: '',
  subtype: 'CREDIT_CARD',
  cardNetwork: '',
  cardLevel: '',
  closingDay: '',
  dueDay: '',
  creditLimit: '',
  descriptionBase: '',
};

// ── Step & flow types ────────────────────────────────────────────────────────

export type BillStep = 'upload' | 'processing' | 'card-matching' | 'tx-review' | 'result' | 'error';
export type TxCategorySource = CategorySource | null;

// ── Card matching types ──────────────────────────────────────────────────────

export interface CardResolution {
  reviewCardId: string;
  financialAccountId: string | null;
}

export interface CardAccountOption {
  id: string;
  name: string;
  issuerId: string | null;
  issuerName: string | null;
  issuerKey: string | null;
  maskedNumber: string | null;
}

export interface DraftCardResolution {
  draftCardId: string;
  lastFourDigits: string;
  financialAccountId: string | null;
  allowMissingLastFour: boolean;
}

export interface DraftBucketSummary {
  draftCardId: string;
  label: string;
  count: number;
  total: number;
}

export interface SharedCardTemplate {
  issuerId: string;
  issuerName: string;
  subtype: string;
  cardNetwork: string;
  cardLevel: string;
  closingDay: string;
  dueDay: string;
  creditLimit: string;
  descriptionBase: string;
}

export interface BillDateOverrides {
  closingDate: string;
  dueDate: string;
}

// ── Transaction selection types ──────────────────────────────────────────────

/** Key: `${reviewCardId}-${txIndex}` */
export type TxSelections = Record<string, boolean>;

export type TxCategorySelections = Record<
  string,
  { categoryId: string | null; categorySource: TxCategorySource }
>;

export type FlatTxAssignments = Record<number, string | null>;
export type FlatTxSelections = Record<number, boolean>;
export type FlatTxInclusions = Record<number, boolean>;

// ── Review payload types ─────────────────────────────────────────────────────

export interface ReviewTransaction {
  date?: string;
  description: string;
  amount: number;
  creditDebitType: string;
  isBillPayment?: boolean;
  installment?: { current: number; total: number };
  categoryId?: string | null;
  categorySource?: TxCategorySource;
}

export interface ReviewCard {
  reviewCardId: string;
  lastFourDigits?: string | null;
  holderName?: string;
  computedTotal: number;
  declaredTotal: number;
  totalMatchesDeclared: boolean;
  matchedFinancialAccountId?: string;
  matchAmbiguous: boolean;
  closingDate?: string;
  dueDate?: string;
  matchCandidates: Array<{ id: string; name: string }>;
  transactions: ReviewTransaction[];
}

export interface ReviewCardForTxReview {
  reviewCardId: string;
  lastFourDigits?: string | null;
  financialAccountId: string | null;
  transactions: Array<ReviewTransaction & { __sourceTxIndex?: number }>;
}
