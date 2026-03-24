package io.github.salomax.neotool.banking.service.import.excel

import io.github.salomax.neotool.banking.domain.import.*
import jakarta.inject.Singleton

@Singleton
open class DeterministicExcelReaderRegistry(
    private val readers: List<DeterministicExcelReader>,
) {
    open fun findByProfile(profileId: String): DeterministicExcelReader? =
        readers.firstOrNull { profileId in it.supportedProfileIds }
}
