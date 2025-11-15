package io.github.salomax.neotool.security.model

import io.github.salomax.neotool.entity.BaseEntity
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "users", schema = "security")
open class UserEntity(
    @Id
    @Column(columnDefinition = "uuid")
    override val id: UUID = UUID.randomUUID(),

    @Column(nullable = false, unique = true)
    open val email: String,

    @Column(name = "display_name")
    open var displayName: String? = null,

    @Column(name = "password_hash")
    open var passwordHash: String? = null,

    @Column(name = "remember_me_token")
    open var rememberMeToken: String? = null,

    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now()
) : BaseEntity<UUID>(id)

@Entity
@Table(name = "roles", schema = "security")
open class RoleEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    override val id: Int? = null,

    @Column(nullable = false, unique = true)
    open var name: String
) : BaseEntity<Int?>(id)

@Entity
@Table(name = "permissions", schema = "security")
open class PermissionEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    override val id: Int? = null,

    @Column(nullable = false, unique = true)
    open var name: String
) : BaseEntity<Int?>(id)
