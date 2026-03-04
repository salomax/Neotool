package io.github.salomax.neotool.banking.infra.kafka

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import jakarta.annotation.PostConstruct
import jakarta.inject.Singleton
import org.apache.kafka.clients.admin.Admin
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.common.config.TopicConfig
import org.slf4j.LoggerFactory
import java.util.Properties

/**
 * Creates import Kafka topics at startup when Kafka is enabled.
 * Idempotent: existing topics are left unchanged (Kafka returns success for already-existing topics).
 * Used in dev/staging; production may create topics via infra (Terraform/Helm).
 */
@Singleton
@Requires(property = "kafka.enabled", value = "true")
class KafkaTopicInitializer(
    @Property(name = "kafka.bootstrap.servers")
    private val bootstrapServers: String,
    @Property(name = "kafka.topics.import-file-requested")
    private val importFileRequested: String,
    @Property(name = "kafka.topics.import-file-requested-dlt")
    private val importFileRequestedDlt: String,
    @Property(name = "kafka.topics.import-job-completed")
    private val importJobCompleted: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostConstruct
    fun createTopics() {
        if (bootstrapServers.isBlank()) {
            log.warn("kafka.bootstrap.servers is blank; skipping topic creation")
            return
        }
        val props = Properties().apply {
            setProperty("bootstrap.servers", bootstrapServers)
        }
        Admin.create(props).use { admin ->
            val topics = listOf(
                NewTopic(importFileRequested, 6, 1)
                    .configs(
                        mapOf(
                            TopicConfig.RETENTION_MS_CONFIG to (7 * 24 * 60 * 60 * 1000).toString(), // 7 days
                            TopicConfig.CLEANUP_POLICY_CONFIG to "delete",
                        ),
                    ),
                NewTopic(importFileRequestedDlt, 1, 1)
                    .configs(
                        mapOf(
                            TopicConfig.RETENTION_MS_CONFIG to (30 * 24 * 60 * 60 * 1000).toString(), // 30 days
                            TopicConfig.CLEANUP_POLICY_CONFIG to "delete",
                        ),
                    ),
                NewTopic(importJobCompleted, 6, 1)
                    .configs(
                        mapOf(
                            TopicConfig.RETENTION_MS_CONFIG to (24 * 60 * 60 * 1000).toString(), // 24 hours
                            TopicConfig.CLEANUP_POLICY_CONFIG to "delete",
                        ),
                    ),
            )
            admin.createTopics(topics).all().get()
            log.info("Kafka import topics created or already exist: {}", topics.map { it.name() })
        }
    }
}
