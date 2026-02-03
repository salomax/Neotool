package io.github.salomax.neotool.comms.domain.rbac

/**
 * Typed constants for comms permissions.
 * Use these constants instead of string literals to avoid typos and ensure consistency.
 */
object CommsPermissions {
    // Email send permissions
    const val COMMS_EMAIL_SEND = "comms:email:send"

    // Template permissions
    const val COMMS_TEMPLATE_VIEW = "comms:template:view"
}
