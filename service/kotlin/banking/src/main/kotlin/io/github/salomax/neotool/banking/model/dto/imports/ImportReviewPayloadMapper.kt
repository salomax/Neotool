package io.github.salomax.neotool.banking.model.dto.imports

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode

data class ImportReviewPayload(
    @field:JsonProperty("schema_id")
    val schemaId: String = ImportReviewPayloadMapper.SCHEMA_ID,
    @field:JsonProperty("schema_version")
    val schemaVersion: String = ImportReviewPayloadMapper.SCHEMA_VERSION,
    @field:JsonProperty("duplicate_candidates")
    val duplicateCandidates: List<ImportDuplicateCandidate> = emptyList(),
    val gate: Map<String, Any?>? = null,
)

object ImportReviewPayloadMapper {
    const val SCHEMA_ID = "banking.import.review-payload"
    const val SCHEMA_VERSION = "1.0.0"

    private val duplicateCandidatesTypeRef = object : TypeReference<List<ImportDuplicateCandidate>>() {}
    private val mapTypeRef = object : TypeReference<Map<String, Any?>>() {}

    fun toJson(
        objectMapper: ObjectMapper,
        duplicateCandidates: List<ImportDuplicateCandidate> = emptyList(),
        gate: Map<String, Any?>? = null,
    ): String? {
        if (duplicateCandidates.isEmpty() && gate == null) return null
        return objectMapper.writeValueAsString(
            ImportReviewPayload(
                duplicateCandidates = duplicateCandidates,
                gate = gate,
            ),
        )
    }

    fun fromJson(
        objectMapper: ObjectMapper,
        reviewPayload: String?,
    ): ImportReviewPayload? {
        if (reviewPayload.isNullOrBlank()) return null
        return runCatching {
            val root = objectMapper.readTree(reviewPayload)
            when {
                root == null -> null
                root.isArray ->
                    ImportReviewPayload(
                        duplicateCandidates = objectMapper.convertValue(root, duplicateCandidatesTypeRef),
                    )
                root.isObject -> parseObjectPayload(objectMapper, root as ObjectNode)
                else -> null
            }
        }.onFailure { e ->
            org.slf4j.LoggerFactory.getLogger(ImportReviewPayloadMapper::class.java)
                .error("Failed to deserialize review_payload", e)
        }.getOrNull()
    }

    fun duplicateCandidates(
        objectMapper: ObjectMapper,
        reviewPayload: String?,
    ): List<ImportDuplicateCandidate> = fromJson(objectMapper, reviewPayload)?.duplicateCandidates ?: emptyList()

    private fun parseObjectPayload(
        objectMapper: ObjectMapper,
        root: ObjectNode,
    ): ImportReviewPayload {
        val schemaId = root["schema_id"]?.asText()?.takeIf { it.isNotBlank() } ?: SCHEMA_ID
        val schemaVersion = root["schema_version"]?.asText()?.takeIf { it.isNotBlank() } ?: SCHEMA_VERSION
        val duplicateCandidates =
            when {
                root.has("duplicate_candidates") -> parseDuplicateCandidates(objectMapper, root["duplicate_candidates"])
                root.has("duplicateCandidates") -> parseDuplicateCandidates(objectMapper, root["duplicateCandidates"])
                else -> emptyList()
            }
        val gate =
            root["gate"]?.takeIf { !it.isNull }?.let { node ->
                objectMapper.convertValue(node, mapTypeRef)
            }
        return ImportReviewPayload(
            schemaId = schemaId,
            schemaVersion = schemaVersion,
            duplicateCandidates = duplicateCandidates,
            gate = gate,
        )
    }

    private fun parseDuplicateCandidates(
        objectMapper: ObjectMapper,
        node: com.fasterxml.jackson.databind.JsonNode?,
    ): List<ImportDuplicateCandidate> {
        if (node == null || !node.isArray) return emptyList()
        return runCatching {
            objectMapper.convertValue(node, duplicateCandidatesTypeRef)
        }.onFailure { e ->
            org.slf4j.LoggerFactory.getLogger(ImportReviewPayloadMapper::class.java)
                .error("Failed to deserialize duplicate_candidates", e)
        }.getOrDefault(emptyList())
    }
}
