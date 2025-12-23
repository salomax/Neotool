package io.github.salomax.neotool.{module}.repo

import io.github.salomax.neotool.{module}.entity.{EntityName}Entity
import java.util.UUID

/**
 * Custom query contract for {@link {EntityName}Repository}.
 * 
 * Use this interface for methods that require custom implementation:
 * - Complex queries with dynamic conditions
 * - Queries using JPA Criteria API
 * - Queries that don't follow Micronaut Data naming conventions
 * - Cursor-based pagination with complex ordering
 * 
 * Replace:
 * - {module} with your module name (app, security, assistant, etc.)
 * - {EntityName} with your entity name (e.g., Product, Customer)
 * 
 * IMPORTANT: This interface should NOT extend JpaRepository or use @Repository annotation.
 * The implementation will be provided by {EntityName}RepositoryImpl.
 * 
 * See repository-pattern.md for complete pattern documentation.
 */
interface {EntityName}RepositoryCustom {
    // Add custom query method signatures here
    // Example: fun searchByNameOrEmail(query: String?, first: Int, after: UUID?): List<{EntityName}Entity>
    // Example: fun countByNameOrEmail(query: String?): Long
}














