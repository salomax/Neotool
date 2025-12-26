package io.github.salomax.neotool.assets.domain

/**
 * Type of resource that an asset is attached to.
 *
 * Defines the context and purpose of an uploaded asset within the application.
 * The client service determines specific validation rules and storage policies.
 *
 * This is intentionally simple - the client defines what type of asset it is.
 * Validation rules (size, MIME types) are configured at the service level,
 * not hardcoded per resource type.
 */
enum class AssetResourceType {
    /**
     * User profile image/avatar.
     * Namespace: user-profiles
     */
    PROFILE_IMAGE,

    /**
     * User profile cover/banner image.
     * Namespace: user-profiles
     */
    COVER_IMAGE,

    /**
     * Group/organization logo.
     * Namespace: group-assets
     */
    GROUP_LOGO,

    /**
     * Group/organization banner image.
     * Namespace: group-assets
     */
    GROUP_BANNER,

    /**
     * Generic attachment (documents, files, etc.).
     * Namespace: attachments
     *
     * Client specifies MIME type and service validates against configured rules.
     * Can be used for PDFs, documents, images, or any file type the service allows.
     */
    ATTACHMENT,
}
