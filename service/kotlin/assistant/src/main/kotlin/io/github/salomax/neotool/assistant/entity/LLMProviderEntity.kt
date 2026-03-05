package io.github.salomax.neotool.assistant.entity

import io.github.salomax.neotool.assistant.domain.enums.ProviderType
import io.github.salomax.neotool.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import jakarta.persistence.Version
import org.hibernate.annotations.Generated
import org.hibernate.generator.EventType
import java.time.Instant
import java.util.UUID

/**
 * JPA entity for assistant.llm_providers.
 */
@Entity
@Table(
    name = "llm_providers",
    schema = "assistant",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_llm_providers_name",
            columnNames = ["name"],
        ),
    ],
)
open class LLMProviderEntity(
    @Id
    @Generated(event = [EventType.INSERT])
    @Column(columnDefinition = "uuid", insertable = false, updatable = false)
    override val id: UUID? = null,
    @Column(nullable = false)
    open var name: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    open var type: ProviderType,
    @Column(name = "base_url", nullable = false)
    open var baseUrl: String,
    @Column(name = "api_key_ref", nullable = false)
    open var apiKeyRef: String,
    @Column(nullable = false)
    open var status: String = "ACTIVE",
    @Column(nullable = false)
    open var priority: Int = 100,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
    @Version
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now(),
) : BaseEntity<UUID?>(id)
