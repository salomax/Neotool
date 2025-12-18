package io.github.salomax.neotool.security.repo

import io.github.salomax.neotool.common.graphql.pagination.CompositeCursor
import io.github.salomax.neotool.security.model.UserEntity
import io.github.salomax.neotool.security.service.UserOrderBy
import io.micronaut.transaction.annotation.ReadOnly
import jakarta.inject.Singleton
import jakarta.persistence.EntityManager
import jakarta.persistence.TypedQuery
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root

/**
 * Custom repository bean that exposes the complex search/count queries used by {@link UserManagementService}.
 * It implements {@link UserRepositoryCustom} and relies on the JPA Criteria API for building the dynamic filters.
 */
@Singleton
open class UserRepositoryImpl(
    private val entityManager: EntityManager,
) : UserRepositoryCustom {
    /**
     * Builds a search filter predicate for querying users by name or email.
     * The generated predicate is shared between the search and count methods.
     * When query is null or empty, returns null (no filter).
     * When query is provided, returns a predicate with case-insensitive LIKE matching
     * on displayName (with COALESCE to handle null) or email.
     *
     * @param root The root entity
     * @param criteriaBuilder The criteria builder
     * @param query Search query (partial match, case-insensitive). If null or empty, returns null.
     * @return Predicate for the search filter, or null if query is null/empty
     */
    private fun buildSearchFilterPredicate(
        root: Root<UserEntity>,
        criteriaBuilder: CriteriaBuilder,
        query: String?,
    ): Predicate? {
        val normalizedQuery = query?.trim()?.takeIf { it.isNotBlank() }

        if (normalizedQuery == null) {
            // Return null when query is null or empty (no filter)
            return null
        }

        val likePattern = "%${normalizedQuery.lowercase()}%"

        // Build OR condition: displayName LIKE OR email LIKE
        // Handle displayName nullability with COALESCE logic
        val displayNamePath = root.get<String?>("displayName")
        val emailPath = root.get<String>("email")

        val displayNamePredicate =
            criteriaBuilder.like(
                criteriaBuilder.lower(
                    criteriaBuilder.coalesce(displayNamePath, criteriaBuilder.literal("")),
                ),
                likePattern,
            )

        val emailPredicate =
            criteriaBuilder.like(
                criteriaBuilder.lower(emailPath),
                likePattern,
            )

        // OR condition: displayName LIKE OR email LIKE
        return criteriaBuilder.or(displayNamePredicate, emailPredicate)
    }

    @ReadOnly
    override fun searchByNameOrEmail(
        query: String?,
        first: Int,
        after: CompositeCursor?,
        orderBy: List<UserOrderBy>,
    ): List<UserEntity> {
        require(orderBy.isNotEmpty()) { "orderBy must not be empty" }
        require(orderBy.last().field == io.github.salomax.neotool.security.service.UserOrderField.ID) {
            "Last orderBy field must be ID for deterministic ordering"
        }

        val criteriaBuilder = entityManager.criteriaBuilder
        val criteriaQuery = criteriaBuilder.createQuery(UserEntity::class.java)
        val root = criteriaQuery.from(UserEntity::class.java)

        // Build WHERE clause predicates
        val predicates = mutableListOf<Predicate>()

        // Add search filter predicate if query is provided
        buildSearchFilterPredicate(root, criteriaBuilder, query)?.let {
            predicates.add(it)
        }

        // Add cursor pagination predicate if after is provided
        if (after != null) {
            val cursorPredicate =
                SortingHelpers.buildUserCursorPredicate(
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
            SortingHelpers.buildUserOrderBy(
                root = root,
                criteriaBuilder = criteriaBuilder,
                orderBy = orderBy,
            )
        criteriaQuery.orderBy(orders)

        // Execute query with limit
        val typedQuery: TypedQuery<UserEntity> = entityManager.createQuery(criteriaQuery)
        typedQuery.maxResults = first

        return typedQuery.resultList
    }

    @ReadOnly
    override fun countByNameOrEmail(query: String?): Long {
        val criteriaBuilder = entityManager.criteriaBuilder
        val criteriaQuery = criteriaBuilder.createQuery(Long::class.java)
        val root = criteriaQuery.from(UserEntity::class.java)

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
