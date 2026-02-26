package io.github.salomax.neotool.financialdata.test.graphql

import graphql.execution.DataFetcherExceptionHandlerParameters
import graphql.execution.ExecutionStepInfo
import graphql.execution.ResultPath
import graphql.schema.DataFetchingEnvironment
import io.github.salomax.neotool.common.error.CommonErrorCode
import io.github.salomax.neotool.financialdata.domain.fixedincome.FixedIncomeInvalidEffectiveRateException
import io.github.salomax.neotool.financialdata.graphql.FinancialDataGraphQLExceptionHandler
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal

@DisplayName("FinancialDataGraphQLExceptionHandler Tests")
class FinancialDataGraphQLExceptionHandlerTest {
    private val handler = FinancialDataGraphQLExceptionHandler()

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
    fun `should map fixed income invalid rate exception to specific code with sanitized message`() {
        val result = handle(FixedIncomeInvalidEffectiveRateException(BigDecimal("-1.5")))
        val error = result.errors.single()

        assertThat(error.message).isEqualTo(FixedIncomeInvalidEffectiveRateException.DEFAULT_MESSAGE)
        assertThat(error.extensions["code"]).isEqualTo(FixedIncomeInvalidEffectiveRateException.CODE)
        assertThat(error.extensions["service"]).isEqualTo("financialdata")
        assertThat(error.message).doesNotContain("-1.5")
    }

    @Test
    fun `should map illegal argument exception to invalid input code`() {
        val result = handle(IllegalArgumentException("Invalid cursor format: abc123"))
        val error = result.errors.single()

        assertThat(error.message).isEqualTo(CommonErrorCode.INVALID_INPUT.defaultMessage)
        assertThat(error.extensions["code"]).isEqualTo(CommonErrorCode.INVALID_INPUT.code)
        assertThat(error.extensions["service"]).isEqualTo("financialdata")
        assertThat(error.message).doesNotContain("abc123")
    }

    @Test
    fun `should map unexpected exceptions to internal error`() {
        val result = handle(RuntimeException("database password leak"))
        val error = result.errors.single()

        assertThat(error.message).isEqualTo(CommonErrorCode.INTERNAL_ERROR.defaultMessage)
        assertThat(error.extensions["code"]).isEqualTo(CommonErrorCode.INTERNAL_ERROR.code)
        assertThat(error.extensions["service"]).isEqualTo("financialdata")
        assertThat(error.message).doesNotContain("password")
    }
}
