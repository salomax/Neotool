package io.github.salomax.neotool.security.test.service.unit.oauth2

import io.github.salomax.neotool.security.service.oauth2.DefaultOAuth2ScopePermissionMapper
import io.github.salomax.neotool.security.service.oauth2.OAuth2Scopes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class OAuth2ScopePermissionMapperTest {
    private val mapper = DefaultOAuth2ScopePermissionMapper()

    @Test
    fun `every defined scope resolves to at least one permission`() {
        mapper.validateMappings()

        assertThat(OAuth2Scopes.all)
            .allSatisfy { scope ->
                assertThat(mapper.requiredPermissions(scope)).isNotEmpty()
            }
    }

    @Test
    fun `finance rules write maps to category manage ceiling`() {
        assertThat(mapper.requiredPermissions(OAuth2Scopes.FINANCE_RULES_WRITE))
            .containsExactly("banking:category:manage")
    }
}
