package io.github.salomax.neotool.security.model.rbac
import io.github.salomax.neotool.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID
import io.github.salomax.neotool.security.domain.rbac.Group as GroupDomain

@Entity
@Table(name = "groups", schema = "security")
open class GroupEntity(
    @Id
    @Column(columnDefinition = "uuid")
    override val id: UUID = UUID.randomUUID(),
    @Column(nullable = false, unique = true)
    open var name: String,
    @Column
    open var description: String? = null,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now(),
    @Version
    open var version: Long = 0,
) : BaseEntity<UUID>(id) {
    fun toDomain(): GroupDomain {
        return GroupDomain(
            id = this.id,
            name = this.name,
            description = this.description,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt,
            version = this.version,
        )
    }
}
