package io.github.salomax.neotool.financialdata.repo

import java.time.Instant

/**
 * Minimal result from bacen_institution for slug listing (sitemap/URL building).
 * Only id, name, and updatedAt; no joins or aggregates.
 */
data class InstitutionSlugResult(
    val bacenCodInst: String,
    val nomeInstituicao: String?,
    val updatedAt: Instant?,
)
