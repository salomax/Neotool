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
        createdAt: Instant = Instant.now()
    ): UserEntity = UserEntity(
        id = id ?: UUID.randomUUID(),
        email = email,
        displayName = displayName,
        passwordHash = passwordHash,
        rememberMeToken = rememberMeToken,
        createdAt = createdAt
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
        createdAt: Instant = Instant.now()
    ): UserEntity = UserEntity(
        id = id ?: UUID.randomUUID(),
        email = email,
        displayName = displayName,
        passwordHash = authenticationService.hashPassword(password),
        rememberMeToken = rememberMeToken,
        createdAt = createdAt
    )

    /**
     * Create a GraphQL signIn mutation
     */
    fun signInMutation(
        email: String = "test@example.com",
        password: String = "TestPassword123!",
        rememberMe: Boolean = false
    ): Map<String, Any> = mapOf(
        "query" to """
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
        "variables" to mapOf(
            "input" to mapOf(
                "email" to email,
                "password" to password,
                "rememberMe" to rememberMe
            )
        )
    )

    /**
     * Create a GraphQL signIn mutation with inline variables (for simple tests)
     */
    fun signInMutationInline(
        email: String = "test@example.com",
        password: String = "TestPassword123!",
        rememberMe: Boolean = false
    ): Map<String, Any> = mapOf(
        "query" to """
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
        """.trimIndent()
    )

    /**
     * Create a GraphQL currentUser query
     */
    fun currentUserQuery(): Map<String, Any> = mapOf(
        "query" to """
            query {
                currentUser {
                    id
                    email
                    displayName
                }
            }
        """.trimIndent()
    )

    /**
     * Generate a unique email for testing
     */
    fun uniqueEmail(prefix: String = "test"): String {
        return "$prefix-${System.currentTimeMillis()}-${Thread.currentThread().id}@example.com"
    }
}

