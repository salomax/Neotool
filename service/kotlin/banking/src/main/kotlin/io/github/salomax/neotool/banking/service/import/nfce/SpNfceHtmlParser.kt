package io.github.salomax.neotool.banking.service.import.nfce

import io.github.salomax.neotool.banking.client.import.nfce.NfceInvalidException
import io.github.salomax.neotool.banking.domain.import.nfce.NfceData
import io.github.salomax.neotool.banking.domain.import.nfce.NfceItem
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField

/**
 * dd/MM/yyyy HH:mm:ss with optional timezone offset (e.g. "-03:00").
 * SP SEFAZ returns datetime like "06/03/2026 10:36:02-03:00".
 */
private val BR_DATETIME_FORMAT: DateTimeFormatter =
    DateTimeFormatterBuilder()
        .appendPattern("dd/MM/yyyy HH:mm:ss")
        .optionalStart()
        .appendOffset("+HH:MM", "-03:00")
        .optionalEnd()
        .parseDefaulting(ChronoField.OFFSET_SECONDS, -3 * 3600L)
        .toFormatter()

/**
 * Parses the detailed HTML page returned by SP SEFAZ "Visualizar em Abas" postback.
 * Extracts NFC-e header fields and all item details (including NCM, EAN).
 *
 * The HTML uses `<label>...</label>\s*<span>...</span>` inside `<td>` cells.
 * Labels may have trailing whitespace before `</label>`.
 * All regex patterns use DOTALL + CASE_INSENSITIVE for robustness.
 */
object SpNfceHtmlParser {
    fun parse(
        rawHtml: String,
        chNFeFromUrl: String?,
    ): NfceData {
        // Normalize non-breaking spaces (U+00A0) to regular spaces — SP SEFAZ uses NBSP in labels
        val html = rawHtml.replace('\u00A0', ' ')

        val chNFe =
            chNFeFromUrl
                ?: extractLabelValue(html, "Chave de [Aa]cesso")
                    ?.replace(Regex("""[\s\-./]"""), "")
                    ?.takeIf { it.matches(Regex("""\d{44}""")) }
                ?: throw NfceInvalidException("SP HTML: chNFe not found")

        val dhEmiStr =
            extractLabelValue(html, "Data de Emiss.o")
                ?: throw NfceInvalidException("SP HTML: dhEmi not found")
        val dhEmi =
            try {
                OffsetDateTime.parse(dhEmiStr.trim(), BR_DATETIME_FORMAT).toInstant()
            } catch (e: Exception) {
                throw NfceInvalidException("SP HTML: dhEmi invalid: $dhEmiStr", e)
            }

        val vNFStr =
            extractLabelValue(html, "Valor Total da Nota Fiscal")
                ?: extractLabelValue(html, "Valor total R\\$")
                ?: throw NfceInvalidException("SP HTML: vNF not found")
        val vNF =
            parseBrDecimal(vNFStr)
                ?: throw NfceInvalidException("SP HTML: vNF invalid: $vNFStr")

        // Extract emitente header section (first CNPJ/Nome in the HTML, before items)
        val cnpjEmitente =
            extractLabelValue(html, "CNPJ")
                ?.replace(Regex("""[.\-/]"""), "")
                ?: throw NfceInvalidException("SP HTML: CNPJ not found")

        val nomeEmitente =
            extractLabelValue(html, "Nome / Raz.o Social")
                ?: extractLabelValue(html, "Raz.o Social")
                ?: ""
        val nomeFantasiaEmitente = extractLabelValue(html, "Nome Fantasia")
        val inscricaoEstadualEmitente = extractLabelValue(html, "Inscri(?:ção|cao) Estadual")

        val municipioField = extractMunicipioField(html)
        val municipio = municipioField.name
        val uf = extractLabelValue(html, """\bUF""") ?: "SP"
        val cepEmitente = extractLabelValue(html, "CEP")?.normalizePostalCode()
        val enderecoEmitente =
            buildIssuerAddress(
                rawAddress = extractLabelValue(html, "Endere.o"),
                street = extractLabelValue(html, "Logradouro"),
                number = extractLabelValue(html, "N.mero"),
                complement = extractLabelValue(html, "Complemento"),
                neighborhood = extractLabelValue(html, "Bairro / Distrito") ?: extractLabelValue(html, "Bairro"),
                municipality = municipioField.name,
                state = uf,
            )

        // CPF do destinatário (comprador)
        val cpfDestinatario =
            extractLabelValue(html, "CPF")
                ?.replace(Regex("""[.\-]"""), "")
                ?.takeIf { it.matches(Regex("""\d{11}""")) }

        // Tributos totais (Lei 12.741/2012) — from summary page class or detail header
        val vTotTribStr =
            extractFirst(html, """Tributos Totais Incidentes[^<]*</label>\s*<span[^>]*>\s*([^<]*?)\s*</span>""")
                ?: extractLabelValue(html, "Valor Total de Tributos")
        val vTotTrib = vTotTribStr?.let { parseBrDecimal(it) }

        val items = parseItems(html)

        return NfceData(
            chNFe = chNFe,
            dhEmi = dhEmi,
            vNF = vNF,
            cnpjEmitente = cnpjEmitente,
            nomeEmitente = nomeEmitente.trim(),
            nomeFantasiaEmitente = nomeFantasiaEmitente?.trim(),
            inscricaoEstadualEmitente = inscricaoEstadualEmitente?.trim(),
            enderecoEmitente = enderecoEmitente,
            cepEmitente = cepEmitente,
            municipio = municipio.trim(),
            uf = uf.trim(),
            items = items,
            cMunFG = municipioField.code,
            cpfDestinatario = cpfDestinatario,
            vTotTrib = vTotTrib,
        )
    }

    internal fun parseItems(html: String): List<NfceItem> {
        val items = mutableListOf<NfceItem>()

        // Each item data row has fixo-prod-serv-descricao with a <span> (not <label>)
        val descPattern = Regex("""fixo-prod-serv-descricao">\s*<span>([^<]+)</span>""")
        val descMatches = descPattern.findAll(html).toList()

        for ((index, descMatch) in descMatches.withIndex()) {
            val xProd = descMatch.groupValues[1].trim()
            val startPos = descMatch.range.last
            val endPos = if (index + 1 < descMatches.size) descMatches[index + 1].range.first else html.length
            val itemBlock = html.substring(startPos, endPos)

            val ncm = extractLabelValue(itemBlock, "C.digo NCM")?.trim() ?: ""
            val cEAN =
                extractLabelValue(itemBlock, "(?:C.digo )?EAN Comercial")
                    ?.trim()?.takeIf { it.isNotBlank() && it != "SEM GTIN" }
            val cProd = extractLabelValue(itemBlock, "C.digo do Produto")?.trim()
            val vUnComStr = extractLabelValue(itemBlock, "Valor unit.rio de comercializa..o")
            val vUnCom = vUnComStr?.let { parseBrDecimal(it) }
            val vDescStr = extractLabelValue(itemBlock, "Valor do Desconto")
            val vDesc = vDescStr?.let { parseBrDecimal(it) }
            val qComStr = extractLabelValue(itemBlock, "Quantidade Comercial")
            val qCom = qComStr?.let { parseBrDecimal(it) } ?: BigDecimal.ONE
            val uCom = extractLabelValue(itemBlock, "Unidade Comercial")?.trim() ?: "UN"

            // vProd from the inline table cell (fixo-prod-serv-vb)
            val vProdInline = extractFirst(itemBlock, """fixo-prod-serv-vb">\s*<span>([^<]+)</span>""")
            val vProd =
                vProdInline?.let { parseBrDecimal(it) }
                    ?: (vUnCom?.multiply(qCom) ?: BigDecimal.ZERO)

            // Valor Aproximado dos Tributos por item (Lei 12.741/2012)
            val vTribItemStr = extractLabelValue(itemBlock, "Valor Aproximado dos Tributos")
            val vTribItem = vTribItemStr?.let { parseBrDecimal(it) }

            items.add(
                NfceItem(
                    xProd = xProd,
                    vProd = vProd,
                    qCom = qCom,
                    uCom = uCom,
                    ncm = ncm,
                    cEAN = cEAN,
                    vUnCom = vUnCom,
                    vDesc = vDesc,
                    cProd = cProd,
                    vTribItem = vTribItem,
                    nItem = index + 1,
                ),
            )
        }

        return items
    }

    /**
     * Extracts the `<span>` value following a `<label>` whose text matches [labelPattern].
     * Handles trailing whitespace inside the label and multiline HTML.
     */
    private fun extractLabelValue(
        text: String,
        labelPattern: String,
    ): String? {
        // Pattern: <label> ... labelText ... </label> \s* <span ...> value </span>
        val pattern =
            Regex(
                """$labelPattern\s*</label>\s*<span[^>]*>\s*([^<]*?)\s*</span>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            )
        val match = pattern.find(text) ?: return null
        val value = match.groupValues[1].trim()
        return value.takeIf { it.isNotEmpty() }
    }

    private fun extractFirst(
        text: String,
        pattern: String,
    ): String? {
        val match =
            Regex(pattern, setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(text)
                ?: return null
        val value = match.groupValues[1].trim()
        return value.takeIf { it.isNotEmpty() }
    }

    private fun buildIssuerAddress(
        rawAddress: String?,
        street: String?,
        number: String?,
        complement: String?,
        neighborhood: String?,
        municipality: String?,
        state: String?,
    ): String? {
        val normalizedRawAddress = rawAddress?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedStreet = street?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedNumber = number?.trim()?.takeIf { it.isNotEmpty() && it != "S/N" }
        val streetLine =
            normalizedStreet?.let { streetValue ->
                listOfNotNull(
                    streetValue,
                    normalizedNumber,
                ).joinToString(", ").takeIf { it.isNotBlank() }
            } ?: normalizedStreet
        val locality =
            listOfNotNull(
                municipality?.trim()?.takeIf { it.isNotEmpty() },
                state?.trim()?.takeIf { it.isNotEmpty() },
            ).takeIf { it.isNotEmpty() }
                ?.joinToString(" - ")
        val baseAddress =
            listOfNotNull(
                streetLine ?: normalizedRawAddress,
                complement?.trim()?.takeIf { it.isNotEmpty() },
                neighborhood?.trim()?.takeIf { it.isNotEmpty() },
            ).joinToString(" • ").takeIf { it.isNotBlank() }
                ?: normalizedRawAddress

        val shouldAppendLocality =
            !locality.isNullOrBlank() && (
                baseAddress.isNullOrBlank() ||
                    (
                        !baseAddress.contains(municipality.orEmpty(), ignoreCase = true) ||
                            !baseAddress.contains(state.orEmpty(), ignoreCase = true)
                    )
            )

        return listOfNotNull(
            baseAddress,
            locality?.takeIf { shouldAppendLocality },
        ).joinToString(" • ").takeIf { it.isNotBlank() }
    }

    /**
     * SP SEFAZ returns municipality as "IBGE_CODE - CityName" or just "CityName".
     * Extract only the city name part.
     */
    private fun extractMunicipioField(block: String): MunicipioField {
        val raw =
            extractLabelValue(block, "Munic.pio")
                ?: return MunicipioField(code = null, name = "")
        // Format may be "3547809\n-\nSanto Andre" → split code/name.
        val parts = raw.split(Regex("""\s*-\s*"""), limit = 2)
        return if (parts.size == 2 && parts[0].matches(Regex("""\d+"""))) {
            MunicipioField(
                code = parts[0].trim(),
                name = parts[1].trim(),
            )
        } else {
            MunicipioField(
                code = null,
                name = raw.trim(),
            )
        }
    }

    private data class MunicipioField(
        val code: String?,
        val name: String,
    )

    internal fun parseBrDecimal(str: String): BigDecimal? {
        val cleaned =
            str.trim()
                .replace(".", "") // thousand separator
                .replace(",", ".") // decimal separator
        return cleaned.toBigDecimalOrNull()
    }

    private fun String.normalizePostalCode(): String? = filter(Char::isDigit).takeIf { it.length == 8 }
}
