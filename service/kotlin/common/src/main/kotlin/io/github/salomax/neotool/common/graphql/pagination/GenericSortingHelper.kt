package io.github.salomax.neotool.common.graphql.pagination

import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.Expression
import jakarta.persistence.criteria.Order
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root

/**
 * Generic interface for order field enums.
 * All order field enums should implement this to work with generic sorting helpers.
 */
interface OrderFieldEnum {
    /**
     * Returns true if this field represents the ID field used for deterministic ordering.
     */
    fun isIdField(): Boolean
}

/**
 * Generic interface for order by specifications.
 */
interface OrderBySpec<F : OrderFieldEnum> {
    val field: F
    val direction: OrderDirection
}

/**
 * Configuration for building generic ORDER BY clauses and cursor predicates.
 *
 * @param F The order field enum type
 * @param O The order by specification type
 */
data class SortingConfig<F : OrderFieldEnum, O : OrderBySpec<F>>(
    /**
     * Maps order field enum to database column name.
     */
    val fieldToColumn: (F) -> String,
    /**
     * Builds JPA Criteria API Expression for a given field.
     * This handles special cases like COALESCE for displayName.
     */
    val buildFieldPath: (Root<*>, CriteriaBuilder, F) -> Expression<out Comparable<*>>,
    /**
     * Extracts cursor value from CompositeCursor for a given field.
     * Handles type conversion and special cases (e.g., ID field from cursor.id).
     */
    val extractCursorValue: (CompositeCursor, F, String) -> Any?,
    /**
     * Set of allowed column names for security (SQL injection prevention).
     */
    val allowedColumns: Set<String>,
    /**
     * Validates that the last field in orderBy is the ID field.
     */
    val validateIdField: (F) -> Boolean = { it.isIdField() },
)

/**
 * Generic sorting helper that works with any entity type through configuration.
 * Provides reusable utilities for building dynamic ORDER BY clauses and cursor predicates
 * using JPA Criteria API.
 */
object GenericSortingHelper {
    /**
     * Build ORDER BY clause from orderBy specifications using configuration.
     *
     * @param root The root entity path
     * @param criteriaBuilder The criteria builder
     * @param orderBy List of order by specifications
     * @param config Configuration for field mapping and path building
     * @return List of Order objects for ORDER BY clause
     */
    fun <F : OrderFieldEnum, O : OrderBySpec<F>> buildOrderBy(
        root: Root<*>,
        criteriaBuilder: CriteriaBuilder,
        orderBy: List<O>,
        config: SortingConfig<F, O>,
    ): List<Order> {
        return orderBy.map { orderBySpec ->
            val columnName = config.fieldToColumn(orderBySpec.field)
            require(columnName in config.allowedColumns) {
                "Invalid column name: $columnName. Allowed: ${config.allowedColumns}"
            }

            val path = config.buildFieldPath(root, criteriaBuilder, orderBySpec.field)

            when (orderBySpec.direction) {
                OrderDirection.ASC -> criteriaBuilder.asc(path)
                OrderDirection.DESC -> criteriaBuilder.desc(path)
            }
        }
    }

    /**
     * Build cursor predicate based on composite cursor and orderBy using configuration.
     * Creates a composite comparison predicate that matches the sort order.
     *
     * The predicate structure is:
     * (field1 > val1) OR (field1 = val1 AND field2 > val2) OR (field1 = val1 AND field2 = val2 AND field3 > val3) OR ...
     *
     * @param root The root entity path
     * @param criteriaBuilder The criteria builder
     * @param cursor Composite cursor with field values and id
     * @param orderBy List of order by specifications (must match cursor structure)
     * @param config Configuration for field mapping, path building, and cursor value extraction
     * @return Predicate for cursor-based pagination
     */
    fun <F : OrderFieldEnum, O : OrderBySpec<F>> buildCursorPredicate(
        root: Root<*>,
        criteriaBuilder: CriteriaBuilder,
        cursor: CompositeCursor,
        orderBy: List<O>,
        config: SortingConfig<F, O>,
    ): Predicate {
        require(orderBy.isNotEmpty()) { "orderBy must not be empty" }
        require(config.validateIdField(orderBy.last().field)) {
            "Last orderBy field must be ID for deterministic ordering"
        }

        // Build composite predicate: (field1 > val1) OR (field1 = val1 AND field2 > val2) OR ...
        val predicates = mutableListOf<Predicate>()

        // For each position in the sort order, build a predicate chain
        for (i in orderBy.indices) {
            val currentOrderBy = orderBy[i]
            val fieldName = config.fieldToColumn(currentOrderBy.field)

            // Get field path
            val fieldPath = config.buildFieldPath(root, criteriaBuilder, currentOrderBy.field)

            // Get cursor value for this field
            val cursorValue = config.extractCursorValue(cursor, currentOrderBy.field, fieldName)

            if (cursorValue == null) {
                continue
            }

            // Build predicate chain: all previous fields equal AND current field greater
            val equalPredicates = mutableListOf<Predicate>()
            for (j in 0 until i) {
                val prevOrderBy = orderBy[j]
                val prevFieldName = config.fieldToColumn(prevOrderBy.field)
                val prevFieldPath = config.buildFieldPath(root, criteriaBuilder, prevOrderBy.field)
                val prevCursorValue = config.extractCursorValue(cursor, prevOrderBy.field, prevFieldName)

                if (prevCursorValue != null) {
                    equalPredicates.add(
                        criteriaBuilder.equal(prevFieldPath, criteriaBuilder.literal(prevCursorValue)),
                    )
                }
            }

            // Current field greater than cursor value (or less than for DESC)
            @Suppress("UNCHECKED_CAST")
            val greaterPredicate =
                when (currentOrderBy.direction) {
                    OrderDirection.ASC ->
                        criteriaBuilder.greaterThan(
                            fieldPath as Expression<out Comparable<Any>>,
                            cursorValue as Comparable<Any>,
                        )
                    OrderDirection.DESC ->
                        criteriaBuilder.lessThan(
                            fieldPath as Expression<out Comparable<Any>>,
                            cursorValue as Comparable<Any>,
                        )
                }

            // Combine: (prev1 = val1 AND prev2 = val2 AND ... AND current > val)
            if (equalPredicates.isNotEmpty()) {
                // Chain equal predicates: p1 AND p2 AND p3 AND ...
                var combinedEqualPredicate = equalPredicates[0]
                for (k in 1 until equalPredicates.size) {
                    combinedEqualPredicate = criteriaBuilder.and(combinedEqualPredicate, equalPredicates[k])
                }
                // Combine with greater/less predicate
                predicates.add(
                    criteriaBuilder.and(combinedEqualPredicate, greaterPredicate),
                )
            } else {
                predicates.add(greaterPredicate)
            }
        }

        // OR all predicates together
        // If no predicates were built (e.g., all cursor values were null), return a predicate that always evaluates to false
        // This can happen with legacy cursors that don't have fieldValues
        if (predicates.isEmpty()) {
            // Return a predicate that always evaluates to false (no results)
            // This handles the case where cursor has no usable field values
            return criteriaBuilder.disjunction() // Always false
        }
        return criteriaBuilder.or(*predicates.toTypedArray())
    }
}
