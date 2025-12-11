package io.github.salomax.neotool.security.test

import io.github.salomax.neotool.security.model.UserEntity
import io.github.salomax.neotool.security.service.AuthenticationService
import java.time.Instant
import java.util.UUID

/**
 * Test data builders for security module test entities with sensible defaults
 */
object SecurityTestDataBuilders {
    /**
     * Create a test user with optional parameters
     */
    fun user(
        id: UUID? = null,
        email: String = "test@example.com",
        displayName: String? = "Test User",
        passwordHash: String? = null,
        rememberMeToken: String? = null,
        createdAt: Instant = Instant.now(),
    ): UserEntity =
        UserEntity(
            id = id,
            email = email,
            displayName = displayName,
            passwordHash = passwordHash,
            rememberMeToken = rememberMeToken,
            createdAt = createdAt,
        )

    /**
     * Create a test user with a hashed password
     */
    fun userWithPassword(
        authenticationService: AuthenticationService,
        id: UUID? = null,
        email: String = "test@example.com",
        displayName: String? = "Test User",
        password: String = "TestPassword123!",
        rememberMeToken: String? = null,
        createdAt: Instant = Instant.now(),
    ): UserEntity =
        UserEntity(
            id = id,
            email = email,
            displayName = displayName,
            passwordHash = authenticationService.hashPassword(password),
            rememberMeToken = rememberMeToken,
            createdAt = createdAt,
        )

    /**
     * Create a GraphQL signIn mutation
     */
    fun signInMutation(
        email: String = "test@example.com",
        password: String = "TestPassword123!",
        rememberMe: Boolean = false,
    ): Map<String, Any> =
        mapOf(
            "query" to
                """
                mutation SignIn(${'$'}input: SignInInput!) {
                    signIn(input: ${'$'}input) {
                        token
                        refreshToken
                        user {
                            id
                            email
                            displayName
                        }
                    }
                }
                """.trimIndent(),
            "variables" to
                mapOf(
                    "input" to
                        mapOf(
                            "email" to email,
                            "password" to password,
                            "rememberMe" to rememberMe,
                        ),
                ),
        )

    /**
     * Create a GraphQL signIn mutation with inline variables (for simple tests)
     */
    fun signInMutationInline(
        email: String = "test@example.com",
        password: String = "TestPassword123!",
        rememberMe: Boolean = false,
    ): Map<String, Any> =
        mapOf(
            "query" to
                """
                mutation {
                    signIn(input: {
                        email: "$email"
                        password: "$password"
                        rememberMe: $rememberMe
                    }) {
                        token
                        refreshToken
                        user {
                            id
                            email
                            displayName
                        }
                    }
                }
                """.trimIndent(),
        )

    /**
     * Create a GraphQL signInWithOAuth mutation
     */
    fun signInWithOAuthMutation(
        provider: String = "google",
        idToken: String = "test-id-token",
        rememberMe: Boolean = false,
    ): Map<String, Any> =
        mapOf(
            "query" to
                """
                mutation SignInWithOAuth(${'$'}input: SignInWithOAuthInput!) {
                    signInWithOAuth(input: ${'$'}input) {
                        token
                        refreshToken
                        user {
                            id
                            email
                            displayName
                        }
                    }
                }
                """.trimIndent(),
            "variables" to
                mapOf(
                    "input" to
                        mapOf(
                            "provider" to provider,
                            "idToken" to idToken,
                            "rememberMe" to rememberMe,
                        ),
                ),
        )

    /**
     * Create a GraphQL currentUser query
     */
    fun currentUserQuery(): Map<String, Any> =
        mapOf(
            "query" to
                """
                query {
                    currentUser {
                        id
                        email
                        displayName
                    }
                }
                """.trimIndent(),
        )

    /**
     * Create a GraphQL signUp mutation
     */
    fun signUpMutation(
        name: String = "Test User",
        email: String = "test@example.com",
        password: String = "TestPassword123!",
    ): Map<String, Any> =
        mapOf(
            "query" to
                """
                mutation SignUp(${'$'}input: SignUpInput!) {
                    signUp(input: ${'$'}input) {
                        token
                        refreshToken
                        user {
                            id
                            email
                            displayName
                        }
                    }
                }
                """.trimIndent(),
            "variables" to
                mapOf(
                    "input" to
                        mapOf(
                            "name" to name,
                            "email" to email,
                            "password" to password,
                        ),
                ),
        )

    /**
     * Create a GraphQL signUp mutation with inline variables (for simple tests)
     */
    fun signUpMutationInline(
        name: String = "Test User",
        email: String = "test@example.com",
        password: String = "TestPassword123!",
    ): Map<String, Any> =
        mapOf(
            "query" to
                """
                mutation {
                    signUp(input: {
                        name: "$name"
                        email: "$email"
                        password: "$password"
                    }) {
                        token
                        refreshToken
                        user {
                            id
                            email
                            displayName
                        }
                    }
                }
                """.trimIndent(),
        )

    /**
     * Generate a unique email for testing
     */
    fun uniqueEmail(prefix: String = "test"): String {
        return "$prefix-${System.currentTimeMillis()}-${Thread.currentThread().threadId()}-" +
            "${UUID.randomUUID().toString().take(8)}@example.com"
    }

    /**
     * Create a GraphQL requestPasswordReset mutation
     */
    fun requestPasswordResetMutation(
        email: String = "test@example.com",
        locale: String? = null,
    ): Map<String, Any> =
        mapOf(
            "query" to
                """
                mutation RequestPasswordReset(${'$'}input: RequestPasswordResetInput!) {
                    requestPasswordReset(input: ${'$'}input) {
                        success
                        message
                    }
                }
                """.trimIndent(),
            "variables" to
                mapOf(
                    "input" to
                        buildMap {
                            put("email", email)
                            if (locale != null) {
                                put("locale", locale)
                            }
                        },
                ),
        )

    /**
     * Create a GraphQL requestPasswordReset mutation with inline variables
     */
    fun requestPasswordResetMutationInline(
        email: String = "test@example.com",
        locale: String? = null,
    ): Map<String, Any> =
        mapOf(
            "query" to
                """
                mutation {
                    requestPasswordReset(input: {
                        email: "$email"${if (locale != null) "\n                    locale: \"$locale\"" else ""}
                    }) {
                        success
                        message
                    }
                }
                """.trimIndent(),
        )

    /**
     * Create a GraphQL resetPassword mutation
     */
    fun resetPasswordMutation(
        token: String = "test-token",
        newPassword: String = "NewPassword123!",
    ): Map<String, Any> =
        mapOf(
            "query" to
                """
                mutation ResetPassword(${'$'}input: ResetPasswordInput!) {
                    resetPassword(input: ${'$'}input) {
                        success
                        message
                    }
                }
                """.trimIndent(),
            "variables" to
                mapOf(
                    "input" to
                        mapOf(
                            "token" to token,
                            "newPassword" to newPassword,
                        ),
                ),
        )

    /**
     * Create a GraphQL resetPassword mutation with inline variables
     */
    fun resetPasswordMutationInline(
        token: String = "test-token",
        newPassword: String = "NewPassword123!",
    ): Map<String, Any> =
        mapOf(
            "query" to
                """
                mutation {
                    resetPassword(input: {
                        token: "$token"
                        newPassword: "$newPassword"
                    }) {
                        success
                        message
                    }
                }
                """.trimIndent(),
        )

    /**
     * Create a test role entity
     */
    fun role(
        id: UUID? = null,
        name: String = "test-role",
        createdAt: Instant = Instant.now(),
        updatedAt: Instant = Instant.now(),
        version: Long = 0,
    ): io.github.salomax.neotool.security.model.RoleEntity =
        io.github.salomax.neotool.security.model.RoleEntity(
            id = id,
            name = name,
            createdAt = createdAt,
            updatedAt = updatedAt,
            version = version,
        )

    /**
     * Create a test permission entity
     */
    fun permission(
        id: UUID? = null,
        name: String = "resource:action",
        createdAt: Instant = Instant.now(),
        updatedAt: Instant = Instant.now(),
        version: Long = 0,
    ): io.github.salomax.neotool.security.model.PermissionEntity =
        io.github.salomax.neotool.security.model.PermissionEntity(
            id = id,
            name = name,
            createdAt = createdAt,
            updatedAt = updatedAt,
            version = version,
        )

    /**
     * Create a test role assignment entity
     */
    fun roleAssignment(
        id: UUID? = null,
        userId: UUID = UUID.randomUUID(),
        roleId: UUID = UUID.randomUUID(),
        validFrom: Instant? = null,
        validUntil: Instant? = null,
        createdAt: Instant = Instant.now(),
        updatedAt: Instant = Instant.now(),
        version: Long = 0,
    ): io.github.salomax.neotool.security.model.rbac.RoleAssignmentEntity =
        io.github.salomax.neotool.security.model.rbac.RoleAssignmentEntity(
            id = id ?: UUID.randomUUID(),
            userId = userId,
            roleId = roleId,
            validFrom = validFrom,
            validUntil = validUntil,
            createdAt = createdAt,
            updatedAt = updatedAt,
            version = version,
        )

    /**
     * Create a test group entity
     */
    fun group(
        id: UUID? = null,
        name: String = "test-group",
        description: String? = null,
        createdAt: Instant = Instant.now(),
        updatedAt: Instant = Instant.now(),
        version: Long = 0,
    ): io.github.salomax.neotool.security.model.rbac.GroupEntity =
        io.github.salomax.neotool.security.model.rbac.GroupEntity(
            id = id ?: UUID.randomUUID(),
            name = name,
            description = description,
            createdAt = createdAt,
            updatedAt = updatedAt,
            version = version,
        )

    /**
     * Create a test group membership entity
     */
    fun groupMembership(
        id: UUID? = null,
        userId: UUID = UUID.randomUUID(),
        groupId: UUID = UUID.randomUUID(),
        membershipType: io.github.salomax.neotool.security.domain.rbac.MembershipType =
            io.github.salomax.neotool.security.domain.rbac.MembershipType.MEMBER,
        validUntil: Instant? = null,
        createdAt: Instant = Instant.now(),
        updatedAt: Instant = Instant.now(),
        version: Long = 0,
    ): io.github.salomax.neotool.security.model.rbac.GroupMembershipEntity =
        io.github.salomax.neotool.security.model.rbac.GroupMembershipEntity(
            id = id ?: UUID.randomUUID(),
            userId = userId,
            groupId = groupId,
            membershipType = membershipType,
            validUntil = validUntil,
            createdAt = createdAt,
            updatedAt = updatedAt,
            version = version,
        )

    /**
     * Create a test group role assignment entity
     */
    fun groupRoleAssignment(
        id: UUID? = null,
        groupId: UUID = UUID.randomUUID(),
        roleId: UUID = UUID.randomUUID(),
        validFrom: Instant? = null,
        validUntil: Instant? = null,
        createdAt: Instant = Instant.now(),
        updatedAt: Instant = Instant.now(),
        version: Long = 0,
    ): io.github.salomax.neotool.security.model.rbac.GroupRoleAssignmentEntity =
        io.github.salomax.neotool.security.model.rbac.GroupRoleAssignmentEntity(
            id = id ?: UUID.randomUUID(),
            groupId = groupId,
            roleId = roleId,
            validFrom = validFrom,
            validUntil = validUntil,
            createdAt = createdAt,
            updatedAt = updatedAt,
            version = version,
        )

    /**
     * Create a test ABAC policy entity
     */
    fun abacPolicy(
        id: UUID? = null,
        name: String = "test-policy",
        description: String? = null,
        effect: io.github.salomax.neotool.security.domain.abac.PolicyEffect =
            io.github.salomax.neotool.security.domain.abac.PolicyEffect.ALLOW,
        condition: String = """{"eq": {"subject.userId": "test"}}""",
        version: Int = 1,
        isActive: Boolean = true,
        createdAt: Instant = Instant.now(),
        updatedAt: Instant = Instant.now(),
    ): io.github.salomax.neotool.security.model.abac.AbacPolicyEntity =
        io.github.salomax.neotool.security.model.abac.AbacPolicyEntity(
            id = id ?: UUID.randomUUID(),
            name = name,
            description = description,
            effect = effect,
            condition = condition,
            version = version,
            isActive = isActive,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
}
