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
    private val scopePermissionMapper: OAuth2ScopePermissionMapper,
    private val consentService: OAuth2ConsentService,
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
        val unknownScopes = scopes.filterNot { it in scopePermissionMapper.allScopes() && client.scopes.contains(it) }
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
    ): Triple<OAuth2AuthorizationRequestEntity, String, OAuth2ConsentRequirement> {
        val request = getPendingRequest(requestId)
        val requestedScopes = parseScopes(request.requestedScopes)
        val consentRequirement =
            consentService.consentRequirement(
                userId = userId,
                clientId = request.clientId,
                requestedScopes = requestedScopes,
            )
        val csrfToken = tokenHashing.newOpaqueToken()
        request.userId = userId
        request.accountId = consentRequirement.accountId
        request.csrfTokenHash = tokenHashing.sha256Hex(csrfToken)
        request.updatedAt = Instant.now()
        return Triple(authorizationRequestRepository.update(request), csrfToken, consentRequirement)
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
        validateCsrf(request, csrfToken)
        val requested = parseScopes(request.requestedScopes).toSet()
        val granted = grantedScopes.filter { it.isNotBlank() }.toSet()
        if (!requested.containsAll(granted)) {
            throw OAuth2InvalidRequestException("Granted scopes must be a subset of requested scopes")
        }
        val consentRequirement =
            consentService.consentRequirement(
                userId = userId,
                clientId = request.clientId,
                requestedScopes = requested,
            )
        val accountId =
            request.accountId ?: consentRequirement.accountId
        val existingScopes = consentRequirement.existingScopes
        val effectiveGrant = (existingScopes + granted).intersect(requested).ifEmpty { granted }
        if (effectiveGrant.isEmpty()) {
            return denyRequest(request)
        }
        val code = tokenHashing.newOpaqueToken()
        request.accountId = accountId
        request.grantedScopes = effectiveGrant.sorted().joinToString(" ")
        request.status = OAuth2AuthorizationRequestStatus.APPROVED
        request.updatedAt = Instant.now()
        authorizationRequestRepository.update(request)
        consentService.persistGrant(
            userId = userId,
            clientId = request.clientId,
            accountId = accountId,
            scopes = existingScopes + granted,
        )
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

    @Transactional
    open fun deny(
        requestId: UUID,
        userId: UUID,
        csrfToken: String?,
    ): String {
        val request = getPendingRequest(requestId)
        if (request.userId != userId) {
            throw OAuth2InvalidRequestException("Authorization request does not belong to this session")
        }
        validateCsrf(request, csrfToken)
        return denyRequest(request)
    }

    @Transactional
    open fun approveExistingConsent(
        requestId: UUID,
        userId: UUID,
        accountId: UUID,
        grantedScopes: Collection<String>,
    ): String {
        val request = getPendingRequest(requestId)
        if (request.userId != userId) {
            throw OAuth2InvalidRequestException("Authorization request does not belong to this session")
        }
        val requested = parseScopes(request.requestedScopes).toSet()
        val granted = grantedScopes.toSet().intersect(requested)
        if (granted.isEmpty()) {
            throw OAuth2InvalidRequestException("No consented scope is available for this request")
        }
        val code = tokenHashing.newOpaqueToken()
        request.accountId = accountId
        request.grantedScopes = granted.sorted().joinToString(" ")
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

    fun parseScopes(scope: String?): List<String> =
        scope
            ?.split(" ")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            ?: emptyList()

    private fun validateCsrf(
        request: OAuth2AuthorizationRequestEntity,
        csrfToken: String?,
    ) {
        val expectedCsrf = request.csrfTokenHash ?: throw OAuth2InvalidRequestException("CSRF token is missing")
        if (csrfToken.isNullOrBlank() ||
            !tokenHashing.constantTimeEquals(
                expectedCsrf,
                tokenHashing.sha256Hex(csrfToken),
            )
        ) {
            throw OAuth2InvalidRequestException("Invalid CSRF token")
        }
    }

    private fun denyRequest(request: OAuth2AuthorizationRequestEntity): String {
        request.status = OAuth2AuthorizationRequestStatus.DENIED
        request.updatedAt = Instant.now()
        authorizationRequestRepository.update(request)
        return redirectWithError(request.redirectUri, "access_denied", request.state)
    }

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

    private fun redirectWithError(
        redirectUri: String,
        error: String,
        state: String?,
    ): String {
        val separator = if (redirectUri.contains("?")) "&" else "?"
        val encodedError = URLEncoder.encode(error, Charsets.UTF_8)
        val statePart = state?.let { "&state=${URLEncoder.encode(it, Charsets.UTF_8)}" } ?: ""
        return "$redirectUri${separator}error=$encodedError$statePart"
    }
}
