package io.github.salomax.neotool.banking.domain.pdfimport.reader

import jakarta.inject.Singleton

@Singleton
open class DeterministicPdfReaderRegistry(
    private val readers: List<DeterministicPdfReader>,
) {
    open fun findByProfile(profileId: String): DeterministicPdfReader? =
        readers.firstOrNull { profileId in it.supportedProfileIds }
}
