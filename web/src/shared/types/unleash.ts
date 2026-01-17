/**
 * Shared types for Unleash feature flags
 */

/**
 * Context used for feature flag evaluation
 * Supports targeting based on user, tenant, role, plan, region, and environment
 */
export interface UnleashEvaluationContext {
  /** User ID for user-based targeting */
  userId?: string;
  /** Tenant ID for multi-tenancy support */
  tenantId?: string;
  /** User role for role-based access control */
  role?: string;
  /** Subscription plan for plan-based features */
  plan?: string;
  /** Geographic region for regional rollouts */
  region?: string;
  /** Environment (development, staging, production) */
  environment?: string;
}

/**
 * Result of feature flag evaluation with metadata
 */
export interface FeatureFlagEvaluationResult {
  /** Whether the feature flag is enabled */
  enabled: boolean;
  /** Whether the evaluation is still in progress */
  loading: boolean;
  /** Error that occurred during evaluation, if any */
  error: Error | null;
}
