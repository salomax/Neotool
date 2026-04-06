'use client';

import React from 'react';
import {
  ASSET_GROUP_ORDER,
  type AssetGroupKey,
  type InvestmentHoldingGroup,
} from './investmentTableHelpers';

const VALID_ASSET_GROUP_KEYS = new Set<AssetGroupKey>(ASSET_GROUP_ORDER);
const CARDS_STORAGE_PREFIX = 'pf-investments-card-state';

type PersistedInvestmentCards = {
  expandedGroups: AssetGroupKey[];
  cashExpanded: boolean;
};

function investmentsCardStorageKey(selectedAccountId: string | null): string {
  return `${CARDS_STORAGE_PREFIX}:${selectedAccountId ?? 'all'}`;
}

function readPersistedInvestmentCards(selectedAccountId: string | null): {
  expanded: Set<AssetGroupKey> | null;
  cashExpanded: boolean | null;
} {
  if (typeof window === 'undefined') return { expanded: null, cashExpanded: null };

  try {
    const raw = window.localStorage.getItem(investmentsCardStorageKey(selectedAccountId));
    if (!raw) return { expanded: null, cashExpanded: null };

    const parsed = JSON.parse(raw) as Partial<PersistedInvestmentCards>;
    if (parsed == null || typeof parsed !== 'object') return { expanded: null, cashExpanded: null };

    let expanded: Set<AssetGroupKey> | null = null;
    if (Array.isArray(parsed.expandedGroups)) {
      const keys = parsed.expandedGroups.filter(
        (key): key is AssetGroupKey => typeof key === 'string' && VALID_ASSET_GROUP_KEYS.has(key as AssetGroupKey),
      );
      expanded = new Set(keys);
    }

    return {
      expanded,
      cashExpanded: typeof parsed.cashExpanded === 'boolean' ? parsed.cashExpanded : null,
    };
  } catch {
    return { expanded: null, cashExpanded: null };
  }
}

interface UseInvestmentCardExpansionOptions {
  selectedAccountId: string | null;
  groups: InvestmentHoldingGroup[];
  hasSearchQuery: boolean;
  loading: boolean;
  hasError: boolean;
}

export function useInvestmentCardExpansion({
  selectedAccountId,
  groups,
  hasSearchQuery,
  loading,
  hasError,
}: UseInvestmentCardExpansionOptions) {
  const [expandedGroups, setExpandedGroups] = React.useState<Set<AssetGroupKey>>(() => new Set());
  const [cashExpanded, setCashExpanded] = React.useState(true);
  const didInitExpansionRef = React.useRef(false);
  const ignoreNextCardPersistRef = React.useRef(false);

  const applyPersistedCardState = React.useCallback((accountId: string | null) => {
    const { expanded, cashExpanded: storedCash } = readPersistedInvestmentCards(accountId);

    if (expanded !== null) {
      setExpandedGroups(expanded);
      didInitExpansionRef.current = true;
    } else {
      setExpandedGroups(new Set());
      didInitExpansionRef.current = false;
    }

    setCashExpanded(storedCash ?? true);
  }, []);

  const toggleGroup = React.useCallback((groupKey: AssetGroupKey, expanded: boolean) => {
    setExpandedGroups((current) => {
      const next = new Set(current);
      if (expanded) next.add(groupKey);
      else next.delete(groupKey);
      return next;
    });
  }, []);

  React.useEffect(() => {
    if (hasSearchQuery) return;

    ignoreNextCardPersistRef.current = true;
    applyPersistedCardState(selectedAccountId);
  }, [applyPersistedCardState, hasSearchQuery, selectedAccountId]);

  React.useEffect(() => {
    if (hasSearchQuery) return;

    if (ignoreNextCardPersistRef.current) {
      ignoreNextCardPersistRef.current = false;
      return;
    }

    try {
      const payload: PersistedInvestmentCards = {
        expandedGroups: Array.from(expandedGroups),
        cashExpanded,
      };
      window.localStorage.setItem(investmentsCardStorageKey(selectedAccountId), JSON.stringify(payload));
    } catch {
      // Ignore storage failures such as private mode or quota exhaustion.
    }
  }, [cashExpanded, expandedGroups, hasSearchQuery, selectedAccountId]);

  React.useEffect(() => {
    if (loading || hasError) return;

    if (groups.length === 0) {
      if (expandedGroups.size > 0) setExpandedGroups(new Set());
      didInitExpansionRef.current = false;
      return;
    }

    if (hasSearchQuery) {
      const nextExpandedGroups = new Set(groups.map((group) => group.key));
      const isSame =
        nextExpandedGroups.size === expandedGroups.size &&
        Array.from(nextExpandedGroups).every((key) => expandedGroups.has(key));

      if (!isSame) setExpandedGroups(nextExpandedGroups);
      didInitExpansionRef.current = true;
      return;
    }

    const firstNonEmpty = groups[0]?.key;

    if (!didInitExpansionRef.current) {
      if (firstNonEmpty) setExpandedGroups(new Set([firstNonEmpty]));
      didInitExpansionRef.current = true;
      return;
    }

    if (expandedGroups.size === 0) return;

    const availableKeys = new Set(groups.map((group) => group.key));
    const nextExpandedGroups = new Set(Array.from(expandedGroups).filter((key) => availableKeys.has(key)));

    if (nextExpandedGroups.size !== expandedGroups.size) {
      if (nextExpandedGroups.size === 0 && firstNonEmpty) {
        setExpandedGroups(new Set([firstNonEmpty]));
      } else {
        setExpandedGroups(nextExpandedGroups);
      }
    }
  }, [expandedGroups, groups, hasError, hasSearchQuery, loading]);

  return {
    cashExpanded,
    expandedGroups,
    setCashExpanded,
    toggleGroup,
  };
}
