package io.github.salomax.neotool.banking.graphql

import io.github.salomax.neotool.banking.graphql.config.*
import graphql.execution.DataFetcherExceptionHandlerParameters
import graphql.execution.ExecutionStepInfo
import graphql.execution.ResultPath
import graphql.schema.DataFetchingEnvironment
import io.github.salomax.neotool.banking.domain.transaction.DuplicateCategoryNameException
import io.github.salomax.neotool.banking.error.BankingErrorCode
import io.github.salomax.neotool.common.error.CommonErrorCode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@DisplayName("BankingGraphQLExceptionHandler Tests")
class BankingGraphQLExceptionHandlerTest {
    private val handler = BankingGraphQLExceptionHandler()

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
    fun `maps category domain exception to code with field and parameters`() {
        val result = handle(DuplicateCategoryNameException("Test 3"))
        val error = result.errors.single()

        assertThat(error.message).isEqualTo(BankingErrorCode.CATEGORY_NAME_ALREADY_EXISTS.defaultMessage)
        assertThat(error.extensions["code"]).isEqualTo(BankingErrorCode.CATEGORY_NAME_ALREADY_EXISTS.code)
        assertThat(error.extensions["service"]).isEqualTo("banking")
        assertThat(error.extensions["field"]).isEqualTo(listOf("name"))

        @Suppress("UNCHECKED_CAST")
        val parameters = error.extensions["parameters"] as Map<String, Any>
        assertThat(parameters["name"]).isEqualTo("Test 3")
    }

    @Test
    fun `maps illegal argument to INVALID_INPUT without leaking details`() {
        val result = handle(IllegalArgumentException("raw parser details"))
        val error = result.errors.single()

        assertThat(error.message).isEqualTo(CommonErrorCode.INVALID_INPUT.defaultMessage)
        assertThat(error.extensions["code"]).isEqualTo(CommonErrorCode.INVALID_INPUT.code)
        assertThat(error.extensions["service"]).isEqualTo("banking")
        assertThat(error.message).doesNotContain("raw parser details")
    }

    @Test
    fun `maps unknown exception to INTERNAL_ERROR`() {
        val result = handle(RuntimeException("db password leaked"))
        val error = result.errors.single()

        assertThat(error.message).isEqualTo(CommonErrorCode.INTERNAL_ERROR.defaultMessage)
        assertThat(error.extensions["code"]).isEqualTo(CommonErrorCode.INTERNAL_ERROR.code)
        assertThat(error.extensions["service"]).isEqualTo("banking")
        assertThat(error.message).doesNotContain("password")
    }
}
