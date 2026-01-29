package io.github.salomax.neotool.comms.email.kafka

import io.github.salomax.neotool.comms.email.events.EmailSendRequestedEvent
import io.micronaut.configuration.kafka.annotation.KafkaClient
import io.micronaut.configuration.kafka.annotation.KafkaKey
import io.micronaut.configuration.kafka.annotation.Topic
import jakarta.inject.Singleton

@Singleton
@KafkaClient
interface EmailSendProducer {
    @Topic(EmailTopics.SEND_REQUESTED)
    fun send(
        @KafkaKey key: String,
        message: EmailSendRequestedEvent,
    )
}
