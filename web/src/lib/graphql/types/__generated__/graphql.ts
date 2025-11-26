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

export type BaseEntityInput = {
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

export type Mutation = {
  __typename: 'Mutation';
  createCustomer: Customer;
  createProduct: Product;
  deleteCustomer: Scalars['Boolean']['output'];
  deleteProduct: Scalars['Boolean']['output'];
  requestPasswordReset: RequestPasswordResetPayload;
  resetPassword: ResetPasswordPayload;
  signIn: SignInPayload;
  signInWithOAuth: SignInPayload;
  signUp: SignUpPayload;
  updateCustomer: Customer;
  updateProduct: Product;
};


export type MutationCreateCustomerArgs = {
  input: CustomerInput;
};


export type MutationCreateProductArgs = {
  input: ProductInput;
};


export type MutationDeleteCustomerArgs = {
  id: Scalars['ID']['input'];
};


export type MutationDeleteProductArgs = {
  id: Scalars['ID']['input'];
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


export type MutationUpdateProductArgs = {
  id: Scalars['ID']['input'];
  input: ProductInput;
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
  currentUser: Maybe<User>;
  customer: Maybe<Customer>;
  customers: Array<Customer>;
  product: Maybe<Product>;
  products: Array<Product>;
};


export type QueryCustomerArgs = {
  id: Scalars['ID']['input'];
};


export type QueryProductArgs = {
  id: Scalars['ID']['input'];
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

export type User = {
  __typename: 'User';
  displayName: Maybe<Scalars['String']['output']>;
  email: Scalars['String']['output'];
  id: Scalars['ID']['output'];
};

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
