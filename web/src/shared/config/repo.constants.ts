/**
 * Repository and documentation constants
 * 
 * These values are typically set during project setup using the rename-project script.
 * If you need to customize them, update project.config.json and re-run the rename script,
 * or set environment variables: NEXT_PUBLIC_APP_NAME, NEXT_PUBLIC_GITHUB_ORG, NEXT_PUBLIC_GITHUB_REPO
 */
export const REPO_CONFIG = {
  /** GitHub repository URL */
  githubUrl: process.env.NEXT_PUBLIC_GITHUB_URL || "https://github.com/salomax/neotool",
  /** Repository name (used in clone commands) */
  repoName: process.env.NEXT_PUBLIC_REPO_NAME || "neotool",
  /** Full repository name for display purposes */
  fullRepoName: process.env.NEXT_PUBLIC_FULL_REPO_NAME || "salomax/neotool",
} as const;

/**
 * Application configuration constants
 * Reads from environment variables with fallbacks to defaults
 * 
 * These values are typically set during project setup using the rename-project script.
 * To customize, set environment variables or update project.config.json and re-run the rename script.
 */
export const APP_CONFIG = {
  /** Application name */
  name: process.env.NEXT_PUBLIC_APP_NAME || "Neotool",
  /** Application version */
  version: process.env.NEXT_PUBLIC_APP_VERSION || "1.0.0",
  /** Required Node.js version (for display) */
  nodeVersion: process.env.NEXT_PUBLIC_NODE_VERSION || "20.x",
  /** Node.js version short (for Docker tags, CI configs) */
  nodeVersionShort: process.env.NEXT_PUBLIC_NODE_VERSION_SHORT || "20",
  /** Docker image name */
  dockerImageName: process.env.NEXT_PUBLIC_DOCKER_IMAGE_NAME || "neotool-web",
  /** Development server URL */
  devUrl: process.env.NEXT_PUBLIC_DEV_URL || "http://localhost:3000",
  /** API server URL - reads from NEXT_PUBLIC_API_URL env var or defaults to localhost:8080 */
  apiUrl: process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080",
  /** GraphQL server URL - reads from NEXT_PUBLIC_GRAPHQL_URL env var or defaults to localhost:4000/graphql */
  graphqlUrl: process.env.NEXT_PUBLIC_GRAPHQL_URL || "http://localhost:4000/graphql",
} as const;

