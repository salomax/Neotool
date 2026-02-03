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

export type BaseEntityInput = {
  name: Scalars['String']['input'];
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

export type CreateGroupInput = {
  description?: InputMaybe<Scalars['String']['input']>;
  name: Scalars['String']['input'];
  userIds?: InputMaybe<Array<Scalars['ID']['input']>>;
};

export type CreateRoleInput = {
  name: Scalars['String']['input'];
};

export type Customer = {
  __typename: 'Customer';
  createdAt: Maybe<Scalars['String']['output']>;
  email: Scalars['String']['output'];
  id: Scalars['ID']['output'];
  name: Scalars['String']['output'];
  status: Scalars['String']['output'];
  updatedAt: Maybe<Scalars['String']['output']>;
  version: Scalars['Int']['output'];
};

export type CustomerInput = {
  email: Scalars['String']['input'];
  name: Scalars['String']['input'];
  status: Scalars['String']['input'];
};

export enum CustomerStatus {
  Active = 'ACTIVE',
  Inactive = 'INACTIVE',
  Pending = 'PENDING'
}

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

export type Mutation = {
  __typename: 'Mutation';
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
  createCustomer: Customer;
  createGroup: Group;
  createProduct: Product;
  createRole: Role;
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
  deleteCustomer: Scalars['Boolean']['output'];
  deleteGroup: Scalars['Boolean']['output'];
  deleteProduct: Scalars['Boolean']['output'];
  deleteRole: Scalars['Boolean']['output'];
  disableUser: User;
  enableUser: User;
  refreshAccessToken: SignInPayload;
  removeGroupFromUser: User;
  removePermissionFromRole: Role;
  removeRoleFromGroup: Group;
  requestEmailSend: EmailSendResult;
  requestPasswordReset: RequestPasswordResetPayload;
  resendVerificationEmail: ResendVerificationEmailPayload;
  resetPassword: ResetPasswordPayload;
  signIn: SignInPayload;
  signInWithOAuth: SignInPayload;
  signUp: SignUpPayload;
  updateCustomer: Customer;
  updateGroup: Group;
  updateProduct: Product;
  updateRole: Role;
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


export type MutationCreateCustomerArgs = {
  input: CustomerInput;
};


export type MutationCreateGroupArgs = {
  input: CreateGroupInput;
};


export type MutationCreateProductArgs = {
  input: ProductInput;
};


export type MutationCreateRoleArgs = {
  input: CreateRoleInput;
};


export type MutationDeleteAssetArgs = {
  assetId: Scalars['ID']['input'];
};


export type MutationDeleteCustomerArgs = {
  id: Scalars['ID']['input'];
};


export type MutationDeleteGroupArgs = {
  groupId: Scalars['ID']['input'];
};


export type MutationDeleteProductArgs = {
  id: Scalars['ID']['input'];
};


export type MutationDeleteRoleArgs = {
  roleId: Scalars['ID']['input'];
};


export type MutationDisableUserArgs = {
  userId: Scalars['ID']['input'];
};


export type MutationEnableUserArgs = {
  userId: Scalars['ID']['input'];
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


export type MutationSignInArgs = {
  input: SignInInput;
};


export type MutationSignInWithOAuthArgs = {
  input: SignInWithOAuthInput;
};


export type MutationSignUpArgs = {
  input: SignUpInput;
};


export type MutationUpdateCustomerArgs = {
  id: Scalars['ID']['input'];
  input: CustomerInput;
};


export type MutationUpdateGroupArgs = {
  groupId: Scalars['ID']['input'];
  input: UpdateGroupInput;
};


export type MutationUpdateProductArgs = {
  id: Scalars['ID']['input'];
  input: ProductInput;
};


export type MutationUpdateRoleArgs = {
  input: UpdateRoleInput;
  roleId: Scalars['ID']['input'];
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

export type PageInfo = {
  __typename: 'PageInfo';
  endCursor: Maybe<Scalars['String']['output']>;
  hasNextPage: Scalars['Boolean']['output'];
  hasPreviousPage: Scalars['Boolean']['output'];
  startCursor: Maybe<Scalars['String']['output']>;
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

export type Product = {
  __typename: 'Product';
  createdAt: Maybe<Scalars['String']['output']>;
  id: Scalars['ID']['output'];
  name: Scalars['String']['output'];
  priceCents: Scalars['Int']['output'];
  sku: Scalars['String']['output'];
  stock: Scalars['Int']['output'];
  updatedAt: Maybe<Scalars['String']['output']>;
  version: Scalars['Int']['output'];
};

export type ProductInput = {
  name: Scalars['String']['input'];
  priceCents: Scalars['Int']['input'];
  sku: Scalars['String']['input'];
  stock: Scalars['Int']['input'];
};

export type Query = {
  __typename: 'Query';
  _empty: Maybe<Scalars['String']['output']>;
  /**
   * Get asset by ID.
   * Returns null if asset not found or user doesn't have access.
   */
  asset: Maybe<Asset>;
  checkPermission: AuthorizationResult;
  currentUser: Maybe<User>;
  customer: Maybe<Customer>;
  customers: Array<Customer>;
  group: Maybe<Group>;
  groups: GroupConnection;
  myVerificationStatus: Maybe<VerificationStatus>;
  permissions: PermissionConnection;
  product: Maybe<Product>;
  products: Array<Product>;
  role: Maybe<Role>;
  roles: RoleConnection;
  user: Maybe<User>;
  users: UserConnection;
};


export type QueryAssetArgs = {
  id: Scalars['ID']['input'];
};


export type QueryCheckPermissionArgs = {
  permission: Scalars['String']['input'];
  resourceId?: InputMaybe<Scalars['ID']['input']>;
  userId: Scalars['ID']['input'];
};


export type QueryCustomerArgs = {
  id: Scalars['ID']['input'];
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


export type QueryPermissionsArgs = {
  after?: InputMaybe<Scalars['String']['input']>;
  first?: InputMaybe<Scalars['Int']['input']>;
  query?: InputMaybe<Scalars['String']['input']>;
};


export type QueryProductArgs = {
  id: Scalars['ID']['input'];
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


export type QueryUserArgs = {
  id: Scalars['ID']['input'];
};


export type QueryUsersArgs = {
  after?: InputMaybe<Scalars['String']['input']>;
  first?: InputMaybe<Scalars['Int']['input']>;
  orderBy?: InputMaybe<Array<UserOrderByInput>>;
  query?: InputMaybe<Scalars['String']['input']>;
};

export type RefreshAccessTokenInput = {
  refreshToken: Scalars['String']['input'];
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

export type UpdateGroupInput = {
  description?: InputMaybe<Scalars['String']['input']>;
  name: Scalars['String']['input'];
  userIds?: InputMaybe<Array<Scalars['ID']['input']>>;
};

export type UpdateRoleInput = {
  name: Scalars['String']['input'];
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
  App = 'APP',
  Assets = 'ASSETS',
  Comms = 'COMMS',
  Security = 'SECURITY'
}

export enum Link__Purpose {
  /** `EXECUTION` features provide metadata necessary for operation execution. */
  Execution = 'EXECUTION',
  /** `SECURITY` features provide metadata necessary to securely resolve fields. */
  Security = 'SECURITY'
}
