package io.github.salomax.neotool.graphql

import io.micronaut.core.annotation.Introspected
import io.micronaut.serde.annotation.Serdeable
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Size

/**
 * Base interface for all GraphQL input DTOs
 */
interface GraphQLInputDTO {
    fun validate(): Unit
}

/**
 * Abstract base class for input DTOs with common validation patterns
 */
@Introspected
@Serdeable
abstract class BaseInputDTO : GraphQLInputDTO {
    
    override fun validate() {
        // Default implementation - can be overridden for custom validation
    }
}

/**
 * Standard patterns for common input fields
 */
object InputPatterns {
    
    @JvmStatic
    fun email(): String = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    
    @JvmStatic
    fun uuid(): String = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
    
    @JvmStatic
    fun alphanumeric(): String = "^[a-zA-Z0-9]+$"
    
    @JvmStatic
    fun statusEnum(vararg values: String): String = values.joinToString("|")
}

/**
 * Common validation annotations for GraphQL inputs
 */
object GraphQLValidations {
    
    @JvmStatic
    fun notBlank(message: String = "Field must not be blank") = NotBlank(message = message)
    
    @JvmStatic
    fun email(message: String = "Must be a valid email address") = Email(message = message)
    
    @JvmStatic
    fun pattern(regex: String, message: String) = Pattern(regexp = regex, message = message)
    
    @JvmStatic
    fun min(value: Long, message: String = "Value must be >= $value") = Min(value = value, message = message)
    
    @JvmStatic
    fun max(value: Long, message: String = "Value must be <= $value") = Max(value = value, message = message)
    
    @JvmStatic
    fun size(min: Int, max: Int, message: String = "Size must be between $min and $max") = Size(min = min, max = max, message = message)
}
