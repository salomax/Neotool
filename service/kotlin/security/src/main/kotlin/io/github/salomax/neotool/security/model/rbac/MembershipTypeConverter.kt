package io.github.salomax.neotool.security.model.rbac

import io.github.salomax.neotool.security.domain.rbac.MembershipType
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

/**
 * JPA AttributeConverter for MembershipType enum.
 * Handles case-insensitive conversion between database strings (lowercase) and enum values (uppercase).
 * 
 * Database stores: "member", "admin", "owner" (lowercase)
 * Enum values: MEMBER, ADMIN, OWNER (uppercase)
 */
@Converter(autoApply = true)
class MembershipTypeConverter : AttributeConverter<MembershipType, String> {
    override fun convertToDatabaseColumn(attribute: MembershipType?): String? {
        return attribute?.name?.lowercase()
    }

    override fun convertToEntityAttribute(dbData: String?): MembershipType? {
        if (dbData == null) {
            return null
        }
        // Handle both lowercase (from database) and uppercase (if already converted)
        return try {
            MembershipType.valueOf(dbData.uppercase())
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException(
                "Invalid membership type: '$dbData'. Valid values are: ${MembershipType.values().joinToString { it.name.lowercase() }}",
                e,
            )
        }
    }
}
