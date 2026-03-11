package io.github.salomax.neotool.assistant.test.unit.kafka

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.salomax.neotool.assistant.infra.kafka.BankingReaderOutputValidator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BankingReaderOutputValidatorTest {
    private val objectMapper = jacksonObjectMapper()
    private val validator = BankingReaderOutputValidator()

    @Test
    fun `validate accepts null statement period bounds`() {
        val output =
            objectMapper.readTree(
                """
                {
                  "bank_name": "Itau",
                  "statement_period": { "from": null, "to": null },
                  "cards": [],
                  "extraction_confidence": 0.9,
                  "needs_review": false,
                  "warnings": []
                }
                """.trimIndent(),
            )

        val errors = validator.validate(documentType = "BANK_STATEMENT", output = output)

        assertThat(errors).isEmpty()
    }

    @Test
    fun `validate accepts BR date format across statement card and transaction dates`() {
        val output =
            objectMapper.readTree(
                """
                {
                  "bank_name": "Nubank",
                  "statement_period": { "from": "01/03/2026", "to": "31/03/2026" },
                  "cards": [
                    {
                      "last_four_digits": "1234",
                      "closing_date": "25/03/2026",
                      "due_date": "05/04/2026",
                      "declared_total": 120.0,
                      "computed_total": 120.0,
                      "total_matches_declared": true,
                      "confidence": 0.9,
                      "transactions": [
                        {
                          "date": "15/03/2026",
                          "description": "PIX RECEBIDO",
                          "amount": 120.0,
                          "credit_debit_type": "CREDIT",
                          "confidence": 0.9
                        }
                      ]
                    }
                  ],
                  "extraction_confidence": 0.9,
                  "needs_review": false,
                  "warnings": []
                }
                """.trimIndent(),
            )

        val errors = validator.validate(documentType = "BANK_STATEMENT", output = output)

        assertThat(errors).isEmpty()
    }

    @Test
    fun `validate rejects invalid date values`() {
        val output =
            objectMapper.readTree(
                """
                {
                  "bank_name": "Inter",
                  "statement_period": { "from": "2026/03/01", "to": "2026-03-31" },
                  "cards": [],
                  "extraction_confidence": 0.8,
                  "needs_review": false,
                  "warnings": []
                }
                """.trimIndent(),
            )

        val errors = validator.validate(documentType = "BANK_STATEMENT", output = output)

        assertThat(errors).contains("statement_period.from must be a valid date (YYYY-MM-DD or DD/MM/YYYY)")
    }
}
