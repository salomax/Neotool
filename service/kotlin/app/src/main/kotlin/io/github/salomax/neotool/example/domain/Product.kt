package io.github.salomax.neotool.example.domain

import io.github.salomax.neotool.example.dto.ProductResponse
import io.github.salomax.neotool.example.entity.ProductEntity
import java.time.Instant
import java.util.UUID

data class Product(
    val id: UUID? = null,
    val name: String,
    val sku: String,
    val priceCents: Long = 0,
    val stock: Int = 0,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val version: Long = 0
) {
    fun toEntity(): ProductEntity {
        return ProductEntity(
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
    
    fun toResponse(): ProductResponse {
        return ProductResponse(
            id = this.id?.toString(),
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
