export type Maybe<T> = T | null;
export type InputMaybe<T> = T | null;
export type Exact<T extends { [key: string]: unknown }> = { [K in keyof T]: T[K] };
export type MakeOptional<T, K extends keyof T> = Omit<T, K> & { [SubKey in K]?: Maybe<T[SubKey]> };
export type MakeMaybe<T, K extends keyof T> = Omit<T, K> & { [SubKey in K]: Maybe<T[SubKey]> };
export type MakeEmpty<T extends { [key: string]: unknown }, K extends keyof T> = { [_ in K]?: never };
export type Incremental<T> = T | { [P in keyof T]?: P extends ' $fragmentName' | '__typename' ? T[P] : never };
/** All built-in and custom scalars, mapped to their actual values */
export type Scalars = {
  ID: { input: string; output: string; }
  String: { input: string; output: string; }
  Boolean: { input: boolean; output: boolean; }
  Int: { input: number; output: number; }
  Float: { input: number; output: number; }
  Date: { input: unknown; output: unknown; }
  DateTime: { input: unknown; output: unknown; }
  JSON: { input: unknown; output: unknown; }
  Long: { input: unknown; output: unknown; }
  UUID: { input: unknown; output: unknown; }
  join__FieldSet: { input: unknown; output: unknown; }
  link__Import: { input: unknown; output: unknown; }
};

/** ANBIMA fund category. Required for correct tax treatment (come-cotas schedule). */
export enum AnbimaCategory {
  Acoes = 'ACOES',
  Cambial = 'CAMBIAL',
  Multimercado = 'MULTIMERCADO',
  RendaFixa = 'RENDA_FIXA'
}

/** Asset metadata and access information. */
export type Asset = {
  __typename: 'Asset';
  /** SHA-256 checksum for integrity verification */
  checksum: Maybe<Scalars['String']['output']>;
  /** Timestamp when asset record was created */
  createdAt: Scalars['DateTime']['output'];
  /** Timestamp when asset was deleted (null if active) */
  deletedAt: Maybe<Scalars['DateTime']['output']>;
  /**
   * Presigned download URL for PRIVATE assets.
   * Returns null for PUBLIC assets (use publicUrl instead).
   * @param ttlSeconds Time-to-live for the presigned URL (default: 3600 seconds / 1 hour)
   */
  downloadUrl: Maybe<Scalars['String']['output']>;
  /** Unique identifier (UUID v7) */
  id: Scalars['ID']['output'];
  /** Client-provided key to prevent duplicate uploads (optional) */
  idempotencyKey: Maybe<Scalars['String']['output']>;
  /** MIME type (e.g., image/jpeg, application/pdf) */
  mimeType: Scalars['String']['output'];
  /** Logical grouping (e.g., user-profiles, group-assets) */
  namespace: Scalars['String']['output'];
  /** Original filename from client upload */
  originalFilename: Maybe<Scalars['String']['output']>;
  /** User or system that owns this asset */
  ownerId: Scalars['String']['output'];
  /**
   * Public CDN URL for accessing the asset.
   * Only available for READY PUBLIC assets. Returns null for PRIVATE assets (use downloadUrl instead).
   */
  publicUrl: Maybe<Scalars['String']['output']>;
  /** File size in bytes (null until upload confirmed) */
  sizeBytes: Maybe<Scalars['Long']['output']>;
  /** Upload status */
  status: AssetStatus;
  /** Storage bucket name */
  storageBucket: Scalars['String']['output'];
  /** Unique key in S3/R2 storage */
  storageKey: Scalars['String']['output'];
  /** Storage region */
  storageRegion: Scalars['String']['output'];
  /** Timestamp when asset record was last updated */
  updatedAt: Scalars['DateTime']['output'];
  /** When the upload URL expires */
  uploadExpiresAt: Maybe<Scalars['DateTime']['output']>;
  /**
   * Pre-signed upload URL (temporary, only available for PENDING assets).
   * Client uploads file directly to this URL.
   */
  uploadUrl: Maybe<Scalars['String']['output']>;
  /** Visibility level - determines access control and URL generation */
  visibility: AssetVisibility;
};


/** Asset metadata and access information. */
export type AssetDownloadUrlArgs = {
  ttlSeconds?: InputMaybe<Scalars['Int']['input']>;
};

export type AssetDailyPrice = {
  __typename: 'AssetDailyPrice';
  avgPrice: Maybe<Scalars['Float']['output']>;
  closePrice: Scalars['Float']['output'];
  date: Scalars['Date']['output'];
  highPrice: Maybe<Scalars['Float']['output']>;
  lowPrice: Maybe<Scalars['Float']['output']>;
  openPrice: Maybe<Scalars['Float']['output']>;
  trades: Maybe<Scalars['Int']['output']>;
  volume: Maybe<Scalars['Float']['output']>;
};

/** Asset upload lifecycle status. */
export enum AssetStatus {
  /** Asset deleted */
  Deleted = 'DELETED',
  /** Upload failed or expired */
  Failed = 'FAILED',
  /** Upload URL generated, waiting for client upload */
  Pending = 'PENDING',
  /** File uploaded and confirmed, ready for use */
  Ready = 'READY'
}

/** Asset class (classe de ativo). Drives pricing model and tax treatment. */
export enum AssetType {
  AgribusinessFund = 'AGRIBUSINESS_FUND',
  Bdr = 'BDR',
  Cdb = 'CDB',
  Coe = 'COE',
  Cra = 'CRA',
  Cri = 'CRI',
  Crypto = 'CRYPTO',
  Debentures = 'DEBENTURES',
  Derivatives = 'DERIVATIVES',
  Etf = 'ETF',
  EtfFixedIncome = 'ETF_FIXED_INCOME',
  EtfInternational = 'ETF_INTERNATIONAL',
  Lc = 'LC',
  Lca = 'LCA',
  Lci = 'LCI',
  Lf = 'LF',
  Lig = 'LIG',
  MutualFund = 'MUTUAL_FUND',
  Option = 'OPTION',
  Other = 'OTHER',
  Pgbl = 'PGBL',
  Rdb = 'RDB',
  RealEstateFund = 'REAL_ESTATE_FUND',
  Stock = 'STOCK',
  StockRights = 'STOCK_RIGHTS',
  Treasury = 'TREASURY',
  Vgbl = 'VGBL'
}

/** Asset visibility level - determines access control and URL generation. */
export enum AssetVisibility {
  /** Private asset - requires ownership check, uses presigned download URLs */
  Private = 'PRIVATE',
  /** Public asset - accessible without owner check, uses stable CDN URLs */
  Public = 'PUBLIC'
}

export type AttachFileToHoldingInput = {
  /** Asset ID returned by confirmAssetUpload (must be READY status). */
  assetId: Scalars['ID']['input'];
  holdingId: Scalars['ID']['input'];
};

export type AttachFileToTransactionInput = {
  /** Asset ID returned by confirmAssetUpload (must be READY status). */
  assetId: Scalars['ID']['input'];
  transactionId: Scalars['ID']['input'];
};

export type AuthorizationResult = {
  __typename: 'AuthorizationResult';
  allowed: Scalars['Boolean']['output'];
  reason: Scalars['String']['output'];
};

export enum B3AssetType {
  Equity = 'EQUITY',
  Fii = 'FII',
  OptionCall = 'OPTION_CALL',
  OptionPut = 'OPTION_PUT'
}

export enum BacenControlType {
  PrivateDomestic = 'PRIVATE_DOMESTIC',
  PrivateForeignControl = 'PRIVATE_FOREIGN_CONTROL',
  Public = 'PUBLIC'
}

export type BacenFinanceInstitution = {
  __typename: 'BacenFinanceInstitution';
  address: Maybe<Scalars['String']['output']>;
  asset: Maybe<Asset>;
  controlType: Maybe<BacenControlType>;
  dataRestriction: Maybe<DataRestriction>;
  founded: Maybe<Scalars['String']['output']>;
  id: Scalars['ID']['output'];
  institutionBankType: Maybe<BacenInstitutionBankType>;
  institutionType: Maybe<BacenInstitutionType>;
  legalName: Maybe<Scalars['String']['output']>;
  logoAsset: Maybe<Asset>;
  mainActivity: Maybe<Scalars['String']['output']>;
  name: Scalars['String']['output'];
  quarter: Maybe<Scalars['String']['output']>;
  /** Historical rating changes for this institution */
  ratingHistory: Maybe<Array<RatingHistory>>;
  /** Current ratings from all issuers for this institution */
  ratings: Maybe<Array<InstitutionRating>>;
  reportDetails: ReportDetails;
  reportsSummary: ReportsAvailability;
  rwaReferenceEquity: Maybe<Scalars['Float']['output']>;
  socialNetworks: Maybe<Array<Scalars['String']['output']>>;
  tipoConsolidadoBancario: Maybe<BacenInstitutionBankType>;
  tradeName: Maybe<Scalars['String']['output']>;
  updatedAt: Maybe<Scalars['DateTime']['output']>;
  website: Maybe<Scalars['String']['output']>;
};


export type BacenFinanceInstitutionRatingHistoryArgs = {
  limit?: InputMaybe<Scalars['Int']['input']>;
};


export type BacenFinanceInstitutionReportDetailsArgs = {
  range: QuarterRangeInput;
  reportId: Scalars['ID']['input'];
};


export type BacenFinanceInstitutionReportsSummaryArgs = {
  range: QuarterRangeInput;
};

export type BacenInstitutionAutocompleteConnection = {
  __typename: 'BacenInstitutionAutocompleteConnection';
  suggestions: Array<BacenInstitutionAutocompleteItem>;
};

export type BacenInstitutionAutocompleteItem = {
  __typename: 'BacenInstitutionAutocompleteItem';
  name: Scalars['String']['output'];
};

export enum BacenInstitutionBankType {
  B1 = 'B1',
  B2 = 'B2',
  B3C = 'B3C',
  B3S = 'B3S',
  B4 = 'B4',
  N1 = 'N1',
  N2 = 'N2',
  N4 = 'N4'
}

export type BacenInstitutionConnection = {
  __typename: 'BacenInstitutionConnection';
  edges: Array<BacenInstitutionEdge>;
  pageInfo: PageInfo;
  totalCount: Maybe<Scalars['Int']['output']>;
};

export type BacenInstitutionEdge = {
  __typename: 'BacenInstitutionEdge';
  cursor: Scalars['String']['output'];
  node: BacenInstitutionSummaryItem;
};

export type BacenInstitutionSlug = {
  __typename: 'BacenInstitutionSlug';
  id: Scalars['ID']['output'];
  name: Scalars['String']['output'];
  updatedAt: Maybe<Scalars['DateTime']['output']>;
};

export type BacenInstitutionSlugConnection = {
  __typename: 'BacenInstitutionSlugConnection';
  edges: Array<BacenInstitutionSlugEdge>;
  pageInfo: FinancialDataPageInfo;
};

export type BacenInstitutionSlugEdge = {
  __typename: 'BacenInstitutionSlugEdge';
  cursor: Scalars['String']['output'];
  node: BacenInstitutionSlug;
};

export type BacenInstitutionSummaryItem = {
  __typename: 'BacenInstitutionSummaryItem';
  bacenInstitutionType: Maybe<Scalars['String']['output']>;
  basileiaIndex: Maybe<Scalars['Float']['output']>;
  dataRestriction: Maybe<DataRestriction>;
  equity: Maybe<Scalars['Float']['output']>;
  id: Scalars['ID']['output'];
  institutionType: Maybe<BacenInstitutionType>;
  legalName: Maybe<Scalars['String']['output']>;
  logoAsset: Maybe<Asset>;
  name: Scalars['String']['output'];
  profit: Maybe<Scalars['Float']['output']>;
  tipoConsolidadoBancario: Maybe<Scalars['String']['output']>;
  tradeName: Maybe<Scalars['String']['output']>;
  yoyEquityGrowth: Maybe<Scalars['Float']['output']>;
};

export enum BacenInstitutionType {
  FinancialGroup = 'FINANCIAL_GROUP',
  IndividualInstitution = 'INDIVIDUAL_INSTITUTION',
  PrudentialGroup = 'PRUDENTIAL_GROUP'
}

/** Bank-specific data. Present only when type = BANK. */
export type BankAccountData = {
  __typename: 'BankAccountData';
  branchCode: Maybe<Scalars['String']['output']>;
  checkDigit: Maybe<Scalars['String']['output']>;
  maskedNumber: Maybe<Scalars['String']['output']>;
  number: Maybe<Scalars['String']['output']>;
};

export type BankAccountDataInput = {
  branchCode?: InputMaybe<Scalars['String']['input']>;
  checkDigit?: InputMaybe<Scalars['String']['input']>;
  maskedNumber?: InputMaybe<Scalars['String']['input']>;
  number?: InputMaybe<Scalars['String']['input']>;
};

export type BillImportJob = {
  __typename: 'BillImportJob';
  createdAt: Scalars['DateTime']['output'];
  errorCode: Maybe<Scalars['String']['output']>;
  /** @deprecated Use errorCode/errorParameters for i18n */
  errorMessage: Maybe<Scalars['String']['output']>;
  errorParameters: Maybe<Scalars['JSON']['output']>;
  fileName: Maybe<Scalars['String']['output']>;
  id: Scalars['ID']['output'];
  importType: ImportType;
  reviewPayload: Maybe<BillImportReview>;
  status: BillImportJobStatus;
};

export enum BillImportJobStatus {
  AwaitingReview = 'AWAITING_REVIEW',
  Done = 'DONE',
  Extracted = 'EXTRACTED',
  Extracting = 'EXTRACTING',
  Failed = 'FAILED',
  Importing = 'IMPORTING',
  LlmProcessing = 'LLM_PROCESSING',
  Pending = 'PENDING'
}

export type BillImportMatchCandidate = {
  __typename: 'BillImportMatchCandidate';
  id: Scalars['ID']['output'];
  name: Scalars['String']['output'];
};

export type BillImportReview = {
  __typename: 'BillImportReview';
  bankName: Maybe<Scalars['String']['output']>;
  cards: Array<BillImportReviewCard>;
  reviewMode: BillImportReviewMode;
  statementPeriod: Maybe<DateRange>;
};

export type BillImportReviewCard = {
  __typename: 'BillImportReviewCard';
  closingDate: Maybe<Scalars['Date']['output']>;
  computedTotal: Scalars['Float']['output'];
  declaredTotal: Scalars['Float']['output'];
  dueDate: Maybe<Scalars['Date']['output']>;
  holderName: Maybe<Scalars['String']['output']>;
  lastFourDigits: Maybe<Scalars['String']['output']>;
  matchAmbiguous: Scalars['Boolean']['output'];
  matchCandidates: Array<BillImportMatchCandidate>;
  matchedFinancialAccountId: Maybe<Scalars['ID']['output']>;
  reviewCardId: Scalars['ID']['output'];
  totalMatchesDeclared: Scalars['Boolean']['output'];
  transactions: Array<BillImportReviewTransaction>;
};

export enum BillImportReviewMode {
  CardBucketMatching = 'CARD_BUCKET_MATCHING',
  TxCardAssignment = 'TX_CARD_ASSIGNMENT'
}

export type BillImportReviewTransaction = {
  __typename: 'BillImportReviewTransaction';
  amount: Scalars['Float']['output'];
  categoryId: Maybe<Scalars['ID']['output']>;
  categorySource: Maybe<CategorySource>;
  creditDebitType: CreditDebitType;
  date: Maybe<Scalars['Date']['output']>;
  description: Scalars['String']['output'];
  installment: Maybe<InstallmentInfo>;
  isBillPayment: Scalars['Boolean']['output'];
};

/**
 * Lifecycle status of a credit card bill (fatura).
 * OPEN    → closingDate >= today (bill still accepting charges).
 * CLOSED  → closingDate < today and dueDate >= today (awaiting payment).
 * OVERDUE → dueDate < today and not yet paid.
 * PAID    → terminal state.
 */
export enum BillStatus {
  Closed = 'CLOSED',
  Open = 'OPEN',
  Overdue = 'OVERDUE',
  Paid = 'PAID'
}

/**
 * Fee breakdown from a broker note (nota de corretagem).
 * 1:1 with InvestmentTransaction. Present only for renda variável transactions.
 * All fee fields are zero when not charged.
 */
export type BrokerNote = {
  __typename: 'BrokerNote';
  brokerNoteNumber: Maybe<Scalars['String']['output']>;
  brokerageFee: Scalars['Float']['output'];
  clearingCustodyFee: Scalars['Float']['output'];
  clearingRegistrationFee: Scalars['Float']['output'];
  clearingSettlementFee: Scalars['Float']['output'];
  createdAt: Scalars['DateTime']['output'];
  grossValue: Scalars['Float']['output'];
  incomeTax: Scalars['Float']['output'];
  netValue: Scalars['Float']['output'];
  stockExchangeAssetTradeNoticeFee: Scalars['Float']['output'];
  stockExchangeFee: Scalars['Float']['output'];
  taxes: Scalars['Float']['output'];
};

/** Fee breakdown from a broker note (nota de corretagem). All fields default to zero. */
export type BrokerNoteInput = {
  brokerNoteNumber?: InputMaybe<Scalars['String']['input']>;
  brokerageFee?: InputMaybe<Scalars['Float']['input']>;
  clearingCustodyFee?: InputMaybe<Scalars['Float']['input']>;
  clearingRegistrationFee?: InputMaybe<Scalars['Float']['input']>;
  clearingSettlementFee?: InputMaybe<Scalars['Float']['input']>;
  grossValue: Scalars['Float']['input'];
  incomeTax?: InputMaybe<Scalars['Float']['input']>;
  netValue: Scalars['Float']['input'];
  stockExchangeAssetTradeNoticeFee?: InputMaybe<Scalars['Float']['input']>;
  stockExchangeFee?: InputMaybe<Scalars['Float']['input']>;
  taxes?: InputMaybe<Scalars['Float']['input']>;
};

export enum BudgetPeriod {
  Daily = 'DAILY',
  Monthly = 'MONTHLY',
  Weekly = 'WEEKLY'
}

export enum BudgetScope {
  Account = 'ACCOUNT',
  Feature = 'FEATURE',
  Provider = 'PROVIDER',
  Service = 'SERVICE'
}

/** Result of a bulk operation (e.g. bulkCategorize). */
export type BulkOperationResult = {
  __typename: 'BulkOperationResult';
  affectedCount: Scalars['Int']['output'];
  /** Soft-deletes a MANUAL investment holding and all of its MANUAL transactions. */
  deleteInvestmentHolding: Scalars['Boolean']['output'];
  /** Soft-deletes a MANUAL investment transaction. Synced transactions cannot be deleted. */
  deleteInvestmentTransaction: Scalars['Boolean']['output'];
  errors: Array<Scalars['String']['output']>;
  /**
   * Records an investment transaction and updates the holding's quantity and averageCost.
   * Creates the holding automatically on the first PURCHASE of an asset.
   */
  recordInvestmentTransaction: InvestmentTransaction;
};


/** Result of a bulk operation (e.g. bulkCategorize). */
export type BulkOperationResultDeleteInvestmentHoldingArgs = {
  id: Scalars['ID']['input'];
};


/** Result of a bulk operation (e.g. bulkCategorize). */
export type BulkOperationResultDeleteInvestmentTransactionArgs = {
  id: Scalars['ID']['input'];
};


/** Result of a bulk operation (e.g. bulkCategorize). */
export type BulkOperationResultRecordInvestmentTransactionArgs = {
  input: RecordInvestmentTransactionInput;
};

/**
 * Card-specific data. Present only when subtype is CREDIT_CARD, PREPAID_CARD, CHARGE_CARD, or VOUCHER_CARD.
 * creditLimit, closingDay and dueDay are required for CREDIT_CARD (enforced at service layer).
 */
export type CardData = {
  __typename: 'CardData';
  availableCreditLimit: Maybe<Scalars['Float']['output']>;
  bin: Maybe<Scalars['String']['output']>;
  cardLevel: Maybe<CreditCardLevel>;
  cardNetwork: Maybe<CreditCardNetwork>;
  closingDay: Maybe<Scalars['Int']['output']>;
  creditLimit: Maybe<Scalars['Float']['output']>;
  dueDay: Maybe<Scalars['Int']['output']>;
  /** Optional bank account that will receive the debit leg of bill-payment transfers. */
  linkedBankAccountId: Maybe<Scalars['ID']['output']>;
  maskedNumber: Maybe<Scalars['String']['output']>;
  minimumPaymentPercentage: Maybe<Scalars['Float']['output']>;
};

export type CardDataInput = {
  bin?: InputMaybe<Scalars['String']['input']>;
  cardLevel?: InputMaybe<CreditCardLevel>;
  cardNetwork?: InputMaybe<CreditCardNetwork>;
  /** Required for CREDIT_CARD. */
  closingDay?: InputMaybe<Scalars['Int']['input']>;
  /** Required for CREDIT_CARD. */
  creditLimit?: InputMaybe<Scalars['Float']['input']>;
  /** Required for CREDIT_CARD. */
  dueDay?: InputMaybe<Scalars['Int']['input']>;
  /** Optional bank account that will receive the debit leg of bill-payment transfers. */
  linkedBankAccountId?: InputMaybe<Scalars['ID']['input']>;
  maskedNumber?: InputMaybe<Scalars['String']['input']>;
};

/** Category applicability (income, expense, transfer, or any). */
export enum CategoryApplicableTo {
  Any = 'ANY',
  Expense = 'EXPENSE',
  Income = 'INCOME',
  Transfer = 'TRANSFER'
}

/** How the transaction's category was assigned. Null when uncategorized. */
export enum CategorySource {
  Ai = 'AI',
  Imported = 'IMPORTED',
  Manual = 'MANUAL',
  Rule = 'RULE'
}

/** Category total for a period (expense/income breakdown). Percentage of period total. */
export type CategoryTotal = {
  __typename: 'CategoryTotal';
  category: TransactionCategory;
  /** Percentage of the period total (0–100). */
  percentage: Scalars['Float']['output'];
  totalAmount: Scalars['Float']['output'];
  transactionCount: Scalars['Int']['output'];
};

export type ChatInput = {
  message: Scalars['String']['input'];
  sessionId: Scalars['String']['input'];
};

export type ChatResponse = {
  __typename: 'ChatResponse';
  conversation: Maybe<Conversation>;
  escalate: Scalars['Boolean']['output'];
  response: Scalars['String']['output'];
  sessionId: Scalars['String']['output'];
};

export type CompletionChunk = {
  __typename: 'CompletionChunk';
  delta: Scalars['String']['output'];
  escalate: Scalars['Boolean']['output'];
  finishReason: Maybe<Scalars['String']['output']>;
  usage: Maybe<TokenUsage>;
};

export type CompletionInput = {
  maxTokens?: InputMaybe<Scalars['Int']['input']>;
  messages?: InputMaybe<Array<MessageInput>>;
  metadata?: InputMaybe<Scalars['JSON']['input']>;
  modelPreference?: InputMaybe<Scalars['String']['input']>;
  outputFormat?: InputMaybe<OutputFormat>;
  promptId?: InputMaybe<Scalars['ID']['input']>;
  promptVariables?: InputMaybe<Scalars['JSON']['input']>;
  purpose: Scalars['String']['input'];
  requiredCapabilities?: InputMaybe<Array<ModelCapability>>;
  temperature?: InputMaybe<Scalars['Float']['input']>;
  tools?: InputMaybe<Array<Scalars['String']['input']>>;
};

export type CompletionResponse = {
  __typename: 'CompletionResponse';
  content: Scalars['String']['output'];
  cost: Scalars['Float']['output'];
  escalate: Scalars['Boolean']['output'];
  finishReason: Scalars['String']['output'];
  id: Scalars['ID']['output'];
  latencyMs: Scalars['Int']['output'];
  model: Scalars['String']['output'];
  toolCalls: Array<ToolCall>;
  usage: TokenUsage;
};

/** Input for confirming an asset upload. */
export type ConfirmAssetUploadInput = {
  /** ID of the asset to confirm */
  assetId: Scalars['ID']['input'];
  /**
   * Optional SHA-256 checksum to verify file integrity.
   * If provided, server will validate against actual file checksum.
   */
  checksum?: InputMaybe<Scalars['String']['input']>;
};

export type ConfirmBillImportInput = {
  cards: Array<ConfirmBillReviewCardInput>;
  closingDate?: InputMaybe<Scalars['Date']['input']>;
  dueDate?: InputMaybe<Scalars['Date']['input']>;
  jobId: Scalars['ID']['input'];
};

export type ConfirmBillReviewCardInput = {
  financialAccountId: Scalars['ID']['input'];
  reviewCardId: Scalars['ID']['input'];
  transactions: Array<ConfirmBillReviewTransactionInput>;
};

export type ConfirmBillReviewTransactionInput = {
  amount: Scalars['Float']['input'];
  categoryId?: InputMaybe<Scalars['ID']['input']>;
  categorySource?: InputMaybe<CategorySource>;
  creditDebitType: CreditDebitType;
  date: Scalars['Date']['input'];
  description: Scalars['String']['input'];
  include: Scalars['Boolean']['input'];
  installment?: InputMaybe<InstallmentInfoInput>;
  isBillPayment?: InputMaybe<Scalars['Boolean']['input']>;
};

export enum ConnectionStatus {
  Connected = 'CONNECTED',
  ConsentExpired = 'CONSENT_EXPIRED',
  ConsentExpiring = 'CONSENT_EXPIRING',
  Disconnected = 'DISCONNECTED',
  Error = 'ERROR',
  Syncing = 'SYNCING'
}

export type Conversation = {
  __typename: 'Conversation';
  escalationReason: Maybe<Scalars['String']['output']>;
  lastMessage: Maybe<Scalars['String']['output']>;
  messageCount: Scalars['Int']['output'];
  messages: Array<ConversationMessage>;
  sessionId: Scalars['String']['output'];
  status: ConversationStatus;
};

export type ConversationMessage = {
  __typename: 'ConversationMessage';
  content: Scalars['String']['output'];
  role: MessageRole;
};

export enum ConversationStatus {
  Active = 'ACTIVE',
  Escalated = 'ESCALATED'
}

/** Input for creating a new asset upload. */
export type CreateAssetUploadInput = {
  /** Original filename */
  filename: Scalars['String']['input'];
  /**
   * Optional idempotency key to prevent duplicate uploads.
   * If provided, subsequent calls with the same key within 24 hours
   * will return the existing asset instead of creating a new one.
   */
  idempotencyKey?: InputMaybe<Scalars['String']['input']>;
  /** MIME type of the file */
  mimeType: Scalars['String']['input'];
  /**
   * Logical namespace for the asset (e.g., "user-profiles", "group-assets").
   * Determines validation rules (allowed MIME types, max file size), visibility, and storage key template.
   */
  namespace: Scalars['String']['input'];
  /** File size in bytes */
  sizeBytes: Scalars['Long']['input'];
};

export type CreateCategoryInput = {
  applicableTo: CategoryApplicableTo;
  color?: InputMaybe<Scalars['String']['input']>;
  icon?: InputMaybe<Scalars['String']['input']>;
  name: Scalars['String']['input'];
  parentId?: InputMaybe<Scalars['ID']['input']>;
  sortOrder?: InputMaybe<Scalars['Int']['input']>;
};

export type CreateFinancialAccountInput = {
  /** Required when type = BANK. */
  bankData?: InputMaybe<BankAccountDataInput>;
  /** Required when subtype is CREDIT_CARD, CHARGE_CARD, PREPAID_CARD, or VOUCHER_CARD. */
  cardData?: InputMaybe<CardDataInput>;
  /** ISO 4217 currency code. Defaults to BRL. */
  currencyCode?: InputMaybe<Scalars['String']['input']>;
  includeInDashboard?: InputMaybe<Scalars['Boolean']['input']>;
  includeInNetWorth?: InputMaybe<Scalars['Boolean']['input']>;
  /** Non-zero account balance on initialBalanceDate. Creates a BALANCE_ADJUSTMENT transaction automatically. */
  initialBalance?: InputMaybe<Scalars['Float']['input']>;
  /** Reference date for the initial balance. Required when initialBalance is provided. */
  initialBalanceDate?: InputMaybe<Scalars['Date']['input']>;
  /** ID from the issuers list. Mutually exclusive with issuerName. */
  issuerId?: InputMaybe<Scalars['ID']['input']>;
  /** Custom institution name. Required when issuerId is null. */
  issuerName?: InputMaybe<Scalars['String']['input']>;
  /** Display name. Defaults to issuer name when omitted. */
  name?: InputMaybe<Scalars['String']['input']>;
  subtype: FinancialAccountSubtype;
  themeColor?: InputMaybe<Scalars['String']['input']>;
  type: FinancialAccountType;
};

export type CreateGroupInput = {
  description?: InputMaybe<Scalars['String']['input']>;
  name: Scalars['String']['input'];
  userIds?: InputMaybe<Array<Scalars['ID']['input']>>;
};

/**
 * Creates a global investment asset entry.
 * Assets are not tenant-scoped and can be reused across accounts.
 */
export type CreateInvestmentAssetInput = {
  assetType: AssetType;
  /** Defaults to BRL when omitted. */
  currencyCode?: InputMaybe<Scalars['String']['input']>;
  fundCnpj?: InputMaybe<Scalars['String']['input']>;
  isinCode?: InputMaybe<Scalars['String']['input']>;
  issuerName?: InputMaybe<Scalars['String']['input']>;
  name: Scalars['String']['input'];
  ticker?: InputMaybe<Scalars['String']['input']>;
};

export type CreateMerchantInput = {
  domain?: InputMaybe<Scalars['String']['input']>;
  name: Scalars['String']['input'];
  sector?: InputMaybe<Scalars['String']['input']>;
};

export type CreatePromptInput = {
  category: Scalars['String']['input'];
  createdBy: Scalars['ID']['input'];
  description?: InputMaybe<Scalars['String']['input']>;
  modelRequirements?: InputMaybe<Scalars['JSON']['input']>;
  name: Scalars['String']['input'];
  outputFormat?: InputMaybe<OutputFormat>;
  performanceMetrics?: InputMaybe<Scalars['JSON']['input']>;
  tags?: InputMaybe<Array<Scalars['String']['input']>>;
  template: Scalars['String']['input'];
  variablesSchema?: InputMaybe<Scalars['JSON']['input']>;
};

export type CreateRoleInput = {
  name: Scalars['String']['input'];
};

/** Input for creating a new manual transaction. */
export type CreateTransactionInput = {
  amount: Scalars['Float']['input'];
  belongsToName?: InputMaybe<Scalars['String']['input']>;
  belongsToUserId?: InputMaybe<Scalars['ID']['input']>;
  cardIdentifier?: InputMaybe<Scalars['String']['input']>;
  categoryId?: InputMaybe<Scalars['ID']['input']>;
  counterpartyAccountNumber?: InputMaybe<Scalars['String']['input']>;
  counterpartyBankCode?: InputMaybe<Scalars['String']['input']>;
  counterpartyDocument?: InputMaybe<Scalars['String']['input']>;
  counterpartyName?: InputMaybe<Scalars['String']['input']>;
  creditDebitType: CreditDebitType;
  currencyCode?: InputMaybe<Scalars['String']['input']>;
  date: Scalars['Date']['input'];
  description: Scalars['String']['input'];
  financialAccountId: Scalars['ID']['input'];
  installmentNumber?: InputMaybe<Scalars['Int']['input']>;
  merchantId?: InputMaybe<Scalars['ID']['input']>;
  notes?: InputMaybe<Scalars['String']['input']>;
  payeeMcc?: InputMaybe<Scalars['Int']['input']>;
  paymentReason?: InputMaybe<Scalars['String']['input']>;
  status?: InputMaybe<TransactionStatus>;
  tags?: InputMaybe<Array<Scalars['String']['input']>>;
  totalInstallments?: InputMaybe<Scalars['Int']['input']>;
  type: TransactionType;
};

export type CreateTransactionRuleInput = {
  applicableTo: CategoryApplicableTo;
  categoryId?: InputMaybe<Scalars['ID']['input']>;
  merchantId?: InputMaybe<Scalars['ID']['input']>;
  pattern: Scalars['String']['input'];
  patternType: PatternType;
  priority?: InputMaybe<Scalars['Int']['input']>;
};

/**
 * A monthly credit card bill (fatura) for a CREDIT_CARD or CHARGE_CARD account.
 * totalAmount is the net spend across all non-deleted transactions in the billing cycle:
 *   SUM(DEBIT amount) - SUM(CREDIT amount).
 * Bills are auto-created when the first transaction is recorded for a billing cycle.
 */
export type CreditCardBill = {
  __typename: 'CreditCardBill';
  /** Last day of the billing cycle (derived from CardData.closingDay). */
  closingDate: Scalars['Date']['output'];
  createdAt: Scalars['DateTime']['output'];
  /** Payment due date (derived from CardData.dueDay relative to closingDate). */
  dueDate: Scalars['Date']['output'];
  financialAccountId: Scalars['ID']['output'];
  id: Scalars['ID']['output'];
  /** Minimum payment amount. Null until computed (Phase 5: Open Finance). */
  minimumPayment: Maybe<Scalars['Float']['output']>;
  status: BillStatus;
  /** Net spend in the billing cycle (DEBIT - CREDIT). Zero until first transaction. */
  totalAmount: Scalars['Float']['output'];
  updatedAt: Scalars['DateTime']['output'];
};

export enum CreditCardLevel {
  Black = 'BLACK',
  Gold = 'GOLD',
  Infinite = 'INFINITE',
  Platinum = 'PLATINUM',
  Standard = 'STANDARD'
}

export enum CreditCardNetwork {
  AmericanExpress = 'AMERICAN_EXPRESS',
  BandeiraPropria = 'BANDEIRA_PROPRIA',
  DinersClub = 'DINERS_CLUB',
  Elo = 'ELO',
  Hipercard = 'HIPERCARD',
  Mastercard = 'MASTERCARD',
  Outras = 'OUTRAS',
  Visa = 'VISA'
}

/**
 * Direction of money flow.
 * Amount is always positive; direction is carried here.
 */
export enum CreditDebitType {
  Credit = 'CREDIT',
  Debit = 'DEBIT'
}

export type DataRestriction = {
  __typename: 'DataRestriction';
  isRestricted: Scalars['Boolean']['output'];
  reason: Maybe<DataRestrictionReason>;
};

export enum DataRestrictionReason {
  InstitutionRequest = 'INSTITUTION_REQUEST'
}

export type DateRange = {
  __typename: 'DateRange';
  from: Maybe<Scalars['Date']['output']>;
  to: Maybe<Scalars['Date']['output']>;
};

/** User resolution for a duplicate candidate (incoming row vs existing transaction(s)). */
export enum DuplicateResolution {
  KeepBoth = 'KEEP_BOTH',
  KeepExisting = 'KEEP_EXISTING',
  UseIncoming = 'USE_INCOMING'
}

export type EconomicIndicator = {
  __typename: 'EconomicIndicator';
  countryCode: Scalars['String']['output'];
  createdAt: Scalars['DateTime']['output'];
  description: Maybe<Scalars['String']['output']>;
  frequency: Scalars['String']['output'];
  id: Scalars['ID']['output'];
  isActive: Scalars['Boolean']['output'];
  issuer: Scalars['String']['output'];
  label: Scalars['String']['output'];
  unit: Scalars['String']['output'];
  updatedAt: Scalars['DateTime']['output'];
  variations: Array<EconomicIndicatorVariation>;
};


export type EconomicIndicatorVariationsArgs = {
  label?: InputMaybe<Scalars['String']['input']>;
};

export type EconomicIndicatorLatestItem = {
  __typename: 'EconomicIndicatorLatestItem';
  indicator: Scalars['String']['output'];
  latest: Maybe<EconomicIndicatorPoint>;
  variation: Scalars['String']['output'];
};

export type EconomicIndicatorPoint = {
  __typename: 'EconomicIndicatorPoint';
  referenceDate: Scalars['Date']['output'];
  value: Scalars['Float']['output'];
};

export type EconomicIndicatorVariation = {
  __typename: 'EconomicIndicatorVariation';
  id: Scalars['ID']['output'];
  incrementalDays: Maybe<Scalars['Int']['output']>;
  incrementalMonths: Maybe<Scalars['Int']['output']>;
  incrementalYears: Maybe<Scalars['Int']['output']>;
  isDefault: Scalars['Boolean']['output'];
  label: Scalars['String']['output'];
  series: Maybe<EconomicIndicatorVariationSeries>;
  unit: Maybe<Scalars['String']['output']>;
};


export type EconomicIndicatorVariationSeriesArgs = {
  limit?: InputMaybe<Scalars['Int']['input']>;
  range?: InputMaybe<IndicatorRangeInput>;
};

export type EconomicIndicatorVariationSeries = {
  __typename: 'EconomicIndicatorVariationSeries';
  unit: Maybe<Scalars['String']['output']>;
  values: Array<EconomicIndicatorPoint>;
};

export enum EmailBodyFormat {
  Html = 'HTML',
  Text = 'TEXT'
}

export type EmailContentInput = {
  body?: InputMaybe<Scalars['String']['input']>;
  format?: InputMaybe<EmailBodyFormat>;
  kind: EmailContentKind;
  locale?: InputMaybe<Scalars['String']['input']>;
  subject?: InputMaybe<Scalars['String']['input']>;
  templateKey?: InputMaybe<Scalars['String']['input']>;
  variables?: InputMaybe<Scalars['JSON']['input']>;
};

export enum EmailContentKind {
  Raw = 'RAW',
  Template = 'TEMPLATE'
}

export type EmailSendRequestInput = {
  content: EmailContentInput;
  to: Scalars['String']['input'];
};

export type EmailSendResult = {
  __typename: 'EmailSendResult';
  requestId: Scalars['ID']['output'];
  status: EmailSendStatus;
};

export enum EmailSendStatus {
  Queued = 'QUEUED'
}

export type FeatureUsage = {
  __typename: 'FeatureUsage';
  completionTokens: Scalars['Int']['output'];
  cost: Scalars['Float']['output'];
  feature: Scalars['String']['output'];
  promptTokens: Scalars['Int']['output'];
  requests: Scalars['Int']['output'];
  totalTokens: Scalars['Int']['output'];
};

/**
 * A personal finance account or card belonging to the authenticated user's account.
 * The tenant association is resolved from the JWT and is not exposed directly.
 */
export type FinancialAccount = {
  __typename: 'FinancialAccount';
  bankData: Maybe<BankAccountData>;
  cardData: Maybe<CardData>;
  /** Open Finance connection status. Null for manually managed accounts. */
  connectionStatus: Maybe<ConnectionStatus>;
  consentExpiresAt: Maybe<Scalars['DateTime']['output']>;
  createdAt: Scalars['DateTime']['output'];
  currencyCode: Scalars['String']['output'];
  id: Scalars['ID']['output'];
  includeInDashboard: Scalars['Boolean']['output'];
  includeInNetWorth: Scalars['Boolean']['output'];
  /** Linked issuer from the issuers seed list. Null when a custom institution is used. */
  issuer: Maybe<Issuer>;
  /** Custom institution name. Set when issuerId is null. Use issuer.name when issuer is present. */
  issuerName: Maybe<Scalars['String']['output']>;
  lastSyncAt: Maybe<Scalars['DateTime']['output']>;
  name: Scalars['String']['output'];
  subtype: FinancialAccountSubtype;
  themeColor: Maybe<Scalars['String']['output']>;
  type: FinancialAccountType;
  updatedAt: Scalars['DateTime']['output'];
};

export enum FinancialAccountSubtype {
  AutoLoan = 'AUTO_LOAN',
  Brokerage = 'BROKERAGE',
  ChargeCard = 'CHARGE_CARD',
  ContaConjunta = 'CONTA_CONJUNTA',
  ContaCorrente = 'CONTA_CORRENTE',
  ContaDigital = 'CONTA_DIGITAL',
  ContaPoupanca = 'CONTA_POUPANCA',
  ContaSalario = 'CONTA_SALARIO',
  CreditCard = 'CREDIT_CARD',
  Crypto = 'CRYPTO',
  Fgts = 'FGTS',
  FixedIncome = 'FIXED_INCOME',
  Inss = 'INSS',
  Mortgage = 'MORTGAGE',
  OtherAsset = 'OTHER_ASSET',
  Pension = 'PENSION',
  PersonalLoan = 'PERSONAL_LOAN',
  PrepaidCard = 'PREPAID_CARD',
  Property = 'PROPERTY',
  Vehicle = 'VEHICLE',
  VoucherCard = 'VOUCHER_CARD',
  Wallet = 'WALLET'
}

export enum FinancialAccountType {
  Asset = 'ASSET',
  Bank = 'BANK',
  Benefit = 'BENEFIT',
  Cash = 'CASH',
  Credit = 'CREDIT',
  Investment = 'INVESTMENT',
  Loan = 'LOAN',
  Payment = 'PAYMENT'
}

export type FinancialDataPageInfo = {
  __typename: 'FinancialDataPageInfo';
  endCursor: Maybe<Scalars['String']['output']>;
  hasNextPage: Scalars['Boolean']['output'];
  hasPreviousPage: Scalars['Boolean']['output'];
  startCursor: Maybe<Scalars['String']['output']>;
};

export type FixedIncomeComparisonInput = {
  cdiAnnualRateBps: Scalars['Int']['input'];
  contractedRateBps: Scalars['Int']['input'];
  endDate: Scalars['Date']['input'];
  ipcaAnnualRateBps: Scalars['Int']['input'];
  principal: Scalars['Float']['input'];
  product?: InputMaybe<FixedIncomeProduct>;
  productGroup: FixedIncomeProductGroup;
  rateModel: FixedIncomeRateModel;
  startDate: Scalars['Date']['input'];
};

export type FixedIncomeComparisonResult = {
  __typename: 'FixedIncomeComparisonResult';
  methodology: FixedIncomeMethodology;
  performanceSeries: Array<FixedIncomePerformanceSeries>;
  rows: Array<FixedIncomeComparisonRow>;
  summary: FixedIncomeSummary;
};

export type FixedIncomeComparisonRow = {
  __typename: 'FixedIncomeComparisonRow';
  contractedRateDisplay: Scalars['String']['output'];
  equivalentRateBps: Scalars['Int']['output'];
  gross: Scalars['Float']['output'];
  ir: Scalars['Float']['output'];
  isBase: Scalars['Boolean']['output'];
  isExempt: Scalars['Boolean']['output'];
  label: Scalars['String']['output'];
  net: Scalars['Float']['output'];
  productGroup: FixedIncomeProductGroup;
  rateModel: FixedIncomeRateModel;
  realGainPercent: Scalars['Float']['output'];
};

export type FixedIncomeDayCountMethodology = {
  __typename: 'FixedIncomeDayCountMethodology';
  accrualConvention: Scalars['String']['output'];
  businessDays: Scalars['Int']['output'];
  calendarDays: Scalars['Int']['output'];
};

export type FixedIncomeMethodology = {
  __typename: 'FixedIncomeMethodology';
  dayCount: FixedIncomeDayCountMethodology;
  tax: FixedIncomeTaxMethodology;
};

export type FixedIncomePerformancePoint = {
  __typename: 'FixedIncomePerformancePoint';
  date: Scalars['Date']['output'];
  netValue: Scalars['Float']['output'];
};

export type FixedIncomePerformanceSeries = {
  __typename: 'FixedIncomePerformanceSeries';
  points: Array<FixedIncomePerformancePoint>;
  productGroup: FixedIncomeProductGroup;
};

export enum FixedIncomeProduct {
  Cdb = 'CDB',
  Debenture = 'DEBENTURE',
  FreeTaxDebenture = 'FREE_TAX_DEBENTURE',
  Lc = 'LC',
  Lca = 'LCA',
  Lci = 'LCI'
}

export enum FixedIncomeProductGroup {
  ExemptIr = 'EXEMPT_IR',
  TaxableIr = 'TAXABLE_IR'
}

export enum FixedIncomeRateModel {
  Cdi = 'CDI',
  Fixed = 'FIXED',
  IpcaPlus = 'IPCA_PLUS'
}

export type FixedIncomeSummary = {
  __typename: 'FixedIncomeSummary';
  baseEquivalentRateBps: Scalars['Int']['output'];
  baseProduct: FixedIncomeProduct;
  baseProductGroup: FixedIncomeProductGroup;
  baseRateDisplay: Scalars['String']['output'];
  baseRateModel: FixedIncomeRateModel;
  gross: Scalars['Float']['output'];
  ir: Scalars['Float']['output'];
  net: Scalars['Float']['output'];
  principal: Scalars['Float']['output'];
  realGainPercent: Scalars['Float']['output'];
};

export type FixedIncomeTaxBracket = {
  __typename: 'FixedIncomeTaxBracket';
  fromCalendarDays: Scalars['Int']['output'];
  rateDecimal: Scalars['Float']['output'];
  toCalendarDays: Maybe<Scalars['Int']['output']>;
};

export type FixedIncomeTaxMethodology = {
  __typename: 'FixedIncomeTaxMethodology';
  activeBracket: FixedIncomeTaxBracket;
  appliesOnProfitOnly: Scalars['Boolean']['output'];
  bracketTable: Array<FixedIncomeTaxBracket>;
};

export type FundDailyNav = {
  __typename: 'FundDailyNav';
  closePrice: Scalars['Float']['output'];
  date: Scalars['Date']['output'];
  totalNetAssets: Maybe<Scalars['Float']['output']>;
};

export enum FundTaxProfile {
  ComeCotasLong = 'COME_COTAS_LONG',
  ComeCotasShort = 'COME_COTAS_SHORT',
  Pension = 'PENSION',
  Unknown = 'UNKNOWN',
  VariableIncome_15 = 'VARIABLE_INCOME_15'
}

export type Group = {
  __typename: 'Group';
  createdAt: Scalars['String']['output'];
  description: Maybe<Scalars['String']['output']>;
  id: Scalars['ID']['output'];
  members: Array<User>;
  name: Scalars['String']['output'];
  roles: Array<Role>;
  updatedAt: Scalars['String']['output'];
};

export type GroupConnection = {
  __typename: 'GroupConnection';
  edges: Array<GroupEdge>;
  pageInfo: PageInfo;
  totalCount: Maybe<Scalars['Int']['output']>;
};

export type GroupEdge = {
  __typename: 'GroupEdge';
  cursor: Scalars['String']['output'];
  node: Group;
};

export type GroupOrderByInput = {
  direction: OrderDirection;
  field: GroupOrderField;
};

export enum GroupOrderField {
  Name = 'NAME'
}

/**
 * A file attached to an InvestmentHolding (nota de corretagem, contract, etc.).
 * The file is stored privately in the assets service.
 * Max 3 attachments per holding. Supported formats: image, plain text, Excel, CSV, PDF.
 */
export type HoldingAttachment = {
  __typename: 'HoldingAttachment';
  assetId: Scalars['ID']['output'];
  createdAt: Scalars['DateTime']['output'];
  /** Pre-signed download URL (valid for 1 hour). Null if the asset is not yet ready. */
  downloadUrl: Maybe<Scalars['String']['output']>;
  /** Original filename as uploaded by the user. */
  filename: Maybe<Scalars['String']['output']>;
  holdingId: Scalars['ID']['output'];
  id: Scalars['ID']['output'];
  /** MIME type of the file (e.g. application/pdf, image/png). */
  mimeType: Maybe<Scalars['String']['output']>;
  /** File size in bytes. */
  sizeBytes: Maybe<Scalars['Int']['output']>;
};

export type HoldingPerformance = {
  __typename: 'HoldingPerformance';
  assetType: AssetType;
  capabilities: HoldingPerformanceCapabilities;
  currencyCode: Scalars['String']['output'];
  holdingId: Scalars['ID']['output'];
  points: Array<HoldingPerformancePoint>;
  resolution: HoldingPerformanceResolution;
  summary: HoldingPerformanceSummary;
  valuationReferenceDate: Maybe<Scalars['Date']['output']>;
  valuationSource: ValuationSource;
};

export type HoldingPerformanceCapabilities = {
  __typename: 'HoldingPerformanceCapabilities';
  defaultResolution: HoldingPerformanceResolution;
  projectionDefaultEnabled: Scalars['Boolean']['output'];
  supportedResolutions: Array<HoldingPerformanceResolution>;
  supportsProjection: Scalars['Boolean']['output'];
};

export type HoldingPerformancePoint = {
  __typename: 'HoldingPerformancePoint';
  currentCostBasis: Maybe<Scalars['Float']['output']>;
  grossGain: Maybe<Scalars['Float']['output']>;
  grossValue: Maybe<Scalars['Float']['output']>;
  incomeTax: Maybe<Scalars['Float']['output']>;
  investedPrincipal: Maybe<Scalars['Float']['output']>;
  iof: Maybe<Scalars['Float']['output']>;
  isProjected: Scalars['Boolean']['output'];
  netContributions: Maybe<Scalars['Float']['output']>;
  netGain: Maybe<Scalars['Float']['output']>;
  netValue: Maybe<Scalars['Float']['output']>;
  quantity: Maybe<Scalars['Float']['output']>;
  timestamp: Scalars['DateTime']['output'];
};

export enum HoldingPerformanceResolution {
  Day = 'DAY',
  Hour = 'HOUR',
  Minute = 'MINUTE',
  Month = 'MONTH'
}

export type HoldingPerformanceSummary = {
  __typename: 'HoldingPerformanceSummary';
  currentCostBasis: Maybe<Scalars['Float']['output']>;
  grossGain: Maybe<Scalars['Float']['output']>;
  grossGainPercent: Maybe<Scalars['Float']['output']>;
  grossValue: Maybe<Scalars['Float']['output']>;
  incomeTax: Maybe<Scalars['Float']['output']>;
  investedPrincipal: Maybe<Scalars['Float']['output']>;
  iof: Maybe<Scalars['Float']['output']>;
  netContributions: Maybe<Scalars['Float']['output']>;
  netGain: Maybe<Scalars['Float']['output']>;
  netValue: Maybe<Scalars['Float']['output']>;
};

export enum HoldingPricingMode {
  Manual = 'MANUAL',
  MarketData = 'MARKET_DATA'
}

export type ImportDuplicateCandidate = {
  __typename: 'ImportDuplicateCandidate';
  candidates: Array<Transaction>;
  parsed: ParsedImportRow;
  rowIndex: Scalars['Int']['output'];
};

export type ImportResult = {
  __typename: 'ImportResult';
  import: TransactionImport;
};

export enum ImportSource {
  Auto = 'AUTO',
  CsvAuto = 'CSV_AUTO',
  CsvBradesco = 'CSV_BRADESCO',
  CsvItau = 'CSV_ITAU',
  CsvNubank = 'CSV_NUBANK',
  CsvSantander = 'CSV_SANTANDER',
  NfcE = 'NFC_E',
  Ofx = 'OFX',
  OpenFinance = 'OPEN_FINANCE',
  Pluggy = 'PLUGGY',
  ReceiptOcr = 'RECEIPT_OCR'
}

export enum ImportStatus {
  Completed = 'COMPLETED',
  Failed = 'FAILED',
  Pending = 'PENDING',
  Processing = 'PROCESSING',
  ReviewCancelled = 'REVIEW_CANCELLED',
  ReviewRequired = 'REVIEW_REQUIRED'
}

export type ImportTransactionsFileInput = {
  fileBase64: Scalars['String']['input'];
  fileName: Scalars['String']['input'];
  financialAccountId: Scalars['ID']['input'];
  source?: InputMaybe<ImportSource>;
};

export enum ImportType {
  BankStatement = 'BANK_STATEMENT',
  CreditCardBill = 'CREDIT_CARD_BILL'
}

/** Fixed income indexer (referenciador). Only applicable to RF holdings. */
export enum Indexer {
  Bcp = 'BCP',
  Cdi = 'CDI',
  Di = 'DI',
  IgpDi = 'IGP_DI',
  IgpM = 'IGP_M',
  Inpc = 'INPC',
  Ipca = 'IPCA',
  Outros = 'OUTROS',
  PreFixado = 'PRE_FIXADO',
  Selic = 'SELIC',
  Tlc = 'TLC',
  Tr = 'TR'
}

export type IndicatorRangeInput = {
  from?: InputMaybe<Scalars['Date']['input']>;
  last?: InputMaybe<Scalars['Int']['input']>;
  to?: InputMaybe<Scalars['Date']['input']>;
};

export type InstallmentInfo = {
  __typename: 'InstallmentInfo';
  current: Scalars['Int']['output'];
  total: Scalars['Int']['output'];
};

export type InstallmentInfoInput = {
  current: Scalars['Int']['input'];
  total: Scalars['Int']['input'];
};

export type InstitutionRating = {
  __typename: 'InstitutionRating';
  customParams: Maybe<Scalars['JSON']['output']>;
  id: Scalars['ID']['output'];
  issuer: RatingIssuer;
  label: Scalars['String']['output'];
  name: Scalars['String']['output'];
  ratingDate: Scalars['Date']['output'];
  updatedAt: Scalars['DateTime']['output'];
};

/**
 * Global investable instrument from the asset catalog.
 * Equities are identified by ticker; fixed income by isinCode; funds by fundCnpj.
 * Market prices are NOT stored here — fetched from financial-data at query time.
 */
export type InvestmentAsset = {
  __typename: 'InvestmentAsset';
  anbimaCategory: Maybe<AnbimaCategory>;
  anbimaClass: Maybe<Scalars['String']['output']>;
  anbimaSubclass: Maybe<Scalars['String']['output']>;
  assetType: AssetType;
  createdAt: Scalars['DateTime']['output'];
  currencyCode: Scalars['String']['output'];
  exchange: Maybe<Scalars['String']['output']>;
  fundCnpj: Maybe<Scalars['String']['output']>;
  id: Scalars['ID']['output'];
  isinCode: Maybe<Scalars['String']['output']>;
  issuerCnpj: Maybe<Scalars['String']['output']>;
  issuerName: Maybe<Scalars['String']['output']>;
  logoAsset: Maybe<Asset>;
  name: Scalars['String']['output'];
  /** B3 pricing multiplier. Most assets = 1. Some debentures quoted per 1000 use 0.001. */
  priceFactor: Scalars['Float']['output'];
  ticker: Maybe<Scalars['String']['output']>;
  updatedAt: Scalars['DateTime']['output'];
};

export type InvestmentFund = {
  __typename: 'InvestmentFund';
  assetType: Scalars['String']['output'];
  cnpj: Scalars['String']['output'];
  id: Scalars['ID']['output'];
  isActive: Scalars['Boolean']['output'];
  latestNav: Maybe<FundDailyNav>;
  name: Scalars['String']['output'];
  priceHistory: Array<FundDailyNav>;
};


export type InvestmentFundPriceHistoryArgs = {
  from?: InputMaybe<Scalars['Date']['input']>;
  last?: InputMaybe<Scalars['Int']['input']>;
  to?: InputMaybe<Scalars['Date']['input']>;
};

export enum InvestmentFundAssetType {
  EquityFund = 'EQUITY_FUND',
  FixedIncomeFund = 'FIXED_INCOME_FUND',
  ForexFund = 'FOREX_FUND',
  MultiMarketFund = 'MULTI_MARKET_FUND',
  MutualFund = 'MUTUAL_FUND',
  Pgbl = 'PGBL',
  Vgbl = 'VGBL'
}

export type InvestmentFundAutocompleteConnection = {
  __typename: 'InvestmentFundAutocompleteConnection';
  suggestions: Array<InvestmentFundAutocompleteItem>;
};

export type InvestmentFundAutocompleteItem = {
  __typename: 'InvestmentFundAutocompleteItem';
  assetType: Scalars['String']['output'];
  cnpj: Scalars['String']['output'];
  id: Scalars['ID']['output'];
  isActive: Scalars['Boolean']['output'];
  name: Scalars['String']['output'];
};

export type InvestmentFundConnection = {
  __typename: 'InvestmentFundConnection';
  edges: Array<InvestmentFundEdge>;
  pageInfo: PageInfo;
  totalCount: Maybe<Scalars['Int']['output']>;
};

export type InvestmentFundEdge = {
  __typename: 'InvestmentFundEdge';
  cursor: Scalars['String']['output'];
  node: InvestmentFund;
};

export type InvestmentFundMarketSnapshot = {
  __typename: 'InvestmentFundMarketSnapshot';
  assetType: Scalars['String']['output'];
  cnpj: Scalars['String']['output'];
  latestNav: Maybe<FundDailyNav>;
  taxProfile: FundTaxProfile;
};

/**
 * User's current position in an asset within a custodian account.
 * One holding per (financialAccountId, asset). Quantity aggregates all lots.
 * averageCost is maintained by the transaction engine — custo médio ponderado (RFB method).
 */
export type InvestmentHolding = {
  __typename: 'InvestmentHolding';
  asset: InvestmentAsset;
  /** Attached files (corretagem, contracts, etc.). Max 3. */
  attachments: Array<HoldingAttachment>;
  /** Weighted average purchase price per unit (custo médio ponderado). */
  averageCost: Scalars['Float']['output'];
  createdAt: Scalars['DateTime']['output'];
  financialAccount: Maybe<FinancialAccount>;
  financialAccountId: Scalars['ID']['output'];
  /** Annual fixed rate or spread (e.g. 0.12 = 12% a.a.). */
  fixedAnnualRate: Maybe<Scalars['Float']['output']>;
  /** Earliest redemption date (carência). */
  gracePeriodDate: Maybe<Scalars['Date']['output']>;
  grossValue: Maybe<Scalars['Float']['output']>;
  id: Scalars['ID']['output'];
  incomeTax: Maybe<Scalars['Float']['output']>;
  indexer: Maybe<Indexer>;
  /** Fraction of indexer applied (e.g. 1.15 = 115% CDI). */
  indexerPercentage: Maybe<Scalars['Float']['output']>;
  iof: Maybe<Scalars['Float']['output']>;
  /** Whether the holding has passed its maturity date. Computed as maturityDate != null && maturityDate < today. */
  isExpired: Scalars['Boolean']['output'];
  /** True for LCI/LCA/CRI/CRA — exempt from IR for individuals. */
  isTaxExempt: Scalars['Boolean']['output'];
  issueDate: Maybe<Scalars['Date']['output']>;
  lastValuedAt: Maybe<Scalars['DateTime']['output']>;
  maturityDate: Maybe<Scalars['Date']['output']>;
  netValue: Maybe<Scalars['Float']['output']>;
  pricingMode: Maybe<HoldingPricingMode>;
  /** Current number of units/shares/cotas held. Zero means position is closed. */
  quantity: Scalars['Float']['output'];
  source: InvestmentSource;
  updatedAt: Scalars['DateTime']['output'];
  /** Date of the most recent indicator used in the calculation (null when source is PROVIDER or UNAVAILABLE). */
  valuationReferenceDate: Maybe<Scalars['Date']['output']>;
  valuationSource: ValuationSource;
};

/** How an investment record entered the system. */
export enum InvestmentSource {
  FileImport = 'FILE_IMPORT',
  Manual = 'MANUAL',
  OpenFinance = 'OPEN_FINANCE'
}

/**
 * An event that changes (or explains) an investment holding.
 * tradeDate is the legally relevant date (fato gerador for IR calculation).
 * grossValue is always positive; direction is INFLOW or OUTFLOW.
 */
export type InvestmentTransaction = {
  __typename: 'InvestmentTransaction';
  brokerNote: Maybe<BrokerNote>;
  createdAt: Scalars['DateTime']['output'];
  direction: InvestmentTransactionDirection;
  financialAccountId: Scalars['ID']['output'];
  /** Annual fixed rate associated with the transaction, when applicable. */
  fixedAnnualRate: Maybe<Scalars['Float']['output']>;
  grossValue: Scalars['Float']['output'];
  holdingId: Scalars['ID']['output'];
  id: Scalars['ID']['output'];
  incomeTax: Maybe<Scalars['Float']['output']>;
  indexerPercentage: Maybe<Scalars['Float']['output']>;
  iof: Maybe<Scalars['Float']['output']>;
  netValue: Maybe<Scalars['Float']['output']>;
  priceFactor: Maybe<Scalars['Float']['output']>;
  /** Null for pure monetary events (DIVIDEND, INTEREST_PAYMENT, etc.). */
  quantity: Maybe<Scalars['Float']['output']>;
  /** Settlement date (D+2 equities, D+1 RF). Null for events with no settlement leg. */
  settlementDate: Maybe<Scalars['Date']['output']>;
  source: InvestmentSource;
  /** Legal event date — fato gerador for IR calculation. */
  tradeDate: Scalars['Date']['output'];
  type: InvestmentTransactionType;
  /** Required when type = OTHER (OFB transactionTypeAdditionalInfo). */
  typeAdditionalInfo: Maybe<Scalars['String']['output']>;
  unitPrice: Maybe<Scalars['Float']['output']>;
  updatedAt: Scalars['DateTime']['output'];
};

/** Direction of an investment event. INFLOW increases or adds; OUTFLOW decreases or withdraws. */
export enum InvestmentTransactionDirection {
  Inflow = 'INFLOW',
  Outflow = 'OUTFLOW'
}

/** Nature of an investment event (OFB/Pluggy/Belvo canonical). */
export enum InvestmentTransactionType {
  Amortization = 'AMORTIZATION',
  BonusShares = 'BONUS_SHARES',
  Cancellation = 'CANCELLATION',
  CustodyTransfer = 'CUSTODY_TRANSFER',
  Dividend = 'DIVIDEND',
  Fine = 'FINE',
  InterestOnEquity = 'INTEREST_ON_EQUITY',
  InterestPayment = 'INTEREST_PAYMENT',
  LatePaymentInterest = 'LATE_PAYMENT_INTEREST',
  Maturity = 'MATURITY',
  Other = 'OTHER',
  OwnershipTransfer = 'OWNERSHIP_TRANSFER',
  Prize = 'PRIZE',
  Purchase = 'PURCHASE',
  QuotaTransfer = 'QUOTA_TRANSFER',
  ReverseSplit = 'REVERSE_SPLIT',
  Sale = 'SALE',
  StockLending = 'STOCK_LENDING',
  StockSplit = 'STOCK_SPLIT',
  Subscription = 'SUBSCRIPTION',
  WithholdingTax = 'WITHHOLDING_TAX'
}

/** Financial institution associated with an account. */
export type Issuer = {
  __typename: 'Issuer';
  category: IssuerCategory;
  compeCode: Maybe<Scalars['String']['output']>;
  countryCode: Scalars['String']['output'];
  domain: Maybe<Scalars['String']['output']>;
  id: Scalars['ID']['output'];
  ispb: Maybe<Scalars['String']['output']>;
  logoAsset: Maybe<Asset>;
  logoKey: Maybe<Scalars['String']['output']>;
  name: Scalars['String']['output'];
};

export enum IssuerCategory {
  Bank = 'BANK',
  Benefit = 'BENEFIT',
  Brokerage = 'BROKERAGE',
  CryptoExchange = 'CRYPTO_EXCHANGE',
  Payment = 'PAYMENT'
}

export type ListedAsset = {
  __typename: 'ListedAsset';
  assetType: Scalars['String']['output'];
  exchange: Maybe<Scalars['String']['output']>;
  id: Scalars['ID']['output'];
  isActive: Scalars['Boolean']['output'];
  latestPrice: Maybe<AssetDailyPrice>;
  logoAsset: Maybe<Asset>;
  name: Scalars['String']['output'];
  priceHistory: Array<AssetDailyPrice>;
  ticker: Scalars['String']['output'];
};


export type ListedAssetPriceHistoryArgs = {
  from?: InputMaybe<Scalars['Date']['input']>;
  last?: InputMaybe<Scalars['Int']['input']>;
  to?: InputMaybe<Scalars['Date']['input']>;
};

export type ListedAssetAutocompleteConnection = {
  __typename: 'ListedAssetAutocompleteConnection';
  suggestions: Array<ListedAssetAutocompleteItem>;
};

export type ListedAssetAutocompleteItem = {
  __typename: 'ListedAssetAutocompleteItem';
  assetType: Scalars['String']['output'];
  id: Scalars['ID']['output'];
  isActive: Scalars['Boolean']['output'];
  name: Scalars['String']['output'];
  ticker: Scalars['String']['output'];
};

export type ListedAssetConnection = {
  __typename: 'ListedAssetConnection';
  edges: Array<ListedAssetEdge>;
  pageInfo: PageInfo;
  totalCount: Maybe<Scalars['Int']['output']>;
};

export type ListedAssetEdge = {
  __typename: 'ListedAssetEdge';
  cursor: Scalars['String']['output'];
  node: ListedAsset;
};

export type ManualFundValuationInput = {
  grossValue: Scalars['Float']['input'];
  valuedAt: Scalars['Date']['input'];
};

/**
 * A merchant (payee) associated with a transaction.
 * System merchants are shared across all accounts; user merchants are account-scoped.
 */
export type Merchant = {
  __typename: 'Merchant';
  domain: Maybe<Scalars['String']['output']>;
  id: Scalars['ID']['output'];
  isSystem: Scalars['Boolean']['output'];
  /** Logo asset via federation. Null until the hydration job has run for the domain. */
  logoAsset: Maybe<Asset>;
  name: Scalars['String']['output'];
  sector: Maybe<Scalars['String']['output']>;
};

export type MessageInput = {
  content?: InputMaybe<Scalars['String']['input']>;
  name?: InputMaybe<Scalars['String']['input']>;
  role: MessageRole;
  toolCallId?: InputMaybe<Scalars['String']['input']>;
};

export enum MessageRole {
  Assistant = 'ASSISTANT',
  System = 'SYSTEM',
  Tool = 'TOOL',
  User = 'USER'
}

export enum ModelCapability {
  JsonMode = 'JSON_MODE',
  Streaming = 'STREAMING',
  Tools = 'TOOLS',
  Vision = 'VISION'
}

export type ModelUsage = {
  __typename: 'ModelUsage';
  completionTokens: Scalars['Int']['output'];
  cost: Scalars['Float']['output'];
  modelId: Maybe<Scalars['ID']['output']>;
  promptTokens: Scalars['Int']['output'];
  requests: Scalars['Int']['output'];
  totalTokens: Scalars['Int']['output'];
};

/**
 * Monthly summary header for a financial account — entradas, saídas, saldo total.
 * Served from the monthly_balance_snapshots cache; transfer and projection fields are on-demand.
 */
export type MonthSummary = {
  __typename: 'MonthSummary';
  /** Saldo total at month-end (includes all types, excludes FUTURE). */
  closingBalance: Scalars['Float']['output'];
  /** Top expense categories for the month (ordered by totalAmount DESC). */
  expensesByCategory: Array<CategoryTotal>;
  /** Top income categories for the month (ordered by totalAmount DESC). */
  incomeByCategory: Array<CategoryTotal>;
  /** True when (year, month) equals the current calendar month. */
  isCurrentMonth: Scalars['Boolean']['output'];
  /** True when (year, month) is strictly after today's month. All values are on-demand projections; no snapshot is persisted. */
  isFutureMonth: Scalars['Boolean']['output'];
  month: Scalars['Int']['output'];
  /** Balance at start of month (prior month's closingBalance). */
  openingBalance: Scalars['Float']['output'];
  /** closingBalance + FUTURE transactions. Null when no FUTURE transactions exist. */
  projectedClosingBalance: Maybe<Scalars['Float']['output']>;
  /** Top tags for the month (ordered by totalAmount DESC). */
  tagsSummary: Array<TagTotal>;
  /** Saídas: total DEBIT excluding TRANSFER and BALANCE_ADJUSTMENT (POSTED/PENDING only). */
  totalExpenses: Scalars['Float']['output'];
  /** Entradas: total CREDIT excluding TRANSFER and BALANCE_ADJUSTMENT (POSTED/PENDING only). */
  totalIncome: Scalars['Float']['output'];
  /** Total CREDIT of type TRANSFER in the month. */
  transfersIn: Scalars['Float']['output'];
  /** Total DEBIT of type TRANSFER in the month. */
  transfersOut: Scalars['Float']['output'];
  year: Scalars['Int']['output'];
};

export type Mutation = {
  __typename: 'Mutation';
  _bankingService: Maybe<Scalars['String']['output']>;
  assignGroupToUser: User;
  assignPermissionToRole: Role;
  assignRoleToGroup: Group;
  /**
   * Attach an already-uploaded asset to an investment holding. The asset must be in READY status.
   * Call createAssetUpload → upload to presigned URL → confirmAssetUpload → attachFileToHolding.
   * Max 3 attachments per holding.
   */
  attachFileToHolding: HoldingAttachment;
  /**
   * Attach an already-uploaded asset to a transaction. The asset must be in READY status.
   * Call createAssetUpload → upload to presigned URL → confirmAssetUpload → attachFileToTransaction.
   * Max 3 attachments per transaction.
   */
  attachFileToTransaction: TransactionAttachment;
  /** Assign the same category to multiple transactions (e.g. post-import). Sets categorySource to MANUAL. */
  bulkCategorize: BulkOperationResult;
  cancelImportReview: Scalars['Boolean']['output'];
  chat: ChatResponse;
  clearConversation: Scalars['Boolean']['output'];
  /**
   * Step 2: Confirm asset upload after client completes file upload.
   *
   * This mutation:
   * 1. Verifies file exists in S3/R2 storage
   * 2. Optionally validates checksum if provided
   * 3. Updates asset status to READY
   * 4. Generates public CDN URL
   * 5. Returns updated asset with publicUrl
   *
   * Example usage:
   * ```graphql
   * mutation {
   * confirmAssetUpload(input: {
   * assetId: "01234567-89ab-cdef-0123-456789abcdef"
   * checksum: "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
   * }) {
   * id
   * status
   * publicUrl
   * }
   * }
   * ```
   */
  confirmAssetUpload: Asset;
  confirmBillImport: BillImportJob;
  /**
   * Step 1: Create asset upload and get pre-signed URL.
   *
   * This mutation:
   * 1. Validates MIME type and file size against namespace configuration
   * 2. Creates PENDING asset record in database
   * 3. Generates pre-signed S3/R2 upload URL (valid for 15 minutes)
   * 4. Returns asset with uploadUrl
   *
   * Client should then:
   * - Upload file directly to S3/R2 using the uploadUrl (PUT request)
   * - Call confirmAssetUpload mutation after upload completes
   *
   * Example usage:
   * ```graphql
   * mutation {
   * createAssetUpload(input: {
   * namespace: "user-profiles"
   * filename: "avatar.jpg"
   * mimeType: "image/jpeg"
   * sizeBytes: 1048576
   * idempotencyKey: "unique-key-123"
   * }) {
   * id
   * uploadUrl
   * uploadExpiresAt
   * status
   * visibility
   * storageKey
   * }
   * }
   * ```
   */
  createAssetUpload: Asset;
  /**
   * Create a new financial account or card.
   * Requires either issuerId (from the issuers list) or issuerName (custom institution).
   * For CREDIT_CARD subtype: cardData.closingDay and cardData.dueDay are required.
   */
  createFinancialAccount: FinancialAccount;
  createGroup: Group;
  /**
   * Records an investment transaction and updates the holding's quantity and averageCost.
   * Creates the holding automatically on the first PURCHASE of an asset.
   */
  createInvestmentAsset: InvestmentAsset;
  /**
   * Create a custom merchant for the authenticated account.
   * System merchants cannot be created via this mutation.
   */
  createMerchant: Merchant;
  createPrompt: Prompt;
  createRole: Role;
  /** Create a new manual transaction. */
  createTransaction: Transaction;
  /** Create a new custom transaction category for the authenticated account. */
  createTransactionCategory: TransactionCategory;
  /** Create a transaction rule for auto-categorization and/or merchant suggestion. Invalid REGEX pattern returns a validation error. */
  createTransactionRule: TransactionRule;
  /**
   * Delete an asset.
   *
   * This mutation:
   * 1. Deletes the file from S3/R2 storage
   * 2. Hard-deletes the asset record from the database
   *
   * Only the asset owner can delete the asset.
   */
  deleteAsset: Scalars['Boolean']['output'];
  deleteGroup: Scalars['Boolean']['output'];
  /** Soft-deletes a MANUAL investment holding and all of its MANUAL transactions. */
  deleteInvestmentHolding: Scalars['Boolean']['output'];
  /** Soft-deletes a MANUAL investment transaction. Synced transactions cannot be deleted. */
  deleteInvestmentTransaction: Scalars['Boolean']['output'];
  deleteRole: Scalars['Boolean']['output'];
  /** Soft-delete a manual transaction. */
  deleteTransaction: Scalars['Boolean']['output'];
  /** Soft-delete a custom transaction category (and its subcategories when deleting a root). */
  deleteTransactionCategory: Scalars['Boolean']['output'];
  /** Soft-delete a user-owned transaction rule. System rules cannot be deleted. */
  deleteTransactionRule: Scalars['Boolean']['output'];
  disableUser: User;
  enableUser: User;
  generateCompletion: CompletionResponse;
  importNfce: ImportResult;
  importTransactionsFile: ImportResult;
  /**
   * Register a payment against a credit card bill. Creates a CREDIT TRANSFER on the card account.
   * Optionally creates a DEBIT TRANSFER on sourceAccountId (e.g. bank account). Marks bill as PAID when payment covers full amount.
   */
  payBill: CreditCardBill;
  publishPromptVersion: PromptVersion;
  /** Records an investment transaction and updates the holding's quantity and averageCost. */
  recordInvestmentTransaction: InvestmentTransaction;
  refreshAccessToken: SignInPayload;
  registerTool: Tool;
  removeGroupFromUser: User;
  /** Remove an attachment from a holding and delete the underlying asset. */
  removeHoldingAttachment: Scalars['Boolean']['output'];
  removePermissionFromRole: Role;
  removeRoleFromGroup: Group;
  /** Remove an attachment from a transaction and delete the underlying asset. */
  removeTransactionAttachment: Scalars['Boolean']['output'];
  requestEmailSend: EmailSendResult;
  requestPasswordReset: RequestPasswordResetPayload;
  resendVerificationEmail: ResendVerificationEmailPayload;
  resetPassword: ResetPasswordPayload;
  resolveImportDuplicate: TransactionImport;
  setUsageBudget: UsageBudget;
  signIn: SignInPayload;
  signInWithOAuth: SignInPayload;
  signUp: SignUpPayload;
  submitBillImport: BillImportJob;
  updateFinancialAccount: FinancialAccount;
  updateGroup: Group;
  updateRole: Role;
  /** Update an existing manual transaction. */
  updateTransaction: Transaction;
  /** Update a custom transaction category for the authenticated account. */
  updateTransactionCategory: TransactionCategory;
  /** Update a user-owned transaction rule. */
  updateTransactionRule: TransactionRule;
  updateUser: User;
  verifyEmailWithToken: VerifyEmailPayload;
};


export type MutationAssignGroupToUserArgs = {
  groupId: Scalars['ID']['input'];
  userId: Scalars['ID']['input'];
};


export type MutationAssignPermissionToRoleArgs = {
  permissionId: Scalars['ID']['input'];
  roleId: Scalars['ID']['input'];
};


export type MutationAssignRoleToGroupArgs = {
  groupId: Scalars['ID']['input'];
  roleId: Scalars['ID']['input'];
};


export type MutationAttachFileToHoldingArgs = {
  input: AttachFileToHoldingInput;
};


export type MutationAttachFileToTransactionArgs = {
  input: AttachFileToTransactionInput;
};


export type MutationBulkCategorizeArgs = {
  categoryId: Scalars['ID']['input'];
  transactionIds: Array<Scalars['ID']['input']>;
};


export type MutationCancelImportReviewArgs = {
  jobId: Scalars['ID']['input'];
};


export type MutationChatArgs = {
  input: ChatInput;
};


export type MutationClearConversationArgs = {
  sessionId: Scalars['String']['input'];
};


export type MutationConfirmAssetUploadArgs = {
  input: ConfirmAssetUploadInput;
};


export type MutationConfirmBillImportArgs = {
  input: ConfirmBillImportInput;
};


export type MutationCreateAssetUploadArgs = {
  input: CreateAssetUploadInput;
};


export type MutationCreateFinancialAccountArgs = {
  input: CreateFinancialAccountInput;
};


export type MutationCreateGroupArgs = {
  input: CreateGroupInput;
};


export type MutationCreateInvestmentAssetArgs = {
  input: CreateInvestmentAssetInput;
};


export type MutationCreateMerchantArgs = {
  input: CreateMerchantInput;
};


export type MutationCreatePromptArgs = {
  input: CreatePromptInput;
};


export type MutationCreateRoleArgs = {
  input: CreateRoleInput;
};


export type MutationCreateTransactionArgs = {
  input: CreateTransactionInput;
};


export type MutationCreateTransactionCategoryArgs = {
  input: CreateCategoryInput;
};


export type MutationCreateTransactionRuleArgs = {
  input: CreateTransactionRuleInput;
};


export type MutationDeleteAssetArgs = {
  assetId: Scalars['ID']['input'];
};


export type MutationDeleteGroupArgs = {
  groupId: Scalars['ID']['input'];
};


export type MutationDeleteInvestmentHoldingArgs = {
  id: Scalars['ID']['input'];
};


export type MutationDeleteInvestmentTransactionArgs = {
  id: Scalars['ID']['input'];
};


export type MutationDeleteRoleArgs = {
  roleId: Scalars['ID']['input'];
};


export type MutationDeleteTransactionArgs = {
  id: Scalars['ID']['input'];
};


export type MutationDeleteTransactionCategoryArgs = {
  id: Scalars['ID']['input'];
};


export type MutationDeleteTransactionRuleArgs = {
  id: Scalars['ID']['input'];
};


export type MutationDisableUserArgs = {
  userId: Scalars['ID']['input'];
};


export type MutationEnableUserArgs = {
  userId: Scalars['ID']['input'];
};


export type MutationGenerateCompletionArgs = {
  input: CompletionInput;
};


export type MutationImportNfceArgs = {
  financialAccountId: Scalars['ID']['input'];
  qrCodeUrl: Scalars['String']['input'];
};


export type MutationImportTransactionsFileArgs = {
  input: ImportTransactionsFileInput;
};


export type MutationPayBillArgs = {
  amount: Scalars['Float']['input'];
  billId: Scalars['ID']['input'];
  sourceAccountId?: InputMaybe<Scalars['ID']['input']>;
};


export type MutationPublishPromptVersionArgs = {
  promptId: Scalars['ID']['input'];
  versionId: Scalars['ID']['input'];
};


export type MutationRecordInvestmentTransactionArgs = {
  input: RecordInvestmentTransactionInput;
};


export type MutationRefreshAccessTokenArgs = {
  input: RefreshAccessTokenInput;
};


export type MutationRegisterToolArgs = {
  input: RegisterToolInput;
};


export type MutationRemoveGroupFromUserArgs = {
  groupId: Scalars['ID']['input'];
  userId: Scalars['ID']['input'];
};


export type MutationRemoveHoldingAttachmentArgs = {
  attachmentId: Scalars['ID']['input'];
};


export type MutationRemovePermissionFromRoleArgs = {
  permissionId: Scalars['ID']['input'];
  roleId: Scalars['ID']['input'];
};


export type MutationRemoveRoleFromGroupArgs = {
  groupId: Scalars['ID']['input'];
  roleId: Scalars['ID']['input'];
};


export type MutationRemoveTransactionAttachmentArgs = {
  attachmentId: Scalars['ID']['input'];
};


export type MutationRequestEmailSendArgs = {
  input: EmailSendRequestInput;
};


export type MutationRequestPasswordResetArgs = {
  input: RequestPasswordResetInput;
};


export type MutationResetPasswordArgs = {
  input: ResetPasswordInput;
};


export type MutationResolveImportDuplicateArgs = {
  input: ResolveImportDuplicateInput;
};


export type MutationSetUsageBudgetArgs = {
  input: SetBudgetInput;
};


export type MutationSignInArgs = {
  input: SignInInput;
};


export type MutationSignInWithOAuthArgs = {
  input: SignInWithOAuthInput;
};


export type MutationSignUpArgs = {
  input: SignUpInput;
};


export type MutationSubmitBillImportArgs = {
  input: SubmitBillImportInput;
};


export type MutationUpdateFinancialAccountArgs = {
  id: Scalars['ID']['input'];
  input: UpdateFinancialAccountInput;
};


export type MutationUpdateGroupArgs = {
  groupId: Scalars['ID']['input'];
  input: UpdateGroupInput;
};


export type MutationUpdateRoleArgs = {
  input: UpdateRoleInput;
  roleId: Scalars['ID']['input'];
};


export type MutationUpdateTransactionArgs = {
  id: Scalars['ID']['input'];
  input: UpdateTransactionInput;
};


export type MutationUpdateTransactionCategoryArgs = {
  id: Scalars['ID']['input'];
  input: UpdateCategoryInput;
};


export type MutationUpdateTransactionRuleArgs = {
  id: Scalars['ID']['input'];
  input: UpdateTransactionRuleInput;
};


export type MutationUpdateUserArgs = {
  input: UpdateUserInput;
  userId: Scalars['ID']['input'];
};


export type MutationVerifyEmailWithTokenArgs = {
  token: Scalars['String']['input'];
};

export enum OrderDirection {
  Asc = 'ASC',
  Desc = 'DESC'
}

export enum OutputFormat {
  Json = 'JSON',
  Text = 'TEXT'
}

/** Relay-style page info for paginated lists. */
export type PageInfo = {
  __typename: 'PageInfo';
  endCursor: Maybe<Scalars['String']['output']>;
  hasNextPage: Scalars['Boolean']['output'];
  hasPreviousPage: Scalars['Boolean']['output'];
  startCursor: Maybe<Scalars['String']['output']>;
};

export type ParsedImportRow = {
  __typename: 'ParsedImportRow';
  amount: Scalars['Float']['output'];
  counterpartyDocument: Maybe<Scalars['String']['output']>;
  counterpartyName: Maybe<Scalars['String']['output']>;
  creditDebitType: CreditDebitType;
  currencyCode: Scalars['String']['output'];
  date: Scalars['Date']['output'];
  description: Scalars['String']['output'];
};

/** Pattern type for transaction rule description matching. */
export enum PatternType {
  Contains = 'CONTAINS',
  Exact = 'EXACT',
  Regex = 'REGEX',
  StartsWith = 'STARTS_WITH'
}

export type Permission = {
  __typename: 'Permission';
  id: Scalars['ID']['output'];
  name: Scalars['String']['output'];
  roles: Array<Role>;
};

export type PermissionConnection = {
  __typename: 'PermissionConnection';
  edges: Array<PermissionEdge>;
  pageInfo: PageInfo;
};

export type PermissionEdge = {
  __typename: 'PermissionEdge';
  cursor: Scalars['String']['output'];
  node: Permission;
};

export type PopularInstitution = {
  __typename: 'PopularInstitution';
  institution: BacenInstitutionSummaryItem;
  month: Maybe<Scalars['Int']['output']>;
  popularInstitutionTopMetrics: Array<PopularInstitutionTopMetric>;
  quarter: Scalars['String']['output'];
  rank: Scalars['Int']['output'];
  shortDescription: Scalars['String']['output'];
  year: Maybe<Scalars['Int']['output']>;
};

export type PopularInstitutionTopMetric = {
  __typename: 'PopularInstitutionTopMetric';
  metric: TopInstitutionMetric;
  popularInstitutionTopMetricItem: PopularInstitutionTopMetricItem;
};

export type PopularInstitutionTopMetricItem = {
  __typename: 'PopularInstitutionTopMetricItem';
  institution: BacenInstitutionSummaryItem;
  metricValue: Maybe<Scalars['Float']['output']>;
  rank: Scalars['Int']['output'];
};

export type Prompt = {
  __typename: 'Prompt';
  activeVersion: Maybe<PromptVersion>;
  category: Scalars['String']['output'];
  createdAt: Scalars['String']['output'];
  createdBy: Scalars['ID']['output'];
  description: Maybe<Scalars['String']['output']>;
  id: Scalars['ID']['output'];
  name: Scalars['String']['output'];
  tags: Array<Scalars['String']['output']>;
  updatedAt: Scalars['String']['output'];
};

export type PromptVariable = {
  __typename: 'PromptVariable';
  defaultValue: Maybe<Scalars['JSON']['output']>;
  name: Scalars['String']['output'];
  required: Scalars['Boolean']['output'];
  type: Maybe<Scalars['String']['output']>;
  validation: Maybe<Scalars['JSON']['output']>;
};

export type PromptVersion = {
  __typename: 'PromptVersion';
  createdAt: Scalars['String']['output'];
  id: Scalars['ID']['output'];
  modelRequirements: Maybe<Scalars['JSON']['output']>;
  outputFormat: OutputFormat;
  performanceMetrics: Maybe<Scalars['JSON']['output']>;
  promptId: Scalars['ID']['output'];
  status: PromptVersionStatus;
  template: Scalars['String']['output'];
  updatedAt: Scalars['String']['output'];
  variables: Array<PromptVariable>;
  variablesSchema: Scalars['JSON']['output'];
  version: Scalars['Int']['output'];
};

export enum PromptVersionStatus {
  Active = 'ACTIVE',
  Deprecated = 'DEPRECATED',
  Draft = 'DRAFT'
}

export type QuarterRangeInput = {
  from?: InputMaybe<Scalars['String']['input']>;
  last?: InputMaybe<Scalars['Int']['input']>;
  to?: InputMaybe<Scalars['String']['input']>;
};

export type QuarterReports = {
  __typename: 'QuarterReports';
  quarter: Scalars['String']['output'];
  reports: Array<ReportDescriptor>;
};

export type QuarterValue = {
  __typename: 'QuarterValue';
  quarter: Scalars['String']['output'];
  value: Maybe<Scalars['Float']['output']>;
};

export type Query = {
  __typename: 'Query';
  _bankingService: Maybe<Scalars['String']['output']>;
  _empty: Maybe<Scalars['String']['output']>;
  /** All holdings across all custodian accounts for the tenant (for net worth). */
  allInvestmentHoldings: Array<InvestmentHolding>;
  /**
   * Get asset by ID.
   * Returns null if asset not found or user doesn't have access.
   */
  asset: Maybe<Asset>;
  availableTools: Array<Tool>;
  bacenFinanceInstitution: Maybe<BacenFinanceInstitution>;
  bacenInstitutionSlugs: BacenInstitutionSlugConnection;
  bacenInstitutions: BacenInstitutionConnection;
  billImportJob: Maybe<BillImportJob>;
  billImportJobs: Array<BillImportJob>;
  checkPermission: AuthorizationResult;
  conversation: Maybe<Conversation>;
  /**
   * A specific credit card bill by closing date.
   * When closingDate is omitted, returns the current open bill (or most recent if no open bill exists).
   */
  creditCardBill: Maybe<CreditCardBill>;
  /**
   * All non-deleted credit card bills for a CREDIT_CARD or CHARGE_CARD account,
   * ordered by closing date descending.
   */
  creditCardBills: Array<CreditCardBill>;
  currentUser: Maybe<User>;
  economicIndicators: Array<EconomicIndicator>;
  /** All financial accounts belonging to the authenticated user's account. */
  financialAccounts: Array<FinancialAccount>;
  fixedIncomeComparison: FixedIncomeComparisonResult;
  group: Maybe<Group>;
  groups: GroupConnection;
  /** Generic performance series and valuation summary for a holding. */
  holdingPerformance: Maybe<HoldingPerformance>;
  indicatorsLatest: Array<EconomicIndicatorLatestItem>;
  /** Look up an asset from the global catalog. */
  investmentAsset: Maybe<InvestmentAsset>;
  investmentAssetByTicker: Maybe<ListedAsset>;
  investmentAssets: ListedAssetConnection;
  investmentAssetsByTickers: Array<ListedAsset>;
  investmentFund: Maybe<InvestmentFund>;
  investmentFundMarketSnapshots: Array<InvestmentFundMarketSnapshot>;
  investmentFunds: InvestmentFundConnection;
  /** Single holding by id. */
  investmentHolding: Maybe<InvestmentHolding>;
  /**
   * All open holdings for a custodian account (type = INVESTMENT/BROKERAGE).
   * Zero-quantity (closed) positions are excluded.
   */
  investmentHoldings: Array<InvestmentHolding>;
  /** Single investment transaction by id. */
  investmentTransaction: Maybe<InvestmentTransaction>;
  /** Full event history for a holding, ordered by tradeDate DESC. */
  investmentTransactions: Array<InvestmentTransaction>;
  isUserActiveMemberOfAccount: Scalars['Boolean']['output'];
  /**
   * All active issuers.
   * When categories is omitted, returns all active issuers.
   */
  issuers: Array<Issuer>;
  /** All merchants (system + user-created) available to the authenticated account. */
  merchants: Array<Merchant>;
  /**
   * Monthly summary (entradas, saídas, saldo) for a financial account or all accounts.
   * When financialAccountId is omitted, returns the aggregate across all accounts.
   * When month/year are not provided, the current month/year are used.
   */
  monthSummary: MonthSummary;
  myUsage: UsageStats;
  myVerificationStatus: Maybe<VerificationStatus>;
  pendingReviewImports: Array<TransactionImport>;
  permissions: PermissionConnection;
  popularInstitutions: Array<PopularInstitution>;
  prompt: Maybe<Prompt>;
  promptVersions: Array<PromptVersion>;
  prompts: Array<Prompt>;
  ratingIssuers: Array<RatingIssuer>;
  role: Maybe<Role>;
  roles: RoleConnection;
  searchBacenInstitutionsAutocomplete: BacenInstitutionAutocompleteConnection;
  searchInvestmentAssetsAutocomplete: ListedAssetAutocompleteConnection;
  searchInvestmentFundsAutocomplete: InvestmentFundAutocompleteConnection;
  serviceUsage: UsageStats;
  /** Top merchants by spend for the authenticated account. Defaults to top 10 for the current month. */
  topMerchants: Array<TopMerchant>;
  /** Single transaction by ID (scoped to the authenticated account). */
  transaction: Maybe<Transaction>;
  /** Distinct values used in transaction attribution ("belongs to"), across all account transactions. */
  transactionBelongsToOptions: Array<TransactionBelongsToOption>;
  /** All categories (system + custom) available to the authenticated account. */
  transactionCategories: Array<TransactionCategory>;
  transactionImport: Maybe<TransactionImport>;
  transactionImports: Array<TransactionImport>;
  /** All user-defined transaction rules (explicit + implicit) for auto-categorization and merchant suggestion. System rules are not returned. */
  transactionRules: Array<TransactionRule>;
  /** Distinct tags used in transactions for the authenticated account. */
  transactionTags: Array<TransactionTagOption>;
  /**
   * Transactions for a single financial account or all accounts belonging to the authenticated account.
   * Results are filtered by month/year and paginated using Relay-style cursors.
   * When month/year are not provided (and billId is null), the current month/year are used by the backend.
   * When billId is provided, it takes precedence over month/year and filters by bill only (used for card bill mode).
   */
  transactions: TransactionConnection;
  /**
   * Category totals for a period (expense/income breakdown). Accepts the same filters as the
   * transactions query — the result reflects the category breakdown of exactly those transactions.
   * When financialAccountId is omitted, aggregates across all accounts.
   */
  transactionsByCategory: Array<CategoryTotal>;
  /**
   * Tag totals for a period. Accepts the same filters as the
   * transactions query — the result reflects the tag breakdown of exactly those transactions.
   * When financialAccountId is omitted, aggregates across all accounts.
   */
  transactionsByTag: Array<TagTotal>;
  user: Maybe<User>;
  users: UserConnection;
};


export type QueryAssetArgs = {
  id: Scalars['ID']['input'];
};


export type QueryBacenFinanceInstitutionArgs = {
  anoMes?: InputMaybe<Scalars['String']['input']>;
  id: Scalars['ID']['input'];
};


export type QueryBacenInstitutionSlugsArgs = {
  after?: InputMaybe<Scalars['String']['input']>;
  anoMes?: InputMaybe<Scalars['String']['input']>;
  first?: InputMaybe<Scalars['Int']['input']>;
};


export type QueryBacenInstitutionsArgs = {
  after?: InputMaybe<Scalars['String']['input']>;
  anoMes?: InputMaybe<Scalars['String']['input']>;
  first?: InputMaybe<Scalars['Int']['input']>;
  query?: InputMaybe<Scalars['String']['input']>;
};


export type QueryBillImportJobArgs = {
  jobId: Scalars['ID']['input'];
};


export type QueryBillImportJobsArgs = {
  financialAccountId?: InputMaybe<Scalars['ID']['input']>;
};


export type QueryCheckPermissionArgs = {
  permission: Scalars['String']['input'];
  resourceId?: InputMaybe<Scalars['ID']['input']>;
  userId: Scalars['ID']['input'];
};


export type QueryConversationArgs = {
  sessionId: Scalars['String']['input'];
};


export type QueryCreditCardBillArgs = {
  closingDate?: InputMaybe<Scalars['Date']['input']>;
  financialAccountId: Scalars['ID']['input'];
};


export type QueryCreditCardBillsArgs = {
  financialAccountId: Scalars['ID']['input'];
};


export type QueryEconomicIndicatorsArgs = {
  label?: InputMaybe<Scalars['String']['input']>;
};


export type QueryFixedIncomeComparisonArgs = {
  input: FixedIncomeComparisonInput;
};


export type QueryGroupArgs = {
  id: Scalars['ID']['input'];
};


export type QueryGroupsArgs = {
  after?: InputMaybe<Scalars['String']['input']>;
  first?: InputMaybe<Scalars['Int']['input']>;
  orderBy?: InputMaybe<Array<GroupOrderByInput>>;
  query?: InputMaybe<Scalars['String']['input']>;
};


export type QueryHoldingPerformanceArgs = {
  holdingId: Scalars['ID']['input'];
  includeProjection?: InputMaybe<Scalars['Boolean']['input']>;
  resolution?: InputMaybe<HoldingPerformanceResolution>;
};


export type QueryInvestmentAssetArgs = {
  id: Scalars['ID']['input'];
};


export type QueryInvestmentAssetByTickerArgs = {
  ticker: Scalars['String']['input'];
};


export type QueryInvestmentAssetsArgs = {
  after?: InputMaybe<Scalars['String']['input']>;
  assetType?: InputMaybe<B3AssetType>;
  first?: InputMaybe<Scalars['Int']['input']>;
  isActive?: InputMaybe<Scalars['Boolean']['input']>;
  query?: InputMaybe<Scalars['String']['input']>;
};


export type QueryInvestmentAssetsByTickersArgs = {
  tickers: Array<Scalars['String']['input']>;
};


export type QueryInvestmentFundArgs = {
  id: Scalars['ID']['input'];
};


export type QueryInvestmentFundMarketSnapshotsArgs = {
  cnpjs: Array<Scalars['String']['input']>;
};


export type QueryInvestmentFundsArgs = {
  after?: InputMaybe<Scalars['String']['input']>;
  assetType?: InputMaybe<InvestmentFundAssetType>;
  first?: InputMaybe<Scalars['Int']['input']>;
  isActive?: InputMaybe<Scalars['Boolean']['input']>;
  query?: InputMaybe<Scalars['String']['input']>;
};


export type QueryInvestmentHoldingArgs = {
  id: Scalars['ID']['input'];
};


export type QueryInvestmentHoldingsArgs = {
  financialAccountId: Scalars['ID']['input'];
};


export type QueryInvestmentTransactionArgs = {
  id: Scalars['ID']['input'];
};


export type QueryInvestmentTransactionsArgs = {
  holdingId: Scalars['ID']['input'];
};


export type QueryIsUserActiveMemberOfAccountArgs = {
  accountId: Scalars['ID']['input'];
  userId: Scalars['ID']['input'];
};


export type QueryIssuersArgs = {
  categories?: InputMaybe<Array<IssuerCategory>>;
};


export type QueryMonthSummaryArgs = {
  financialAccountId?: InputMaybe<Scalars['ID']['input']>;
  month?: InputMaybe<Scalars['Int']['input']>;
  year?: InputMaybe<Scalars['Int']['input']>;
};


export type QueryMyUsageArgs = {
  period: UsagePeriod;
};


export type QueryPendingReviewImportsArgs = {
  financialAccountId?: InputMaybe<Scalars['ID']['input']>;
};


export type QueryPermissionsArgs = {
  after?: InputMaybe<Scalars['String']['input']>;
  first?: InputMaybe<Scalars['Int']['input']>;
  query?: InputMaybe<Scalars['String']['input']>;
};


export type QueryPopularInstitutionsArgs = {
  anoMes?: InputMaybe<Scalars['String']['input']>;
};


export type QueryPromptArgs = {
  id: Scalars['ID']['input'];
};


export type QueryPromptVersionsArgs = {
  promptId: Scalars['ID']['input'];
};


export type QueryPromptsArgs = {
  category?: InputMaybe<Scalars['String']['input']>;
  tags?: InputMaybe<Array<Scalars['String']['input']>>;
};


export type QueryRoleArgs = {
  id: Scalars['ID']['input'];
};


export type QueryRolesArgs = {
  after?: InputMaybe<Scalars['String']['input']>;
  first?: InputMaybe<Scalars['Int']['input']>;
  orderBy?: InputMaybe<Array<RoleOrderByInput>>;
  query?: InputMaybe<Scalars['String']['input']>;
};


export type QuerySearchBacenInstitutionsAutocompleteArgs = {
  anoMes?: InputMaybe<Scalars['String']['input']>;
  query: Scalars['String']['input'];
};


export type QuerySearchInvestmentAssetsAutocompleteArgs = {
  assetType?: InputMaybe<B3AssetType>;
  query: Scalars['String']['input'];
};


export type QuerySearchInvestmentFundsAutocompleteArgs = {
  query: Scalars['String']['input'];
};


export type QueryServiceUsageArgs = {
  period: UsagePeriod;
  serviceId: Scalars['String']['input'];
};


export type QueryTopMerchantsArgs = {
  financialAccountId?: InputMaybe<Scalars['ID']['input']>;
  limit?: InputMaybe<Scalars['Int']['input']>;
  month?: InputMaybe<Scalars['Int']['input']>;
  year?: InputMaybe<Scalars['Int']['input']>;
};


export type QueryTransactionArgs = {
  id: Scalars['ID']['input'];
};


export type QueryTransactionImportArgs = {
  id: Scalars['ID']['input'];
};


export type QueryTransactionImportsArgs = {
  financialAccountId?: InputMaybe<Scalars['ID']['input']>;
};


export type QueryTransactionsArgs = {
  after?: InputMaybe<Scalars['String']['input']>;
  belongsToName?: InputMaybe<Scalars['String']['input']>;
  belongsToUserId?: InputMaybe<Scalars['ID']['input']>;
  billId?: InputMaybe<Scalars['ID']['input']>;
  categoryId?: InputMaybe<Scalars['ID']['input']>;
  creditDebitType?: InputMaybe<CreditDebitType>;
  financialAccountId?: InputMaybe<Scalars['ID']['input']>;
  first?: InputMaybe<Scalars['Int']['input']>;
  month?: InputMaybe<Scalars['Int']['input']>;
  tags?: InputMaybe<Array<Scalars['String']['input']>>;
  types?: InputMaybe<Array<TransactionType>>;
  uncategorized?: InputMaybe<Scalars['Boolean']['input']>;
  year?: InputMaybe<Scalars['Int']['input']>;
};


export type QueryTransactionsByCategoryArgs = {
  applicableTo?: InputMaybe<CategoryApplicableTo>;
  belongsToName?: InputMaybe<Scalars['String']['input']>;
  belongsToUserId?: InputMaybe<Scalars['ID']['input']>;
  billId?: InputMaybe<Scalars['ID']['input']>;
  categoryId?: InputMaybe<Scalars['ID']['input']>;
  financialAccountId?: InputMaybe<Scalars['ID']['input']>;
  month?: InputMaybe<Scalars['Int']['input']>;
  tags?: InputMaybe<Array<Scalars['String']['input']>>;
  uncategorized?: InputMaybe<Scalars['Boolean']['input']>;
  year?: InputMaybe<Scalars['Int']['input']>;
};


export type QueryTransactionsByTagArgs = {
  applicableTo?: InputMaybe<CategoryApplicableTo>;
  belongsToName?: InputMaybe<Scalars['String']['input']>;
  belongsToUserId?: InputMaybe<Scalars['ID']['input']>;
  billId?: InputMaybe<Scalars['ID']['input']>;
  categoryId?: InputMaybe<Scalars['ID']['input']>;
  financialAccountId?: InputMaybe<Scalars['ID']['input']>;
  month?: InputMaybe<Scalars['Int']['input']>;
  tags?: InputMaybe<Array<Scalars['String']['input']>>;
  uncategorized?: InputMaybe<Scalars['Boolean']['input']>;
  year?: InputMaybe<Scalars['Int']['input']>;
};


export type QueryUserArgs = {
  id: Scalars['ID']['input'];
};


export type QueryUsersArgs = {
  after?: InputMaybe<Scalars['String']['input']>;
  first?: InputMaybe<Scalars['Int']['input']>;
  orderBy?: InputMaybe<Array<UserOrderByInput>>;
  query?: InputMaybe<Scalars['String']['input']>;
};

export type RatingHistory = {
  __typename: 'RatingHistory';
  changedAt: Scalars['DateTime']['output'];
  customParams: Maybe<Scalars['JSON']['output']>;
  id: Scalars['ID']['output'];
  issuerCode: Scalars['String']['output'];
  label: Scalars['String']['output'];
  name: Scalars['String']['output'];
  ratingDate: Scalars['Date']['output'];
};

export type RatingIssuer = {
  __typename: 'RatingIssuer';
  code: Scalars['String']['output'];
  id: Scalars['ID']['output'];
  logoAssetId: Maybe<Scalars['String']['output']>;
  name: Scalars['String']['output'];
  website: Maybe<Scalars['String']['output']>;
};

/**
 * Records a new investment transaction and updates the corresponding holding.
 * The holding is resolved from (financialAccountId, assetId) — created if this is the first transaction.
 * RF-specific terms (indexer, maturityDate, etc.) are only used when creating a brand-new holding.
 */
export type RecordInvestmentTransactionInput = {
  assetId: Scalars['ID']['input'];
  brokerNote?: InputMaybe<BrokerNoteInput>;
  direction: InvestmentTransactionDirection;
  financialAccountId: Scalars['ID']['input'];
  fixedAnnualRate?: InputMaybe<Scalars['Float']['input']>;
  gracePeriodDate?: InputMaybe<Scalars['Date']['input']>;
  grossValue: Scalars['Float']['input'];
  incomeTax?: InputMaybe<Scalars['Float']['input']>;
  indexer?: InputMaybe<Indexer>;
  indexerPercentage?: InputMaybe<Scalars['Float']['input']>;
  iof?: InputMaybe<Scalars['Float']['input']>;
  isTaxExempt?: InputMaybe<Scalars['Boolean']['input']>;
  issueDate?: InputMaybe<Scalars['Date']['input']>;
  manualValuation?: InputMaybe<ManualFundValuationInput>;
  maturityDate?: InputMaybe<Scalars['Date']['input']>;
  netValue?: InputMaybe<Scalars['Float']['input']>;
  priceFactor?: InputMaybe<Scalars['Float']['input']>;
  pricingMode?: InputMaybe<HoldingPricingMode>;
  providerInvestmentId?: InputMaybe<Scalars['String']['input']>;
  quantity?: InputMaybe<Scalars['Float']['input']>;
  settlementDate?: InputMaybe<Scalars['Date']['input']>;
  tradeDate: Scalars['Date']['input'];
  type: InvestmentTransactionType;
  /** Required when type = OTHER. */
  typeAdditionalInfo?: InputMaybe<Scalars['String']['input']>;
  unitPrice?: InputMaybe<Scalars['Float']['input']>;
};

export type RefreshAccessTokenInput = {
  refreshToken: Scalars['String']['input'];
};

export type RegisterToolInput = {
  category: Scalars['String']['input'];
  description: Scalars['String']['input'];
  executorEndpoint: Scalars['String']['input'];
  executorService: Scalars['String']['input'];
  inputSchema?: InputMaybe<Scalars['JSON']['input']>;
  name: Scalars['String']['input'];
  outputSchema?: InputMaybe<Scalars['JSON']['input']>;
  retryPolicy?: InputMaybe<Scalars['JSON']['input']>;
  status?: InputMaybe<ToolStatus>;
  timeoutMs: Scalars['Int']['input'];
};

export type ReportAccount = {
  __typename: 'ReportAccount';
  group: Maybe<Scalars['String']['output']>;
  id: Maybe<Scalars['String']['output']>;
  name: Maybe<Scalars['String']['output']>;
  valuesByQuarter: Array<QuarterValue>;
};

export type ReportDescriptor = {
  __typename: 'ReportDescriptor';
  group: Maybe<Scalars['String']['output']>;
  id: Maybe<Scalars['String']['output']>;
  name: Maybe<Scalars['String']['output']>;
  reportId: Scalars['ID']['output'];
};

export type ReportDetails = {
  __typename: 'ReportDetails';
  accounts: Array<ReportAccount>;
  name: Maybe<Scalars['String']['output']>;
  reportId: Scalars['ID']['output'];
};

export type ReportsAvailability = {
  __typename: 'ReportsAvailability';
  quarters: Array<QuarterReports>;
};

export type RequestPasswordResetInput = {
  email: Scalars['String']['input'];
  locale?: InputMaybe<Scalars['String']['input']>;
};

export type RequestPasswordResetPayload = {
  __typename: 'RequestPasswordResetPayload';
  message: Scalars['String']['output'];
  success: Scalars['Boolean']['output'];
};

export type ResendVerificationEmailPayload = {
  __typename: 'ResendVerificationEmailPayload';
  canResendAt: Maybe<Scalars['String']['output']>;
  message: Maybe<Scalars['String']['output']>;
  success: Scalars['Boolean']['output'];
};

export type ResetPasswordInput = {
  newPassword: Scalars['String']['input'];
  token: Scalars['String']['input'];
};

export type ResetPasswordPayload = {
  __typename: 'ResetPasswordPayload';
  message: Scalars['String']['output'];
  success: Scalars['Boolean']['output'];
};

export type ResolveImportDuplicateInput = {
  jobId: Scalars['ID']['input'];
  resolution: DuplicateResolution;
  rowIndex: Scalars['Int']['input'];
};

export type Role = {
  __typename: 'Role';
  createdAt: Scalars['String']['output'];
  groups: Array<Group>;
  id: Scalars['ID']['output'];
  name: Scalars['String']['output'];
  permissions: Array<Permission>;
  updatedAt: Scalars['String']['output'];
};

export type RoleConnection = {
  __typename: 'RoleConnection';
  edges: Array<RoleEdge>;
  pageInfo: PageInfo;
  totalCount: Maybe<Scalars['Int']['output']>;
};

export type RoleEdge = {
  __typename: 'RoleEdge';
  cursor: Scalars['String']['output'];
  node: Role;
};

export type RoleOrderByInput = {
  direction: OrderDirection;
  field: RoleOrderField;
};

export enum RoleOrderField {
  Name = 'NAME'
}

export type SetBudgetInput = {
  alertThreshold: Scalars['Float']['input'];
  limitAmount: Scalars['Float']['input'];
  period: BudgetPeriod;
  scope: BudgetScope;
  scopeId: Scalars['ID']['input'];
};

export type SignInInput = {
  email: Scalars['String']['input'];
  password: Scalars['String']['input'];
  rememberMe?: InputMaybe<Scalars['Boolean']['input']>;
};

export type SignInPayload = {
  __typename: 'SignInPayload';
  refreshToken: Maybe<Scalars['String']['output']>;
  token: Scalars['String']['output'];
  user: User;
};

export type SignInWithOAuthInput = {
  idToken: Scalars['String']['input'];
  provider: Scalars['String']['input'];
  rememberMe?: InputMaybe<Scalars['Boolean']['input']>;
};

export type SignUpInput = {
  email: Scalars['String']['input'];
  locale?: InputMaybe<Scalars['String']['input']>;
  name: Scalars['String']['input'];
  password: Scalars['String']['input'];
};

export type SignUpPayload = {
  __typename: 'SignUpPayload';
  refreshToken: Maybe<Scalars['String']['output']>;
  requiresVerification: Scalars['Boolean']['output'];
  token: Scalars['String']['output'];
  user: User;
};

export type SubmitBillImportInput = {
  fileBase64: Scalars['String']['input'];
  fileName: Scalars['String']['input'];
  financialAccountId?: InputMaybe<Scalars['ID']['input']>;
  importType: ImportType;
  password?: InputMaybe<Scalars['String']['input']>;
};

export type Subscription = {
  __typename: 'Subscription';
  messageStream: CompletionChunk;
};


export type SubscriptionMessageStreamArgs = {
  conversationId: Scalars['ID']['input'];
  input: MessageInput;
};

/** Tag total for a period. Percentage of grouped tag total. */
export type TagTotal = {
  __typename: 'TagTotal';
  /** Percentage of the grouped tag total (0–100). */
  percentage: Scalars['Float']['output'];
  tag: Scalars['String']['output'];
  totalAmount: Scalars['Float']['output'];
  transactionCount: Scalars['Int']['output'];
};

export type TokenUsage = {
  __typename: 'TokenUsage';
  completionTokens: Scalars['Int']['output'];
  promptTokens: Scalars['Int']['output'];
  totalTokens: Scalars['Int']['output'];
};

export type Tool = {
  __typename: 'Tool';
  category: Scalars['String']['output'];
  createdAt: Scalars['String']['output'];
  description: Scalars['String']['output'];
  executorEndpoint: Scalars['String']['output'];
  executorService: Scalars['String']['output'];
  id: Scalars['ID']['output'];
  inputSchema: Scalars['JSON']['output'];
  name: Scalars['String']['output'];
  outputSchema: Scalars['JSON']['output'];
  retryPolicy: Maybe<Scalars['JSON']['output']>;
  status: ToolStatus;
  timeoutMs: Scalars['Int']['output'];
  updatedAt: Scalars['String']['output'];
};

export type ToolCall = {
  __typename: 'ToolCall';
  arguments: Scalars['String']['output'];
  id: Scalars['String']['output'];
  name: Scalars['String']['output'];
  type: Scalars['String']['output'];
};

export enum ToolStatus {
  Active = 'ACTIVE',
  Disabled = 'DISABLED'
}

export enum TopInstitutionMetric {
  BasileiaIndex = 'BASILEIA_INDEX',
  MobilizationIndex = 'MOBILIZATION_INDEX',
  Profit = 'PROFIT'
}

/** A top-merchant analytics row. */
export type TopMerchant = {
  __typename: 'TopMerchant';
  lastUsedAt: Scalars['Date']['output'];
  merchant: Merchant;
  totalAmount: Scalars['Float']['output'];
  transactionCount: Scalars['Int']['output'];
};

/** Core ledger transaction row. */
export type Transaction = {
  __typename: 'Transaction';
  amount: Scalars['Float']['output'];
  /** BRL equivalent for FX transactions (null for BRL). */
  amountBrl: Maybe<Scalars['Float']['output']>;
  attachments: Array<TransactionAttachment>;
  belongsToName: Maybe<Scalars['String']['output']>;
  belongsToUserId: Maybe<Scalars['ID']['output']>;
  billId: Maybe<Scalars['ID']['output']>;
  billPostDate: Maybe<Scalars['Date']['output']>;
  cardIdentifier: Maybe<Scalars['String']['output']>;
  category: Maybe<TransactionCategory>;
  /** How the category was assigned. Null when uncategorized. */
  categorySource: Maybe<CategorySource>;
  counterpartyAccountNumber: Maybe<Scalars['String']['output']>;
  counterpartyBankCode: Maybe<Scalars['String']['output']>;
  counterpartyDocument: Maybe<Scalars['String']['output']>;
  counterpartyName: Maybe<Scalars['String']['output']>;
  createdAt: Scalars['DateTime']['output'];
  creditDebitType: CreditDebitType;
  currencyCode: Scalars['String']['output'];
  date: Scalars['Date']['output'];
  description: Scalars['String']['output'];
  financialAccountId: Scalars['ID']['output'];
  id: Scalars['ID']['output'];
  installmentNumber: Maybe<Scalars['Int']['output']>;
  merchant: Maybe<Merchant>;
  notes: Maybe<Scalars['String']['output']>;
  payeeMcc: Maybe<Scalars['Int']['output']>;
  paymentReason: Maybe<Scalars['String']['output']>;
  source: TransactionSource;
  status: TransactionStatus;
  tags: Array<Scalars['String']['output']>;
  totalInstallments: Maybe<Scalars['Int']['output']>;
  /** Exact date-time of the transaction when provided (e.g. by Open Finance). Null when only date is known. */
  transactionDatetime: Maybe<Scalars['DateTime']['output']>;
  transferPeerTransactionId: Maybe<Scalars['ID']['output']>;
  type: TransactionType;
  updatedAt: Scalars['DateTime']['output'];
};

/**
 * A file attached to a Transaction (receipt, contract, product guarantee, etc.).
 * The file is stored privately in the assets service.
 * Max 3 attachments per transaction. Supported formats: image, plain text, Excel, CSV, PDF.
 */
export type TransactionAttachment = {
  __typename: 'TransactionAttachment';
  assetId: Scalars['ID']['output'];
  createdAt: Scalars['DateTime']['output'];
  /** Pre-signed download URL (valid for 1 hour). Null if the asset is not yet ready. */
  downloadUrl: Maybe<Scalars['String']['output']>;
  /** Original filename as uploaded by the user. */
  filename: Maybe<Scalars['String']['output']>;
  id: Scalars['ID']['output'];
  /** MIME type of the file (e.g. application/pdf, image/png). */
  mimeType: Maybe<Scalars['String']['output']>;
  /** File size in bytes. */
  sizeBytes: Maybe<Scalars['Int']['output']>;
  transactionId: Scalars['ID']['output'];
};

/**
 * Distinct attribution option (user or name tag) used by transactions in the account.
 * Exactly one field is expected to be non-null.
 */
export type TransactionBelongsToOption = {
  __typename: 'TransactionBelongsToOption';
  belongsToName: Maybe<Scalars['String']['output']>;
  belongsToUserId: Maybe<Scalars['ID']['output']>;
};

/** Category for classifying transactions (system + user-defined). */
export type TransactionCategory = {
  __typename: 'TransactionCategory';
  applicableTo: CategoryApplicableTo;
  color: Maybe<Scalars['String']['output']>;
  icon: Maybe<Scalars['String']['output']>;
  id: Scalars['ID']['output'];
  isSystem: Scalars['Boolean']['output'];
  name: Scalars['String']['output'];
  parentId: Maybe<Scalars['ID']['output']>;
  sortOrder: Scalars['Int']['output'];
};

/** Relay connection for paginated transactions. */
export type TransactionConnection = {
  __typename: 'TransactionConnection';
  edges: Array<TransactionEdge>;
  pageInfo: PageInfo;
  totalCount: Maybe<Scalars['Int']['output']>;
};

/** Edge in a TransactionConnection. */
export type TransactionEdge = {
  __typename: 'TransactionEdge';
  cursor: Scalars['String']['output'];
  node: Transaction;
};

export type TransactionImport = {
  __typename: 'TransactionImport';
  completedAt: Maybe<Scalars['DateTime']['output']>;
  createdAt: Scalars['DateTime']['output'];
  dateFrom: Maybe<Scalars['Date']['output']>;
  dateTo: Maybe<Scalars['Date']['output']>;
  duplicateCandidates: Array<ImportDuplicateCandidate>;
  duplicateReviewCount: Maybe<Scalars['Int']['output']>;
  errorCount: Maybe<Scalars['Int']['output']>;
  errorDetails: Maybe<Scalars['String']['output']>;
  fileName: Maybe<Scalars['String']['output']>;
  financialAccountId: Scalars['ID']['output'];
  id: Scalars['ID']['output'];
  importedCount: Maybe<Scalars['Int']['output']>;
  skippedCount: Maybe<Scalars['Int']['output']>;
  source: ImportSource;
  status: ImportStatus;
  totalRows: Maybe<Scalars['Int']['output']>;
  updatedAt: Scalars['DateTime']['output'];
};

/**
 * Auto-assignment rule that maps a description pattern to a category and/or merchant.
 * Explicit or implicit. System rules are not exposed; only user rules are returned.
 */
export type TransactionRule = {
  __typename: 'TransactionRule';
  applicableTo: CategoryApplicableTo;
  category: Maybe<TransactionCategory>;
  categoryId: Maybe<Scalars['ID']['output']>;
  createdAt: Scalars['DateTime']['output'];
  id: Scalars['ID']['output'];
  isImplicit: Scalars['Boolean']['output'];
  isSystem: Scalars['Boolean']['output'];
  matchCount: Scalars['Int']['output'];
  merchant: Maybe<Merchant>;
  merchantId: Maybe<Scalars['ID']['output']>;
  pattern: Scalars['String']['output'];
  patternType: PatternType;
  priority: Scalars['Int']['output'];
};

/** How the transaction entered the system. */
export enum TransactionSource {
  Manual = 'MANUAL',
  OpenFinance = 'OPEN_FINANCE',
  PdfBill = 'PDF_BILL',
  Pluggy = 'PLUGGY'
}

/**
 * Lifecycle status of a transaction.
 * FUTURE = manually scheduled; excluded from current balance but included in projected balance.
 */
export enum TransactionStatus {
  Future = 'FUTURE',
  Pending = 'PENDING',
  Posted = 'POSTED'
}

/** Distinct tag already used in transactions for the authenticated account. */
export type TransactionTagOption = {
  __typename: 'TransactionTagOption';
  tag: Scalars['String']['output'];
};

/**
 * Transaction type (OFB-aligned).
 * BALANCE_ADJUSTMENT is neutral — used for opening balance and reconciliation.
 */
export enum TransactionType {
  BalanceAdjustment = 'BALANCE_ADJUSTMENT',
  Boleto = 'BOLETO',
  Card = 'CARD',
  Cashback = 'CASHBACK',
  Deposit = 'DEPOSIT',
  Doc = 'DOC',
  Fee = 'FEE',
  Interest = 'INTEREST',
  InvestmentReturn = 'INVESTMENT_RETURN',
  LoanPayment = 'LOAN_PAYMENT',
  OtherExpense = 'OTHER_EXPENSE',
  OtherIncome = 'OTHER_INCOME',
  Pix = 'PIX',
  Salary = 'SALARY',
  Tax = 'TAX',
  Ted = 'TED',
  Transfer = 'TRANSFER',
  Withdrawal = 'WITHDRAWAL'
}

export type UpdateCategoryInput = {
  color?: InputMaybe<Scalars['String']['input']>;
  icon?: InputMaybe<Scalars['String']['input']>;
  name?: InputMaybe<Scalars['String']['input']>;
  sortOrder?: InputMaybe<Scalars['Int']['input']>;
};

export type UpdateFinancialAccountInput = {
  bankData?: InputMaybe<BankAccountDataInput>;
  cardData?: InputMaybe<CardDataInput>;
  currencyCode?: InputMaybe<Scalars['String']['input']>;
  includeInDashboard?: InputMaybe<Scalars['Boolean']['input']>;
  includeInNetWorth?: InputMaybe<Scalars['Boolean']['input']>;
  issuerId?: InputMaybe<Scalars['ID']['input']>;
  issuerName?: InputMaybe<Scalars['String']['input']>;
  name?: InputMaybe<Scalars['String']['input']>;
  subtype: FinancialAccountSubtype;
  themeColor?: InputMaybe<Scalars['String']['input']>;
  type: FinancialAccountType;
};

export type UpdateGroupInput = {
  description?: InputMaybe<Scalars['String']['input']>;
  name: Scalars['String']['input'];
  userIds?: InputMaybe<Array<Scalars['ID']['input']>>;
};

export type UpdateRoleInput = {
  name: Scalars['String']['input'];
};

/**
 * Input for updating an existing manual transaction.
 * All fields are optional and only provided fields are updated.
 */
export type UpdateTransactionInput = {
  amount?: InputMaybe<Scalars['Float']['input']>;
  belongsToName?: InputMaybe<Scalars['String']['input']>;
  belongsToUserId?: InputMaybe<Scalars['ID']['input']>;
  cardIdentifier?: InputMaybe<Scalars['String']['input']>;
  categoryId?: InputMaybe<Scalars['ID']['input']>;
  counterpartyAccountNumber?: InputMaybe<Scalars['String']['input']>;
  counterpartyBankCode?: InputMaybe<Scalars['String']['input']>;
  counterpartyDocument?: InputMaybe<Scalars['String']['input']>;
  counterpartyName?: InputMaybe<Scalars['String']['input']>;
  creditDebitType?: InputMaybe<CreditDebitType>;
  date?: InputMaybe<Scalars['Date']['input']>;
  description?: InputMaybe<Scalars['String']['input']>;
  merchantId?: InputMaybe<Scalars['ID']['input']>;
  notes?: InputMaybe<Scalars['String']['input']>;
  payeeMcc?: InputMaybe<Scalars['Int']['input']>;
  paymentReason?: InputMaybe<Scalars['String']['input']>;
  status?: InputMaybe<TransactionStatus>;
  tags?: InputMaybe<Array<Scalars['String']['input']>>;
  type?: InputMaybe<TransactionType>;
};

export type UpdateTransactionRuleInput = {
  categoryId?: InputMaybe<Scalars['ID']['input']>;
  merchantId?: InputMaybe<Scalars['ID']['input']>;
  pattern?: InputMaybe<Scalars['String']['input']>;
  patternType?: InputMaybe<PatternType>;
  priority?: InputMaybe<Scalars['Int']['input']>;
};

export type UpdateUserInput = {
  displayName?: InputMaybe<Scalars['String']['input']>;
};

export type UsageBudget = {
  __typename: 'UsageBudget';
  alertThreshold: Scalars['Float']['output'];
  createdAt: Scalars['String']['output'];
  currentUsage: Scalars['Float']['output'];
  id: Scalars['ID']['output'];
  limitAmount: Scalars['Float']['output'];
  period: BudgetPeriod;
  scope: BudgetScope;
  scopeId: Scalars['ID']['output'];
  updatedAt: Scalars['String']['output'];
};

export enum UsagePeriod {
  Daily = 'DAILY',
  Monthly = 'MONTHLY',
  Weekly = 'WEEKLY'
}

export type UsageStats = {
  __typename: 'UsageStats';
  completionTokens: Scalars['Int']['output'];
  cost: Scalars['Float']['output'];
  endTime: Scalars['String']['output'];
  featureUsage: Array<FeatureUsage>;
  modelUsage: Array<ModelUsage>;
  period: UsagePeriod;
  promptTokens: Scalars['Int']['output'];
  requests: Scalars['Int']['output'];
  startTime: Scalars['String']['output'];
  totalTokens: Scalars['Int']['output'];
};

export type User = {
  __typename: 'User';
  avatarUrl: Maybe<Scalars['String']['output']>;
  createdAt: Scalars['String']['output'];
  displayName: Maybe<Scalars['String']['output']>;
  email: Scalars['String']['output'];
  emailVerified: Scalars['Boolean']['output'];
  emailVerifiedAt: Maybe<Scalars['String']['output']>;
  enabled: Scalars['Boolean']['output'];
  groups: Array<Group>;
  id: Scalars['ID']['output'];
  permissions: Array<Permission>;
  roles: Array<Role>;
  updatedAt: Scalars['String']['output'];
};

export type UserConnection = {
  __typename: 'UserConnection';
  edges: Array<UserEdge>;
  pageInfo: PageInfo;
  totalCount: Maybe<Scalars['Int']['output']>;
};

export type UserEdge = {
  __typename: 'UserEdge';
  cursor: Scalars['String']['output'];
  node: User;
};

export type UserOrderByInput = {
  direction: OrderDirection;
  field: UserOrderField;
};

export enum UserOrderField {
  DisplayName = 'DISPLAY_NAME',
  Email = 'EMAIL'
}

/** Indicates the origin and reliability of a holding's current value. */
export enum ValuationSource {
  /** Value computed from the contracted rate with no external variables (PRE_FIXADO only). Exact. */
  Calculated = 'CALCULATED',
  /** Value projected using latest economic indicators (CDI, IPCA, etc.). Approximate. */
  Estimated = 'ESTIMATED',
  /** Value entered by the user for a manual fund holding. */
  Manual = 'MANUAL',
  /** Value provided by the app's internal market-data services (e.g. CVM NAV). */
  MarketData = 'MARKET_DATA',
  /** Value provided by the data provider (Open Finance / file import). Authoritative. */
  Provider = 'PROVIDER',
  /** Value cannot be determined (unsupported indexer, missing data, or no issueDate). */
  Unavailable = 'UNAVAILABLE'
}

export type VerificationStatus = {
  __typename: 'VerificationStatus';
  canResendCode: Scalars['Boolean']['output'];
  emailVerified: Scalars['Boolean']['output'];
  emailVerifiedAt: Maybe<Scalars['String']['output']>;
  nextResendAvailableAt: Maybe<Scalars['String']['output']>;
  verificationCodeExpiresAt: Maybe<Scalars['String']['output']>;
  verificationCodeSentAt: Maybe<Scalars['String']['output']>;
};

export type VerifyEmailPayload = {
  __typename: 'VerifyEmailPayload';
  attemptsRemaining: Maybe<Scalars['Int']['output']>;
  message: Maybe<Scalars['String']['output']>;
  success: Scalars['Boolean']['output'];
  user: Maybe<User>;
};

export enum Join__Graph {
  Assets = 'ASSETS',
  Assistant = 'ASSISTANT',
  Banking = 'BANKING',
  Comms = 'COMMS',
  Financialdata = 'FINANCIALDATA',
  Security = 'SECURITY'
}

export enum Link__Purpose {
  /** `EXECUTION` features provide metadata necessary for operation execution. */
  Execution = 'EXECUTION',
  /** `SECURITY` features provide metadata necessary to securely resolve fields. */
  Security = 'SECURITY'
}
