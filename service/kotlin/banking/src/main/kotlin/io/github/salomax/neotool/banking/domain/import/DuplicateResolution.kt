package io.github.salomax.neotool.banking.domain.import

/**
 * User resolution for a duplicate candidate (Level 2 cross-source).
 * - [KEEP_BOTH]: Insert the incoming row; keep existing transaction(s).
 * - [USE_INCOMING]: Soft-delete existing, then insert the incoming row.
 * - [KEEP_EXISTING]: Drop the incoming row; keep existing transaction(s).
 */
enum class DuplicateResolution {
    KEEP_BOTH,
    USE_INCOMING,
    KEEP_EXISTING,
}
