'use client';

import { CategoryApplicableTo, PatternType } from '@/lib/graphql/types/__generated__/graphql';

export interface TransactionRule {
  id: string;
  pattern: string;
  patternType: PatternType;
  applicableTo: CategoryApplicableTo;
  priority: number;
  isSystem: boolean;
  isDisabledForAccount: boolean;
  isImplicit: boolean;
  matchCount: number;
  createdAt: string;
  descriptionOverride?: string | null;
  tagsOverride?: string[] | null;
  categoryId?: string | null;
  category?: {
    id: string;
    name: string;
    icon?: string | null;
    color?: string | null;
    applicableTo: CategoryApplicableTo;
  } | null;
  merchantId?: string | null;
  merchant?: {
    id: string;
    name: string;
    domain?: string | null;
    logoAsset?: { id: string; publicUrl?: string | null } | null;
  } | null;
}

