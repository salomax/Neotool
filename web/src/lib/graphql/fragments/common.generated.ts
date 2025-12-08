export type Maybe<T> = T | null;
export type InputMaybe<T> = Maybe<T>;
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
  description: Maybe<Scalars['String']['output']>;
  id: Scalars['ID']['output'];
  members: Array<User>;
  name: Scalars['String']['output'];
  roles: Array<Role>;
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
  assignRoleToUser: User;
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
  removeRoleFromUser: User;
  requestPasswordReset: RequestPasswordResetPayload;
  resetPassword: ResetPasswordPayload;
  signIn: SignInPayload;
  signInWithOAuth: SignInPayload;
  signUp: SignUpPayload;
  updateCustomer: Customer;
  updateGroup: Group;
  updateProduct: Product;
  updateRole: Role;
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


export type MutationAssignRoleToUserArgs = {
  roleId: Scalars['ID']['input'];
  userId: Scalars['ID']['input'];
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


export type MutationRemoveRoleFromUserArgs = {
  roleId: Scalars['ID']['input'];
  userId: Scalars['ID']['input'];
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
  getUserPermissions: Array<Permission>;
  getUserRoles: Array<Role>;
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


export type QueryGetUserPermissionsArgs = {
  userId: Scalars['ID']['input'];
};


export type QueryGetUserRolesArgs = {
  userId: Scalars['ID']['input'];
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
  id: Scalars['ID']['output'];
  name: Scalars['String']['output'];
  permissions: Array<Permission>;
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

export type User = {
  __typename: 'User';
  displayName: Maybe<Scalars['String']['output']>;
  email: Scalars['String']['output'];
  enabled: Scalars['Boolean']['output'];
  groups: Array<Group>;
  id: Scalars['ID']['output'];
  permissions: Array<Permission>;
  roles: Array<Role>;
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

export type CustomerFieldsFragment = { __typename: 'Customer', id: string, name: string, email: string, status: string, createdAt: string | null, updatedAt: string | null };

export type ProductFieldsFragment = { __typename: 'Product', id: string, name: string, sku: string, priceCents: number, stock: number, createdAt: string | null, updatedAt: string | null };

export type UserFieldsFragment = { __typename: 'User', id: string, email: string, displayName: string | null, enabled: boolean };

export type GroupFieldsFragment = { __typename: 'Group', id: string, name: string, description: string | null, members: Array<{ __typename: 'User', id: string, email: string, displayName: string | null, enabled: boolean }> };

export type RoleFieldsFragment = { __typename: 'Role', id: string, name: string };

export type PermissionFieldsFragment = { __typename: 'Permission', id: string, name: string };

export type SignInMutationVariables = Exact<{
  input: SignInInput;
}>;


export type SignInMutation = { signIn: { __typename: 'SignInPayload', token: string, refreshToken: string | null, user: { __typename: 'User', id: string, email: string, displayName: string | null, roles: Array<{ __typename: 'Role', id: string, name: string }>, permissions: Array<{ __typename: 'Permission', id: string, name: string }> } } };

export type SignInWithOAuthMutationVariables = Exact<{
  input: SignInWithOAuthInput;
}>;


export type SignInWithOAuthMutation = { signInWithOAuth: { __typename: 'SignInPayload', token: string, refreshToken: string | null, user: { __typename: 'User', id: string, email: string, displayName: string | null, roles: Array<{ __typename: 'Role', id: string, name: string }>, permissions: Array<{ __typename: 'Permission', id: string, name: string }> } } };

export type SignUpMutationVariables = Exact<{
  input: SignUpInput;
}>;


export type SignUpMutation = { signUp: { __typename: 'SignUpPayload', token: string, refreshToken: string | null, user: { __typename: 'User', id: string, email: string, displayName: string | null, roles: Array<{ __typename: 'Role', id: string, name: string }>, permissions: Array<{ __typename: 'Permission', id: string, name: string }> } } };

export type RequestPasswordResetMutationVariables = Exact<{
  input: RequestPasswordResetInput;
}>;


export type RequestPasswordResetMutation = { requestPasswordReset: { __typename: 'RequestPasswordResetPayload', success: boolean, message: string } };

export type ResetPasswordMutationVariables = Exact<{
  input: ResetPasswordInput;
}>;


export type ResetPasswordMutation = { resetPassword: { __typename: 'ResetPasswordPayload', success: boolean, message: string } };

export type CurrentUserQueryVariables = Exact<{ [key: string]: never; }>;


export type CurrentUserQuery = { currentUser: { __typename: 'User', id: string, email: string, displayName: string | null, roles: Array<{ __typename: 'Role', id: string, name: string }>, permissions: Array<{ __typename: 'Permission', id: string, name: string }> } | null };

export type EnableUserMutationVariables = Exact<{
  userId: Scalars['ID']['input'];
}>;


export type EnableUserMutation = { enableUser: { __typename: 'User', id: string, email: string, displayName: string | null, enabled: boolean } };

export type DisableUserMutationVariables = Exact<{
  userId: Scalars['ID']['input'];
}>;


export type DisableUserMutation = { disableUser: { __typename: 'User', id: string, email: string, displayName: string | null, enabled: boolean } };

export type CreateGroupMutationVariables = Exact<{
  input: CreateGroupInput;
}>;


export type CreateGroupMutation = { createGroup: { __typename: 'Group', id: string, name: string, description: string | null, members: Array<{ __typename: 'User', id: string, email: string, displayName: string | null, enabled: boolean }> } };

export type UpdateGroupMutationVariables = Exact<{
  groupId: Scalars['ID']['input'];
  input: UpdateGroupInput;
}>;


export type UpdateGroupMutation = { updateGroup: { __typename: 'Group', id: string, name: string, description: string | null, members: Array<{ __typename: 'User', id: string, email: string, displayName: string | null, enabled: boolean }> } };

export type DeleteGroupMutationVariables = Exact<{
  groupId: Scalars['ID']['input'];
}>;


export type DeleteGroupMutation = { deleteGroup: boolean };

export type CreateRoleMutationVariables = Exact<{
  input: CreateRoleInput;
}>;


export type CreateRoleMutation = { createRole: { __typename: 'Role', id: string, name: string } };

export type UpdateRoleMutationVariables = Exact<{
  roleId: Scalars['ID']['input'];
  input: UpdateRoleInput;
}>;


export type UpdateRoleMutation = { updateRole: { __typename: 'Role', id: string, name: string } };

export type DeleteRoleMutationVariables = Exact<{
  roleId: Scalars['ID']['input'];
}>;


export type DeleteRoleMutation = { deleteRole: boolean };

export type AssignPermissionToRoleMutationVariables = Exact<{
  roleId: Scalars['ID']['input'];
  permissionId: Scalars['ID']['input'];
}>;


export type AssignPermissionToRoleMutation = { assignPermissionToRole: { __typename: 'Role', id: string, name: string } };

export type RemovePermissionFromRoleMutationVariables = Exact<{
  roleId: Scalars['ID']['input'];
  permissionId: Scalars['ID']['input'];
}>;


export type RemovePermissionFromRoleMutation = { removePermissionFromRole: { __typename: 'Role', id: string, name: string } };

export type AssignRoleToUserMutationVariables = Exact<{
  userId: Scalars['ID']['input'];
  roleId: Scalars['ID']['input'];
}>;


export type AssignRoleToUserMutation = { assignRoleToUser: { __typename: 'User', id: string, email: string, displayName: string | null, enabled: boolean } };

export type RemoveRoleFromUserMutationVariables = Exact<{
  userId: Scalars['ID']['input'];
  roleId: Scalars['ID']['input'];
}>;


export type RemoveRoleFromUserMutation = { removeRoleFromUser: { __typename: 'User', id: string, email: string, displayName: string | null, enabled: boolean } };

export type AssignGroupToUserMutationVariables = Exact<{
  userId: Scalars['ID']['input'];
  groupId: Scalars['ID']['input'];
}>;


export type AssignGroupToUserMutation = { assignGroupToUser: { __typename: 'User', id: string, email: string, displayName: string | null, enabled: boolean } };

export type RemoveGroupFromUserMutationVariables = Exact<{
  userId: Scalars['ID']['input'];
  groupId: Scalars['ID']['input'];
}>;


export type RemoveGroupFromUserMutation = { removeGroupFromUser: { __typename: 'User', id: string, email: string, displayName: string | null, enabled: boolean } };

export type AssignRoleToGroupMutationVariables = Exact<{
  groupId: Scalars['ID']['input'];
  roleId: Scalars['ID']['input'];
}>;


export type AssignRoleToGroupMutation = { assignRoleToGroup: { __typename: 'Group', id: string, name: string, description: string | null, members: Array<{ __typename: 'User', id: string, email: string, displayName: string | null, enabled: boolean }> } };

export type RemoveRoleFromGroupMutationVariables = Exact<{
  groupId: Scalars['ID']['input'];
  roleId: Scalars['ID']['input'];
}>;


export type RemoveRoleFromGroupMutation = { removeRoleFromGroup: { __typename: 'Group', id: string, name: string, description: string | null, members: Array<{ __typename: 'User', id: string, email: string, displayName: string | null, enabled: boolean }> } };

export type GetUsersQueryVariables = Exact<{
  first?: InputMaybe<Scalars['Int']['input']>;
  after?: InputMaybe<Scalars['String']['input']>;
  query?: InputMaybe<Scalars['String']['input']>;
  orderBy?: InputMaybe<Array<UserOrderByInput> | UserOrderByInput>;
}>;


export type GetUsersQuery = { users: { __typename: 'UserConnection', totalCount: number | null, edges: Array<{ __typename: 'UserEdge', cursor: string, node: { __typename: 'User', id: string, email: string, displayName: string | null, enabled: boolean } }>, pageInfo: { __typename: 'PageInfo', hasNextPage: boolean, hasPreviousPage: boolean, startCursor: string | null, endCursor: string | null } } };

export type GetGroupsQueryVariables = Exact<{
  first?: InputMaybe<Scalars['Int']['input']>;
  after?: InputMaybe<Scalars['String']['input']>;
  query?: InputMaybe<Scalars['String']['input']>;
  orderBy?: InputMaybe<Array<GroupOrderByInput> | GroupOrderByInput>;
}>;


export type GetGroupsQuery = { groups: { __typename: 'GroupConnection', totalCount: number | null, edges: Array<{ __typename: 'GroupEdge', cursor: string, node: { __typename: 'Group', id: string, name: string, description: string | null, members: Array<{ __typename: 'User', id: string, email: string, displayName: string | null, enabled: boolean }> } }>, pageInfo: { __typename: 'PageInfo', hasNextPage: boolean, hasPreviousPage: boolean, startCursor: string | null, endCursor: string | null } } };

export type GetRolesQueryVariables = Exact<{
  first?: InputMaybe<Scalars['Int']['input']>;
  after?: InputMaybe<Scalars['String']['input']>;
  query?: InputMaybe<Scalars['String']['input']>;
  orderBy?: InputMaybe<Array<RoleOrderByInput> | RoleOrderByInput>;
}>;


export type GetRolesQuery = { roles: { __typename: 'RoleConnection', totalCount: number | null, edges: Array<{ __typename: 'RoleEdge', cursor: string, node: { __typename: 'Role', id: string, name: string } }>, pageInfo: { __typename: 'PageInfo', hasNextPage: boolean, hasPreviousPage: boolean, startCursor: string | null, endCursor: string | null } } };

export type GetPermissionsQueryVariables = Exact<{
  first?: InputMaybe<Scalars['Int']['input']>;
  after?: InputMaybe<Scalars['String']['input']>;
  query?: InputMaybe<Scalars['String']['input']>;
}>;


export type GetPermissionsQuery = { permissions: { __typename: 'PermissionConnection', edges: Array<{ __typename: 'PermissionEdge', cursor: string, node: { __typename: 'Permission', id: string, name: string } }>, pageInfo: { __typename: 'PageInfo', hasNextPage: boolean, hasPreviousPage: boolean, startCursor: string | null, endCursor: string | null } } };

export type GetUserWithRelationshipsQueryVariables = Exact<{
  id: Scalars['ID']['input'];
}>;


export type GetUserWithRelationshipsQuery = { user: { __typename: 'User', id: string, email: string, displayName: string | null, enabled: boolean, groups: Array<{ __typename: 'Group', id: string, name: string, description: string | null, members: Array<{ __typename: 'User', id: string, email: string, displayName: string | null, enabled: boolean }> }>, roles: Array<{ __typename: 'Role', id: string, name: string }>, permissions: Array<{ __typename: 'Permission', id: string, name: string }> } | null };

export type GetGroupWithRelationshipsQueryVariables = Exact<{
  id: Scalars['ID']['input'];
}>;


export type GetGroupWithRelationshipsQuery = { group: { __typename: 'Group', id: string, name: string, description: string | null, roles: Array<{ __typename: 'Role', id: string, name: string }>, members: Array<{ __typename: 'User', id: string, email: string, displayName: string | null, enabled: boolean }> } | null };

export type GetRolesWithPermissionsQueryVariables = Exact<{ [key: string]: never; }>;


export type GetRolesWithPermissionsQuery = { roles: { __typename: 'RoleConnection', edges: Array<{ __typename: 'RoleEdge', node: { __typename: 'Role', id: string, name: string, permissions: Array<{ __typename: 'Permission', id: string, name: string }> } }> } };

export type GetRoleWithUsersAndGroupsQueryVariables = Exact<{ [key: string]: never; }>;


export type GetRoleWithUsersAndGroupsQuery = { users: { __typename: 'UserConnection', edges: Array<{ __typename: 'UserEdge', node: { __typename: 'User', id: string, email: string, displayName: string | null, enabled: boolean, roles: Array<{ __typename: 'Role', id: string, name: string }> } }> }, groups: { __typename: 'GroupConnection', edges: Array<{ __typename: 'GroupEdge', node: { __typename: 'Group', id: string, name: string, description: string | null, roles: Array<{ __typename: 'Role', id: string, name: string }>, members: Array<{ __typename: 'User', id: string, email: string, displayName: string | null, enabled: boolean }> } }> } };

export type CreateCustomerMutationVariables = Exact<{
  input: CustomerInput;
}>;


export type CreateCustomerMutation = { createCustomer: { __typename: 'Customer', id: string, name: string, email: string, status: string, createdAt: string | null, updatedAt: string | null } };

export type UpdateCustomerMutationVariables = Exact<{
  id: Scalars['ID']['input'];
  input: CustomerInput;
}>;


export type UpdateCustomerMutation = { updateCustomer: { __typename: 'Customer', id: string, name: string, email: string, status: string, createdAt: string | null, updatedAt: string | null } };

export type DeleteCustomerMutationVariables = Exact<{
  id: Scalars['ID']['input'];
}>;


export type DeleteCustomerMutation = { deleteCustomer: boolean };

export type GetCustomersQueryVariables = Exact<{ [key: string]: never; }>;


export type GetCustomersQuery = { customers: Array<{ __typename: 'Customer', id: string, name: string, email: string, status: string, createdAt: string | null, updatedAt: string | null }> };

export type GetCustomerQueryVariables = Exact<{
  id: Scalars['ID']['input'];
}>;


export type GetCustomerQuery = { customer: { __typename: 'Customer', id: string, name: string, email: string, status: string, createdAt: string | null, updatedAt: string | null } | null };

export type CreateProductMutationVariables = Exact<{
  input: ProductInput;
}>;


export type CreateProductMutation = { createProduct: { __typename: 'Product', id: string, name: string, sku: string, priceCents: number, stock: number, createdAt: string | null, updatedAt: string | null } };

export type UpdateProductMutationVariables = Exact<{
  id: Scalars['ID']['input'];
  input: ProductInput;
}>;


export type UpdateProductMutation = { updateProduct: { __typename: 'Product', id: string, name: string, sku: string, priceCents: number, stock: number, createdAt: string | null, updatedAt: string | null } };

export type DeleteProductMutationVariables = Exact<{
  id: Scalars['ID']['input'];
}>;


export type DeleteProductMutation = { deleteProduct: boolean };

export type GetProductsQueryVariables = Exact<{ [key: string]: never; }>;


export type GetProductsQuery = { products: Array<{ __typename: 'Product', id: string, name: string, sku: string, priceCents: number, stock: number, createdAt: string | null, updatedAt: string | null }> };

export type GetProductQueryVariables = Exact<{
  id: Scalars['ID']['input'];
}>;


export type GetProductQuery = { product: { __typename: 'Product', id: string, name: string, sku: string, priceCents: number, stock: number, createdAt: string | null, updatedAt: string | null } | null };
