package io.github.salomax.neotool.mcp.protocol

import io.micronaut.serde.annotation.Serdeable

@Serdeable
data class JsonRpcResponse(
    val jsonrpc: String = "2.0",
    val id: Any?,
    val result: Any? = null,
    val error: JsonRpcError? = null,
)

@Serdeable
data class JsonRpcError(
    val code: Int,
    val message: String,
    val data: Map<String, Any?>? = null,
)

object JsonRpcErrorCodes {
    const val PARSE_ERROR = -32700
    const val INVALID_REQUEST = -32600
    const val METHOD_NOT_FOUND = -32601
    const val INVALID_PARAMS = -32602
    const val INTERNAL_ERROR = -32603
}
