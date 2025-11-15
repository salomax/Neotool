package io.github.salomax.neotool.entity

/**
 * Base entity class that provides common functionality for all entities.
 * Includes optimistic locking support and proper equals/hashCode implementation.
 */
abstract class BaseEntity<T>(
    open val id: T
) {
    // No-arg constructor for JPA
    constructor() : this(null as T)
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as BaseEntity<*>
        return id == other.id
    }

    override fun hashCode(): Int = id?.hashCode() ?: 0
}
