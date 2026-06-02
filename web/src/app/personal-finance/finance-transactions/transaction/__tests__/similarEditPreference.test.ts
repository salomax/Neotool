import { describe, expect, it } from 'vitest';
import { RuleApplicationMode } from '@/lib/graphql/types/__generated__/graphql';
import { resolveSimilarEditBehavior } from '../similarEditPreference';

describe('resolveSimilarEditBehavior', () => {
  it('skips when the backend does not support future propagation', () => {
    expect(resolveSimilarEditBehavior(RuleApplicationMode.AlwaysPrompt, false)).toBe('skip');
  });

  it('skips when user preference is never', () => {
    expect(resolveSimilarEditBehavior(RuleApplicationMode.Never, true)).toBe('skip');
  });

  it('auto applies when user preference is always', () => {
    expect(resolveSimilarEditBehavior(RuleApplicationMode.Always, true)).toBe('auto');
  });

  it('prompts when user preference is always prompt', () => {
    expect(resolveSimilarEditBehavior(RuleApplicationMode.AlwaysPrompt, true)).toBe('prompt');
  });
});
