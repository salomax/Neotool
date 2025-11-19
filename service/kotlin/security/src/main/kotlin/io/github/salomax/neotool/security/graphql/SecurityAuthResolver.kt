package io.github.salomax.neotool.security.graphql

import io.github.salomax.neotool.security.graphql.dto.SignInInputDTO
import io.github.salomax.neotool.security.graphql.dto.SignInPayloadDTO
import io.github.salomax.neotool.security.graphql.dto.SignUpInputDTO
import io.github.salomax.neotool.security.graphql.dto.SignUpPayloadDTO
import io.github.salomax.neotool.security.graphql.dto.UserDTO
import io.github.salomax.neotool.common.graphql.GraphQLArgumentUtils.createMutationDataFetcher
import io.github.salomax.neotool.common.graphql.GraphQLArgumentUtils.createValidatedDataFetcher
import io.github.salomax.neotool.common.graphql.payload.GraphQLPayload
import io.github.salomax.neotool.common.graphql.payload.GraphQLPayloadFactory
import io.github.salomax.neotool.security.model.UserEntity
import io.github.salomax.neotool.security.service.AuthenticationService
import io.github.salomax.neotool.security.repo.UserRepository
import jakarta.inject.Singleton
import mu.KotlinLogging

/**
 * GraphQL resolver for authentication operations in security module.
 * 
 * Uses JWT tokens for authentication:
 * - Access tokens: Short-lived (configurable via jwt.access-token-expiration-seconds, default: 15 minutes), 
 *   stateless, used for API requests
 * - Refresh tokens: Long-lived (configurable via jwt.refresh-token-expiration-seconds, default: 7 days), 
 *   stored in database, used to obtain new access tokens
 * 
 * @see io.github.salomax.neotool.security.config.JwtConfig
 */
@Singleton
class SecurityAuthResolver(
    private val authenticationService: AuthenticationService,
    private val userRepository: UserRepository
) {
    private val logger = KotlinLogging.logger {}
    
    /**
     * Sign in mutation resolver
     */
    fun signIn(input: Map<String, Any?>): GraphQLPayload<SignInPayloadDTO> {
        return try {
            val email = input["email"] as? String ?: throw IllegalArgumentException("Email is required")
            val password = input["password"] as? String ?: throw IllegalArgumentException("Password is required")
            val rememberMe = input["rememberMe"] as? Boolean ?: false
            
            logger.debug { "Sign in attempt for email: $email" }
            
            val user = authenticationService.authenticate(email, password)
                ?: throw IllegalArgumentException("Invalid email or password")
            
            // Generate JWT access token (short-lived, stateless)
            val token = authenticationService.generateAccessToken(user)
            
            // Handle remember me - generate and store refresh token
            var refreshToken: String? = null
            if (rememberMe) {
                refreshToken = authenticationService.generateRefreshToken(user)
                // Store refresh token in database for revocation support
                authenticationService.saveRememberMeToken(user.id, refreshToken)
            }
            
            logger.info { "User signed in successfully: ${user.email}" }
            
            val payload = SignInPayloadDTO(
                token = token,
                refreshToken = refreshToken,
                user = userToDTO(user)
            )
            
            GraphQLPayloadFactory.success(payload)
        } catch (e: Exception) {
            logger.warn { "Sign in failed: ${e.message}" }
            GraphQLPayloadFactory.error(e)
        }
    }
    
    /**
     * Get current user from JWT access token.
     * 
     * Validates the JWT token and returns the authenticated user.
     * 
     * @param token The JWT access token from Authorization header
     * @return UserDTO if token is valid, null otherwise
     */
    fun getCurrentUser(token: String?): UserDTO? {
        if (token == null) {
            logger.debug { "No token provided" }
            return null
        }
        
        // Validate JWT access token
        val user = authenticationService.validateAccessToken(token)
            ?: run {
                logger.debug { "Invalid or expired access token" }
                return null
            }
        
        return userToDTO(user)
    }
    
    /**
     * Sign up mutation resolver
     */
    fun signUp(input: Map<String, Any?>): GraphQLPayload<SignUpPayloadDTO> {
        return try {
            val name = input["name"] as? String ?: throw IllegalArgumentException("Name is required")
            val email = input["email"] as? String ?: throw IllegalArgumentException("Email is required")
            val password = input["password"] as? String ?: throw IllegalArgumentException("Password is required")
            
            logger.debug { "Sign up attempt for email: $email" }
            
            // Register user (validates email uniqueness and password strength)
            val user = authenticationService.registerUser(name, email, password)
            
            // Generate JWT access token
            val token = authenticationService.generateAccessToken(user)
            
            // Generate refresh token (for automatic sign-in after signup)
            val refreshToken = authenticationService.generateRefreshToken(user)
            authenticationService.saveRememberMeToken(user.id, refreshToken)
            
            logger.info { "User signed up successfully: ${user.email}" }
            
            val payload = SignUpPayloadDTO(
                token = token,
                refreshToken = refreshToken,
                user = userToDTO(user)
            )
            
            GraphQLPayloadFactory.success(payload)
        } catch (e: Exception) {
            logger.warn { "Sign up failed: ${e.message}" }
            GraphQLPayloadFactory.error(e)
        }
    }
    
    /**
     * Convert UserEntity to DTO
     */
    private fun userToDTO(user: UserEntity): UserDTO {
        return UserDTO(
            id = user.id.toString(),
            email = user.email,
            displayName = user.displayName
        )
    }
}

