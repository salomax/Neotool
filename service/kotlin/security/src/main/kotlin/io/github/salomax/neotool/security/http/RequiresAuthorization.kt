package io.github.salomax.neotool.security.http

import io.micronaut.aop.Around
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

/**
 * Annotation to require authorization for REST endpoints.
 *
 * This annotation can be applied to controller methods or classes to enforce
 * permission-based authorization. When applied, the [AuthorizationInterceptor]
 * will validate the request's Bearer token and check if the principal has the
 * required permission.
 *
 * Usage example:
 * ```
 * @Controller("/api/users")
 * class UserController {
 *     @Get("/{id}")
 *     @RequiresAuthorization("security:user:view")
 *     fun getUser(id: UUID): HttpResponse<User> {
 *         // ...
 *     }
 * }
 * ```
 *
 * Future REST modules can use this annotation to protect their endpoints
 * without needing to implement authorization logic themselves.
 *
 * @param permission The permission string required to access the endpoint
 *                   (e.g., "security:user:view", "security:user:save")
 */
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.CLASS,
)
@Retention(RetentionPolicy.RUNTIME)
@Around
annotation class RequiresAuthorization(
    val permission: String,
)
