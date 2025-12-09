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
import io.github.salomax.neotool.security.domain.abac.AbacPolicyVersion as AbacPolicyVersionDomain

@Entity
@Table(name = "abac_policy_versions", schema = "security")
open class AbacPolicyVersionEntity(
    @Id
    @Column(columnDefinition = "uuid")
    override val id: UUID = UUID.randomUUID(),
    @Column(name = "policy_id", nullable = false, columnDefinition = "uuid")
    open var policyId: UUID,
    @Column(nullable = false)
    open var version: Int,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    open var effect: PolicyEffect,
    @Column(nullable = false, columnDefinition = "TEXT")
    open var condition: String,
    @Column(name = "is_active", nullable = false)
    open var isActive: Boolean = false,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
    @Column(name = "created_by", columnDefinition = "uuid")
    open var createdBy: UUID? = null,
) : BaseEntity<UUID>(id) {
    fun toDomain(): AbacPolicyVersionDomain {
        return AbacPolicyVersionDomain(
            id = this.id,
            policyId = this.policyId,
            version = this.version,
            effect = this.effect,
            condition = this.condition,
            isActive = this.isActive,
            createdAt = this.createdAt,
            createdBy = this.createdBy,
        )
    }
}
