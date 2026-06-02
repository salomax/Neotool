# Feature: User Profile — Account Name, Avatar Upload, Keep Me Connected

**Services:** Invistus Security Service · Invistus Assets Service · Web Frontend  
**Module:** Security — User Profile  
**Document date:** 2026-04-12  
**Status:** Ready for implementation

---

## Overview

This document specifies three features to be added to the User Profile page:

1. **Account Name Change** — Account owners can rename their account directly from the profile page.
2. **Avatar Upload** — Users registered by email (not via OAuth / identity provider) can upload a profile picture.
3. **Keep Me Connected** — OAuth users are kept connected by default; all users can toggle this preference on the profile page.

---

## Codebase Context (read before implementing)

### Security Service

- **Main package:** `io.github.salomax.neotool.security`
- **Build:** `./gradlew :security:testUnit` (unit) / `./gradlew :security:testIntegration` (requires Docker)
- **Framework:** Micronaut (`@Singleton`, `@MicronautTest`, `@Repository`)
- **Clean arch pattern:** domain interfaces → `Default*` implementations → `*EntityRepository` (JPA)
- **DB migrations:** `service/kotlin/security/src/main/resources/db/migration/V0_N__description.sql`
- **Latest migration:** `V0_30__add_financialdata_market_data_export_permission.sql` → next is `V0_31`
- **GraphQL:** `SecurityWiringFactory`, schema at `src/main/resources/graphql/schema.graphqls`

### Relevant Existing Files

| File | Notes |
|---|---|
| `domain/rbac/User.kt` | Domain data class: `id`, `email`, `displayName`, `avatarUrl` |
| `model/Entities.kt` — `UserEntity` | `avatarUrl`, `avatarProvider` (null for email users, `"google"` etc for OAuth), `avatarUpdatedAt`, `passwordHash` |
| `model/account/AccountEntity.kt` | `accountName`, `ownerUserId`, `accountType`, `accountStatus` |
| `model/account/AccountEnums.kt` | `AccountRole` (OWNER/ADMIN/MEMBER/VIEWER), `AccountType` (PERSONAL/FAMILY/BUSINESS) |
| `model/account/AccountMembershipEntity.kt` | Links users to accounts with roles |
| `model/preferences/UserPreferencesEntity.kt` | `userId`, `language`, `theme`, `blurBalances`, `firstDayOfWeek` |
| `domain/AccountManagement.kt` | `UpdateAccountCommand` — validates name (not blank, max 100 chars) |
| `service/management/AccountService.kt` | `update(cmd)` — already enforces OWNER-only via `MembershipPolicyEngine`. **No GraphQL resolver exists yet.** |
| `service/management/UserManagementService.kt` | `getUserById()`, `updateUser()` (only `displayName`) |
| `service/preferences/UserPreferencesService.kt` | `get(userId)` returns defaults; `update(userId, patch...)` upserts |
| `graphql/dto/UserPreferencesDTO.kt` | Current DTO: `language`, `theme`, `blurBalances`, `firstDayOfWeek` |
| `graphql/resolver/UserPreferencesResolver.kt` | Current resolver: `userPreferences` query + `updateUserPreferences` mutation |
| `graphql/SecurityWiringFactory.kt` | Wires all resolvers — add new data fetchers here |
| `schema.graphqls` | Current schema — `Account` type does NOT exist yet |

### Relevant Existing Files — Assets Service

| File | Notes |
|---|---|
| `graphql/resolver/AssetMutationResolver.kt` | `createAssetUpload(input, ownerId, principalType)` + `confirmAssetUpload(input, ownerId, principalType)` |
| `graphql/dto/CreateAssetUploadInput.kt` | `namespace`, `filename`, `mimeType`, `sizeBytes`, `idempotencyKey?` |
| `config/AssetConfigProperties.kt` | Loads `asset-config.yml`; namespace `user-profiles` is PRIVATE, 2 MB |
| `storage/BucketResolver.kt` | `getPublicBaseUrl()` — CDN base URL for PUBLIC assets |
| `storage/S3StorageClient.kt` | Handles presigned PUT (upload) and GET (download) URLs |

### Web Frontend

| File | Notes |
|---|---|
| `web/src/app/profile/page.tsx` | Current profile page — has preferences section (theme, language, rule mode). Avatar displayed read-only. No account name edit. |
| `web/src/shared/ui/shell/UserProfileMenu.tsx` | Avatar shown in top-bar using `user.avatarUrl` |
| `web/src/lib/graphql/operations/auth/queries.ts` | `CURRENT_USER`, `USER_PREFERENCES` |
| `web/src/lib/graphql/operations/auth/mutations.ts` | `UPDATE_USER_PREFERENCES` |
| `shared/providers/useAuth` | Provides `user` (including `avatarUrl`) |

---

## Feature 1 — Account Name Change (Owner Only)

### 1.1 Requirements

- An authenticated user who is the **OWNER** of an account can rename that account.
- Account owners can see and rename **all accounts they own** (PERSONAL, FAMILY, BUSINESS).
- Non-owners (ADMIN, MEMBER, VIEWER) see the account name in read-only mode.
- Name constraints: not blank, max 100 characters (already enforced in `UpdateAccountCommand`).
- Account `type` is immutable — not exposed for editing.

### 1.2 Backend — Security Service

#### 1.2.1 What Already Exists (do NOT recreate)

- `AccountService.update(UpdateAccountCommand)` — fully implemented with owner-only enforcement. Do not modify.
- `AccountManagement.UpdateAccountCommand` — validates name length and blank check.
- `MembershipPolicyEngine` + `MembershipOperation.UPDATE_ACCOUNT` — enforces OWNER role.

#### 1.2.2 What to Build

**GraphQL Schema additions** (`schema.graphqls`):

```graphql
type Account {
  id: ID!
  accountName: String!
  accountType: String!   # PERSONAL | FAMILY | BUSINESS
  accountStatus: String! # ACTIVE | SUSPENDED | DELETED
  isOwner: Boolean!      # true when the current user is the OWNER
}

input UpdateAccountInput {
  accountName: String!
}

extend type Query {
  myAccounts: [Account!]!
}

extend type Mutation {
  updateAccount(accountId: ID!, input: UpdateAccountInput!): Account!
}
```

**New Resolver** — `graphql/resolver/AccountResolver.kt`:

```kotlin
@Singleton
class AccountResolver(
    private val accountService: AccountService,
    private val accountMembershipRepository: AccountMembershipRepository,
) {
    fun myAccounts(principal: RequestPrincipal): List<AccountDTO> {
        val userId = requireNotNull(principal.userId)
        // Find all ACTIVE memberships for the user, map to Account
        return accountMembershipRepository
            .findByUserIdAndMembershipStatus(userId, MembershipStatus.ACTIVE)
            .mapNotNull { membership ->
                accountService.getById(membership.accountId)
                    ?.let { account ->
                        AccountDTO(
                            id = account.id.toString(),
                            accountName = account.accountName,
                            accountType = account.accountType.name,
                            accountStatus = account.accountStatus.name,
                            isOwner = membership.accountRole == AccountRole.OWNER,
                        )
                    }
            }
    }

    fun updateAccount(
        accountId: String,
        input: Map<String, Any?>,
        principal: RequestPrincipal,
    ): AccountDTO {
        val actorUserId = requireNotNull(principal.userId)
        val name = input["accountName"] as? String
            ?: throw ValidationException(SecurityErrorCode.ACCOUNT_NAME_REQUIRED, "accountName")
        val command = AccountManagement.UpdateAccountCommand(
            accountId = UUID.fromString(accountId),
            accountName = name,
            actorUserId = actorUserId,
        )
        val updated = accountService.update(command)
        val membership = accountMembershipRepository
            .findOneByAccountIdAndUserId(updated.id!!, actorUserId)
            .orElseThrow { ... }
        return AccountDTO(
            id = updated.id.toString(),
            accountName = updated.accountName,
            accountType = updated.accountType.name,
            accountStatus = updated.accountStatus.name,
            isOwner = membership.accountRole == AccountRole.OWNER,
        )
    }
}
```

**New DTO** — `graphql/dto/AccountDTO.kt`:

```kotlin
data class AccountDTO(
    val id: String,
    val accountName: String,
    val accountType: String,
    val accountStatus: String,
    val isOwner: Boolean,
)
```

**Wire in `SecurityWiringFactory`:**

Register `myAccounts` on `QueryType` and `updateAccount` on `MutationType` pointing to `AccountResolver`.

#### 1.2.3 Error Cases

| Scenario | Error |
|---|---|
| User is not the account owner | `SecurityErrorCode.MEMBERSHIP_OPERATION_NOT_ALLOWED` (already thrown by `MembershipPolicyEngine`) |
| Account name is blank | `SecurityErrorCode.ACCOUNT_NAME_REQUIRED` |
| Account name > 100 chars | `SecurityErrorCode.ACCOUNT_NAME_TOO_LONG` |
| Account not found | `SecurityErrorCode.ACCOUNT_NOT_FOUND` |
| Account already deleted | `SecurityErrorCode.ACCOUNT_DELETION_NOT_ALLOWED` |

### 1.3 Frontend — Profile Page

**New section in `profile/page.tsx`** — "Account" section, placed above the Preferences section:

```
┌─────────────────────────────────────────────────────┐
│  [Avatar]  displayName                              │
│            email@example.com                        │
│  ─────────────────────────────────────────────────  │
│  Email verified: Yes      Plan: —                   │
└─────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────┐
│  ACCOUNTS                                           │
│  ─────────────────────────────────────────────────  │
│  My Personal Account        [edit icon]  ← owner   │
│  Family Account             [edit icon]  ← owner   │
│  Shared Workspace           (read-only)  ← member  │
└─────────────────────────────────────────────────────┘
```

**Edit UX:**
- Clicking the edit icon on an account the user owns → inline text field (MUI `TextField`) replaces the account name label.
- On Enter or blur → call `updateAccount` mutation.
- On error → revert to original name + show `toast.error`.
- Non-owner accounts: no edit icon shown (condition: `!account.isOwner`).

**New GraphQL operations** (`lib/graphql/operations/auth/queries.ts` and `mutations.ts`):

```typescript
export const MY_ACCOUNTS = gql`
  query MyAccounts {
    myAccounts {
      id
      accountName
      accountType
      accountStatus
      isOwner
    }
  }
`;

export const UPDATE_ACCOUNT = gql`
  mutation UpdateAccount($accountId: ID!, $input: UpdateAccountInput!) {
    updateAccount(accountId: $accountId, input: $input) {
      id
      accountName
      accountType
      accountStatus
      isOwner
    }
  }
`;
```

---

## Feature 2 — Avatar Upload (Email Users Only)

### 2.1 Requirements

- A user who registered **by email** (not via OAuth / identity provider) can upload an image to use as their avatar.
- **OAuth users** (Google, Microsoft, etc.) already have an avatar populated from their identity provider. The upload feature is **not shown** for them.
- Detection: `user.avatarProvider == null` in the `currentUser` query → email user → show upload UI.
- Supported formats: JPEG, PNG, WebP, GIF (matches `user-profiles` namespace).
- Max file size: 2 MB (enforced by the assets service namespace config).
- The new avatar is immediately reflected in the `UserProfileMenu` top-bar after upload.
- The user can upload a new avatar at any time (replaces the previous one).

### 2.2 Asset Namespace

The `user-profiles` namespace in `asset-config.yml` is already configured:

```yaml
user-profiles:
  description: "User profile images"
  visibility: PRIVATE
  keyTemplate: "{namespace}/{ownerId}/{assetId}"
  maxSizeBytes: 2097152  # 2 MB
  allowedMimeTypes:
    - image/jpeg
    - image/png
    - image/webp
    - image/gif
  uploadTtlSeconds: 900
```

**Change:** Update `visibility` from `PRIVATE` to `PUBLIC` so avatars are served via stable CDN URLs (consistent with how OAuth providers serve profile pictures). Without this change, every request to render the avatar would require a short-lived presigned URL, complicating the frontend.

```yaml
user-profiles:
  description: "User profile images"
  visibility: PUBLIC           # changed from PRIVATE
  keyTemplate: "{namespace}/{ownerId}/{assetId}"
  maxSizeBytes: 2097152
  ...
```

### 2.3 Backend — Security Service

#### 2.3.1 GraphQL Schema Additions

```graphql
extend type User {
  avatarProvider: String   # null = email user; "google" / "microsoft" = OAuth
}

input UpdateUserAvatarInput {
  avatarUrl: String!   # Public CDN URL returned by confirmAssetUpload
}

extend type Mutation {
  updateUserAvatar(input: UpdateUserAvatarInput!): User!
  removeUserAvatar: User!
}
```

> **Note on design:** Rather than the security service making a synchronous call to the assets service to resolve an assetId into a URL (which requires cross-service HTTP), the client passes the `publicUrl` returned by `confirmAssetUpload`. The security service validates that the URL starts with the known assets CDN base domain (configured as `assets.public-base-url` in application config) to prevent arbitrary URL injection.

#### 2.3.2 New Methods in `UserManagementService`

```kotlin
@Transactional
open fun updateAvatar(userId: UUID, avatarUrl: String): User {
    val user = userRepository.findById(userId)
        .orElseThrow { NotFoundException(SecurityErrorCode.USER_NOT_FOUND, "userId") }

    // Guard: only email users may use this endpoint
    if (user.avatarProvider != null) {
        throw ValidationException(
            errorCode = SecurityErrorCode.AVATAR_UPLOAD_NOT_ALLOWED_FOR_OAUTH_USERS,
            field = "avatarUrl",
        )
    }

    // Validate URL domain against known assets CDN
    if (!avatarUrl.startsWith(assetsCdnBaseUrl)) {
        throw ValidationException(
            errorCode = SecurityErrorCode.INVALID_AVATAR_URL,
            field = "avatarUrl",
        )
    }

    user.avatarUrl = avatarUrl
    user.avatarUpdatedAt = Instant.now()
    user.updatedAt = Instant.now()
    userRepository.update(user)
    return user.toDomain()
}

@Transactional
open fun removeAvatar(userId: UUID): User {
    val user = userRepository.findById(userId)
        .orElseThrow { NotFoundException(SecurityErrorCode.USER_NOT_FOUND, "userId") }
    if (user.avatarProvider != null) {
        throw ValidationException(
            errorCode = SecurityErrorCode.AVATAR_UPLOAD_NOT_ALLOWED_FOR_OAUTH_USERS,
            field = null,
        )
    }
    user.avatarUrl = null
    user.avatarUpdatedAt = Instant.now()
    user.updatedAt = Instant.now()
    userRepository.update(user)
    return user.toDomain()
}
```

Inject `@Property(name = "assets.public-base-url") private val assetsCdnBaseUrl: String` from application config.

#### 2.3.3 New Resolver Methods in `UserManagementResolver`

```kotlin
fun updateUserAvatar(input: Map<String, Any?>, principal: RequestPrincipal): UserDTO {
    val userId = requireNotNull(principal.userId)
    val avatarUrl = input["avatarUrl"] as? String
        ?: throw ValidationException(SecurityErrorCode.INVALID_AVATAR_URL, "avatarUrl")
    return mapper.userToDTO(userManagementService.updateAvatar(userId, avatarUrl))
}

fun removeUserAvatar(principal: RequestPrincipal): UserDTO {
    val userId = requireNotNull(principal.userId)
    return mapper.userToDTO(userManagementService.removeAvatar(userId))
}
```

Wire both in `SecurityWiringFactory` on `MutationType`.

#### 2.3.4 `currentUser` query — expose `avatarProvider`

Add `avatarProvider` to `UserDTO` and the `User` GraphQL type so the frontend can detect email vs OAuth users:

- `UserEntity.toDomain()` → `User.avatarProvider` (add field to `User.kt` domain class)
- `UserManagementMapper.userToDTO()` → include `avatarProvider`
- `currentUser` query returns `avatarProvider`

#### 2.3.5 New Error Codes (add to `SecurityErrorCode.kt`)

```kotlin
AVATAR_UPLOAD_NOT_ALLOWED_FOR_OAUTH_USERS,
INVALID_AVATAR_URL,
```

### 2.4 Frontend — Profile Page

#### 2.4.1 Upload Flow

```
User opens profile page
  → if user.avatarProvider == null (email user):
      show clickable avatar with camera overlay icon
  → User clicks avatar
      → hidden <input type="file" accept="image/jpeg,image/png,image/webp,image/gif" />
      → user selects file
  → Client validates file size <= 2 MB locally (show error if over limit)
  → Call createAssetUpload({ namespace: "user-profiles", filename, mimeType, sizeBytes })
      → get { uploadUrl, id: assetId }
  → PUT file to uploadUrl (fetch / XMLHttpRequest with progress)
  → Call confirmAssetUpload({ assetId, checksum? })
      → get { publicUrl }
  → Call updateUserAvatar({ avatarUrl: publicUrl })
      → update Apollo cache: currentUser.avatarUrl
  → Avatar in UserProfileMenu updates immediately (Apollo cache)
```

#### 2.4.2 Remove Avatar

- Below the avatar, show a "Remove photo" link (visible only for email users who have a non-null `avatarUrl`).
- Calls `removeUserAvatar` mutation → clears avatar → UI falls back to initials.

#### 2.4.3 New GraphQL Operations

```typescript
export const UPDATE_USER_AVATAR = gql`
  mutation UpdateUserAvatar($input: UpdateUserAvatarInput!) {
    updateUserAvatar(input: $input) {
      id
      avatarUrl
      avatarProvider
    }
  }
`;

export const REMOVE_USER_AVATAR = gql`
  mutation RemoveUserAvatar {
    removeUserAvatar {
      id
      avatarUrl
    }
  }
`;
```

Update `CURRENT_USER` query to include `avatarProvider`:

```typescript
export const CURRENT_USER = gql`
  query CurrentUser {
    currentUser {
      id
      email
      displayName
      avatarUrl
      avatarProvider   # ADD THIS
      ...
    }
  }
`;
```

#### 2.4.4 UI Detail

```
┌─────────────────────────────────────────────────────┐
│  [Avatar + camera icon on hover]  displayName       │ ← email user only
│                                   email@example.com  │
│            [Remove photo]                            │ ← shown if avatarUrl is set
└─────────────────────────────────────────────────────┘
```

- While uploading: show a circular progress indicator over the avatar.
- On error at any step: `toast.error("Erro ao carregar imagem. Tente novamente.")`.
- For OAuth users: avatar is displayed normally (read-only), no camera icon, no remove link.

---

## Feature 3 — Keep Me Connected (OAuth Default True)

### 3.1 Requirements

- Users authenticated via **OAuth / identity provider** are kept connected by default (`keepMeConnected = true`).
- Users authenticated via **email/password** are NOT kept connected by default (`keepMeConnected = false`).
- This default represents what happens when the user does not explicitly pass `rememberMe` during sign-in.
- The user can change this preference on the profile page.
- When `rememberMe` is **explicitly passed** in the `signIn` / `signInWithOAuth` input, the explicit value takes precedence over the saved preference.
- When `rememberMe` is **null** (omitted from input), the system uses `userPreferences.keepMeConnected`.

### 3.2 Default Derivation Logic

| Auth method | Default `keepMeConnected` |
|---|---|
| Email / password | `false` |
| OAuth (Google, Microsoft, etc.) | `true` |

Detection at service layer: `userEntity.passwordHash != null` → email user; `passwordHash == null && avatarProvider != null` → OAuth user. Use `avatarProvider` as the discriminator (already stored, populated by `authenticateWithOAuth`).

> If no `user_preferences` row exists yet (lazy init), use the above default instead of querying the DB.

### 3.3 Backend — Security Service

#### 3.3.1 DB Migration — `V0_31__add_keep_me_connected_to_user_preferences.sql`

```sql
ALTER TABLE security.user_preferences
  ADD COLUMN keep_me_connected BOOLEAN NOT NULL DEFAULT false;
```

Note: Default is `false` in the DB (safe fallback). The service layer computes the correct per-user default when no row exists.

#### 3.3.2 `UserPreferencesEntity` — add field

```kotlin
@Column(name = "keep_me_connected", nullable = false)
open var keepMeConnected: Boolean = false,
```

#### 3.3.3 Domain — `UserPreferences.kt` — add field

```kotlin
data class UserPreferences(
    val userId: UUID,
    val language: Language,
    val theme: Theme,
    val blurBalances: Boolean,
    val firstDayOfWeek: Int,
    val keepMeConnected: Boolean,  // ADD
)
```

#### 3.3.4 `UserPreferencesService` — update `get()` default

```kotlin
fun getDefault(userId: UUID): UserPreferences {
    val isOAuthUser = userRepository.findById(userId)
        .map { it.avatarProvider != null && it.passwordHash == null }
        .orElse(false)
    return UserPreferences(
        userId = userId,
        language = Language.PORTUGUESE,
        theme = Theme.SYSTEM,
        blurBalances = false,
        firstDayOfWeek = 1,
        keepMeConnected = isOAuthUser,   // true for OAuth, false for email
    )
}
```

#### 3.3.5 `UserPreferencesResolver` — update DTO and resolver

Add `keepMeConnected: Boolean!` to `UserPreferencesDTO` and expose it in the GraphQL type.

#### 3.3.6 GraphQL Schema Additions

```graphql
type UserPreferences {
  language: Language!
  theme: Theme!
  blurBalances: Boolean!
  firstDayOfWeek: Int!
  keepMeConnected: Boolean!    # ADD
}

input UpdateUserPreferencesInput {
  language: Language
  theme: Theme
  blurBalances: Boolean
  firstDayOfWeek: Int
  keepMeConnected: Boolean     # ADD
}
```

#### 3.3.7 Sign-in Flow Update — `SecurityAuthResolver`

Update both `signIn()` and `signInWithOAuth()` to use the preference when `rememberMe` is null:

```kotlin
// In signIn():
val rememberMe = input["rememberMe"] as? Boolean
    ?: userPreferencesService.get(user.id!!).keepMeConnected

// In signInWithOAuth():
val rememberMe = input["rememberMe"] as? Boolean
    ?: userPreferencesService.get(user.id!!).keepMeConnected
```

Inject `UserPreferencesService` into `SecurityAuthResolver`.

> The `rememberMe` field in `SignInInput` and `SignInWithOAuthInput` remains type `Boolean` (nullable) — no schema change needed. When the client omits it, the resolver receives `null` and falls back to the preference.

#### 3.3.8 Error Cases

None specific — preference update uses the existing `updateUserPreferences` mutation with the new optional `keepMeConnected` field. Empty input (all fields null) already throws `ValidationException` in the service.

### 3.4 Frontend — Profile Page

**New toggle in the existing Preferences section**, placed after "Interface" and "Banking" sub-sections:

```
┌─────────────────────────────────────────────────────┐
│  PREFERENCES                                        │
│  ─────────────────────────────────────────────────  │
│  Interface                                          │
│    Theme     [Light | Dark | System]                │
│    Language  [Português ▾]                          │
│                                                     │
│  Session                                            │
│    Keep me connected   [toggle ON/OFF]              │
│    "Stay signed in between sessions. Disable if     │
│     you share this device."                         │
│                                                     │
│  Banking                                            │
│    Apply transaction rules  [Never | Always | Ask]  │
└─────────────────────────────────────────────────────┘
```

**Implementation:**
- Use MUI `Switch` component.
- Optimistic update: toggle immediately, call `updateUserPreferences({ keepMeConnected: !current })`, revert + `toast.error` on failure.
- Update `USER_PREFERENCES` query to include `keepMeConnected`:

```typescript
export const USER_PREFERENCES = gql`
  query UserPreferences {
    userPreferences {
      language
      theme
      blurBalances
      firstDayOfWeek
      keepMeConnected   # ADD
    }
  }
`;
```

---

## Implementation Order

The three features are independent. Suggested order:

1. **Feature 3 (Keep Me Connected)** — smallest surface area; only modifies existing preferences infrastructure. Start here.
2. **Feature 1 (Account Name)** — backend is mostly done (`AccountService`); mainly needs GraphQL wiring + frontend section.
3. **Feature 2 (Avatar Upload)** — largest; involves two services (assets + security) and a multi-step upload UX.

---

## Checklist for the Implementing Agent

### Feature 3 — Keep Me Connected

- [ ] Write migration `V0_31__add_keep_me_connected_to_user_preferences.sql`
- [ ] Add `keepMeConnected` to `UserPreferencesEntity`
- [ ] Add `keepMeConnected` to `UserPreferences` domain class
- [ ] Update `UserPreferencesService.getDefault()` to derive per-user default (OAuth vs email)
- [ ] Update `UserPreferencesService.update()` to accept `keepMeConnected` patch
- [ ] Update `UserPreferencesDTO` + `UserPreferencesResolver`
- [ ] Update `schema.graphqls`: `UserPreferences` type + `UpdateUserPreferencesInput`
- [ ] Inject `UserPreferencesService` into `SecurityAuthResolver`
- [ ] Update `signIn()` and `signInWithOAuth()` to use preference when `rememberMe` is null
- [ ] Unit tests: default derivation (OAuth vs email), sign-in flow
- [ ] Web: add `keepMeConnected` to `USER_PREFERENCES` query
- [ ] Web: add `keepMeConnected` toggle to profile page (Session section)
- [ ] Web: call `UPDATE_USER_PREFERENCES({ keepMeConnected })` on toggle change

### Feature 1 — Account Name Change

- [ ] Add `Account` GraphQL type to `schema.graphqls`
- [ ] Add `myAccounts` query to `schema.graphqls`
- [ ] Add `updateAccount` mutation to `schema.graphqls`
- [ ] Create `graphql/dto/AccountDTO.kt`
- [ ] Create `graphql/resolver/AccountResolver.kt`
- [ ] Wire `myAccounts` and `updateAccount` in `SecurityWiringFactory`
- [ ] Unit tests for `AccountResolver` (owner can edit, non-owner gets error)
- [ ] Web: add `MY_ACCOUNTS` query operation
- [ ] Web: add `UPDATE_ACCOUNT` mutation operation
- [ ] Web: add "Accounts" section to profile page with inline edit for owned accounts

### Feature 2 — Avatar Upload

- [ ] Update `asset-config.yml`: change `user-profiles` visibility from `PRIVATE` to `PUBLIC`
- [ ] Add `avatarProvider` to `User.kt` domain class
- [ ] Add `avatarProvider` to `UserDTO` and `UserManagementMapper`
- [ ] Add `avatarProvider` to `User` GraphQL type
- [ ] Add `avatarProvider` to `CURRENT_USER` GraphQL query (web)
- [ ] Add `updateUserAvatar(input: UpdateUserAvatarInput!)` mutation to `schema.graphqls`
- [ ] Add `removeUserAvatar` mutation to `schema.graphqls`
- [ ] Add error codes `AVATAR_UPLOAD_NOT_ALLOWED_FOR_OAUTH_USERS`, `INVALID_AVATAR_URL` to `SecurityErrorCode`
- [ ] Add `updateAvatar()` and `removeAvatar()` to `UserManagementService`
- [ ] Add corresponding methods to `UserManagementResolver`
- [ ] Wire mutations in `SecurityWiringFactory`
- [ ] Add `assets.public-base-url` to security service `application.yml` (for URL domain validation)
- [ ] Unit tests: email user can upload, OAuth user gets error, invalid URL rejected
- [ ] Web: add `UPDATE_USER_AVATAR` and `REMOVE_USER_AVATAR` mutation operations
- [ ] Web: update profile page avatar to be clickable (email users only) with file input
- [ ] Web: implement upload flow (createAssetUpload → PUT → confirmAssetUpload → updateUserAvatar)
- [ ] Web: show upload progress on avatar
- [ ] Web: show "Remove photo" link when email user has avatar
- [ ] Web: update Apollo cache after avatar change so `UserProfileMenu` updates immediately
