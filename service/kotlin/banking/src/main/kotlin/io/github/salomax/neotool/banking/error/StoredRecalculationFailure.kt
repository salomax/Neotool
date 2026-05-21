package io.github.salomax.neotool.banking.error

import io.github.salomax.neotool.common.error.CommonErrorCode
import io.github.salomax.neotool.common.error.ErrorCode

/**
 * Maps persisted portfolio recalculation failure fields to the shared [ErrorCode] catalog
 * (see docs/08-architecture/error-handling-system.md).
 */
object StoredRecalculationFailure {
    fun lookup(storedCode: String?): ErrorCode? {
        if (storedCode.isNullOrBlank()) return null
        BankingErrorCode.entries.find { it.code == storedCode }?.let { return it }
        CommonErrorCode.entries.find { it.code == storedCode }?.let { return it }
        return null
    }

    /**
     * Fallback user-facing string when the UI does not translate [storedCode] yet.
     * Prefer i18n via [storedCode] on the client; avoid surfacing raw [errorDetails] when a code exists.
     */
    fun displayMessage(
        storedCode: String?,
        errorDetails: String?,
    ): String? {
        val resolved = lookup(storedCode)
        if (resolved != null) return resolved.defaultMessage
        return errorDetails?.takeIf { it.isNotBlank() }
    }
}
