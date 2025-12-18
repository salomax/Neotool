package io.github.salomax.neotool.security.test.http

import io.github.salomax.neotool.security.domain.rbac.SecurityPermissions
import io.github.salomax.neotool.security.http.RequiresAuthorization
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post

/**
 * Fake controller for testing REST authorization infrastructure.
 *
 * This controller is only used in tests and should NOT be included in production builds.
 * It provides endpoints protected by [RequiresAuthorization] to validate that the
 * authorization interceptor and exception handlers work correctly.
 */
@Controller("/protected")
open class ProtectedController {
    /**
     * GET endpoint protected by SECURITY_USER_VIEW permission.
     */
    @Get("/view")
    @RequiresAuthorization(SecurityPermissions.SECURITY_USER_VIEW)
    open fun view(): HttpResponse<String> {
        return HttpResponse.ok("ok")
    }

    /**
     * POST endpoint protected by SECURITY_USER_SAVE permission.
     */
    @Post("/save")
    @RequiresAuthorization(SecurityPermissions.SECURITY_USER_SAVE)
    open fun save(): HttpResponse<String> {
        return HttpResponse.ok("saved")
    }
}
