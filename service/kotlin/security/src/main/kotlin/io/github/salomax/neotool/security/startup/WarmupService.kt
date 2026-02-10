package io.github.salomax.neotool.security.startup

import io.github.salomax.neotool.common.security.principal.PrincipalType
import io.github.salomax.neotool.common.startup.AbstractWarmupService
import io.github.salomax.neotool.security.repo.GroupMembershipRepository
import io.github.salomax.neotool.security.repo.PrincipalRepository
import io.github.salomax.neotool.security.repo.UserRepository
import io.github.salomax.neotool.security.service.email.EmailService
import jakarta.inject.Singleton
import java.time.Instant
import java.util.UUID

@Singleton
class WarmupService(
    private val userRepository: UserRepository,
    private val principalRepository: PrincipalRepository,
    private val groupMembershipRepository: GroupMembershipRepository,
    @Suppress("unused") private val emailService: EmailService,
) : AbstractWarmupService() {
    override fun runWarmupQueries() {
        val dummyId = UUID(0, 0)
        userRepository.findByEmail("__warmup__")
        principalRepository.findByPrincipalTypeAndExternalId(PrincipalType.USER, "__warmup__")
        groupMembershipRepository.findActiveMembershipsByUserId(dummyId, Instant.now())
    }
}
