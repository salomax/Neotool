package io.github.salomax.neotool.security.repo

import io.github.salomax.neotool.security.model.UserEntity
import io.micronaut.transaction.annotation.ReadOnly
import jakarta.inject.Singleton
import jakarta.persistence.EntityManager
import jakarta.persistence.TypedQuery
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import java.util.UUID

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

        val displayNamePredicate = criteriaBuilder.like(
            criteriaBuilder.lower(
                criteriaBuilder.coalesce(displayNamePath, criteriaBuilder.literal(""))
            ),
            likePattern
        )

        val emailPredicate = criteriaBuilder.like(
            criteriaBuilder.lower(emailPath),
            likePattern
        )

        // OR condition: displayName LIKE OR email LIKE
        return criteriaBuilder.or(displayNamePredicate, emailPredicate)
    }

    @ReadOnly
    override fun searchByNameOrEmail(
        query: String?,
        first: Int,
        after: UUID?,
    ): List<UserEntity> {
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
        // CRITICAL: The cursor predicate must match the sort order
        // Since we sort by COALESCE(displayName, email) ASC, id ASC,
        // we need to use composite comparison to correctly paginate
        if (after != null) {
            // Fetch the last item to get its displayName/email for composite cursor
            val lastItem = entityManager.find(UserEntity::class.java, after)
                ?: throw IllegalArgumentException("Invalid cursor: entity not found")
            
            val displayNamePath = root.get<String?>("displayName")
            val emailPath = root.get<String>("email")
            val coalesceExpression = criteriaBuilder.coalesce(displayNamePath, emailPath)
            val idPath = root.get<UUID>("id")
            
            val lastItemSortKey = lastItem.displayName ?: lastItem.email
            val lastItemId = lastItem.id
                ?: throw IllegalStateException("Entity found but has null ID - data integrity issue")
            
            // Build composite cursor predicate:
            // (COALESCE(displayName, email) > lastItemSortKey) 
            // OR (COALESCE(displayName, email) = lastItemSortKey AND id > lastItemId)
            // This ensures we get all items that come after the cursor in the sort order
            val nameGreater = criteriaBuilder.greaterThan(
                coalesceExpression,
                criteriaBuilder.literal(lastItemSortKey)
            )
            val nameEqual = criteriaBuilder.equal(
                coalesceExpression,
                criteriaBuilder.literal(lastItemSortKey)
            )
            val idGreater = criteriaBuilder.greaterThan(idPath, lastItemId)
            val nameEqualAndIdGreater = criteriaBuilder.and(nameEqual, idGreater)
            
            predicates.add(criteriaBuilder.or(nameGreater, nameEqualAndIdGreater))
        }

        // Apply WHERE clause
        if (predicates.isNotEmpty()) {
            criteriaQuery.where(*predicates.toTypedArray())
        }

        // Build ordering: COALESCE(displayName, email) ASC, id ASC
        val displayNamePath = root.get<String?>("displayName")
        val emailPath = root.get<String>("email")
        val coalesceExpression = criteriaBuilder.coalesce(displayNamePath, emailPath)
        val orders = listOf(
            criteriaBuilder.asc(coalesceExpression),
            criteriaBuilder.asc(root.get<UUID>("id"))
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
