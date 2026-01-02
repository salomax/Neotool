package io.github.salomax.neotool.security.repo

import io.github.salomax.neotool.common.graphql.pagination.SortingConfig
import io.github.salomax.neotool.security.model.GroupOrderBy
import io.github.salomax.neotool.security.model.GroupOrderField
import io.github.salomax.neotool.security.model.RoleOrderBy
import io.github.salomax.neotool.security.model.RoleOrderField
import io.github.salomax.neotool.security.model.UserOrderBy
import io.github.salomax.neotool.security.model.UserOrderField
import java.util.UUID

/**
 * Sorting configurations for each entity type.
 * Provides field mapping, path building, and cursor value extraction for generic sorting helper.
 */
object SortingConfigs {
    /**
     * Configuration for User entity sorting.
     * Note: ENABLED ordering was removed because enabled status is now stored in Principal table.
     */
    val USER_CONFIG =
        SortingConfig<UserOrderField, UserOrderBy>(
            fieldToColumn = { field ->
                when (field) {
                    UserOrderField.DISPLAY_NAME -> "displayName"
                    UserOrderField.EMAIL -> "email"
                    UserOrderField.ID -> "id"
                }
            },
            buildFieldPath = { root, criteriaBuilder, field ->
                when (field) {
                    UserOrderField.DISPLAY_NAME -> {
                        // Handle COALESCE for displayName: use displayName if not null, else email
                        val displayNamePath = root.get<String?>("displayName")
                        val emailPath = root.get<String>("email")
                        criteriaBuilder.coalesce(displayNamePath, emailPath)
                    }

                    UserOrderField.EMAIL -> {
                        root.get<String>("email")
                    }

                    UserOrderField.ID -> {
                        root.get<UUID>("id")
                    }
                }
            },
            extractCursorValue = { cursor, field, fieldName ->
                when (field) {
                    UserOrderField.ID -> {
                        try {
                            UUID.fromString(cursor.id)
                        } catch (e: Exception) {
                            throw IllegalArgumentException("Invalid cursor ID format: ${cursor.id}", e)
                        }
                    }

                    UserOrderField.DISPLAY_NAME -> {
                        // If displayName is missing from cursor, use email as fallback (matching COALESCE logic)
                        val value = cursor.fieldValues[fieldName] ?: cursor.fieldValues["email"]
                        when (value) {
                            is String -> value
                            null -> null
                            else -> value.toString()
                        }
                    }

                    UserOrderField.EMAIL -> {
                        val value = cursor.fieldValues[fieldName]
                        when (value) {
                            is String -> value
                            null -> null
                            else -> value.toString()
                        }
                    }
                }
            },
            allowedColumns = setOf("displayName", "email", "id"),
        )

    /**
     * Configuration for Group entity sorting.
     */
    val GROUP_CONFIG =
        SortingConfig<GroupOrderField, GroupOrderBy>(
            fieldToColumn = { field ->
                when (field) {
                    GroupOrderField.NAME -> "name"
                    GroupOrderField.ID -> "id"
                }
            },
            buildFieldPath = { root, _, field ->
                when (field) {
                    GroupOrderField.NAME -> root.get<String>("name")
                    GroupOrderField.ID -> root.get<UUID>("id")
                }
            },
            extractCursorValue = { cursor, field, fieldName ->
                when (field) {
                    GroupOrderField.ID -> UUID.fromString(cursor.id)
                    else -> cursor.fieldValues[fieldName]
                }
            },
            allowedColumns = setOf("name", "id"),
        )

    /**
     * Configuration for Role entity sorting.
     */
    val ROLE_CONFIG =
        SortingConfig<RoleOrderField, RoleOrderBy>(
            fieldToColumn = { field ->
                when (field) {
                    RoleOrderField.NAME -> "name"
                    RoleOrderField.ID -> "id"
                }
            },
            buildFieldPath = { root, _, field ->
                when (field) {
                    RoleOrderField.NAME -> root.get<String>("name")
                    RoleOrderField.ID -> root.get<UUID>("id")
                }
            },
            extractCursorValue = { cursor, field, fieldName ->
                when (field) {
                    RoleOrderField.ID -> UUID.fromString(cursor.id)
                    else -> cursor.fieldValues[fieldName]
                }
            },
            allowedColumns = setOf("name", "id"),
        )
}
