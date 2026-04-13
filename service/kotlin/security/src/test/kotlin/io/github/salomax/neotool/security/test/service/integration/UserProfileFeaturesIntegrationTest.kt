package io.github.salomax.neotool.security.test.service.integration

import io.github.salomax.neotool.common.test.assertions.assertNoErrors
import io.github.salomax.neotool.common.test.assertions.shouldBeJson
import io.github.salomax.neotool.common.test.assertions.shouldBeSuccessful
import io.github.salomax.neotool.common.test.assertions.shouldHaveNonEmptyBody
import io.github.salomax.neotool.common.test.http.exchangeAsString
import io.github.salomax.neotool.common.test.integration.BaseIntegrationTest
import io.github.salomax.neotool.common.test.integration.PostgresIntegrationTest
import io.github.salomax.neotool.common.test.json.read
import io.github.salomax.neotool.common.test.transaction.runTransaction
import io.github.salomax.neotool.security.model.UserEntity
import io.github.salomax.neotool.security.model.account.AccountEntity
import io.github.salomax.neotool.security.model.account.AccountMembershipEntity
import io.github.salomax.neotool.security.model.account.AccountRole
import io.github.salomax.neotool.security.model.account.AccountStatus
import io.github.salomax.neotool.security.model.account.AccountType
import io.github.salomax.neotool.security.model.account.MembershipStatus
import io.github.salomax.neotool.security.repo.AccountMembershipRepository
import io.github.salomax.neotool.security.repo.AccountRepository
import io.github.salomax.neotool.security.repo.PrincipalRepository
import io.github.salomax.neotool.security.repo.RefreshTokenRepository
import io.github.salomax.neotool.security.repo.UserPreferencesRepository
import io.github.salomax.neotool.security.repo.UserRepository
import io.github.salomax.neotool.security.service.authentication.AuthContextFactory
import io.github.salomax.neotool.security.service.authentication.AuthenticationService
import io.github.salomax.neotool.security.service.preferences.UserPreferencesService
import io.github.salomax.neotool.security.test.SecurityTestDataBuilders
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.json.tree.JsonNode
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@MicronautTest(startApplication = true)
@DisplayName("User Profile Features Integration Tests")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
@Tag("graphql")
@Tag("security")
open class UserProfileFeaturesIntegrationTest :
    BaseIntegrationTest(),
    PostgresIntegrationTest {
    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var authenticationService: AuthenticationService

    @Inject
    lateinit var authContextFactory: AuthContextFactory

    @Inject
    lateinit var userPreferencesService: UserPreferencesService

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    @Inject
    lateinit var entityManager: EntityManager

    @Inject
    lateinit var refreshTokenRepository: RefreshTokenRepository

    @Inject
    lateinit var accountRepository: AccountRepository

    @Inject
    lateinit var accountMembershipRepository: AccountMembershipRepository

    @Inject
    lateinit var principalRepository: PrincipalRepository

    private fun saveUser(user: UserEntity) {
        entityManager.runTransaction {
            authenticationService.saveUser(user)
            entityManager.flush()
        }
    }

    private fun tokenFor(user: UserEntity): String {
        val persisted = userRepository.findByEmail(user.email) ?: user
        val authContext = authContextFactory.build(persisted)
        return authenticationService.generateAccessToken(authContext)
    }

    private fun graphql(
        body: Map<String, Any?>,
        token: String,
    ): JsonNode {
        val request =
            HttpRequest
                .POST("/graphql", body)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $token")
        val response = httpClient.exchangeAsString(request)
        response
            .shouldBeSuccessful()
            .shouldBeJson()
            .shouldHaveNonEmptyBody()
        return json.read(response)
    }

    @AfterEach
    fun cleanupTestData() {
        try {
            entityManager.runTransaction {
                refreshTokenRepository.deleteAll()
                userPreferencesRepository.deleteAll()
                accountMembershipRepository.deleteAll()
                accountRepository.deleteAll()
                principalRepository.deleteAll()
                userRepository.deleteAll()
                entityManager.flush()
            }
            entityManager.clear()
        } catch (_: Exception) {
        }
    }

    @Test
    fun `should return keepMeConnected true by default for email user`() {
        val user =
            SecurityTestDataBuilders.userWithPassword(
                authenticationService = authenticationService,
                email = SecurityTestDataBuilders.uniqueEmail("profile-email-default"),
                password = "TestPassword123!",
            )
        saveUser(user)
        val token = tokenFor(user)

        val payload =
            graphql(
                body =
                    mapOf(
                        "query" to
                            """
                            query {
                              userPreferences {
                                keepMeConnected
                              }
                            }
                            """.trimIndent(),
                    ),
                token = token,
            )

        payload["errors"].assertNoErrors()
        assertThat(payload["data"]["userPreferences"]["keepMeConnected"].booleanValue).isTrue()
    }

    @Test
    fun `should return keepMeConnected true by default for oauth user`() {
        val oauthUser =
            UserEntity(
                email = SecurityTestDataBuilders.uniqueEmail("profile-oauth-default"),
                displayName = "OAuth User",
                passwordHash = null,
                avatarProvider = "google",
            )
        saveUser(oauthUser)
        val token = tokenFor(oauthUser)

        val payload =
            graphql(
                body =
                    mapOf(
                        "query" to
                            """
                            query {
                              userPreferences {
                                keepMeConnected
                              }
                            }
                            """.trimIndent(),
                    ),
                token = token,
            )

        payload["errors"].assertNoErrors()
        assertThat(payload["data"]["userPreferences"]["keepMeConnected"].booleanValue).isTrue()
    }

    @Test
    fun `should use keepMeConnected when rememberMe is omitted in signIn`() {
        val email = SecurityTestDataBuilders.uniqueEmail("profile-signin-remember")
        val password = "TestPassword123!"
        val user =
            SecurityTestDataBuilders.userWithPassword(
                authenticationService = authenticationService,
                email = email,
                password = password,
            )
        saveUser(user)
        entityManager.runTransaction {
            userPreferencesService.update(
                userId = user.id!!,
                language = null,
                theme = null,
                blurBalances = null,
                firstDayOfWeek = null,
                keepMeConnected = true,
            )
            entityManager.flush()
        }

        val payload =
            graphql(
                body =
                    mapOf(
                        "query" to
                            """
                            mutation SignIn(${'$'}input: SignInInput!) {
                              signIn(input: ${'$'}input) {
                                token
                                refreshToken
                              }
                            }
                            """.trimIndent(),
                        "variables" to
                            mapOf(
                                "input" to
                                    mapOf(
                                        "email" to email,
                                        "password" to password,
                                    ),
                            ),
                    ),
                token = tokenFor(user),
            )

        payload["errors"].assertNoErrors()
        assertThat(payload["data"]["signIn"]["token"].stringValue).isNotBlank()
        assertThat(payload["data"]["signIn"]["refreshToken"].isNull).isFalse()
    }

    @Test
    fun `should list and update owned account via profile graphql`() {
        val user =
            SecurityTestDataBuilders.userWithPassword(
                authenticationService = authenticationService,
                email = SecurityTestDataBuilders.uniqueEmail("profile-account-owner"),
                password = "TestPassword123!",
            )
        saveUser(user)
        val account =
            entityManager.runTransaction {
                val created =
                    accountRepository.save(
                        AccountEntity(
                            accountName = "Original Name",
                            accountType = AccountType.FAMILY,
                            accountStatus = AccountStatus.ACTIVE,
                            ownerUserId = user.id,
                        ),
                    )
                accountMembershipRepository.save(
                    AccountMembershipEntity(
                        accountId = created.id!!,
                        userId = user.id!!,
                        accountRole = AccountRole.OWNER,
                        membershipStatus = MembershipStatus.ACTIVE,
                    ),
                )
                entityManager.flush()
                created
            }
        val token = tokenFor(user)

        val queryPayload =
            graphql(
                body =
                    mapOf(
                        "query" to
                            """
                            query {
                              myAccounts {
                                id
                                accountName
                                isOwner
                              }
                            }
                            """.trimIndent(),
                    ),
                token = token,
            )

        queryPayload["errors"].assertNoErrors()
        val accounts = queryPayload["data"]["myAccounts"]
        var matching: JsonNode? = null
        for (index in 0 until accounts.size()) {
            val candidate = accounts[index]
            if (candidate["id"].stringValue == account.id.toString()) {
                matching = candidate
                break
            }
        }
        assertThat(matching).isNotNull()
        assertThat(matching!!["isOwner"]?.booleanValue).isTrue()

        val mutationPayload =
            graphql(
                body =
                    mapOf(
                        "query" to
                            """
                            mutation UpdateAccount(${'$'}accountId: ID!, ${'$'}input: UpdateAccountInput!) {
                              updateAccount(accountId: ${'$'}accountId, input: ${'$'}input) {
                                id
                                accountName
                                isOwner
                              }
                            }
                            """.trimIndent(),
                        "variables" to
                            mapOf(
                                "accountId" to account.id.toString(),
                                "input" to mapOf("accountName" to "Renamed Account"),
                            ),
                    ),
                token = token,
            )

        mutationPayload["errors"].assertNoErrors()
        assertThat(mutationPayload["data"]["updateAccount"]["accountName"].stringValue).isEqualTo("Renamed Account")
        assertThat(mutationPayload["data"]["updateAccount"]["isOwner"].booleanValue).isTrue()
    }

    @Test
    fun `should reject avatar update for oauth user`() {
        val oauthUser =
            UserEntity(
                email = SecurityTestDataBuilders.uniqueEmail("profile-avatar-oauth"),
                displayName = "OAuth User",
                passwordHash = null,
                avatarProvider = "google",
            )
        saveUser(oauthUser)
        val token = tokenFor(oauthUser)

        val payload =
            graphql(
                body =
                    mapOf(
                        "query" to
                            """
                            mutation UpdateAvatar(${'$'}input: UpdateUserAvatarInput!) {
                              updateUserAvatar(input: ${'$'}input) {
                                id
                                avatarUrl
                              }
                            }
                            """.trimIndent(),
                        "variables" to
                            mapOf(
                                "input" to
                                    mapOf(
                                        "avatarUrl" to
                                            "https://assets.test.local/public/user-profiles/avatar-1",
                                    ),
                            ),
                    ),
                token = token,
            )

        val errors = payload["errors"]
        assertThat(errors).isNotNull()
        assertThat(errors.size()).isGreaterThan(0)
        assertThat(errors[0]["message"].stringValue).containsIgnoringCase("avatar")
    }
}
