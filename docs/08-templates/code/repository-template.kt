package io.github.salomax.neotool.{module}.repo

import io.github.salomax.neotool.{module}.entity.{EntityName}Entity
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * Repository for {EntityName} entity.
 * 
 * Replace:
 * - {module} with your module name (app, security, assistant, etc.)
 * - {EntityName} with your entity name (e.g., Product, Customer)
 * - Add custom query methods as needed
 * 
 * Query method naming conventions:
 * - findBy{Field} - Find single entity by field
 * - findAllBy{Field} - Find multiple entities by field
 * - existsBy{Field} - Check if entity exists
 * - countBy{Field} - Count entities by field
 */
@Repository
interface {EntityName}Repository : JpaRepository<{EntityName}Entity, UUID> {
    // Add custom query methods here
    // Example: fun findByEmail(email: String): {EntityName}Entity?
    // Example: fun findAllByStatus(status: String): List<{EntityName}Entity>
}
