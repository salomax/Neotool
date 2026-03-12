package io.github.salomax.neotool.assistant.infra.kafka

class BankingReaderProcessingException(
    val code: String,
    override val message: String,
    val retryable: Boolean,
    val stage: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
