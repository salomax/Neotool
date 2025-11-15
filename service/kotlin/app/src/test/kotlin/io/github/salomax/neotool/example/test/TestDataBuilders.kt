package io.github.salomax.neotool.example.test

import io.github.salomax.neotool.example.domain.Customer
import io.github.salomax.neotool.example.domain.CustomerStatus
import io.github.salomax.neotool.example.domain.Product
import java.time.Instant
import java.util.UUID

/**
 * Test data builders for creating test entities with sensible defaults
 */
object TestDataBuilders {

    fun product(
        id: UUID? = null,
        name: String = "Test Product",
        sku: String = "TEST-001",
        priceCents: Long = 9999L,
        stock: Int = 10,
        createdAt: Instant = Instant.now(),
        updatedAt: Instant = Instant.now()
    ): Product = Product(
        id = id,
        name = name,
        sku = sku,
        priceCents = priceCents,
        stock = stock,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    fun customer(
        id: UUID? = null,
        name: String = "Test Customer",
        email: String = "test@example.com",
        status: CustomerStatus = CustomerStatus.ACTIVE,
        createdAt: Instant = Instant.now(),
        updatedAt: Instant = Instant.now()
    ): Customer = Customer(
        id = id,
        name = name,
        email = email,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    fun productInput(
        name: String = "Test Product",
        sku: String = "TEST-001",
        priceCents: Long = 9999L,
        stock: Int = 10
    ): Map<String, Any> = mapOf(
        "name" to name,
        "sku" to sku,
        "priceCents" to priceCents,
        "stock" to stock
    )

    fun customerInput(
        name: String = "Test Customer",
        email: String = "test@example.com",
        status: String = "ACTIVE"
    ): Map<String, Any> = mapOf(
        "name" to name,
        "email" to email,
        "status" to status
    )

    fun graphQLQuery(query: String, variables: Map<String, Any>? = null): Map<String, Any> =
        mapOf("query" to query) + if (variables != null) mapOf("variables" to variables) else emptyMap()

    fun createProductMutation(
        name: String = "Test Product",
        sku: String = "TEST-001",
        priceCents: Long = 9999L,
        stock: Int = 10
    ): Map<String, Any> = graphQLQuery(
        """
        mutation {
            createProduct(input: {
                name: "$name"
                sku: "$sku"
                priceCents: $priceCents
                stock: $stock
            }) {
                id
                name
                sku
                priceCents
                stock
                createdAt
                updatedAt
            }
        }
        """.trimIndent()
    )

    fun createCustomerMutation(
        name: String = "Test Customer",
        email: String = "test@example.com",
        status: String = "ACTIVE"
    ): Map<String, Any> = graphQLQuery(
        """
        mutation {
            createCustomer(input: {
                name: "$name"
                email: "$email"
                status: "$status"
            }) {
                id
                name
                email
                status
                createdAt
                updatedAt
            }
        }
        """.trimIndent()
    )

    fun productsQuery(): Map<String, Any> = graphQLQuery(
        """
        query {
            products {
                id
                name
                sku
                priceCents
                stock
                createdAt
                updatedAt
            }
        }
        """.trimIndent()
    )

    fun customersQuery(): Map<String, Any> = graphQLQuery(
        """
        query {
            customers {
                id
                name
                email
                status
                createdAt
                updatedAt
            }
        }
        """.trimIndent()
    )
}
