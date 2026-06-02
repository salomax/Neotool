package io.github.salomax.neotool.assets.graphql

import graphql.execution.DataFetcherExceptionHandlerParameters
import graphql.execution.ExecutionStepInfo
import graphql.execution.ResultPath
import graphql.schema.DataFetchingEnvironment
import io.github.salomax.neotool.assets.error.AssetErrorCode
import io.github.salomax.neotool.assets.exception.StorageUnavailableException
import io.github.salomax.neotool.common.error.CommonErrorCode
import io.github.salomax.neotool.common.error.ValidationException
import io.github.salomax.neotool.common.security.exception.AuthorizationDeniedException
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.StaleStateException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@DisplayName("AssetGraphQLExceptionHandler Unit Tests")
class AssetGraphQLExceptionHandlerTest {
    private lateinit var handler: AssetGraphQLExceptionHandler

    @BeforeEach
    fun setUp() {
        handler = AssetGraphQLExceptionHandler()
    }

    @Nested
    @DisplayName("handleException()")
    inner class HandleExceptionTests {
        @Test
        fun `should handle DomainException with asset error code`() {
            // Arrange
            val exception =
                ValidationException(
                    errorCode = AssetErrorCode.ASSET_FORMAT_UNSUPPORTED,
                    field = "mimeType",
                    parameters = mapOf("mimeType" to "application/pdf"),
                )
            val parameters = createHandlerParameters(exception)

            val result = handler.handleException(parameters).get()

            // Assert
            assertThat(result.errors).hasSize(1)
            assertThat(result.errors[0].message).isEqualTo(AssetErrorCode.ASSET_FORMAT_UNSUPPORTED.defaultMessage)
            assertThat(result.errors[0].extensions).containsEntry("code", AssetErrorCode.ASSET_FORMAT_UNSUPPORTED.code)
            assertThat(result.errors[0].extensions).containsEntry("parameters", mapOf("mimeType" to "application/pdf"))
            assertThat(result.errors[0].extensions).containsEntry("field", listOf("mimeType"))
            assertThat(result.errors[0].extensions).containsEntry("service", "assets")
        }

        @Test
        fun `should handle IllegalArgumentException as invalid input`() {
            // Arrange
            val exception = IllegalArgumentException()
            val parameters = createHandlerParameters(exception)

            val result = handler.handleException(parameters).get()

            // Assert
            assertThat(result.errors).hasSize(1)
            assertThat(result.errors[0].message).isEqualTo(CommonErrorCode.INVALID_INPUT.defaultMessage)
            assertThat(result.errors[0].extensions).containsEntry("code", CommonErrorCode.INVALID_INPUT.code)
        }

        @Test
        fun `should map AuthorizationDeniedException without exposing details`() {
            val exception = AuthorizationDeniedException("user abc cannot access asset 123")
            val parameters = createHandlerParameters(exception)

            val result = handler.handleException(parameters).get()

            // Assert
            assertThat(result.errors).hasSize(1)
            assertThat(result.errors[0].message).isEqualTo("Access denied")
            assertThat(result.errors[0].extensions).containsEntry("code", "AUTH_AUTHORIZATION_DENIED")
            assertThat(result.errors[0].extensions).containsEntry("service", "assets")
        }

        @Test
        fun `should map IllegalArgumentException to INVALID_INPUT`() {
            val exception = IllegalArgumentException("raw detail should not leak")
            val parameters = createHandlerParameters(exception)

            val result = handler.handleException(parameters).get()

            // Assert
            assertThat(result.errors).hasSize(1)
            assertThat(result.errors[0].message).isEqualTo(CommonErrorCode.INVALID_INPUT.defaultMessage)
            assertThat(result.errors[0].extensions).containsEntry("code", CommonErrorCode.INVALID_INPUT.code)
        }

        @Test
        fun `should map StorageUnavailableException to ASSET_STORAGE_UNAVAILABLE`() {
            val exception = StorageUnavailableException("dial tcp 10.0.0.1:9000 timeout")
            val parameters = createHandlerParameters(exception)

            val result = handler.handleException(parameters).get()

            // Assert
            assertThat(result.errors).hasSize(1)
            assertThat(result.errors[0].message).isEqualTo(AssetErrorCode.ASSET_STORAGE_UNAVAILABLE.defaultMessage)
            assertThat(result.errors[0].extensions).containsEntry("code", AssetErrorCode.ASSET_STORAGE_UNAVAILABLE.code)
            assertThat(result.errors[0].extensions).containsEntry("service", "assets")
        }

        @Test
        fun `should map optimistic lock exception to OPTIMISTIC_LOCK_ERROR`() {
            val exception = StaleStateException("stale row version")
            val parameters = createHandlerParameters(exception)

            val result = handler.handleException(parameters).get()

            // Assert
            assertThat(result.errors).hasSize(1)
            assertThat(result.errors[0].message).isEqualTo(CommonErrorCode.OPTIMISTIC_LOCK_ERROR.defaultMessage)
            assertThat(result.errors[0].extensions).containsEntry("code", CommonErrorCode.OPTIMISTIC_LOCK_ERROR.code)
        }

        @Test
        fun `should map unknown exception to INTERNAL_ERROR`() {
            val exception = RuntimeException("sensitive internal stack detail")
            val parameters = createHandlerParameters(exception)

            val result = handler.handleException(parameters).get()

            assertThat(result.errors).hasSize(1)
            assertThat(result.errors[0].message).isEqualTo(CommonErrorCode.INTERNAL_ERROR.defaultMessage)
            assertThat(result.errors[0].extensions).containsEntry("code", CommonErrorCode.INTERNAL_ERROR.code)
            assertThat(result.errors[0].message).doesNotContain("sensitive")
        }
    }

    private fun createMockEnvironment(): DataFetchingEnvironment {
        val env = mock<DataFetchingEnvironment>()
        val executionStepInfo = mock<ExecutionStepInfo>()
        val resultPath = ResultPath.rootPath()

        whenever(executionStepInfo.path).thenReturn(resultPath)
        whenever(env.getExecutionStepInfo()).thenReturn(executionStepInfo)

        return env
    }

    private fun createHandlerParameters(exception: Exception): DataFetcherExceptionHandlerParameters {
        val env = createMockEnvironment()
        return DataFetcherExceptionHandlerParameters
            .newExceptionParameters()
            .exception(exception)
            .dataFetchingEnvironment(env)
            .build()
    }
}
