package io.github.salomax.neotool.security.service.oauth2

import com.github.mustachejava.DefaultMustacheFactory
import jakarta.inject.Singleton
import java.io.InputStreamReader
import java.io.StringReader
import java.io.StringWriter

@Singleton
class OAuth2TemplateRenderer {
    private val mustacheFactory = DefaultMustacheFactory()

    fun render(
        templateName: String,
        locale: String?,
        variables: Map<String, Any?>,
    ): String {
        val resolvedLocale = resolveLocale(locale)
        val bodyPath = "templates/$templateName/body.$resolvedLocale.html"
        val body =
            javaClass.classLoader.getResourceAsStream(bodyPath)?.use { stream ->
                InputStreamReader(stream, Charsets.UTF_8).readText()
            } ?: javaClass.classLoader.getResourceAsStream("templates/$templateName/body.en.html")!!.use { stream ->
                InputStreamReader(stream, Charsets.UTF_8).readText()
            }
        val writer = StringWriter()
        mustacheFactory.compile(StringReader(body), templateName).execute(writer, variables).flush()
        return writer.toString()
    }

    private fun resolveLocale(locale: String?): String {
        val normalized = locale?.trim()?.replace("_", "-") ?: return "en"
        return when {
            normalized.equals("pt-BR", ignoreCase = true) -> "pt-BR"
            normalized.startsWith("pt", ignoreCase = true) -> "pt"
            normalized.startsWith("en", ignoreCase = true) -> "en"
            else -> "en"
        }
    }
}
