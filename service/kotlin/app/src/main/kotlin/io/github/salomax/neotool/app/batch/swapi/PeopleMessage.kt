package io.github.salomax.neotool.app.batch.swapi

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import java.time.Instant

/**
 * Root message structure for SWAPI people data in Kafka.
 * Matches the schema defined in workflow/example/swapi/schemas/people_message.json
 */
@Serdeable
data class PeopleMessage(
    @JsonProperty("batch_id")
    val batchId: String,
    @JsonProperty("record_id")
    val recordId: String,
    // ISO 8601 format
    @JsonProperty("ingested_at")
    val ingestedAt: String,
    @JsonProperty("payload")
    val payload: PeoplePayload,
) {
    /**
     * Parse ingested_at as Instant for easier handling
     */
    fun getIngestedAtInstant(): Instant {
        return Instant.parse(ingestedAt)
    }
}

/**
 * Payload containing the actual person data from SWAPI.
 */
@Serdeable
data class PeoplePayload(
    val name: String,
    val height: String?,
    val mass: String?,
    @JsonProperty("hair_color")
    val hairColor: String?,
    @JsonProperty("skin_color")
    val skinColor: String?,
    @JsonProperty("eye_color")
    val eyeColor: String?,
    @JsonProperty("birth_year")
    val birthYear: String?,
    val gender: String?,
    @JsonProperty("homeworld_url")
    val homeworldUrl: String?,
    val films: List<String> = emptyList(),
    val species: List<String> = emptyList(),
    val vehicles: List<String> = emptyList(),
    val starships: List<String> = emptyList(),
)
