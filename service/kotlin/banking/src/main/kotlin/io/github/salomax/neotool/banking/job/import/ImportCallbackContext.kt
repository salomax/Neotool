package io.github.salomax.neotool.banking.job.import

import io.micronaut.serde.annotation.Serdeable
import java.util.UUID

@Serdeable
data class ImportCallbackContext(
    val job_id: UUID,
    val account_id: UUID,
    val user_id: UUID,
)
