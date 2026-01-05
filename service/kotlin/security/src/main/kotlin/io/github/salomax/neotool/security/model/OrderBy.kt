package io.github.salomax.neotool.security.model

import io.github.salomax.neotool.common.graphql.pagination.OrderBySpec
import io.github.salomax.neotool.common.graphql.pagination.OrderDirection
import io.github.salomax.neotool.common.graphql.pagination.OrderFieldEnum

/**
 * Order by specification for user queries.
 */
data class UserOrderBy(
    override val field: UserOrderField,
    override val direction: OrderDirection,
) : OrderBySpec<UserOrderField>

/**
 * Order by field for user queries.
 * Note: ENABLED ordering was removed because enabled status is now stored in Principal table.
 */
enum class UserOrderField : OrderFieldEnum {
    DISPLAY_NAME,
    EMAIL,
    ID, // Always included as final sort for deterministic ordering
    ;

    override fun isIdField(): Boolean = this == ID
}

/**
 * Order by specification for group queries.
 */
data class GroupOrderBy(
    override val field: GroupOrderField,
    override val direction: OrderDirection,
) : OrderBySpec<GroupOrderField>

/**
 * Order by field for group queries.
 */
enum class GroupOrderField : OrderFieldEnum {
    NAME,
    ID, // Always included as final sort for deterministic ordering
    ;

    override fun isIdField(): Boolean = this == ID
}

/**
 * Order by specification for role queries.
 */
data class RoleOrderBy(
    override val field: RoleOrderField,
    override val direction: OrderDirection,
) : OrderBySpec<RoleOrderField>

/**
 * Order by field for role queries.
 */
enum class RoleOrderField : OrderFieldEnum {
    NAME,
    ID, // Always included as final sort for deterministic ordering
    ;

    override fun isIdField(): Boolean = this == ID
}
