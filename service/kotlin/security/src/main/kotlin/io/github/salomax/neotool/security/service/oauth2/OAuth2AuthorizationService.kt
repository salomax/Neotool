package io.github.salomax.neotool.security.service.oauth2

import io.github.salomax.neotool.security.config.OAuthConfig
import io.github.salomax.neotool.security.model.OAuth2AuthorizationCodeEntity
import io.github.salomax.neotool.security.model.OAuth2AuthorizationRequestEntity
import io.github.salomax.neotool.security.model.OAuth2AuthorizationRequestStatus
import io.github.salomax.neotool.security.repo.OAuth2AuthorizationCodeRepository
import io.github.salomax.neotool.security.repo.OAuth2AuthorizationRequestRepository
import jakarta.inject.Singleton
import jakarta.transaction.Transactional
import java.net.URLEncoder
import java.time.Instant
import java.util.UUID

data class OAuth2AuthorizeInput(
    val responseType: String?,
    val clientId: String?,
    val redirectUri: String?,
    val scope: String?,
    val state: String?,
    val codeChallenge: String?,
    val codeChallengeMethod: String?,
    val resource: String?,
)

data class OAuth2ScopeDescription(
    val name: String,
    val description: String,
)

@Singleton
open class OAuth2AuthorizationService(
    private val clientRegistry: OAuth2ClientRegistry,
    private val authorizationRequestRepository: OAuth2AuthorizationRequestRepository,
    private val authorizationCodeRepository: OAuth2AuthorizationCodeRepository,
    private val tokenHashing: OAuth2TokenHashing,
    private val oauthConfig: OAuthConfig,
) {
    @Transactional
    open fun createAuthorizationRequest(input: OAuth2AuthorizeInput): OAuth2AuthorizationRequestEntity {
        if (input.responseType != "code") {
            throw OAuth2InvalidRequestException("response_type must be code")
        }
        val clientId =
            input.clientId?.takeIf { it.isNotBlank() }
                ?: throw OAuth2InvalidRequestException("client_id is required")
        val redirectUri =
            input.redirectUri?.takeIf { it.isNotBlank() }
                ?: throw OAuth2InvalidRequestException("redirect_uri is required")
        val client =
            clientRegistry.find(clientId)
                ?: throw OAuth2InvalidRequestException("Unknown client")
        if (!client.redirectUris.contains(redirectUri)) {
            throw OAuth2InvalidRequestException("redirect_uri does not match registered client")
        }
        if (input.codeChallenge.isNullOrBlank()) {
            throw OAuth2InvalidRequestException("code_challenge is required")
        }
        if (input.codeChallengeMethod != "S256") {
            throw OAuth2InvalidRequestException("code_challenge_method must be S256")
        }
        val resource =
            input.resource?.takeIf { it.isNotBlank() }
                ?: throw OAuth2InvalidRequestException("resource is required")
        if (!client.resources.contains(resource)) {
            throw OAuth2InvalidRequestException("resource is not registered for client")
        }
        val scopes = parseScopes(input.scope)
        if (scopes.isEmpty()) {
            throw OAuth2InvalidRequestException("scope is required")
        }
        val unknownScopes = scopes.filterNot { client.scopes.contains(it) }
        if (unknownScopes.isNotEmpty()) {
            throw OAuth2InvalidRequestException("Unknown scope requested")
        }
        val now = Instant.now()
        return authorizationRequestRepository.save(
            OAuth2AuthorizationRequestEntity(
                clientId = client.clientId,
                redirectUri = redirectUri,
                requestedScopes = scopes.joinToString(" "),
                codeChallenge = input.codeChallenge,
                resource = resource,
                state = input.state,
                expiresAt = now.plusSeconds(oauthConfig.authorizationServer.authorizationRequestTtlSeconds),
            ),
        )
    }

    fun getPendingRequest(id: UUID): OAuth2AuthorizationRequestEntity =
        authorizationRequestRepository.findByIdAndStatus(id, OAuth2AuthorizationRequestStatus.PENDING)
            ?.takeIf { it.expiresAt.isAfter(Instant.now()) }
            ?: throw OAuth2InvalidRequestException("Authorization request expired or not found")

    @Transactional
    open fun bindUserAndCsrf(
        requestId: UUID,
        userId: UUID,
    ): Pair<OAuth2AuthorizationRequestEntity, String> {
        val request = getPendingRequest(requestId)
        val csrfToken = tokenHashing.newOpaqueToken()
        request.userId = userId
        request.csrfTokenHash = tokenHashing.sha256Hex(csrfToken)
        request.updatedAt = Instant.now()
        return authorizationRequestRepository.update(request) to csrfToken
    }

    @Transactional
    open fun approve(
        requestId: UUID,
        userId: UUID,
        csrfToken: String?,
        grantedScopes: List<String>,
    ): String {
        val request = getPendingRequest(requestId)
        if (request.userId != userId) {
            throw OAuth2InvalidRequestException("Authorization request does not belong to this session")
        }
        val expectedCsrf = request.csrfTokenHash ?: throw OAuth2InvalidRequestException("CSRF token is missing")
        if (csrfToken.isNullOrBlank() ||
            !tokenHashing.constantTimeEquals(
                expectedCsrf,
                tokenHashing.sha256Hex(csrfToken),
            )
        ) {
            throw OAuth2InvalidRequestException("Invalid CSRF token")
        }
        val requested = parseScopes(request.requestedScopes).toSet()
        val granted = grantedScopes.filter { it.isNotBlank() }.toSet()
        if (granted.isEmpty() || !requested.containsAll(granted)) {
            throw OAuth2InvalidRequestException("Granted scopes must be a non-empty subset of requested scopes")
        }
        val code = tokenHashing.newOpaqueToken()
        request.grantedScopes = granted.joinToString(" ")
        request.status = OAuth2AuthorizationRequestStatus.APPROVED
        request.updatedAt = Instant.now()
        authorizationRequestRepository.update(request)
        authorizationCodeRepository.save(
            OAuth2AuthorizationCodeEntity(
                codeHash = tokenHashing.sha256Hex(code),
                authorizationRequestId = requestId,
                expiresAt =
                    Instant.now().plusSeconds(
                        oauthConfig.authorizationServer.authorizationCodeTtlSeconds.coerceAtMost(60),
                    ),
            ),
        )
        return redirectWithCode(request.redirectUri, code, request.state)
    }

    fun clientName(clientId: String): String = clientRegistry.find(clientId)?.clientName ?: clientId

    fun describeScopes(scopes: String): List<OAuth2ScopeDescription> =
        parseScopes(scopes).map { scope ->
            OAuth2ScopeDescription(
                name = scope,
                description =
                    when (scope) {
                        "finance:cashflow:read" ->
                            "Read cash-flow summaries for all accounts, including investment accounts."
                        "finance:transactions:read" -> "Read transaction history."
                        "finance:accounts:read" -> "Read financial account metadata."
                        else -> "Access this permission."
                    },
            )
        }

    fun parseScopes(scope: String?): List<String> =
        scope
            ?.split(" ")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            ?: emptyList()

    private fun redirectWithCode(
        redirectUri: String,
        code: String,
        state: String?,
    ): String {
        val separator = if (redirectUri.contains("?")) "&" else "?"
        val encodedCode = URLEncoder.encode(code, Charsets.UTF_8)
        val statePart = state?.let { "&state=${URLEncoder.encode(it, Charsets.UTF_8)}" } ?: ""
        return "$redirectUri${separator}code=$encodedCode$statePart"
    }
}
