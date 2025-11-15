package io.github.salomax.neotool.test.json

import io.micronaut.core.type.Argument
import io.micronaut.http.HttpResponse
import io.micronaut.json.JsonMapper
import io.micronaut.json.tree.JsonNode

inline fun <reified T : Any> JsonMapper.read(response: HttpResponse<*>): T {
  val body = response.body.orElseThrow { IllegalStateException("Empty body") }
  val bytes = when (body) {
    is String -> body.toByteArray()
    is ByteArray -> body
    else -> writeValueAsBytes(body)
  }
  return if (T::class == JsonNode::class) {
    val any = readValue(bytes, Argument.of(Any::class.java))
    @Suppress("UNCHECKED_CAST")
    writeValueToTree(any) as T
  } else {
    readValue(bytes, Argument.of(T::class.java))
  }
}

