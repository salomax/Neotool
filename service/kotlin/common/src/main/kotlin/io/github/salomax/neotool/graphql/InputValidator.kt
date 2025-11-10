package io.github.salomax.neotool.framework.graphql

import jakarta.inject.Singleton
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validator

/**
 * Wrapper for Jakarta Bean Validation that can be injected and reused.
 * 
 * This singleton provides a convenient way to validate GraphQL input DTOs
 * using Bean Validation annotations (e.g., `@NotBlank`, `@Email`, `@Size`).
 * 
 * **Usage:**
 * ```kotlin
 * @Singleton
 * class MyResolver(private val inputValidator: InputValidator) {
 *     fun createProduct(input: ProductInputDTO) {
 *         inputValidator.validate(input)  // Throws ConstraintViolationException if invalid
 *         // ... create product
 *     }
 * }
 * ```
 * 
 * **Note:** [GenericCrudResolver] automatically validates inputs using
 * Bean Validation, so you typically don't need to use this directly unless
 * you're writing custom resolvers.
 * 
 * **Validation Annotations:**
 * Use standard Jakarta Bean Validation annotations on your input DTOs:
 * ```kotlin
 * data class ProductInputDTO(
 *     @NotBlank(message = "Name is required")
 *     val name: String,
 *     
 *     @Min(0, message = "Price must be positive")
 *     val price: Int
 * )
 * ```
 */
@Singleton
class InputValidator(private val validator: Validator) {
    /**
     * Validates a bean using Bean Validation.
     * 
     * @param bean The object to validate
     * @throws ConstraintViolationException if validation fails
     */
    fun <T> validate(bean: T) {
        val violations = validator.validate(bean)
        if (violations.isNotEmpty()) throw ConstraintViolationException(violations)
    }
}
