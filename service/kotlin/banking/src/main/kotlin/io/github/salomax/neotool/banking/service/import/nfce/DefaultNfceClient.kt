package io.github.salomax.neotool.banking.service.import.nfce

import io.github.salomax.neotool.banking.client.import.nfce.NfceHttpFetcher
import io.github.salomax.neotool.banking.client.import.nfce.NfceInvalidException
import io.github.salomax.neotool.banking.client.import.nfce.NfceUnavailableException
import io.github.salomax.neotool.banking.domain.import.nfce.NfceData
import io.github.salomax.neotool.banking.domain.import.nfce.NfceItem
import jakarta.inject.Singleton
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import java.math.BigDecimal
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory

private val CHNFE_PATTERN = Regex("""\d{44}""")
private val DH_EMI_FORMAT = DateTimeFormatter.ISO_DATE_TIME

/**
 * XML-based NFC-e parser. Handles states that return XML from QR code URLs.
 * Used as the default/fallback fetcher by [NfceFetcherRouter].
 */
@Singleton
open class DefaultNfceClient(
    private val httpFetcher: NfceHttpFetcher,
) : NfceFetcher {
    override fun supportedUfCodes(): Set<String> = emptySet() // fallback — no specific UF

    override fun fetch(qrCodeUrl: String): NfceData {
        val chNFeFromUrl = CHNFE_PATTERN.find(qrCodeUrl)?.value

        val response =
            try {
                httpFetcher.fetch(qrCodeUrl)
            } catch (e: Exception) {
                throw NfceUnavailableException("NFC-e fetch failed: ${e.message}", e)
            }

        if (response.statusCode != 200) {
            throw NfceUnavailableException("NFC-e returned HTTP ${response.statusCode}")
        }

        val xml = response.body
        if (xml.isBlank()) {
            throw NfceInvalidException("NFC-e response body is empty")
        }

        return parseXml(xml, chNFeFromUrl)
    }

    private fun parseXml(
        xml: String,
        chNFeFromUrl: String?,
    ): NfceData {
        val doc =
            try {
                val factory =
                    DocumentBuilderFactory.newInstance().apply {
                        isNamespaceAware = true
                    }
                factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
            } catch (e: Exception) {
                throw NfceInvalidException("NFC-e XML is malformed: ${e.message}", e)
            }

        val infNFeForCh = firstElementByLocalName(doc.documentElement, "infNFe") ?: doc.documentElement
        val chNFe =
            chNFeFromUrl
                ?: textContent(infNFeForCh, "chNFe") ?: textContent(doc.documentElement, "chNFe")
                ?: throw NfceInvalidException("NFC-e chNFe not found in URL or XML")

        val cStat =
            textContent(doc.documentElement, "cStat")
                ?: textContent(infNFeForCh, "cStat")
        if (cStat != null && cStat != "100" && cStat != "150") {
            throw NfceInvalidException("NFC-e is not authorized or is cancelled (cStat=$cStat)")
        }

        val ide = firstElementByLocalName(infNFeForCh, "ide")
        val dhEmiStr =
            textContent(ide, "dhEmi") ?: textContent(infNFeForCh, "dhEmi")
                ?: throw NfceInvalidException("NFC-e dhEmi not found")
        val cMunFG = textContent(ide, "cMunFG") ?: textContent(infNFeForCh, "cMunFG")
        val dhEmi =
            try {
                Instant.from(DH_EMI_FORMAT.parse(dhEmiStr.replace(" ", "T")))
            } catch (e: Exception) {
                throw NfceInvalidException("NFC-e dhEmi invalid: $dhEmiStr", e)
            }

        val total = firstElementByLocalName(infNFeForCh, "total")
        val icmsTot = total?.let { firstElementByLocalName(it, "ICMSTot") }
        val vNFStr =
            textContent(icmsTot, "vNF") ?: textContent(total, "vNF") ?: textContent(infNFeForCh, "vNF")
                ?: throw NfceInvalidException("NFC-e vNF not found")
        val vNF =
            runCatching { BigDecimal(vNFStr.trim()) }.getOrElse {
                throw NfceInvalidException("NFC-e vNF invalid: $vNFStr", it)
            }

        val infNFe =
            firstElementByLocalName(doc.documentElement, "infNFe")
                ?: doc.documentElement
        val emit = firstElementByLocalName(infNFe, "emit")
        val enderEmit = emit?.let { firstElementByLocalName(it, "enderEmit") }

        val cnpjEmitente =
            textContent(emit, "CNPJ") ?: textContent(infNFe, "CNPJ")
                ?: throw NfceInvalidException("NFC-e emit CNPJ not found")
        val nomeEmitente = textContent(emit, "xNome") ?: ""
        val nomeFantasiaEmitente = textContent(emit, "xFant")
        val inscricaoEstadualEmitente = textContent(emit, "IE")
        val municipio = textContent(enderEmit, "xMun") ?: ""
        val uf = textContent(enderEmit, "UF") ?: ""
        val cepEmitente = textContent(enderEmit, "CEP")?.normalizePostalCode()
        val enderecoEmitente =
            buildIssuerAddress(
                street = textContent(enderEmit, "xLgr"),
                number = textContent(enderEmit, "nro"),
                complement = textContent(enderEmit, "xCpl"),
                neighborhood = textContent(enderEmit, "xBairro"),
                municipality = municipio,
                state = uf,
            )

        // CPF do destinatário
        val dest = firstElementByLocalName(infNFe, "dest")
        val cpfDestinatario = textContent(dest, "CPF")

        // Tributos totais (ICMSTot → vTotTrib)
        val vTotTribStr = textContent(icmsTot, "vTotTrib")
        val vTotTrib =
            vTotTribStr?.let {
                runCatching { BigDecimal(it.trim()) }.getOrNull()
            }

        val items = mutableListOf<NfceItem>()
        val detList = doc.getElementsByTagNameNS("*", "det")
        for (i in 0 until detList.length) {
            val det = detList.item(i)
            val nItemAttr = (det as? org.w3c.dom.Element)?.getAttribute("nItem")
            val prod = firstElementByLocalName(det, "prod") ?: continue
            val xProd = textContent(prod, "xProd") ?: continue
            val vProdStr = textContent(prod, "vProd") ?: "0"
            val vProd = BigDecimal(vProdStr.trim().replace(",", "."))
            val qComStr = textContent(prod, "qCom") ?: "1"
            val qCom = BigDecimal(qComStr.trim().replace(",", "."))
            val uCom = textContent(prod, "uCom") ?: "UN"
            val ncm = textContent(prod, "NCM") ?: ""
            val cEAN = textContent(prod, "cEAN")?.takeIf { it != "SEM GTIN" }
            val vUnComStr = textContent(prod, "vUnCom")
            val vUnCom = vUnComStr?.let { BigDecimal(it.trim().replace(",", ".")) }
            val vDescStr = textContent(prod, "vDesc")
            val vDesc = vDescStr?.let { BigDecimal(it.trim().replace(",", ".")) }
            val cProd = textContent(prod, "cProd")
            val nItem = nItemAttr?.toIntOrNull()
            // Valor aproximado dos tributos por item (Lei 12.741)
            val imposto = firstElementByLocalName(det, "imposto")
            val vTribItemStr = textContent(imposto, "vTotTrib") ?: textContent(det, "vTotTrib")
            val vTribItem =
                vTribItemStr?.let {
                    runCatching { BigDecimal(it.trim().replace(",", ".")) }.getOrNull()
                }
            items.add(
                NfceItem(
                    xProd = xProd, vProd = vProd, qCom = qCom, uCom = uCom, ncm = ncm,
                    cEAN = cEAN, vUnCom = vUnCom, vDesc = vDesc, cProd = cProd, nItem = nItem,
                    vTribItem = vTribItem,
                ),
            )
        }

        return NfceData(
            chNFe = chNFe,
            dhEmi = dhEmi,
            vNF = vNF,
            cnpjEmitente = cnpjEmitente,
            nomeEmitente = nomeEmitente,
            nomeFantasiaEmitente = nomeFantasiaEmitente,
            inscricaoEstadualEmitente = inscricaoEstadualEmitente,
            enderecoEmitente = enderecoEmitente,
            cepEmitente = cepEmitente,
            municipio = municipio,
            uf = uf,
            items = items,
            cMunFG = cMunFG,
            cpfDestinatario = cpfDestinatario,
            vTotTrib = vTotTrib,
        )
    }

    private fun buildIssuerAddress(
        street: String?,
        number: String?,
        complement: String?,
        neighborhood: String?,
        municipality: String?,
        state: String?,
    ): String? {
        val streetLine =
            listOfNotNull(
                street?.trim()?.takeIf { it.isNotEmpty() },
                number?.trim()?.takeIf { it.isNotEmpty() && it != "S/N" },
            ).joinToString(", ").takeIf { it.isNotBlank() }
                ?: street?.trim()?.takeIf { it.isNotEmpty() }
                ?: number?.trim()?.takeIf { it.isNotEmpty() }
        val locality =
            listOfNotNull(
                municipality?.trim()?.takeIf { it.isNotEmpty() },
                state?.trim()?.takeIf { it.isNotEmpty() },
            ).takeIf { it.isNotEmpty() }
                ?.joinToString(" - ")

        return listOfNotNull(
            streetLine,
            complement?.trim()?.takeIf { it.isNotEmpty() },
            neighborhood?.trim()?.takeIf { it.isNotEmpty() },
            locality,
        ).joinToString(" • ").takeIf { it.isNotBlank() }
    }

    private fun String.normalizePostalCode(): String? = filter(Char::isDigit).takeIf { it.length == 8 }

    private fun textContent(
        parent: Node?,
        localName: String,
    ): String? {
        val el = firstElementByLocalName(parent, localName) ?: return null
        return el.textContent?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun firstElementByLocalName(
        parent: Node?,
        localName: String,
    ): Node? {
        if (parent == null) return null
        val list = parent.childNodes
        for (i in 0 until list.length) {
            val n = list.item(i)
            if (n.nodeType == Node.ELEMENT_NODE && (n.localName == localName || n.nodeName == localName)) return n
        }
        return null
    }
}
