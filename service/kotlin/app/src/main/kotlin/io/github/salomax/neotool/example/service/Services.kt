package io.github.salomax.neotool.example.service

import io.github.salomax.neotool.example.domain.Customer
import io.github.salomax.neotool.example.domain.Product
import io.github.salomax.neotool.example.repo.CustomerRepository
import io.github.salomax.neotool.example.repo.ProductRepository
import io.github.salomax.neotool.logging.LoggingUtils.logAuditData
import io.github.salomax.neotool.logging.LoggingUtils.logMethodEntry
import io.github.salomax.neotool.logging.LoggingUtils.logMethodExit
import io.micronaut.http.server.exceptions.NotFoundException
import jakarta.inject.Singleton
import jakarta.transaction.Transactional
import mu.KotlinLogging
import java.util.UUID

@Singleton
open class ProductService(
    private val repo: ProductRepository
) {
    private val logger = KotlinLogging.logger {}

    fun list(): List<Product> {
        val entities = repo.findAll()
        val products = entities.map { it.toDomain() }
        logAuditData("SELECT_ALL", "ProductService", null, "count" to products.size)
        return products
    }
    
    fun get(id: UUID): Product? {
        val entity = repo.findById(id).orElse(null)
        val product = entity?.toDomain()
        if (product != null) {
            logAuditData("SELECT_BY_ID", "ProductService", id.toString())
            logger.debug { "Product found: ${product.name} (SKU: ${product.sku})" }
        } else {
            logAuditData("SELECT_BY_ID", "ProductService", id.toString(), "result" to "NOT_FOUND")
            logger.debug { "Product not found with ID: $id" }
        }
        return product
    }

    @Transactional
    open fun create(product: Product): Product {
        val entity = product.toEntity()
        val saved = repo.save(entity)
        val result = saved.toDomain()
        logAuditData("INSERT", "ProductService", result.id.toString(), "name" to result.name, "sku" to result.sku)
        logger.info { "Product created successfully: ${result.name} (ID: ${result.id})" }
        return result
    }

    @Transactional
    open fun update(product: Product): Product {
        val updatedEntity = product.toEntity()
        val saved = repo.update(updatedEntity)
        val result = saved.toDomain()
        logAuditData("UPDATE", "ProductService", result.id.toString(), "name" to result.name, "sku" to result.sku)
        logger.info { "Product updated successfully: ${result.name} (ID: ${result.id})" }
        return result
    }

    @Transactional
    open fun delete(id: UUID) {
        val found = repo.findById(id).orElseThrow {
            logger.warn { "Attempted to delete non-existent product with ID: $id" }
            NotFoundException() 
        }
        repo.delete(found)
        logAuditData("DELETE", "ProductService", id.toString(), "name" to found.name, "sku" to found.sku)
        logger.info { "Product deleted successfully: ${found.name} (ID: $id)" }
    }
}

@Singleton
open class CustomerService(
    private val repo: CustomerRepository
) {
    private val logger = KotlinLogging.logger {}

    fun list(): List<Customer> {
        val entities = repo.findAll()
        val customers = entities.map { it.toDomain() }
        logAuditData("SELECT_ALL", "CustomerService", null, "count" to customers.size)
        return customers
    }
    
    fun get(id: UUID): Customer? {
        val entity = repo.findById(id).orElse(null)
        val customer = entity?.toDomain()
        if (customer != null) {
            logAuditData("SELECT_BY_ID", "CustomerService", id.toString())
            logger.debug { "Customer found: ${customer.name} (Email: ${customer.email}, Version: ${customer.version})" }
            println("DEBUG: CustomerService.get - entity version: ${entity?.version}, customer version: ${customer.version}")
        } else {
            logAuditData("SELECT_BY_ID", "CustomerService", id.toString(), "result" to "NOT_FOUND")
            logger.debug { "Customer not found with ID: $id" }
        }
        return customer
    }

    @Transactional
    open fun create(customer: Customer): Customer {
        val entity = customer.toEntity()
        val saved = repo.save(entity)
        val result = saved.toDomain()
        logAuditData("INSERT", "CustomerService", result.id.toString(), "name" to result.name, "email" to result.email)
        logger.info { "Customer created successfully: ${result.name} (ID: ${result.id})" }
        return result
    }

    @Transactional
    open fun update(customer: Customer): Customer {
        println("DEBUG: CustomerService.update - customer: $customer, version: ${customer.version}")
        
        // Fetch the existing entity to preserve the version and other fields
        val existingEntity = repo.findById(customer.id!!).orElseThrow {
            logger.warn { "Attempted to update non-existent customer with ID: ${customer.id}" }
            NotFoundException()
        }
        
        println("DEBUG: CustomerService.update - existingEntity version: ${existingEntity.version}")
        
        // Check if the version matches (optimistic locking)
        if (existingEntity.version != customer.version) {
            throw org.hibernate.StaleObjectStateException(
                "Customer with id ${customer.id} was modified by another user",
                customer.id
            )
        }
        
        // Update only the fields that changed
        existingEntity.name = customer.name
        existingEntity.email = customer.email
        existingEntity.status = customer.status
        existingEntity.updatedAt = java.time.Instant.now()
        
        // Save the updated entity (JPA will automatically increment the version)
        val saved = repo.save(existingEntity)
        val result = saved.toDomain()
        logAuditData("UPDATE", "CustomerService", result.id.toString(), "name" to result.name, "email" to result.email)
        logger.info { "Customer updated successfully: ${result.name} (ID: ${result.id})" }
        return result
    }

    @Transactional
    open fun delete(id: UUID) {
        val found = repo.findById(id).orElseThrow {
            logger.warn { "Attempted to delete non-existent customer with ID: $id" }
            NotFoundException() 
        }
        repo.delete(found)
        
        logAuditData("DELETE", "CustomerService", id.toString(), "name" to found.name, "email" to found.email)
        logger.info { "Customer deleted successfully: ${found.name} (ID: $id)" }
    }
}
