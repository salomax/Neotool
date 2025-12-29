package io.github.salomax.neotool.security.test.service.unit

import graphql.GraphQLContext
import graphql.schema.DataFetchingEnvironment
import io.github.salomax.neotool.security.service.RequestPrincipal
import io.github.salomax.neotool.security.service.RequestPrincipalProvider
import io.github.salomax.neotool.security.service.TokenPrincipalDecoder
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
    private lateinit var principalDecoder: TokenPrincipalDecoder
    private lateinit var requestPrincipalProvider: RequestPrincipalProvider

    @BeforeEach
    fun setUp() {
        principalDecoder = mock()
        requestPrincipalProvider = RequestPrincipalProvider(principalDecoder)
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

            verify(principalDecoder, never()).fromToken(any())
        }

        @Test
        fun `should delegate to decoder when token is present`() {
            // Arrange
            val token = "valid-access-token"
            val principal =
                RequestPrincipal(
                    userId = UUID.randomUUID(),
                    token = token,
                    permissionsFromToken = listOf("p1"),
                )
            whenever(principalDecoder.fromToken(token)).thenReturn(principal)

            // Act
            val result = requestPrincipalProvider.fromToken(token)

            // Assert
            assertThat(result).isSameAs(principal)
            verify(principalDecoder).fromToken(token)
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
            verify(principalDecoder, never()).fromToken(any())
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
            whenever(principalDecoder.fromToken(token)).thenReturn(
                RequestPrincipal(
                    userId = userId,
                    token = token,
                    permissionsFromToken = permissions,
                ),
            )

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
            whenever(principalDecoder.fromToken(token)).thenThrow(AuthenticationRequiredException("boom"))

            // Act & Assert
            assertThatThrownBy { requestPrincipalProvider.fromGraphQl(env) }
                .isInstanceOf(AuthenticationRequiredException::class.java)
                .hasMessageContaining("boom")
        }
    }
}
