package io.github.salomax.neotool.banking.domain.billimport.pdf

import jakarta.inject.Singleton

@Singleton
open class DeterministicPdfReaderRegistry(
    private val readers: List<DeterministicPdfReader>,
) {
    open fun findByProfile(profileId: String): DeterministicPdfReader? =
        readers.firstOrNull { profileId in it.supportedProfileIds }
}
