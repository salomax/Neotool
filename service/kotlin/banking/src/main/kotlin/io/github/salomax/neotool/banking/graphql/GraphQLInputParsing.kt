package io.github.salomax.neotool.banking.graphql

import io.github.salomax.neotool.common.error.CommonErrorCode
import io.github.salomax.neotool.common.error.ValidationException

internal inline fun <reified T : Enum<T>> parseEnumValue(
    field: String,
    rawValue: String,
): T =
    enumValues<T>().firstOrNull { it.name == rawValue }
        ?: throw ValidationException(
            errorCode = CommonErrorCode.INVALID_INPUT,
            field = field,
            parameters = mapOf("value" to rawValue),
        )
