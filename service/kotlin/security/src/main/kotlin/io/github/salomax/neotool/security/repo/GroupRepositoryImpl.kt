package io.github.salomax.neotool.security.repo

import io.github.salomax.neotool.common.graphql.pagination.CompositeCursor
import io.github.salomax.neotool.security.model.GroupOrderBy
import io.github.salomax.neotool.security.model.GroupOrderField
import io.github.salomax.neotool.security.model.rbac.GroupEntity
import io.micronaut.transaction.annotation.ReadOnly
import jakarta.inject.Singleton
import jakarta.persistence.EntityManager
import jakarta.persistence.TypedQuery
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root

/**
 * Custom repository bean that exposes the complex search/count queries used by {@link GroupManagementService}.
 * It implements {@link GroupRepositoryCustom} and relies on the JPA Criteria API for building the dynamic filters.
 */
@Singleton
open class GroupRepositoryImpl(
    private val entityManager: EntityManager,
) : GroupRepositoryCustom {
    /**
     * Builds a search filter predicate for querying groups by name.
     * The generated predicate is shared between the search and count methods.
     * When query is null or empty, returns null (no filter).
     * When query is provided, returns a predicate with case-insensitive LIKE matching on name.
     *
     * @param root The root entity
     * @param criteriaBuilder The criteria builder
     * @param query Search query (partial match, case-insensitive). If null or empty, returns null.
     * @return Predicate for the search filter, or null if query is null/empty
     */
    private fun buildSearchFilterPredicate(
        root: Root<GroupEntity>,
        criteriaBuilder: CriteriaBuilder,
        query: String?,
    ): Predicate? {
        val normalizedQuery = query?.trim()?.takeIf { it.isNotBlank() }

        if (normalizedQuery == null) {
            // Return null when query is null or empty (no filter)
            return null
        }

        val likePattern = "%${normalizedQuery.lowercase()}%"

        // Build LIKE condition on name field
        val namePath = root.get<String>("name")
        return criteriaBuilder.like(
            criteriaBuilder.lower(namePath),
            likePattern,
        )
    }

    @ReadOnly
    override fun searchByName(
        query: String?,
        first: Int,
        after: CompositeCursor?,
        orderBy: List<GroupOrderBy>,
    ): List<GroupEntity> {
        require(orderBy.isNotEmpty()) { "orderBy must not be empty" }
        require(orderBy.last().field == GroupOrderField.ID) {
            "Last orderBy field must be ID for deterministic ordering"
        }

        val criteriaBuilder = entityManager.criteriaBuilder
        val criteriaQuery = criteriaBuilder.createQuery(GroupEntity::class.java)
        val root = criteriaQuery.from(GroupEntity::class.java)

        // Build WHERE clause predicates
        val predicates = mutableListOf<Predicate>()

        // Add search filter predicate if query is provided
        buildSearchFilterPredicate(root, criteriaBuilder, query)?.let {
            predicates.add(it)
        }

        // Add cursor pagination predicate if after is provided
        if (after != null) {
            val cursorPredicate =
                SortingHelpers.buildGroupCursorPredicate(
                    root = root,
                    criteriaBuilder = criteriaBuilder,
                    cursor = after,
                    orderBy = orderBy,
                )
            predicates.add(cursorPredicate)
        }

        // Apply WHERE clause
        if (predicates.isNotEmpty()) {
            criteriaQuery.where(*predicates.toTypedArray())
        }

        // Build ordering dynamically from orderBy
        val orders =
            SortingHelpers.buildGroupOrderBy(
                root = root,
                criteriaBuilder = criteriaBuilder,
                orderBy = orderBy,
            )
        criteriaQuery.orderBy(orders)

        // Execute query with limit
        val typedQuery: TypedQuery<GroupEntity> = entityManager.createQuery(criteriaQuery)
        typedQuery.maxResults = first

        return typedQuery.resultList
    }

    @ReadOnly
    override fun countByName(query: String?): Long {
        val criteriaBuilder = entityManager.criteriaBuilder
        val criteriaQuery = criteriaBuilder.createQuery(Long::class.java)
        val root = criteriaQuery.from(GroupEntity::class.java)

        // Select COUNT(*)
        criteriaQuery.select(criteriaBuilder.count(root))

        // Build WHERE clause predicate
        buildSearchFilterPredicate(root, criteriaBuilder, query)?.let {
            criteriaQuery.where(it)
        }

        // Execute count query
        return entityManager.createQuery(criteriaQuery).singleResult
    }
}
