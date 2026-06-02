package io.github.salomax.neotool.banking.service.import.fingerprint

enum class FingerprintFileType {
    PDF,
    XLSX,
    XLS,
}

data class FingerprintClassificationInput(
    val fileType: FingerprintFileType,
    val documentType: String?,
    val extractedText: String?,
    val extractionMetadata: Map<String, Any?>,
)

data class FingerprintClassification(
    val status: String,
    val reason: String,
    val profileId: String? = null,
) {
    init {
        require(status == KNOWN_PROFILE || status == UNKNOWN_PROFILE) {
            "status must be '$KNOWN_PROFILE' or '$UNKNOWN_PROFILE'"
        }
        if (status == KNOWN_PROFILE) {
            require(!profileId.isNullOrBlank()) { "profileId is required for known_profile classification" }
        }
    }

    fun isKnownProfile(): Boolean = status == KNOWN_PROFILE

    companion object {
        const val KNOWN_PROFILE = "known_profile"
        const val UNKNOWN_PROFILE = "unknown_profile"

        fun known(
            profileId: String,
            reason: String,
        ): FingerprintClassification =
            FingerprintClassification(
                status = KNOWN_PROFILE,
                reason = reason,
                profileId = profileId,
            )

        fun unknown(reason: String): FingerprintClassification =
            FingerprintClassification(status = UNKNOWN_PROFILE, reason = reason, profileId = null)
    }
}

interface FingerprintClassifier {
    val supportedFileTypes: Set<FingerprintFileType>

    fun classify(input: FingerprintClassificationInput): FingerprintClassification
}
