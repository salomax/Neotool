/**
 * Test helper utilities for common testing patterns.
 * 
 * These helpers address common issues in tests:
 * - Multiple element rendering (React StrictMode, test setup)
 * - Apollo Client mocking
 * - Storage mocking (localStorage/sessionStorage)
 * - Common query patterns
 */

export * from './test-utils';
export * from './query-helpers';
export * from './apollo-mock-helpers';
export * from './storage-helpers';
