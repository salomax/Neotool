package io.github.salomax.neotool.comms.graphql

import graphql.execution.DataFetcherExceptionHandlerParameters
import graphql.execution.ExecutionStepInfo
import graphql.execution.ResultPath
import graphql.schema.DataFetchingEnvironment
import io.github.salomax.neotool.common.error.CommonErrorCode
import io.github.salomax.neotool.common.security.exception.AuthenticationRequiredException
import io.github.salomax.neotool.comms.template.domain.Channel
import io.github.salomax.neotool.comms.template.registry.TemplateNotFoundException
import io.github.salomax.neotool.comms.template.substitution.MissingVariableException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Locale

@DisplayName("CommsGraphQLExceptionHandler Tests")
class CommsGraphQLExceptionHandlerTest {
    private val handler = CommsGraphQLExceptionHandler()

    private fun createMockEnvironment(): DataFetchingEnvironment {
        val env = mock<DataFetchingEnvironment>()
        val executionStepInfo = mock<ExecutionStepInfo>()
        whenever(executionStepInfo.path).thenReturn(ResultPath.rootPath())
        whenever(env.getExecutionStepInfo()).thenReturn(executionStepInfo)
        return env
    }

    private fun handle(exception: Throwable) =
        handler
            .handleException(
                DataFetcherExceptionHandlerParameters
                    .newExceptionParameters()
                    .exception(exception)
                    .dataFetchingEnvironment(createMockEnvironment())
                    .build(),
            ).get()

    @Test
    fun `should map authentication required to AUTH code`() {
        val result = handle(AuthenticationRequiredException("token missing"))
        val error = result.errors.single()

        assertThat(error.message).isEqualTo("Authentication required")
        assertThat(error.extensions["code"]).isEqualTo("AUTH_AUTHENTICATION_REQUIRED")
        assertThat(error.extensions["service"]).isEqualTo("comms")
    }

    @Test
    fun `should map illegal argument to INVALID_INPUT`() {
        val result = handle(IllegalArgumentException("raw parsing detail"))
        val error = result.errors.single()

        assertThat(error.message).isEqualTo(CommonErrorCode.INVALID_INPUT.defaultMessage)
        assertThat(error.extensions["code"]).isEqualTo(CommonErrorCode.INVALID_INPUT.code)
        assertThat(error.extensions["service"]).isEqualTo("comms")
    }

    @Test
    fun `should map template not found without leaking internals`() {
        val result =
            handle(
                TemplateNotFoundException(
                    key = "secret-template",
                    locale = Locale.ENGLISH,
                    channel = Channel.EMAIL,
                ),
            )
        val error = result.errors.single()

        assertThat(error.message).isEqualTo(CommonErrorCode.NOT_FOUND.defaultMessage)
        assertThat(error.extensions["code"]).isEqualTo(CommonErrorCode.NOT_FOUND.code)
        assertThat(error.message).doesNotContain("secret-template")
    }

    @Test
    fun `should map missing variables to INVALID_INPUT with parameters`() {
        val result = handle(MissingVariableException(listOf("resetUrl", "name")))
        val error = result.errors.single()

        assertThat(error.message).isEqualTo(CommonErrorCode.INVALID_INPUT.defaultMessage)
        assertThat(error.extensions["code"]).isEqualTo(CommonErrorCode.INVALID_INPUT.code)

        @Suppress("UNCHECKED_CAST")
        val parameters = error.extensions["parameters"] as Map<String, Any>
        assertThat(parameters["missingVariables"]).isEqualTo(listOf("resetUrl", "name"))
    }

    @Test
    fun `should map unexpected exception to INTERNAL_ERROR`() {
        val result = handle(RuntimeException("db password exposed"))
        val error = result.errors.single()

        assertThat(error.message).isEqualTo(CommonErrorCode.INTERNAL_ERROR.defaultMessage)
        assertThat(error.extensions["code"]).isEqualTo(CommonErrorCode.INTERNAL_ERROR.code)
        assertThat(error.message).doesNotContain("password")
    }
}
