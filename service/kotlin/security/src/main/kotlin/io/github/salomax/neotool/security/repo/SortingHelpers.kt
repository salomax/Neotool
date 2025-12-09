package io.github.salomax.neotool.security.repo

import io.github.salomax.neotool.common.graphql.pagination.CompositeCursor
import io.github.salomax.neotool.common.graphql.pagination.GenericSortingHelper
import io.github.salomax.neotool.security.service.GroupOrderBy
import io.github.salomax.neotool.security.service.RoleOrderBy
import io.github.salomax.neotool.security.service.UserOrderBy
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.Order
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root

/**
 * Shared utilities for building dynamic ORDER BY clauses and cursor predicates.
 * Provides type-safe helpers for constructing JPA Criteria API queries with flexible sorting.
 *
 * This object delegates to GenericSortingHelper for actual implementation,
 * providing a convenient API for entity-specific sorting operations.
 */
object SortingHelpers {
    /**
     * Build ORDER BY clause for User entity from orderBy specifications.
     *
     * @param root The root entity path
     * @param criteriaBuilder The criteria builder
     * @param orderBy List of order by specifications
     * @return List of Order objects for ORDER BY clause
     */
    fun buildUserOrderBy(
        root: Root<*>,
        criteriaBuilder: CriteriaBuilder,
        orderBy: List<UserOrderBy>,
    ): List<Order> {
        return GenericSortingHelper.buildOrderBy(root, criteriaBuilder, orderBy, SortingConfigs.USER_CONFIG)
    }

    /**
     * Build ORDER BY clause for Group entity from orderBy specifications.
     *
     * @param root The root entity path
     * @param criteriaBuilder The criteria builder
     * @param orderBy List of order by specifications
     * @return List of Order objects for ORDER BY clause
     */
    fun buildGroupOrderBy(
        root: Root<*>,
        criteriaBuilder: CriteriaBuilder,
        orderBy: List<GroupOrderBy>,
    ): List<Order> {
        return GenericSortingHelper.buildOrderBy(root, criteriaBuilder, orderBy, SortingConfigs.GROUP_CONFIG)
    }

    /**
     * Build ORDER BY clause for Role entity from orderBy specifications.
     *
     * @param root The root entity path
     * @param criteriaBuilder The criteria builder
     * @param orderBy List of order by specifications
     * @return List of Order objects for ORDER BY clause
     */
    fun buildRoleOrderBy(
        root: Root<*>,
        criteriaBuilder: CriteriaBuilder,
        orderBy: List<RoleOrderBy>,
    ): List<Order> {
        return GenericSortingHelper.buildOrderBy(root, criteriaBuilder, orderBy, SortingConfigs.ROLE_CONFIG)
    }

    /**
     * Build cursor predicate for User entity based on composite cursor and orderBy.
     * Creates a composite comparison predicate that matches the sort order.
     *
     * @param root The root entity path
     * @param criteriaBuilder The criteria builder
     * @param cursor Composite cursor with field values and id
     * @param orderBy List of order by specifications (must match cursor structure)
     * @return Predicate for cursor-based pagination
     */
    fun buildUserCursorPredicate(
        root: Root<*>,
        criteriaBuilder: CriteriaBuilder,
        cursor: CompositeCursor,
        orderBy: List<UserOrderBy>,
    ): Predicate {
        return GenericSortingHelper.buildCursorPredicate(
            root,
            criteriaBuilder,
            cursor,
            orderBy,
            SortingConfigs.USER_CONFIG,
        )
    }

    /**
     * Build cursor predicate for Group entity based on composite cursor and orderBy.
     *
     * @param root The root entity path
     * @param criteriaBuilder The criteria builder
     * @param cursor Composite cursor with field values and id
     * @param orderBy List of order by specifications (must match cursor structure)
     * @return Predicate for cursor-based pagination
     */
    fun buildGroupCursorPredicate(
        root: Root<*>,
        criteriaBuilder: CriteriaBuilder,
        cursor: CompositeCursor,
        orderBy: List<GroupOrderBy>,
    ): Predicate {
        return GenericSortingHelper.buildCursorPredicate(
            root,
            criteriaBuilder,
            cursor,
            orderBy,
            SortingConfigs.GROUP_CONFIG,
        )
    }

    /**
     * Build cursor predicate for Role entity based on composite cursor and orderBy.
     *
     * @param root The root entity path
     * @param criteriaBuilder The criteria builder
     * @param cursor Composite cursor with field values and id
     * @param orderBy List of order by specifications (must match cursor structure)
     * @return Predicate for cursor-based pagination
     */
    fun buildRoleCursorPredicate(
        root: Root<*>,
        criteriaBuilder: CriteriaBuilder,
        cursor: CompositeCursor,
        orderBy: List<RoleOrderBy>,
    ): Predicate {
        return GenericSortingHelper.buildCursorPredicate(
            root,
            criteriaBuilder,
            cursor,
            orderBy,
            SortingConfigs.ROLE_CONFIG,
        )
    }
}
