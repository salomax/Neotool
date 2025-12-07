package io.github.salomax.neotool.security.test.service.unit

import graphql.GraphQLContext
import graphql.schema.DataFetchingEnvironment
import io.github.salomax.neotool.security.service.JwtService
import io.github.salomax.neotool.security.service.RequestPrincipal
import io.github.salomax.neotool.security.service.RequestPrincipalProvider
import io.github.salomax.neotool.security.service.exception.AuthenticationRequiredException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional
import java.util.UUID

@DisplayName("RequestPrincipalProvider Unit Tests")
class RequestPrincipalProviderTest {
    private lateinit var jwtService: JwtService
    private lateinit var requestPrincipalProvider: RequestPrincipalProvider

    @BeforeEach
    fun setUp() {
        jwtService = mock()
        requestPrincipalProvider = RequestPrincipalProvider(jwtService)
    }

    @Nested
    @DisplayName("fromToken")
    inner class FromTokenTests {
        @Test
        fun `should throw AuthenticationRequiredException when token is blank`() {
            // Act & Assert
            assertThatThrownBy { requestPrincipalProvider.fromToken("") }
                .isInstanceOf(AuthenticationRequiredException::class.java)
                .hasMessageContaining("Access token is required")

            assertThatThrownBy { requestPrincipalProvider.fromToken("   ") }
                .isInstanceOf(AuthenticationRequiredException::class.java)
                .hasMessageContaining("Access token is required")
        }

        @Test
        fun `should throw AuthenticationRequiredException when token is not an access token`() {
            // Arrange
            val token = "invalid-token"
            whenever(jwtService.isAccessToken(token)).thenReturn(false)

            // Act & Assert
            assertThatThrownBy { requestPrincipalProvider.fromToken(token) }
                .isInstanceOf(AuthenticationRequiredException::class.java)
                .hasMessageContaining("Invalid or expired access token")
        }

        @Test
        fun `should throw AuthenticationRequiredException when user ID cannot be extracted`() {
            // Arrange
            val token = "valid-access-token"
            whenever(jwtService.isAccessToken(token)).thenReturn(true)
            whenever(jwtService.getUserIdFromToken(token)).thenReturn(null)

            // Act & Assert
            assertThatThrownBy { requestPrincipalProvider.fromToken(token) }
                .isInstanceOf(AuthenticationRequiredException::class.java)
                .hasMessageContaining("Invalid token: missing user ID")
        }

        @Test
        fun `should return RequestPrincipal with permissions when token is valid`() {
            // Arrange
            val userId = UUID.randomUUID()
            val token = "valid-access-token"
            val permissions = listOf("security:user:view", "security:user:save")

            whenever(jwtService.isAccessToken(token)).thenReturn(true)
            whenever(jwtService.getUserIdFromToken(token)).thenReturn(userId)
            whenever(jwtService.getPermissionsFromToken(token)).thenReturn(permissions)

            // Act
            val principal = requestPrincipalProvider.fromToken(token)

            // Assert
            assertThat(principal.userId).isEqualTo(userId)
            assertThat(principal.token).isEqualTo(token)
            assertThat(principal.permissionsFromToken).containsExactlyInAnyOrderElementsOf(permissions)
        }

        @Test
        fun `should return RequestPrincipal with empty permissions when token has no permissions claim`() {
            // Arrange
            val userId = UUID.randomUUID()
            val token = "valid-access-token"

            whenever(jwtService.isAccessToken(token)).thenReturn(true)
            whenever(jwtService.getUserIdFromToken(token)).thenReturn(userId)
            whenever(jwtService.getPermissionsFromToken(token)).thenReturn(null)

            // Act
            val principal = requestPrincipalProvider.fromToken(token)

            // Assert
            assertThat(principal.userId).isEqualTo(userId)
            assertThat(principal.token).isEqualTo(token)
            assertThat(principal.permissionsFromToken).isEmpty()
        }
    }

    @Nested
    @DisplayName("fromGraphQl")
    inner class FromGraphQlTests {
        @Test
        fun `should throw AuthenticationRequiredException when token is missing from context`() {
            // Arrange
            val env = mock<DataFetchingEnvironment>()
            val graphQlContext = mock<GraphQLContext>()
            whenever(env.graphQlContext).thenReturn(graphQlContext)
            // When key doesn't exist, getOrEmpty() returns Optional.empty()
            whenever(graphQlContext.getOrEmpty<RequestPrincipal>(eq("requestPrincipal"))).thenReturn(Optional.empty())
            whenever(graphQlContext.getOrEmpty<String>(eq("token"))).thenReturn(Optional.empty())

            // Act & Assert
            assertThatThrownBy { requestPrincipalProvider.fromGraphQl(env) }
                .isInstanceOf(AuthenticationRequiredException::class.java)
                .hasMessageContaining("Access token is required")
        }

        @Test
        fun `should return cached principal when available`() {
            // Arrange
            val userId = UUID.randomUUID()
            val token = "valid-token"
            val cachedPrincipal =
                RequestPrincipal(
                    userId = userId,
                    token = token,
                    permissionsFromToken = emptyList(),
                )
            val env = mock<DataFetchingEnvironment>()
            val graphQlContext = mock<GraphQLContext>()
            whenever(env.graphQlContext).thenReturn(graphQlContext)
            whenever(
                graphQlContext.getOrEmpty<RequestPrincipal>(eq("requestPrincipal")),
            ).thenReturn(Optional.of(cachedPrincipal))

            // Act
            val principal = requestPrincipalProvider.fromGraphQl(env)

            // Assert
            assertThat(principal).isSameAs(cachedPrincipal)
            verify(jwtService, never()).isAccessToken(any())
            verify(jwtService, never()).getUserIdFromToken(any())
        }

        @Test
        fun `should extract and validate principal from token when not cached`() {
            // Arrange
            val userId = UUID.randomUUID()
            val token = "valid-access-token"
            val permissions = listOf("security:user:view")
            val env = mock<DataFetchingEnvironment>()
            val graphQlContext = mock<GraphQLContext>()
            whenever(env.graphQlContext).thenReturn(graphQlContext)

            // When cached principal doesn't exist, getOrEmpty() returns Optional.empty()
            whenever(graphQlContext.getOrEmpty<RequestPrincipal>(eq("requestPrincipal"))).thenReturn(Optional.empty())
            whenever(graphQlContext.getOrEmpty<String>(eq("token"))).thenReturn(Optional.of(token))
            whenever(jwtService.isAccessToken(token)).thenReturn(true)
            whenever(jwtService.getUserIdFromToken(token)).thenReturn(userId)
            whenever(jwtService.getPermissionsFromToken(token)).thenReturn(permissions)

            // Act
            val principal = requestPrincipalProvider.fromGraphQl(env)

            // Assert
            assertThat(principal.userId).isEqualTo(userId)
            assertThat(principal.token).isEqualTo(token)
            assertThat(principal.permissionsFromToken).containsExactlyInAnyOrderElementsOf(permissions)
        }

        @Test
        fun `should throw AuthenticationRequiredException when token in context is invalid`() {
            // Arrange
            val token = "invalid-token"
            val env = mock<DataFetchingEnvironment>()
            val graphQlContext = mock<GraphQLContext>()
            whenever(env.graphQlContext).thenReturn(graphQlContext)

            // When cached principal doesn't exist, getOrEmpty() returns Optional.empty()
            whenever(graphQlContext.getOrEmpty<RequestPrincipal>(eq("requestPrincipal"))).thenReturn(Optional.empty())
            whenever(graphQlContext.getOrEmpty<String>(eq("token"))).thenReturn(Optional.of(token))
            whenever(jwtService.isAccessToken(token)).thenReturn(false)

            // Act & Assert
            assertThatThrownBy { requestPrincipalProvider.fromGraphQl(env) }
                .isInstanceOf(AuthenticationRequiredException::class.java)
                .hasMessageContaining("Invalid or expired access token")
        }
    }
}
