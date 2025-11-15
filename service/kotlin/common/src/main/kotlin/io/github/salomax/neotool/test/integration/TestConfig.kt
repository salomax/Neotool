package io.github.salomax.neotool.test.integration

import io.micronaut.context.env.yaml.YamlPropertySourceLoader
import io.micronaut.core.io.scan.ClassPathResourceLoader
import kotlin.collections.emptyMap

object TestConfig {
  private val props: Map<String, String> by lazy {
    try {
      val loader = YamlPropertySourceLoader()
      val cp = ClassPathResourceLoader.defaultLoader(TestConfig::class.java.classLoader)
      val inputStream = cp.getResourceAsStream("test-config.yml")
        .or { cp.getResourceAsStream("test-config.yaml") }
      val sources = loader.read("test-config", inputStream.get()) // read test-config.yml/.yaml
      sources.map { mapEntry -> mapEntry.key to mapEntry.value.toString() }.toMap()
    } catch (_: Throwable) {
      emptyMap()
    }
  }

  fun str(key: String, default: String): String =
    props[key] ?: default

  fun bool(key: String, default: Boolean): Boolean =
    props[key]?.toBooleanStrictOrNull() ?: default

  fun int(key: String, default: Int): Int =
    props[key]?.toIntOrNull() ?: default
}
