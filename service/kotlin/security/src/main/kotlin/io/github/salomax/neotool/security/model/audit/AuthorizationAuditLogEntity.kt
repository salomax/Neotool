package io.github.salomax.neotool.security.model.audit
import io.github.salomax.neotool.common.entity.BaseEntity
import io.github.salomax.neotool.security.domain.audit.AuthorizationResult
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID
import io.github.salomax.neotool.security.domain.audit.AuthorizationAuditLog as AuthorizationAuditLogDomain

@Entity
@Table(name = "authorization_audit_logs", schema = "security")
open class AuthorizationAuditLogEntity(
    @Id
    @Column(columnDefinition = "uuid")
    override val id: UUID = UUID.randomUUID(),
    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    open var userId: UUID,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "groups", columnDefinition = "jsonb")
    open var groups: List<UUID>? = null,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "roles", columnDefinition = "jsonb")
    open var roles: List<UUID>? = null,
    @Column(name = "requested_action", nullable = false)
    open var requestedAction: String,
    @Column(name = "resource_type")
    open var resourceType: String? = null,
    @Column(name = "resource_id", columnDefinition = "uuid")
    open var resourceId: UUID? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "rbac_result", nullable = false, length = 16)
    open var rbacResult: AuthorizationResult,
    @Enumerated(EnumType.STRING)
    @Column(name = "abac_result", length = 16)
    open var abacResult: AuthorizationResult? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "final_decision", nullable = false, length = 16)
    open var finalDecision: AuthorizationResult,
    @Column(nullable = false)
    open var timestamp: Instant = Instant.now(),
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    open var metadata: Map<String, Any>? = null,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
) : BaseEntity<UUID>(id) {
    fun toDomain(): AuthorizationAuditLogDomain {
        return AuthorizationAuditLogDomain(
            id = this.id,
            userId = this.userId,
            groups = this.groups,
            roles = this.roles,
            requestedAction = this.requestedAction,
            resourceType = this.resourceType,
            resourceId = this.resourceId,
            rbacResult = this.rbacResult,
            abacResult = this.abacResult,
            finalDecision = this.finalDecision,
            timestamp = this.timestamp,
            metadata = this.metadata,
            createdAt = this.createdAt,
        )
    }
}
