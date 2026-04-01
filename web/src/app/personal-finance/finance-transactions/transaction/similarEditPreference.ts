import { RuleApplicationMode } from '@/lib/graphql/types/__generated__/graphql';

export type SimilarEditBehavior = 'auto' | 'prompt' | 'skip';

export function resolveSimilarEditBehavior(
  ruleApplicationMode: RuleApplicationMode,
  supportsFuturePropagation: boolean,
): SimilarEditBehavior {
  if (!supportsFuturePropagation) {
    return 'skip';
  }

  switch (ruleApplicationMode) {
    case RuleApplicationMode.Never:
      return 'skip';
    case RuleApplicationMode.Always:
      return 'auto';
    case RuleApplicationMode.AlwaysPrompt:
    default:
      return 'prompt';
  }
}
