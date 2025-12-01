package io.github.salomax.neotool.{module}.service

import io.github.salomax.neotool.common.logging.LoggingUtils.logAuditData
import io.github.salomax.neotool.{module}.domain.{DomainName}
import io.github.salomax.neotool.{module}.repo.{EntityName}Repository
import io.micronaut.http.server.exceptions.NotFoundException
import jakarta.inject.Singleton
import jakarta.transaction.Transactional
import mu.KotlinLogging
import java.util.UUID

/**
 * Service for {EntityName} business logic.
 * 
 * Replace:
 * - {module} with your module name (app, security, assistant, etc.)
 * - {EntityName} with your entity name (e.g., Product, Customer)
 * - {DomainName} with your domain object name (e.g., Product, Customer)
 */
@Singleton
open class {EntityName}Service(
    private val repo: {EntityName}Repository,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * List all {entityName}s.
     * Read operation - no @Transactional needed.
     */
    fun list(): List<{DomainName}> {
        val entities = repo.findAll()
        val domains = entities.map { it.toDomain() }
        logAuditData("SELECT_ALL", "{EntityName}Service", null, "count" to domains.size)
        return domains
    }

    /**
     * Get {entityName} by ID.
     * Read operation - no @Transactional needed.
     */
    fun get(id: UUID): {DomainName}? {
        val entity = repo.findById(id).orElse(null)
        val domain = entity?.toDomain()
        if (domain != null) {
            logAuditData("SELECT_BY_ID", "{EntityName}Service", id.toString())
            logger.debug { "{EntityName} found: ${domain.name}" }
        } else {
            logAuditData("SELECT_BY_ID", "{EntityName}Service", id.toString(), "result" to "NOT_FOUND")
            logger.debug { "{EntityName} not found with ID: $id" }
        }
        return domain
    }

    /**
     * Create new {entityName}.
     * Write operation - @Transactional required.
     */
    @Transactional
    open fun create(domain: {DomainName}): {DomainName} {
        val entity = domain.toEntity()
        val saved = repo.save(entity)
        val result = saved.toDomain()
        logAuditData("INSERT", "{EntityName}Service", result.id.toString(), "name" to result.name)
        logger.info { "{EntityName} created successfully: ${result.name} (ID: ${result.id})" }
        return result
    }

    /**
     * Update existing {entityName}.
     * Write operation - @Transactional required.
     */
    @Transactional
    open fun update(domain: {DomainName}): {DomainName} {
        val updatedEntity = domain.toEntity()
        val saved = repo.update(updatedEntity)
        val result = saved.toDomain()
        logAuditData("UPDATE", "{EntityName}Service", result.id.toString(), "name" to result.name)
        logger.info { "{EntityName} updated successfully: ${result.name} (ID: ${result.id})" }
        return result
    }

    /**
     * Delete {entityName} by ID.
     * Write operation - @Transactional required.
     */
    @Transactional
    open fun delete(id: UUID) {
        val found =
            repo.findById(id).orElseThrow {
                logger.warn { "Attempted to delete non-existent {entityName} with ID: $id" }
                NotFoundException()
            }
        repo.delete(found)
        logAuditData("DELETE", "{EntityName}Service", id.toString(), "name" to found.name)
        logger.info { "{EntityName} deleted successfully: ${found.name} (ID: $id)" }
    }
}
