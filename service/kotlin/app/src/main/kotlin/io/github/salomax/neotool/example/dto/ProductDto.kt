package io.github.salomax.neotool.example.dto

import io.github.salomax.neotool.example.domain.Product
import io.micronaut.serde.annotation.Serdeable
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

@Serdeable
data class CreateProductRequest(
    @field:NotBlank(message = "Product name is required")
    val name: String,
    
    @field:NotBlank(message = "SKU is required")
    val sku: String,
    
    @field:NotNull(message = "Price is required")
    @field:DecimalMin(value = "0.0", message = "Price must be non-negative")
    val priceCents: Long,
    
    @field:NotNull(message = "Stock is required")
    @field:Min(value = 0, message = "Stock must be non-negative")
    val stock: Int
) {
  fun toDomain(): Product {
    return Product(
      name = this.name,
      sku = this.sku,
      priceCents = this.priceCents,
      stock = this.stock
    )
  }
}

@Serdeable
data class UpdateProductRequest(
    @field:NotBlank(message = "Product name is required")
    val name: String,
    
    @field:NotBlank(message = "SKU is required")
    val sku: String,
    
    @field:NotNull(message = "Price is required")
    @field:DecimalMin(value = "0.0", message = "Price must be non-negative")
    val priceCents: Long,
    
    @field:NotNull(message = "Stock is required")
    @field:Min(value = 0, message = "Stock must be non-negative")
    val stock: Int,

    val version: Long
) {
  fun toDomain(id: UUID): Product {
    return Product(
      id = id,
      name = this.name,
      sku = this.sku,
      priceCents = this.priceCents,
      stock = this.stock,
      updatedAt = Instant.now(),
      version = this.version
    )
  }
}

@Serdeable
data class ProductResponse(
  val id: String?,
  val name: String,
  val sku: String,
  val priceCents: Long,
  val stock: Int,
  val createdAt: Instant,
  val updatedAt: Instant,
  val version: Long
)

@Serdeable
data class ProductListResponse(
    val products: List<ProductResponse>,
    val total: Int
)
