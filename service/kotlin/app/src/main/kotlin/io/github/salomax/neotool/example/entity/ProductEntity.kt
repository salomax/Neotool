package io.github.salomax.neotool.example.entity

import io.github.salomax.neotool.entity.BaseEntity
import io.github.salomax.neotool.example.domain.Product
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "products")
open class ProductEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "uuid")
    override val id: UUID?,

  @Column(nullable = false)
  open var name: String,

  @Column(nullable = false, unique = true)
  open var sku: String,

  @Column(name = "price_cents", nullable = false)
  open var priceCents: Long = 0,

  @Column(nullable = false)
  open var stock: Int = 0,

  @Column(name = "created_at", nullable = false)
  open var createdAt: Instant = Instant.now(),

  @Column(name = "updated_at", nullable = false)
  open var updatedAt: Instant = Instant.now(),

  @Version
  open var version: Long = 0
) : BaseEntity<UUID?>(id) {
    fun toDomain(): Product {
        return Product(
            id = this.id,
            name = this.name,
            sku = this.sku,
            priceCents = this.priceCents,
            stock = this.stock,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt,
            version = this.version
        )
    }
}
