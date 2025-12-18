/**
 * Global teardown for Playwright E2E tests
 * This runs once after all tests
 */

import { FullConfig } from '@playwright/test';

async function globalTeardown(config: FullConfig) {
  console.log('Running global teardown...');
  
  // Cleanup can be added here:
  // - Clean up test data
  // - Reset database state
  // - Close connections
  // - Generate final reports
  
  console.log('Global teardown completed');
}

export default globalTeardown;
