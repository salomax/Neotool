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

/** Asset visibility level - determines access control and URL generation. */
export enum AssetVisibility {
  /** Private asset - requires ownership check, uses presigned download URLs */
  Private = 'PRIVATE',
  /** Public asset - accessible without owner check, uses stable CDN URLs */
  Public = 'PUBLIC'
}

export type AuthorizationResult = {
  __typename: 'AuthorizationResult';
  allowed: Scalars['Boolean']['output'];
  reason: Scalars['String']['output'];
};

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
  maskedNumber?: InputMaybe<Scalars['String']['input']>;
};

/** Category applicability (income, expense, transfer, or any). */
export enum CategoryApplicableTo {
  Any = 'ANY',
  Expense = 'EXPENSE',
  Income = 'INCOME',
  Transfer = 'TRANSFER'
}

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

export enum ConnectionStatus {
  Connected = 'CONNECTED',
  ConsentExpired = 'CONSENT_EXPIRED',
  ConsentExpiring = 'CONSENT_EXPIRING',
  Disconnected = 'DISCONNECTED',
  Error = 'ERROR',
  Syncing = 'SYNCING'
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
  /**
   * Optional owner ID for service-to-service uploads (e.g. banking import: accountId as tenant).
   * When provided, used as asset owner instead of the authenticated user ID.
   */
  ownerId?: InputMaybe<Scalars['String']['input']>;
  /** File size in bytes */
  sizeBytes: Scalars['Long']['input'];
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
  notes?: InputMaybe<Scalars['String']['input']>;
  payeeMcc?: InputMaybe<Scalars['Int']['input']>;
  paymentReason?: InputMaybe<Scalars['String']['input']>;
  status?: InputMaybe<TransactionStatus>;
  tags?: InputMaybe<Array<Scalars['String']['input']>>;
  totalInstallments?: InputMaybe<Scalars['Int']['input']>;
  type: TransactionType;
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
  FixedIncome = 'FIXED_INCOME',
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
  ReviewRequired = 'REVIEW_REQUIRED'
}

export type ImportTransactionsFileInput = {
  fileBase64: Scalars['String']['input'];
  fileName: Scalars['String']['input'];
  financialAccountId: Scalars['ID']['input'];
  source?: InputMaybe<ImportSource>;
};

export type IndicatorRangeInput = {
  from?: InputMaybe<Scalars['Date']['input']>;
  last?: InputMaybe<Scalars['Int']['input']>;
  to?: InputMaybe<Scalars['Date']['input']>;
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

/** Financial institution associated with an account. */
export type Issuer = {
  __typename: 'Issuer';
  compeCode: Maybe<Scalars['String']['output']>;
  countryCode: Scalars['String']['output'];
  domain: Maybe<Scalars['String']['output']>;
  id: Scalars['ID']['output'];
  ispb: Maybe<Scalars['String']['output']>;
  logoAsset: Maybe<Asset>;
  logoKey: Maybe<Scalars['String']['output']>;
  name: Scalars['String']['output'];
};

/**
 * Monthly summary header for a financial account — entradas, saídas, saldo total.
 * Served from the monthly_balance_snapshots cache; transfer and projection fields are on-demand.
 */
export type MonthSummary = {
  __typename: 'MonthSummary';
  /** Saldo total at month-end (includes all types, excludes FUTURE). */
  closingBalance: Scalars['Float']['output'];
  /** True when (year, month) equals the current calendar month. */
  isCurrentMonth: Scalars['Boolean']['output'];
  month: Scalars['Int']['output'];
  /** Balance at start of month (prior month's closingBalance). */
  openingBalance: Scalars['Float']['output'];
  /** closingBalance + FUTURE transactions. Null when no FUTURE transactions exist. */
  projectedClosingBalance: Maybe<Scalars['Float']['output']>;
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
  createRole: Role;
  /** Create a new manual transaction. */
  createTransaction: Transaction;
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
  deleteRole: Scalars['Boolean']['output'];
  /** Soft-delete a manual transaction. */
  deleteTransaction: Scalars['Boolean']['output'];
  disableUser: User;
  enableUser: User;
  importNfce: ImportResult;
  importTransactionsFile: ImportResult;
  refreshAccessToken: SignInPayload;
  removeGroupFromUser: User;
  removePermissionFromRole: Role;
  removeRoleFromGroup: Group;
  requestEmailSend: EmailSendResult;
  requestPasswordReset: RequestPasswordResetPayload;
  resendVerificationEmail: ResendVerificationEmailPayload;
  resetPassword: ResetPasswordPayload;
  resolveImportDuplicate: TransactionImport;
  signIn: SignInPayload;
  signInWithOAuth: SignInPayload;
  signUp: SignUpPayload;
  updateGroup: Group;
  updateRole: Role;
  /** Update an existing manual transaction. */
  updateTransaction: Transaction;
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


export type MutationConfirmAssetUploadArgs = {
  input: ConfirmAssetUploadInput;
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


export type MutationCreateRoleArgs = {
  input: CreateRoleInput;
};


export type MutationCreateTransactionArgs = {
  input: CreateTransactionInput;
};


export type MutationDeleteAssetArgs = {
  assetId: Scalars['ID']['input'];
};


export type MutationDeleteGroupArgs = {
  groupId: Scalars['ID']['input'];
};


export type MutationDeleteRoleArgs = {
  roleId: Scalars['ID']['input'];
};


export type MutationDeleteTransactionArgs = {
  id: Scalars['ID']['input'];
};


export type MutationDisableUserArgs = {
  userId: Scalars['ID']['input'];
};


export type MutationEnableUserArgs = {
  userId: Scalars['ID']['input'];
};


export type MutationImportNfceArgs = {
  financialAccountId: Scalars['ID']['input'];
  qrCodeUrl: Scalars['String']['input'];
};


export type MutationImportTransactionsFileArgs = {
  input: ImportTransactionsFileInput;
};


export type MutationRefreshAccessTokenArgs = {
  input: RefreshAccessTokenInput;
};


export type MutationRemoveGroupFromUserArgs = {
  groupId: Scalars['ID']['input'];
  userId: Scalars['ID']['input'];
};


export type MutationRemovePermissionFromRoleArgs = {
  permissionId: Scalars['ID']['input'];
  roleId: Scalars['ID']['input'];
};


export type MutationRemoveRoleFromGroupArgs = {
  groupId: Scalars['ID']['input'];
  roleId: Scalars['ID']['input'];
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


export type MutationSignInArgs = {
  input: SignInInput;
};


export type MutationSignInWithOAuthArgs = {
  input: SignInWithOAuthInput;
};


export type MutationSignUpArgs = {
  input: SignUpInput;
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
  /**
   * Get asset by ID.
   * Returns null if asset not found or user doesn't have access.
   */
  asset: Maybe<Asset>;
  bacenFinanceInstitution: Maybe<BacenFinanceInstitution>;
  bacenInstitutionSlugs: BacenInstitutionSlugConnection;
  bacenInstitutions: BacenInstitutionConnection;
  checkPermission: AuthorizationResult;
  currentUser: Maybe<User>;
  economicIndicators: Array<EconomicIndicator>;
  /** All financial accounts belonging to the authenticated user's account. */
  financialAccounts: Array<FinancialAccount>;
  fixedIncomeComparison: FixedIncomeComparisonResult;
  group: Maybe<Group>;
  groups: GroupConnection;
  isUserActiveMemberOfAccount: Scalars['Boolean']['output'];
  /** All active issuers. Small list — no pagination or filter needed. */
  issuers: Array<Issuer>;
  /**
   * Monthly summary (entradas, saídas, saldo) for a financial account or all accounts.
   * When financialAccountId is omitted, returns the aggregate across all accounts.
   * When month/year are not provided, the current month/year are used.
   */
  monthSummary: MonthSummary;
  myVerificationStatus: Maybe<VerificationStatus>;
  permissions: PermissionConnection;
  ratingIssuers: Array<RatingIssuer>;
  role: Maybe<Role>;
  roles: RoleConnection;
  searchBacenInstitutionsAutocomplete: BacenInstitutionAutocompleteConnection;
  /** Single transaction by ID (scoped to the authenticated account). */
  transaction: Maybe<Transaction>;
  /** Distinct values used in transaction attribution ("belongs to"), across all account transactions. */
  transactionBelongsToOptions: Array<TransactionBelongsToOption>;
  /** All categories (system + custom) available to the authenticated account. */
  transactionCategories: Array<TransactionCategory>;
  transactionImport: Maybe<TransactionImport>;
  transactionImports: Array<TransactionImport>;
  /** Distinct tags used in transactions for the authenticated account. */
  transactionTags: Array<TransactionTagOption>;
  /**
   * Transactions for a single financial account or all accounts belonging to the authenticated account.
   * Results are filtered by month/year and paginated using Relay-style cursors.
   * When month/year are not provided, the current month/year are used by the backend.
   */
  transactions: TransactionConnection;
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


export type QueryCheckPermissionArgs = {
  permission: Scalars['String']['input'];
  resourceId?: InputMaybe<Scalars['ID']['input']>;
  userId: Scalars['ID']['input'];
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


export type QueryIsUserActiveMemberOfAccountArgs = {
  accountId: Scalars['ID']['input'];
  userId: Scalars['ID']['input'];
};


export type QueryMonthSummaryArgs = {
  financialAccountId?: InputMaybe<Scalars['ID']['input']>;
  month?: InputMaybe<Scalars['Int']['input']>;
  year?: InputMaybe<Scalars['Int']['input']>;
};


export type QueryPermissionsArgs = {
  after?: InputMaybe<Scalars['String']['input']>;
  first?: InputMaybe<Scalars['Int']['input']>;
  query?: InputMaybe<Scalars['String']['input']>;
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
  financialAccountId?: InputMaybe<Scalars['ID']['input']>;
  first?: InputMaybe<Scalars['Int']['input']>;
  month?: InputMaybe<Scalars['Int']['input']>;
  tags?: InputMaybe<Array<Scalars['String']['input']>>;
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

export type RefreshAccessTokenInput = {
  refreshToken: Scalars['String']['input'];
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

/** Core ledger transaction row. */
export type Transaction = {
  __typename: 'Transaction';
  amount: Scalars['Float']['output'];
  /** BRL equivalent for FX transactions (null for BRL). */
  amountBrl: Maybe<Scalars['Float']['output']>;
  belongsToName: Maybe<Scalars['String']['output']>;
  belongsToUserId: Maybe<Scalars['ID']['output']>;
  billPostDate: Maybe<Scalars['Date']['output']>;
  cardIdentifier: Maybe<Scalars['String']['output']>;
  category: Maybe<TransactionCategory>;
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

/** How the transaction entered the system. */
export enum TransactionSource {
  Manual = 'MANUAL',
  OpenFinance = 'OPEN_FINANCE',
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
  notes?: InputMaybe<Scalars['String']['input']>;
  payeeMcc?: InputMaybe<Scalars['Int']['input']>;
  paymentReason?: InputMaybe<Scalars['String']['input']>;
  status?: InputMaybe<TransactionStatus>;
  tags?: InputMaybe<Array<Scalars['String']['input']>>;
  type?: InputMaybe<TransactionType>;
};

export type UpdateUserInput = {
  displayName?: InputMaybe<Scalars['String']['input']>;
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
