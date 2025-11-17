package io.github.salomax.neotool.assistant.graphql

import io.micronaut.serde.annotation.Serdeable

@Serdeable
data class GraphQLRequest(
    val query: String,
    val variables: Map<String, Any?> = emptyMap(),
    val operationName: String? = null
)

@Serdeable
data class GraphQLResponse(
    val data: Map<*, *>?,
    val errors: List<Map<*, *>>? = null
)

