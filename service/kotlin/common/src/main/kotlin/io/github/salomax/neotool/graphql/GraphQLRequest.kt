package io.github.salomax.neotool.framework.graphql

import io.micronaut.serde.annotation.Serdeable
import io.micronaut.core.annotation.Introspected

@Serdeable
@Introspected
data class GraphQLRequest(
  val query: String,
  val variables: Map<String, Any?>? = null,
  val operationName: String? = null)
