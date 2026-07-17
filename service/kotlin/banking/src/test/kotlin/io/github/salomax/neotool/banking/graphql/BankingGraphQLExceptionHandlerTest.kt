package io.github.salomax.neotool.banking.graphql

import graphql.execution.DataFetcherExceptionHandlerParameters
import graphql.execution.ExecutionStepInfo
import graphql.execution.ResultPath
import graphql.schema.DataFetchingEnvironment
import io.github.salomax.neotool.banking.domain.transaction.DuplicateCategoryNameException
import io.github.salomax.neotool.banking.error.BankingErrorCode
import io.github.salomax.neotool.banking.graphql.config.BankingGraphQLExceptionHandler
import io.github.salomax.neotool.common.error.CommonErrorCode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

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
    fun `maps NfceUnavailableException to SERVICE_UNAVAILABLE without leaking details`() {
        val result =
            handle(
                io.github.salomax.neotool.banking.client.import.nfce.NfceUnavailableException(
                    "SEFAZ timed out with token=secret",
                ),
            )
        val error = result.errors.single()

        assertThat(error.message).isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE.defaultMessage)
        assertThat(error.extensions["code"]).isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE.code)
        assertThat(error.extensions["service"]).isEqualTo("banking")
        assertThat(error.message).doesNotContain("secret")
    }

    @Test
    fun `maps NfceInvalidException to INVALID_INPUT for qrCodeUrl`() {
        val result =
            handle(
                io.github.salomax.neotool.banking.client.import.nfce.NfceInvalidException(
                    "cancelled NF-e",
                ),
            )
        val error = result.errors.single()

        assertThat(error.message).isEqualTo(CommonErrorCode.INVALID_INPUT.defaultMessage)
        assertThat(error.extensions["code"]).isEqualTo(CommonErrorCode.INVALID_INPUT.code)
        assertThat(error.extensions["field"]).isEqualTo(listOf("qrCodeUrl"))
    }

    @Test
    fun `maps NoSuchElementException to RESOURCE_NOT_FOUND without leaking id`() {
        val result = handle(NoSuchElementException("Transaction deadbeef not found"))
        val error = result.errors.single()

        assertThat(error.message).isEqualTo(CommonErrorCode.RESOURCE_NOT_FOUND.defaultMessage)
        assertThat(error.extensions["code"]).isEqualTo(CommonErrorCode.RESOURCE_NOT_FOUND.code)
        assertThat(error.extensions["service"]).isEqualTo("banking")
        assertThat(error.message).doesNotContain("deadbeef")
    }

    @Test
    fun `maps NoSuchElementException when wrapped by runtime or completion failure`() {
        val wrappedRuntime =
            handle(RuntimeException("outer", NoSuchElementException("Transaction deadbeef not found")))
        assertThat(wrappedRuntime.errors.single().extensions["code"])
            .isEqualTo(CommonErrorCode.RESOURCE_NOT_FOUND.code)

        val wrappedCompletion =
            handle(
                java.util.concurrent.CompletionException(
                    NoSuchElementException("missing"),
                ),
            )
        assertThat(wrappedCompletion.errors.single().extensions["code"])
            .isEqualTo(CommonErrorCode.RESOURCE_NOT_FOUND.code)
    }

    @Test
    fun `maps transaction not found text in cause chain when wrapper type is unknown`() {
        val inner = Exception("Transaction ${UUID.randomUUID()} not found")
        val result = handle(RuntimeException("wrapped", inner))
        val error = result.errors.single()

        assertThat(error.message).isEqualTo(CommonErrorCode.RESOURCE_NOT_FOUND.defaultMessage)
        assertThat(error.extensions["code"]).isEqualTo(CommonErrorCode.RESOURCE_NOT_FOUND.code)
        assertThat(error.extensions["service"]).isEqualTo("banking")
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
