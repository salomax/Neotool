import { RuleCreationMode } from '@/lib/graphql/types/__generated__/graphql';

export type SimilarEditBehavior = 'auto' | 'prompt' | 'skip';

export function resolveSimilarEditBehavior(
  ruleCreationMode: RuleCreationMode,
  supportsFuturePropagation: boolean,
): SimilarEditBehavior {
  if (!supportsFuturePropagation) {
    return 'skip';
  }

  switch (ruleCreationMode) {
    case RuleCreationMode.Never:
      return 'skip';
    case RuleCreationMode.Always:
      return 'auto';
    case RuleCreationMode.AlwaysPrompt:
    default:
      return 'prompt';
  }
}
