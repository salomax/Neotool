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
  join__FieldSet: { input: unknown; output: unknown; }
  link__Import: { input: unknown; output: unknown; }
};

export type AuthorizationResult = {
  __typename: 'AuthorizationResult';
  allowed: Scalars['Boolean']['output'];
  reason: Scalars['String']['output'];
};

export type BaseEntityInput = {
  name: Scalars['String']['input'];
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
  createCustomer: Customer;
  createGroup: Group;
  createProduct: Product;
  createRole: Role;
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
  requestPasswordReset: RequestPasswordResetPayload;
  resetPassword: ResetPasswordPayload;
  signIn: SignInPayload;
  signInWithOAuth: SignInPayload;
  signUp: SignUpPayload;
  updateCustomer: Customer;
  updateGroup: Group;
  updateProduct: Product;
  updateRole: Role;
  updateUser: User;
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
  checkPermission: AuthorizationResult;
  currentUser: Maybe<User>;
  customer: Maybe<Customer>;
  customers: Array<Customer>;
  group: Maybe<Group>;
  groups: GroupConnection;
  permissions: PermissionConnection;
  product: Maybe<Product>;
  products: Array<Product>;
  role: Maybe<Role>;
  roles: RoleConnection;
  user: Maybe<User>;
  users: UserConnection;
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
  name: Scalars['String']['input'];
  password: Scalars['String']['input'];
};

export type SignUpPayload = {
  __typename: 'SignUpPayload';
  refreshToken: Maybe<Scalars['String']['output']>;
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
  Email = 'EMAIL',
  Enabled = 'ENABLED'
}

export enum Join__Graph {
  App = 'APP',
  Security = 'SECURITY'
}

export enum Link__Purpose {
  /** `EXECUTION` features provide metadata necessary for operation execution. */
  Execution = 'EXECUTION',
  /** `SECURITY` features provide metadata necessary to securely resolve fields. */
  Security = 'SECURITY'
}
