package io.github.salomax.neotool.comms.email.kafka

object EmailTopics {
    const val SEND_REQUESTED = "comms.email.send.v1"
    const val SEND_DLQ = "comms.email.send.dlq"
}
