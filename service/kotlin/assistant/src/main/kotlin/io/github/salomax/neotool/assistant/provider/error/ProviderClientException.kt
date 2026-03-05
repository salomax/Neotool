package io.github.salomax.neotool.assistant.provider.error

class ProviderClientException(
    val errorType: ErrorType,
    override val message: String,
    val statusCode: Int? = null,
    val providerErrorType: String? = null,
    val providerErrorCode: String? = null,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
