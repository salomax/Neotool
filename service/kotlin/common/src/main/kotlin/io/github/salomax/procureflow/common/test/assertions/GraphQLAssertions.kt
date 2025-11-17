package io.github.salomax.procureflow.common.test.assertions

import io.micronaut.json.tree.JsonNode
import org.assertj.core.api.Assertions.assertThat

/**
 * GraphQL-specific test assertions.
 * 
 * These utilities help test GraphQL responses by providing common assertion patterns
 * for GraphQL-specific structures like errors arrays and null handling.
 */

/**
 * Assert that GraphQL errors are absent.
 * 
 * GraphQL may return errors as:
 * - null (field omitted)
 * - JsonNull (field explicitly set to null)
 * - Empty array [] (field present but empty)
 * 
 * This function handles all these cases and provides detailed error messages
 * if errors are present.
 * 
 * @param errors The errors JsonNode from the GraphQL response
 * @param description Optional description for better error messages
 * @throws AssertionError if errors are present
 */
fun JsonNode?.assertNoErrors(description: String = "GraphQL errors must be absent") {
    if (this == null || this.isNull) {
        // Field is omitted or explicitly null - this is valid
        return
    }
    // If errors is present and not null, it must be an empty array
    assertThat(this.isArray)
        .describedAs("$description: errors should be an array if present, but was: ${this.javaClass.simpleName}")
        .isTrue()

    val errorCount = this.size()
    if (errorCount > 0) {
        // Print actual errors for debugging
        val errorMessages = (0 until errorCount).joinToString("\n") { i ->
            val error = this[i]
            val message = error["message"]?.stringValue ?: "No message"
            val path = error["path"]?.let { it.toString() } ?: "No path"
            "  Error $i: $message (path: $path)"
        }
        throw AssertionError(
            "$description: errors array should be empty, but has $errorCount error(s):\n$errorMessages"
        )
    }
}

