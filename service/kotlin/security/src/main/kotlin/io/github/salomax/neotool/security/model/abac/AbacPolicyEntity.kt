package io.github.salomax.neotool.security.model.abac
import io.github.salomax.neotool.common.entity.BaseEntity
import io.github.salomax.neotool.security.domain.abac.PolicyEffect
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID
import io.github.salomax.neotool.security.domain.abac.AbacPolicy as AbacPolicyDomain

@Entity
@Table(name = "abac_policies", schema = "security")
open class AbacPolicyEntity(
    @Id
    @Column(columnDefinition = "uuid")
    override val id: UUID = UUID.randomUUID(),
    @Column(nullable = false, unique = true)
    open var name: String,
    @Column
    open var description: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    open var effect: PolicyEffect,
    @Column(nullable = false, columnDefinition = "TEXT")
    open var condition: String,
    @Column(nullable = false)
    open var version: Int = 1,
    @Column(name = "is_active", nullable = false)
    open var isActive: Boolean = true,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now(),
) : BaseEntity<UUID>(id) {
    fun toDomain(): AbacPolicyDomain {
        return AbacPolicyDomain(
            id = this.id,
            name = this.name,
            description = this.description,
            effect = this.effect,
            condition = this.condition,
            version = this.version,
            isActive = this.isActive,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt,
        )
    }
}
