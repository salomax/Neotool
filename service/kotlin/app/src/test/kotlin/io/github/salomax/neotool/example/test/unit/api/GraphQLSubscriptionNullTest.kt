package io.github.salomax.neotool.example.test.unit.api

import io.micronaut.json.JsonMapper
import io.micronaut.json.tree.JsonNode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GraphQLSubscriptionNullTest {

    @Test
    fun `json read converts null to JsonNull correctly`() {
        // This simulates what happens in the integration test
        val jsonString = """{"data":null}"""
        val jsonMapper = JsonMapper.createDefault()
        val jsonNode = jsonMapper.readValue(jsonString, JsonNode::class.java)
        
        println("JSON String: $jsonString")
        println("JsonNode: $jsonNode")
        println("Data value: ${jsonNode["data"]}")
        println("Data type: ${jsonNode["data"]?.javaClass}")
        println("Is data null? ${jsonNode["data"] == null}")
        println("Is data JsonNull? ${jsonNode["data"]?.isNull}")
        
        val data = jsonNode["data"]
        
        // This is what the original test was checking (and failing)
        // assertThat(data).isNull() // This would fail because data is JsonNull, not null
        
        // This is our fix - check for JsonNull instead
        assertThat(data)
          .describedAs("Data should not be null (it's a JsonNull object)")
          .isNotNull()
        assertThat(data.isNull)
          .describedAs("Data should be JsonNull (representing null)")
          .isTrue()
    }
}
