package io.github.salomax.neotool.banking.infra.kafka

/**
 * Kafka topic names for import flows.
 * Actual values are configured in application.yml under `kafka.topics`.
 */
object ImportTopics {
    const val FILE_REQUESTED = "banking.import.file-requested"
    const val FILE_REQUESTED_DLT = "banking.import.file-requested.DLT"
    const val JOB_COMPLETED = "banking.import.job-completed"
}
