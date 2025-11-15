package io.github.salomax.neotool.example.graphql.auth

import io.github.salomax.neotool.example.graphql.dto.SignInInputDTO
import io.github.salomax.neotool.example.graphql.dto.SignInPayloadDTO
import io.github.salomax.neotool.example.graphql.dto.UserDTO
import io.github.salomax.neotool.graphql.GraphQLArgumentUtils.createMutationDataFetcher
import io.github.salomax.neotool.graphql.GraphQLArgumentUtils.createValidatedDataFetcher
import io.github.salomax.neotool.graphql.payload.GraphQLPayload
import io.github.salomax.neotool.graphql.payload.GraphQLPayloadFactory
import io.github.salomax.neotool.security.model.UserEntity
import io.github.salomax.neotool.security.service.AuthenticationService
import io.github.salomax.neotool.security.repo.UserRepository
import jakarta.inject.Singleton
import mu.KotlinLogging

/**
 * GraphQL resolver for authentication operations.
 * @see io.github.salomax.neotool.security.config.JwtConfig
 */
@Singleton
class AuthResolver(
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

