package io.github.salomax.neotool.example.entity

import io.github.salomax.neotool.entity.BaseEntity
import io.github.salomax.neotool.example.domain.Customer
import io.github.salomax.neotool.example.domain.CustomerStatus
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "customers")
open class CustomerEntity(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(columnDefinition = "uuid")
  override val id: UUID?,

  @Column(nullable = false)
  open var name: String,

  @Column(nullable = false, unique = true)
  open var email: String,

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  open var status: CustomerStatus = CustomerStatus.ACTIVE,

  @Column(name = "created_at", nullable = false)
  open var createdAt: Instant = Instant.now(),

  @Column(name = "updated_at", nullable = false)
  open var updatedAt: Instant = Instant.now(),

  @Version
  open var version: Long = 0
) : BaseEntity<UUID?>(id) {
    fun toDomain(): Customer {
        return Customer(
            id = this.id,
            name = this.name,
            email = this.email,
            status = this.status,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt,
            version = this.version
        )
    }
}
