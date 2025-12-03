package io.github.salomax.neotool.security.repo

import io.github.salomax.neotool.security.model.RoleEntity
import io.micronaut.transaction.annotation.ReadOnly
import jakarta.inject.Singleton
import jakarta.persistence.EntityManager
import jakarta.persistence.TypedQuery
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root

/**
 * Custom repository bean that exposes the complex search/count queries used by {@link RoleManagementService}.
 * It implements {@link RoleRepositoryCustom} and relies on the JPA Criteria API for building the dynamic filters.
 */
@Singleton
open class RoleRepositoryImpl(
    private val entityManager: EntityManager,
) : RoleRepositoryCustom {

    /**
     * Builds a search filter predicate for querying roles by name.
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
        root: Root<RoleEntity>,
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
            likePattern
        )
    }

    @ReadOnly
    override fun searchByName(
        query: String?,
        first: Int,
        after: Int?,
    ): List<RoleEntity> {
        val criteriaBuilder = entityManager.criteriaBuilder
        val criteriaQuery = criteriaBuilder.createQuery(RoleEntity::class.java)
        val root = criteriaQuery.from(RoleEntity::class.java)

        // Build WHERE clause predicates
        val predicates = mutableListOf<Predicate>()

        // Add search filter predicate if query is provided
        buildSearchFilterPredicate(root, criteriaBuilder, query)?.let {
            predicates.add(it)
        }

        // Add cursor pagination predicate if after is provided
        if (after != null) {
            // Since we sort by name ASC, id ASC, we need composite cursor logic
            // Fetch the last item to get its name for composite cursor
            val lastItem = entityManager.find(RoleEntity::class.java, after)
                ?: throw IllegalArgumentException("Invalid cursor: entity not found")
            
            val namePath = root.get<String>("name")
            val idPath = root.get<Int>("id")
            
            val lastItemName = lastItem.name
            val lastItemId = lastItem.id
                ?: throw IllegalStateException("Entity found but has null ID - data integrity issue")
            
            // Build composite cursor predicate:
            // (name > lastItemName) OR (name = lastItemName AND id > lastItemId)
            // This ensures we get all items that come after the cursor in the sort order
            val nameGreater = criteriaBuilder.greaterThan(
                namePath,
                criteriaBuilder.literal(lastItemName)
            )
            val nameEqual = criteriaBuilder.equal(
                namePath,
                criteriaBuilder.literal(lastItemName)
            )
            val idGreater = criteriaBuilder.greaterThan(idPath, lastItemId)
            val nameEqualAndIdGreater = criteriaBuilder.and(nameEqual, idGreater)
            
            predicates.add(criteriaBuilder.or(nameGreater, nameEqualAndIdGreater))
        }

        // Apply WHERE clause
        if (predicates.isNotEmpty()) {
            criteriaQuery.where(*predicates.toTypedArray())
        }

        // Build ordering: name ASC, id ASC
        val orders = listOf(
            criteriaBuilder.asc(root.get<String>("name")),
            criteriaBuilder.asc(root.get<Int>("id"))
        )
        criteriaQuery.orderBy(orders)

        // Execute query with limit
        val typedQuery: TypedQuery<RoleEntity> = entityManager.createQuery(criteriaQuery)
        typedQuery.maxResults = first

        return typedQuery.resultList
    }

    @ReadOnly
    override fun countByName(query: String?): Long {
        val criteriaBuilder = entityManager.criteriaBuilder
        val criteriaQuery = criteriaBuilder.createQuery(Long::class.java)
        val root = criteriaQuery.from(RoleEntity::class.java)

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

