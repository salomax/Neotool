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
            id = id ?: UUID.randomUUID(),
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
            id = id ?: UUID.randomUUID(),
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
        return "$prefix-${System.currentTimeMillis()}-${Thread.currentThread().id}-${UUID.randomUUID().toString().take(8)}@example.com"
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
}
