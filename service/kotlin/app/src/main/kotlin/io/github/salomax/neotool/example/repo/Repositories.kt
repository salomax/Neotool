package io.github.salomax.neotool.example.repo

import io.github.salomax.neotool.example.entity.CustomerEntity
import io.github.salomax.neotool.example.entity.ProductEntity
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.UUID

@Repository
interface ProductRepository : JpaRepository<ProductEntity, UUID> {
    fun findBySku(sku: String): ProductEntity?
}

@Repository
interface CustomerRepository : JpaRepository<CustomerEntity, UUID> {
    fun findByEmail(email: String): CustomerEntity?
}
