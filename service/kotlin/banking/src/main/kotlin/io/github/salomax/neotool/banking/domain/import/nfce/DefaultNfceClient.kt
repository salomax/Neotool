package io.github.salomax.neotool.banking.domain.import.nfce

import jakarta.inject.Singleton
import java.math.BigDecimal
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Node
import org.xml.sax.InputSource

private val CHNFE_PATTERN = Regex("""\d{44}""")
private val DH_EMI_FORMAT = DateTimeFormatter.ISO_DATE_TIME

/**
 * Fetches NFC-e XML from a QR code URL and parses it into [NfceData].
 * Timeout 5s; throws [NfceUnavailableException] on timeout or non-200;
 * throws [NfceInvalidException] on malformed XML or cancelled NF-e.
 */
@Singleton
open class DefaultNfceClient(
    private val httpFetcher: NfceHttpFetcher,
) : NfceClient {

    override fun fetch(qrCodeUrl: String): NfceData {
        val chNFeFromUrl = CHNFE_PATTERN.find(qrCodeUrl)?.value

        val response = try {
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

    private fun parseXml(xml: String, chNFeFromUrl: String?): NfceData {
        val doc = try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
            }
            factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
        } catch (e: Exception) {
            throw NfceInvalidException("NFC-e XML is malformed: ${e.message}", e)
        }

        val infNFeForCh = firstElementByLocalName(doc.documentElement, "infNFe") ?: doc.documentElement
        val chNFe = chNFeFromUrl
            ?: textContent(infNFeForCh, "chNFe") ?: textContent(doc.documentElement, "chNFe")
            ?: throw NfceInvalidException("NFC-e chNFe not found in URL or XML")

        val cStat = textContent(doc.documentElement, "cStat")
            ?: textContent(infNFeForCh, "cStat")
        if (cStat != null && cStat != "100" && cStat != "150") {
            throw NfceInvalidException("NFC-e is not authorized or is cancelled (cStat=$cStat)")
        }

        val ide = firstElementByLocalName(infNFeForCh, "ide")
        val dhEmiStr = textContent(ide, "dhEmi") ?: textContent(infNFeForCh, "dhEmi")
            ?: throw NfceInvalidException("NFC-e dhEmi not found")
        val dhEmi = try {
            Instant.from(DH_EMI_FORMAT.parse(dhEmiStr.replace(" ", "T")))
        } catch (e: Exception) {
            throw NfceInvalidException("NFC-e dhEmi invalid: $dhEmiStr", e)
        }

        val total = firstElementByLocalName(infNFeForCh, "total")
        val icmsTot = total?.let { firstElementByLocalName(it, "ICMSTot") }
        val vNFStr = textContent(icmsTot, "vNF") ?: textContent(total, "vNF") ?: textContent(infNFeForCh, "vNF")
            ?: throw NfceInvalidException("NFC-e vNF not found")
        val vNF = runCatching { BigDecimal(vNFStr.trim()) }.getOrElse {
            throw NfceInvalidException("NFC-e vNF invalid: $vNFStr", it)
        }

        val infNFe = firstElementByLocalName(doc.documentElement, "infNFe")
            ?: doc.documentElement
        val emit = firstElementByLocalName(infNFe, "emit")
        val enderEmit = emit?.let { firstElementByLocalName(it, "enderEmit") }

        val cnpjEmitente = textContent(emit, "CNPJ") ?: textContent(infNFe, "CNPJ")
            ?: throw NfceInvalidException("NFC-e emit CNPJ not found")
        val nomeEmitente = textContent(emit, "xNome") ?: ""
        val municipio = textContent(enderEmit, "xMun") ?: ""
        val uf = textContent(enderEmit, "UF") ?: ""

        val items = mutableListOf<NfceItem>()
        val detList = doc.getElementsByTagNameNS("*", "det")
        for (i in 0 until detList.length) {
            val det = detList.item(i)
            val prod = firstElementByLocalName(det, "prod") ?: continue
            val xProd = textContent(prod, "xProd") ?: continue
            val vProdStr = textContent(prod, "vProd") ?: "0"
            val vProd = BigDecimal(vProdStr.trim().replace(",", "."))
            val qComStr = textContent(prod, "qCom") ?: "1"
            val qCom = BigDecimal(qComStr.trim().replace(",", "."))
            val uCom = textContent(prod, "uCom") ?: "UN"
            val ncm = textContent(prod, "NCM") ?: ""
            items.add(NfceItem(xProd = xProd, vProd = vProd, qCom = qCom, uCom = uCom, ncm = ncm))
        }

        return NfceData(
            chNFe = chNFe,
            dhEmi = dhEmi,
            vNF = vNF,
            cnpjEmitente = cnpjEmitente,
            nomeEmitente = nomeEmitente,
            municipio = municipio,
            uf = uf,
            items = items,
        )
    }

    private fun textContent(parent: Node?, localName: String): String? {
        val el = firstElementByLocalName(parent, localName) ?: return null
        return el.textContent?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun firstElementByLocalName(parent: Node?, localName: String): Node? {
        if (parent == null) return null
        val list = parent.childNodes
        for (i in 0 until list.length) {
            val n = list.item(i)
            if (n.nodeType == Node.ELEMENT_NODE && (n.localName == localName || n.nodeName == localName)) return n
        }
        return null
    }
}
