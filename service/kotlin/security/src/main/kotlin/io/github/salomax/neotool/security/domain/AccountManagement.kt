package io.github.salomax.neotool.security.domain

import io.github.salomax.neotool.common.error.ValidationException
import io.github.salomax.neotool.security.error.SecurityErrorCode
import io.github.salomax.neotool.security.model.account.AccountType
import java.util.UUID

/**
 * Command DTOs for account management operations (FR-5).
 */
object AccountManagement {
    private const val MAX_ACCOUNT_NAME_LENGTH = 100

    /**
     * Command to create a new account (FAMILY or BUSINESS only).
     * PERSONAL accounts are auto-created on signup (FR-2).
     *
     * @throws ValidationException if validation fails with appropriate error code
     */
    data class CreateAccountCommand(
        val accountName: String,
        val accountType: AccountType,
        val ownerUserId: UUID,
    ) {
        init {
            // Validate account name
            if (accountName.isBlank()) {
                throw ValidationException(
                    errorCode = SecurityErrorCode.ACCOUNT_NAME_REQUIRED,
                    field = "accountName",
                )
            }
            if (accountName.length > MAX_ACCOUNT_NAME_LENGTH) {
                throw ValidationException(
                    errorCode = SecurityErrorCode.ACCOUNT_NAME_TOO_LONG,
                    field = "accountName",
                    parameters =
                        mapOf(
                            "maxLength" to MAX_ACCOUNT_NAME_LENGTH,
                            "actualLength" to accountName.length,
                        ),
                )
            }

            // Validate account type
            if (accountType != AccountType.FAMILY && accountType != AccountType.BUSINESS) {
                throw ValidationException(
                    errorCode = SecurityErrorCode.ACCOUNT_TYPE_MUST_BE_FAMILY_OR_BUSINESS,
                    field = "accountType",
                    parameters = mapOf("providedType" to accountType.name),
                )
            }
        }
    }

    /**
     * Command to update an existing account (owner only; type immutable).
     *
     * @throws ValidationException if validation fails with appropriate error code
     */
    data class UpdateAccountCommand(
        val accountId: UUID,
        val accountName: String,
        val actorUserId: UUID,
    ) {
        init {
            // Validate account name
            if (accountName.isBlank()) {
                throw ValidationException(
                    errorCode = SecurityErrorCode.ACCOUNT_NAME_REQUIRED,
                    field = "accountName",
                )
            }
            if (accountName.length > MAX_ACCOUNT_NAME_LENGTH) {
                throw ValidationException(
                    errorCode = SecurityErrorCode.ACCOUNT_NAME_TOO_LONG,
                    field = "accountName",
                    parameters =
                        mapOf(
                            "maxLength" to MAX_ACCOUNT_NAME_LENGTH,
                            "actualLength" to accountName.length,
                        ),
                )
            }
        }
    }

    /**
     * Command to delete an account (owner only; soft delete).
     * For PERSONAL: user must have at least one other ACTIVE account (FR-5.4).
     */
    data class DeleteAccountCommand(
        val accountId: UUID,
        val actorUserId: UUID,
    )
}
