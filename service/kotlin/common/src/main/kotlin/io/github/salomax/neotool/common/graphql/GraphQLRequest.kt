package io.github.salomax.neotool.common.graphql

import io.micronaut.core.annotation.Introspected
import io.micronaut.serde.annotation.Serdeable

@Serdeable
@Introspected
data class GraphQLRequest(
    val query: String,
    val variables: Map<String, Any?>? = null,
    val operationName: String? = null,
)
