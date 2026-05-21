package io.github.salomax.neotool.banking.service.import.parser

import io.github.salomax.neotool.banking.domain.import.ParsedTransaction
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.model.dto.imports.OfxParseResult
import org.w3c.dom.Document
import org.w3c.dom.Node
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Parses OFX 1.x (SGML) and OFX 2.x (XML) file bytes into [ParsedTransaction] list.
 * Uses a lightweight implementation (no ofx4j); OFX SGML is not valid XML.
 */
object OFXParser {
    private val SGML_HEADER = "OFXHEADER:"
    private val XML_OFX_PREFIX = "<?OFX"
    private val XML_DECL_PREFIX = "<?xml"

    private val STMTTRN_START_SGML = "<STMTTRN>"
    private val TAG_VALUE = Pattern.compile("<([A-Za-z0-9]+)>([^<]*)")

    private val DTPOSTED_FORMAT_DATE = DateTimeFormatter.ofPattern("yyyyMMdd")

    /**
     * Parses OFX file bytes. Detects SGML vs XML via content.
     * @return List of parsed transactions (empty if none or parse error)
     */
    fun parse(bytes: ByteArray): List<ParsedTransaction> = parseWithIntent(bytes).rows

    /**
     * Parses OFX file bytes and classifies high-level statement intent from message-set nodes.
     */
    fun parseWithIntent(bytes: ByteArray): OfxParseResult {
        val text = String(bytes, StandardCharsets.UTF_8)
        val intent = classifyStatementIntent(text)
        return when {
            text.trimStart().startsWith(SGML_HEADER, ignoreCase = true) ->
                parseSgml(text).let { parsed ->
                    OfxParseResult(
                        rows = parsed.rows,
                        statementIntent = intent,
                        format = OfxDocumentFormat.SGML,
                        accountIdentifier = parsed.accountIdentifier,
                        statementFrom = parsed.statementFrom,
                        statementTo = parsed.statementTo,
                    )
                }
            text.trimStart().startsWith(XML_OFX_PREFIX, ignoreCase = true) ||
                text.trimStart().startsWith(XML_DECL_PREFIX, ignoreCase = true) ->
                parseXml(bytes).let { parsed ->
                    OfxParseResult(
                        rows = parsed.rows,
                        statementIntent = intent,
                        format = OfxDocumentFormat.XML,
                        accountIdentifier = parsed.accountIdentifier,
                        statementFrom = parsed.statementFrom,
                        statementTo = parsed.statementTo,
                    )
                }
            else ->
                OfxParseResult(
                    rows = emptyList(),
                    statementIntent = OfxStatementIntent.UNKNOWN,
                    format = OfxDocumentFormat.UNKNOWN,
                )
        }
    }

    private fun classifyStatementIntent(text: String): OfxStatementIntent {
        val normalized = text.uppercase()
        val hasBankStatements = normalized.contains("<BANKMSGSRSV1")
        val hasCreditCardStatements = normalized.contains("<CREDITCARDMSGSRSV1")
        return when {
            hasBankStatements && !hasCreditCardStatements -> OfxStatementIntent.BANK_STATEMENT
            hasCreditCardStatements && !hasBankStatements -> OfxStatementIntent.CREDIT_CARD_STATEMENT
            else -> OfxStatementIntent.UNKNOWN
        }
    }

    private fun parseSgml(text: String): ParsedOfxDocument {
        val blocks = splitSgmlStmtTrnBlocks(text)
        return ParsedOfxDocument(
            rows = blocks.mapNotNull { block -> parseSgmlBlock(block) },
            accountIdentifier = extractSgmlTagValue(text, "ACCTID"),
            statementFrom = extractSgmlTagValue(text, "DTSTART")?.let(::parseOfxDateOnly),
            statementTo = extractSgmlTagValue(text, "DTEND")?.let(::parseOfxDateOnly),
        )
    }

    private fun splitSgmlStmtTrnBlocks(text: String): List<String> {
        val result = mutableListOf<String>()
        var start = 0
        while (true) {
            val begin = text.indexOf(STMTTRN_START_SGML, start, ignoreCase = true)
            if (begin == -1) break
            val nextBegin = text.indexOf(STMTTRN_START_SGML, begin + STMTTRN_START_SGML.length, ignoreCase = true)
            val end = if (nextBegin == -1) text.length else nextBegin
            result.add(text.substring(begin, end))
            start = end
        }
        return result
    }

    private fun parseSgmlBlock(block: String): ParsedTransaction? {
        val map = mutableMapOf<String, String>()
        val matcher = TAG_VALUE.matcher(block)
        while (matcher.find()) {
            val tag = matcher.group(1)!!
            val value = matcher.group(2)!!.trim()
            if (value.isNotEmpty()) map[tag.uppercase()] = value
        }
        return mapToParsedTransaction(map)
    }

    private fun parseXml(bytes: ByteArray): ParsedOfxDocument {
        return try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = false
            hardenXmlFactory(factory)
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(bytes.inputStream())
            ParsedOfxDocument(
                rows = extractStmtTrnFromDocument(doc),
                accountIdentifier =
                    doc.firstTagText(
                        "CCACCTFROM",
                        "ACCTID",
                    ) ?: doc.firstTagText("BANKACCTFROM", "ACCTID"),
                statementFrom = doc.firstTagText("BANKTRANLIST", "DTSTART")?.let(::parseOfxDateOnly),
                statementTo = doc.firstTagText("BANKTRANLIST", "DTEND")?.let(::parseOfxDateOnly),
            )
        } catch (e: Exception) {
            ParsedOfxDocument()
        }
    }

    /**
     * Hardens XML parser settings to prevent XXE and external entity resolution.
     */
    private fun hardenXmlFactory(factory: DocumentBuilderFactory) {
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        runCatching { factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        runCatching { factory.setXIncludeAware(false) }
        runCatching { factory.setExpandEntityReferences(false) }
    }

    private fun extractStmtTrnFromDocument(doc: Document): List<ParsedTransaction> {
        val result = mutableListOf<ParsedTransaction>()
        val stmttrns = doc.getElementsByTagName("STMTTRN")
        for (i in 0 until stmttrns.length) {
            val node = stmttrns.item(i)
            val map = nodeToMap(node)
            mapToParsedTransaction(map)?.let { result.add(it) }
        }
        return result
    }

    private fun nodeToMap(node: Node): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val children = node.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                val tag = child.nodeName.uppercase()
                val value = child.textContent?.trim() ?: ""
                if (value.isNotEmpty()) map[tag] = value
            }
        }
        return map
    }

    private fun extractSgmlTagValue(
        text: String,
        tag: String,
    ): String? =
        Regex(
            "(?is)<$tag>\\s*([^<\\r\\n]+)",
        ).find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }

    private fun mapToParsedTransaction(map: Map<String, String>): ParsedTransaction? {
        val dtPosted = map["DTPOSTED"] ?: return null
        val trnAmtStr = map["TRNAMT"] ?: return null
        val amount = runCatching { BigDecimal(trnAmtStr.trim().replace(",", ".")) }.getOrNull() ?: return null

        val (date, transactionDatetime) = parseDtPosted(dtPosted)
        val creditDebitType = if (amount.signum() >= 0) CreditDebitType.CREDIT else CreditDebitType.DEBIT
        val amountAbs = amount.abs()

        val fitId = map["FITID"]?.takeIf { it.isNotBlank() }
        val name = map["NAME"]?.takeIf { it.isNotBlank() } ?: map["PAYEE"]?.takeIf { it.isNotBlank() } ?: ""
        val memo = map["MEMO"]?.takeIf { it.isNotBlank() }
        val curDef = map["CURDEF"]?.takeIf { it.isNotBlank() } ?: "BRL"

        return ParsedTransaction(
            providerTransactionId = fitId,
            creditDebitType = creditDebitType,
            amount = amountAbs,
            date = date,
            transactionDatetime = transactionDatetime,
            description = name.ifEmpty { memo ?: "OFX" },
            descriptionRaw = memo,
            counterpartyName = null,
            counterpartyDocument = null,
            currencyCode = curDef,
        )
    }

    private fun parseDtPosted(value: String): Pair<LocalDate, Instant?> {
        val cleaned = value.trim().replace("[.].*".toRegex(), "").take(14)
        return when (cleaned.length) {
            8 -> {
                val date = LocalDate.parse(cleaned, DTPOSTED_FORMAT_DATE)
                Pair(date, null)
            }
            in 12..14 -> {
                val date = LocalDate.parse(cleaned.take(8), DTPOSTED_FORMAT_DATE)
                val time = cleaned.drop(8).padEnd(6, '0').take(6)
                val instant =
                    runCatching {
                        val hour = time.take(2).toIntOrNull() ?: 0
                        val min = time.take(4).drop(2).toIntOrNull() ?: 0
                        val sec = time.take(6).drop(4).toIntOrNull() ?: 0
                        date.atTime(hour, min, sec).toInstant(ZoneOffset.UTC)
                    }.getOrNull()
                Pair(date, instant)
            }
            else -> {
                val date =
                    runCatching { LocalDate.parse(cleaned.take(8), DTPOSTED_FORMAT_DATE) }
                        .getOrElse { LocalDate.now() }
                Pair(date, null)
            }
        }
    }

    private fun parseOfxDateOnly(value: String): LocalDate? = runCatching { parseDtPosted(value).first }.getOrNull()

    private fun Document.firstTagText(
        parentTag: String,
        childTag: String,
    ): String? {
        val parents = getElementsByTagName(parentTag)
        for (i in 0 until parents.length) {
            val parent = parents.item(i) ?: continue
            val children = parent.childNodes
            for (j in 0 until children.length) {
                val child = children.item(j)
                if (child.nodeType == Node.ELEMENT_NODE && child.nodeName.equals(childTag, ignoreCase = true)) {
                    return child.textContent?.trim()?.takeIf { it.isNotEmpty() }
                }
            }
        }
        return null
    }
}

enum class OfxStatementIntent {
    BANK_STATEMENT,
    CREDIT_CARD_STATEMENT,
    UNKNOWN,
}

enum class OfxDocumentFormat {
    SGML,
    XML,
    UNKNOWN,
}

private data class ParsedOfxDocument(
    val rows: List<ParsedTransaction> = emptyList(),
    val accountIdentifier: String? = null,
    val statementFrom: LocalDate? = null,
    val statementTo: LocalDate? = null,
)
