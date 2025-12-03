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
 * 
 * IMPORTANT: Only add methods that Micronaut Data can auto-generate.
 * For complex queries requiring custom implementation, use the custom repository pattern:
 * - Create {EntityName}RepositoryCustom interface
 * - Create {EntityName}RepositoryImpl class implementing it
 * - Inject both repositories in services
 * 
 * Query method naming conventions (auto-generatable):
 * - findBy{Field} - Find single entity by field
 * - findAllBy{Field} - Find multiple entities by field
 * - existsBy{Field} - Check if entity exists
 * - countBy{Field} - Count entities by field
 * 
 * See repository-pattern.md for custom implementation pattern.
 */
@Repository
interface {EntityName}Repository : JpaRepository<{EntityName}Entity, UUID> {
    // Add auto-generatable query methods here
    // Example: fun findByEmail(email: String): {EntityName}Entity?
    // Example: fun findAllByStatus(status: String): List<{EntityName}Entity>
}
