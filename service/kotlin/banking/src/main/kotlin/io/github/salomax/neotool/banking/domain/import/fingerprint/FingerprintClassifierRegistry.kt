package io.github.salomax.neotool.banking.domain.import.fingerprint

import jakarta.inject.Singleton

@Singleton
open class FingerprintClassifierRegistry(
    private val classifiers: List<FingerprintClassifier>,
) {
    open fun classify(input: FingerprintClassificationInput): FingerprintClassification {
        val classifier =
            classifiers.firstOrNull { input.fileType in it.supportedFileTypes }
                ?: return FingerprintClassification.unknown("no_classifier_registered_for_${input.fileType.name.lowercase()}")
        return classifier.classify(input)
    }
}
