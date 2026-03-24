package io.github.salomax.neotool.banking.domain.billimport

import io.github.salomax.neotool.banking.service.billimport.*
import io.github.salomax.neotool.banking.domain.billimport.AssistantBillExtractionMeta
import io.github.salomax.neotool.banking.service.billimport.AssistantBillTransactionExtractor
import io.github.salomax.neotool.banking.domain.billimport.AssistantBillCardBlock
import io.github.salomax.neotool.banking.domain.billimport.AssistantBillTransactionPattern
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AssistantBillTransactionExtractorTest {
    private val extractor = AssistantBillTransactionExtractor()

    // --- Sequential anchor_start (end = next card's anchor_start) ---

    @Test
    fun `two cards - end of first block is start of second block`() {
        val rawText =
            """
            Cartão Final 1234
            05/02 SHOPEE R${'$'} 30,09
            21/01 NETFLIX.COM R${'$'} 55,90
            Cartão Final 5678
            10/02 AMAZON PRIME R${'$'} 19,90
            Rodapé legal
            """.trimIndent()

        val meta =
            tableCardMeta(
                closingDate = "2026-02-28",
                cardBlocks = listOf(
                    AssistantBillCardBlock(lastFour = "1234", declaredTotal = 85.99, anchorStart = "Cartão Final 1234"),
                    AssistantBillCardBlock(lastFour = "5678", declaredTotal = 19.90, anchorStart = "Cartão Final 5678"),
                ),
            )

        val result = extractor.extract(rawText, meta)

        assertThat(result.cards).hasSize(2)

        val card1234 = result.cards.find { it.lastFour == "1234" }!!
        assertThat(card1234.transactions).hasSize(2)
        assertThat(card1234.transactions[0].description).isEqualTo("SHOPEE")
        assertThat(card1234.transactions[1].description).isEqualTo("NETFLIX.COM")

        val card5678 = result.cards.find { it.lastFour == "5678" }!!
        assertThat(card5678.transactions).hasSize(1)
        assertThat(card5678.transactions[0].description).isEqualTo("AMAZON PRIME")
        assertThat(card5678.computedTotal).isEqualTo(19.90)
        assertThat(card5678.totalMatchesDeclared).isTrue()
    }

    @Test
    fun `last card block extends to end of document`() {
        val rawText =
            """
            Cartão Final 9999
            10/02 SPOTIFY R${'$'} 21,90
            Rodapé com texto longo sem relação com transações
            """.trimIndent()

        val meta =
            tableCardMeta(
                closingDate = "2026-02-28",
                cardBlocks = listOf(
                    AssistantBillCardBlock(lastFour = "9999", declaredTotal = null, anchorStart = "Cartão Final 9999"),
                ),
            )

        val result = extractor.extract(rawText, meta)

        assertThat(result.cards).hasSize(1)
        assertThat(result.cards[0].transactions).hasSize(1)
        assertThat(result.cards[0].transactions[0].description).isEqualTo("SPOTIFY")
    }

    @Test
    fun `missing anchor_start adds warning and returns empty card`() {
        val meta =
            tableCardMeta(
                closingDate = "2026-02-28",
                cardBlocks = listOf(
                    AssistantBillCardBlock(lastFour = "1234", declaredTotal = null, anchorStart = "THIS ANCHOR DOES NOT EXIST"),
                ),
            )

        val result = extractor.extract("some text without anchor", meta)

        assertThat(result.warnings).anyMatch { it.contains("anchor_start") && it.contains("not found") }
        assertThat(result.cards[0].transactions).isEmpty()
    }

    // --- Installments ---

    @Test
    fun `installment is extracted and description is cleaned of empty parens`() {
        val rawText =
            """
            Cartão Final 1234
            05/11 Abc Motors (4/7) R${'$'} 227,00
            Subtotal
            """.trimIndent()

        val meta =
            tableCardMeta(
                closingDate = "2026-02-01",
                cardBlocks = listOf(
                    AssistantBillCardBlock(lastFour = "1234", declaredTotal = null, anchorStart = "Cartão Final 1234"),
                ),
                installmentPattern = """(\d{1,2})/(\d{1,2})""",
            )

        val result = extractor.extract(rawText, meta)

        val tx = result.cards[0].transactions[0]
        assertThat(tx.installment!!.current).isEqualTo(4)
        assertThat(tx.installment!!.total).isEqualTo(7)
        assertThat(tx.description).isEqualTo("Abc Motors") // "(4/7)" removed, "()" cleaned
    }

    // --- Inline card (last_four_pattern) ---

    @Test
    fun `inline card pattern groups transactions by extracted card number`() {
        val rawText =
            """
            Compras no Crédito
            08 FEV •••• 5048 Godaddy R${'$'} 64,99
            18 FEV •••• 1639 Hdi Seguros R${'$'} 236,66
            15 FEV •••• 5048 Spotify R${'$'} 21,90
            """.trimIndent()

        val meta =
            AssistantBillExtractionMeta(
                bankName = "Nubank",
                closingDate = "2026-02-28",
                dueDate = "2026-03-15",
                declaredTotal = null,
                cardBlocks = listOf(
                    AssistantBillCardBlock(lastFour = null, declaredTotal = null, anchorStart = "Compras no Crédito"),
                ),
                transactionPattern = AssistantBillTransactionPattern(
                    dateFormat = "DD_MMM",
                    lastFourPattern = """[^\w\s]+\s*(\d{4})""",
                    currencySymbols = listOf("R$"),
                    decimalSeparator = "comma",
                    installmentPattern = null,
                ),
            )

        val result = extractor.extract(rawText, meta)

        assertThat(result.cards).hasSize(2)
        val card5048 = result.cards.find { it.lastFour == "5048" }!!
        assertThat(card5048.transactions).hasSize(2)
        assertThat(card5048.transactions.map { it.description }).containsExactly("Godaddy", "Spotify")

        val card1639 = result.cards.find { it.lastFour == "1639" }!!
        assertThat(card1639.transactions).hasSize(1)
        assertThat(card1639.transactions[0].description).isEqualTo("Hdi Seguros")
    }

    @Test
    fun `inline card pattern with empty card blocks scans whole document and preserves rows without card`() {
        val rawText =
            """
            15 SET •••• 4416 Dm *Helphbomaxcom R${'$'} 19,95
            18 SET •••• 1639 Hdi Seguros Sa 04/12 R${'$'} 236,66
            30 SET IOF de "Cursor, Ai Powered Ide" R${'$'} 3,88
            30 SET •••• 5954 Cursor, Ai Powered Ide R${'$'} 110,72
            03 OUT •••• 1769 Netflix.Com R${'$'} 44,90
            08 SET Pagamento em 08 SET −R${'$'} 418,33
            15 SET Saldo restante da fatura anterior R${'$'} 0,00
            """.trimIndent()

        val meta =
            AssistantBillExtractionMeta(
                bankName = "Nubank",
                closingDate = "2025-10-07",
                dueDate = "2025-10-14",
                declaredTotal = 416.11,
                cardBlocks = emptyList(),
                transactionPattern = AssistantBillTransactionPattern(
                    dateFormat = "DD_MMM",
                    lastFourPattern = """\*{4}(\d{4})""",
                    currencySymbols = listOf("R$"),
                    decimalSeparator = "comma",
                    installmentPattern = """(\d{1,2})/(\d{1,2})""",
                ),
            )

        val result = extractor.extract(rawText, meta)

        assertThat(result.cards).hasSize(5)
        assertThat(result.cards.sumOf { it.transactions.size }).isEqualTo(6)

        val unresolvedBucket = result.cards.first { it.lastFour.isBlank() }
        assertThat(unresolvedBucket.transactions).hasSize(2)
        assertThat(unresolvedBucket.transactions.map { it.description })
            .containsExactly("IOF de \"Cursor, Ai Powered Ide\"", "Pagamento em 08 SET")
        assertThat(unresolvedBucket.transactions.last().isBillPayment).isTrue()
        assertThat(unresolvedBucket.transactions.last().creditDebitType).isEqualTo("CREDIT")

        val card1639 = result.cards.first { it.lastFour == "1639" }
        assertThat(card1639.transactions.single().installment?.current).isEqualTo(4)
        assertThat(card1639.transactions.single().installment?.total).isEqualTo(12)
    }

    @Test
    fun `block card extraction parses rows without currency symbol and optional leading row index`() {
        val rawText =
            """
            MARCIO ANTUNES DA ROCHA - 5428 XXXX XXXX 2945
            Pagamento e Demais Créditos
            Compra Data Descrição Parcela R$ US$
            21/01 PAGAMENTO DE FATURA-INTERNET -99,10
            12/02 DESCONTO DO MES -1,22
            Despesas
            Compra Data Descrição Parcela R$ US$
            1 24/01 HORTIFRUTI UNIAO 75,77
            1 01/02 ASSAI ATACADISTA LJ254 22,80
            VALOR TOTAL 98,57 0,00
            """.trimIndent()

        val meta =
            tableCardMeta(
                closingDate = "2026-02-12",
                cardBlocks = listOf(
                    AssistantBillCardBlock(
                        lastFour = "2945",
                        declaredTotal = 151.25,
                        anchorStart = "MARCIO ANTUNES DA ROCHA - 5428 XXXX XXXX 2945",
                    ),
                ),
            )

        val result = extractor.extract(rawText, meta)

        assertThat(result.cards).hasSize(1)
        val card = result.cards.single()
        assertThat(card.transactions).hasSize(4)
        assertThat(card.transactions.map { it.description })
            .containsExactly(
                "PAGAMENTO DE FATURA-INTERNET",
                "DESCONTO DO MES",
                "HORTIFRUTI UNIAO",
                "ASSAI ATACADISTA LJ254",
            )
        assertThat(card.transactions.first().isBillPayment).isTrue()
        assertThat(card.transactions.first().creditDebitType).isEqualTo("CREDIT")
        assertThat(card.transactions[1].creditDebitType).isEqualTo("CREDIT")
        assertThat(card.computedTotal).isEqualTo(97.35)
        assertThat(card.totalMatchesDeclared).isFalse()
    }

    @Test
    fun `duplicate parsed transaction lines are deduplicated after parsing`() {
        val rawText =
            """
            03 OUT ****1769 Netflix.Com R${'$'} 44,90
            03 OUT ****1769 Netflix.Com R${'$'} 44,90
            """.trimIndent()

        val meta =
            AssistantBillExtractionMeta(
                bankName = "Nubank",
                closingDate = "2025-10-07",
                dueDate = "2025-10-14",
                declaredTotal = null,
                cardBlocks = emptyList(),
                transactionPattern = AssistantBillTransactionPattern(
                    dateFormat = "DD_MMM",
                    lastFourPattern = """\*{4}(\d{4})""",
                    currencySymbols = listOf("R$"),
                    decimalSeparator = "comma",
                    installmentPattern = null,
                ),
            )

        val result = extractor.extract(rawText, meta)

        assertThat(result.cards).hasSize(1)
        assertThat(result.cards[0].transactions).hasSize(1)
        assertThat(result.cards[0].transactions[0].description).isEqualTo("Netflix.Com")
    }

    // --- Currency code ---

    @Test
    fun `BRL transaction has currency_code BRL`() {
        val rawText = """
            Cartão Final 1234
            10/02 SPOTIFY R${'$'} 21,90
        """.trimIndent()
        val tx = extractor.extract(rawText, simpleTableMeta("2026-02-28")).cards[0].transactions[0]
        assertThat(tx.currencyCode).isEqualTo("BRL")
    }

    @Test
    fun `USD transaction has currency_code USD`() {
        val rawText = """
            Cartão Final 1234
            15/01 NETFLIX.COM US${'$'} 15,49
        """.trimIndent()
        val meta = tableCardMeta(
            closingDate = "2026-02-28",
            cardBlocks = listOf(AssistantBillCardBlock(lastFour = "1234", declaredTotal = null, anchorStart = "Cartão Final 1234")),
        ).let { it.copy(transactionPattern = it.transactionPattern.copy(currencySymbols = listOf("R$", "US$"))) }
        val tx = extractor.extract(rawText, meta).cards[0].transactions[0]
        assertThat(tx.currencyCode).isEqualTo("USD")
        assertThat(tx.amount).isEqualTo(15.49)
    }

    // --- Credit / bill payment ---

    @Test
    fun `negative amount is parsed as CREDIT`() {
        val rawText =
            """
            Cartão Final 1234
            10/02 ESTORNO COMPRA -R${'$'} 50,00
            """.trimIndent()

        val result = extractor.extract(rawText, simpleTableMeta("2026-02-28"))

        assertThat(result.cards[0].transactions[0].creditDebitType).isEqualTo("CREDIT")
        assertThat(result.cards[0].transactions[0].amount).isEqualTo(50.0)
    }

    @Test
    fun `bill payment description is detected and typed as CREDIT`() {
        val rawText =
            """
            Cartão Final 1234
            07/02 PAGAMENTO DE FATURA R${'$'} 236,66
            """.trimIndent()

        val result = extractor.extract(rawText, simpleTableMeta("2026-02-28"))

        val tx = result.cards[0].transactions[0]
        assertThat(tx.isBillPayment).isTrue()
        assertThat(tx.creditDebitType).isEqualTo("CREDIT")
    }

    @Test
    fun `Banco do Brasil PGTO DEBITO CONTA is detected as bill payment`() {
        val rawText =
            """
            Cartão Final 1234
            07/02 PGTO DEBITO CONTA R${'$'} 236,66
            """.trimIndent()

        val result = extractor.extract(rawText, simpleTableMeta("2026-02-28"))

        val tx = result.cards[0].transactions[0]
        assertThat(tx.isBillPayment).isTrue()
        assertThat(tx.creditDebitType).isEqualTo("CREDIT")
    }

    // --- Multi transaction per line ---

    @Test
    fun `DD_MMM format extracts two transactions from same line`() {
        val rawText =
            """
            Cartão Final 1234
            21 Out Mercado Livre (5/10) R${'$'} 49,40 28 Jan Great Assessoria Em Id R${'$'} 606,00
            Cartão Final 5678
            """.trimIndent()

        val meta =
            AssistantBillExtractionMeta(
                bankName = "BTG",
                closingDate = "2026-02-01",
                dueDate = "2026-02-15",
                declaredTotal = null,
                cardBlocks = listOf(
                    AssistantBillCardBlock(lastFour = "1234", declaredTotal = null, anchorStart = "Cartão Final 1234"),
                    AssistantBillCardBlock(lastFour = "5678", declaredTotal = null, anchorStart = "Cartão Final 5678"),
                ),
                transactionPattern = AssistantBillTransactionPattern(
                    dateFormat = "DD_MMM",
                    lastFourPattern = null,
                    currencySymbols = listOf("R$"),
                    decimalSeparator = "comma",
                    installmentPattern = """(\d{1,2})/(\d{1,2})""",
                ),
            )

        val result = extractor.extract(rawText, meta)

        assertThat(result.cards[0].transactions).hasSize(2)

        val tx1 = result.cards[0].transactions[0]
        assertThat(tx1.description).isEqualTo("Mercado Livre") // "(5/10)" and "()" cleaned
        assertThat(tx1.amount).isEqualTo(49.40)
        assertThat(tx1.installment?.current).isEqualTo(5)
        assertThat(tx1.installment?.total).isEqualTo(10)

        val tx2 = result.cards[0].transactions[1]
        assertThat(tx2.description).contains("Great Assessoria")
        assertThat(tx2.amount).isEqualTo(606.00)
    }

    // --- Helpers ---

    private fun tableCardMeta(
        closingDate: String,
        cardBlocks: List<AssistantBillCardBlock>,
        installmentPattern: String? = null,
    ): AssistantBillExtractionMeta =
        AssistantBillExtractionMeta(
            bankName = "Itaú",
            closingDate = closingDate,
            dueDate = "2026-03-15",
            declaredTotal = null,
            cardBlocks = cardBlocks,
            transactionPattern = AssistantBillTransactionPattern(
                dateFormat = "DD_MM",
                lastFourPattern = null,
                currencySymbols = listOf("R$"),
                decimalSeparator = "comma",
                installmentPattern = installmentPattern,
            ),
        )

    private fun simpleTableMeta(closingDate: String): AssistantBillExtractionMeta =
        tableCardMeta(
            closingDate = closingDate,
            cardBlocks = listOf(
                AssistantBillCardBlock(lastFour = "1234", declaredTotal = null, anchorStart = "Cartão Final 1234"),
            ),
        )
}
