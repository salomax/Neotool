package io.github.salomax.neotool.banking.domain.import

/**
 * Canonical parse output shared by deterministic and LLM reader paths.
 *
 * normalizedRecords intentionally uses generic maps to support heterogeneous
 * record shapes (transactions, statement summaries, card bill items, etc).
 */
data class CanonicalReaderOutput(
    val schemaId: String,
    val schemaVersion: String,
    val processorType: CanonicalProcessorType,
    val readerId: String,
    val confidence: Double,
    val warnings: List<String> = emptyList(),
    val normalizedRecords: List<Map<String, Any?>> = emptyList(),
    val rawMetadata: Map<String, Any?> = emptyMap(),
) {
    init {
        require(schemaId.isNotBlank()) { "schemaId must not be blank" }
        require(schemaVersion.isNotBlank()) { "schemaVersion must not be blank" }
        require(readerId.isNotBlank()) { "readerId must not be blank" }
        require(confidence in 0.0..1.0) { "confidence must be between 0.0 and 1.0" }
    }
}

enum class CanonicalProcessorType {
    DETERMINISTIC,
    AUTO,
    LLM,
}

