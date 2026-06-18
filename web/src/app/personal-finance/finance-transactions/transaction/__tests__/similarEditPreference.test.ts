import { describe, expect, it } from 'vitest';
import { RuleCreationMode } from '@/lib/graphql/types/__generated__/graphql';
import { resolveSimilarEditBehavior } from '../similarEditPreference';

describe('resolveSimilarEditBehavior', () => {
  it('skips when the backend does not support future propagation', () => {
    expect(resolveSimilarEditBehavior(RuleCreationMode.AlwaysPrompt, false)).toBe('skip');
  });

  it('skips when user preference is never', () => {
    expect(resolveSimilarEditBehavior(RuleCreationMode.Never, true)).toBe('skip');
  });

  it('auto applies when user preference is always', () => {
    expect(resolveSimilarEditBehavior(RuleCreationMode.Always, true)).toBe('auto');
  });

  it('prompts when user preference is always prompt', () => {
    expect(resolveSimilarEditBehavior(RuleCreationMode.AlwaysPrompt, true)).toBe('prompt');
  });
});
