/**
 * Repository and documentation constants
 */
export const REPO_CONFIG = {
  /** GitHub repository URL */
  githubUrl: "https://github.com/salomax/neotool",
  /** Repository name (used in clone commands) */
  repoName: "neotool",
  /** Full repository name for display purposes */
  fullRepoName: "salomax/neotool",
} as const;

/**
 * Application configuration constants
 * Reads from environment variables with fallbacks to defaults
 */
export const APP_CONFIG = {
  /** Application name */
  name: "Neotool",
  /** Application version */
  version: "1.0.0",
  /** Required Node.js version (for display) */
  nodeVersion: "18.x",
  /** Node.js version short (for Docker tags, CI configs) */
  nodeVersionShort: "18",
  /** Docker image name */
  dockerImageName: "neotool-web",
  /** Development server URL */
  devUrl: "http://localhost:3000",
  /** API server URL - reads from NEXT_PUBLIC_API_URL env var or defaults to localhost:8080 */
  apiUrl: process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080",
  /** GraphQL server URL - reads from NEXT_PUBLIC_GRAPHQL_URL env var or defaults to localhost:4000/graphql */
  graphqlUrl: process.env.NEXT_PUBLIC_GRAPHQL_URL || "http://localhost:4000/graphql",
} as const;

