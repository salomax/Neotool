import { useCallback, useMemo, useState } from 'react';
import { buildCardAccountOption, toIssuerKey } from '../billImportUtils';
import type { CardAccountOption } from '../billImportTypes';

interface UseInlineCardCreationOptions {
  baseCardAccountOptions: CardAccountOption[];
}

export function useInlineCardCreation({ baseCardAccountOptions }: UseInlineCardCreationOptions) {
  const [inlineCreatedAccounts, setInlineCreatedAccounts] = useState<CardAccountOption[]>([]);
  const [createCardForIndex, setCreateCardForIndex] = useState<number | null>(null);
  const [isCreateCardDrawerOpen, setIsCreateCardDrawerOpen] = useState(false);
  const [createDraftCardId, setCreateDraftCardId] = useState<string | null>(null);
  const [createForFlatSelection, setCreateForFlatSelection] = useState(false);

  const effectiveCardAccountOptions = useMemo<CardAccountOption[]>(() => {
    const mergedInline = inlineCreatedAccounts.filter(
      (ia) => !baseCardAccountOptions.some((a) => a.id === ia.id),
    );
    return [...baseCardAccountOptions, ...mergedInline];
  }, [baseCardAccountOptions, inlineCreatedAccounts]);

  const openCreateCardForIndex = useCallback((ci: number) => {
    setCreateCardForIndex(ci);
    setCreateDraftCardId(null);
    setCreateForFlatSelection(false);
    setIsCreateCardDrawerOpen(true);
  }, []);

  const openCreateCardForDraft = useCallback((draftCardId: string) => {
    setCreateDraftCardId(draftCardId);
    setCreateCardForIndex(null);
    setCreateForFlatSelection(false);
    setIsCreateCardDrawerOpen(true);
  }, []);

  const openCreateCardForFlatSelection = useCallback(() => {
    setCreateDraftCardId(null);
    setCreateCardForIndex(null);
    setCreateForFlatSelection(true);
    setIsCreateCardDrawerOpen(true);
  }, []);

  const close = useCallback(() => {
    setIsCreateCardDrawerOpen(false);
    setCreateCardForIndex(null);
    setCreateDraftCardId(null);
    setCreateForFlatSelection(false);
  }, []);

  const addInlineAccount = useCallback((account: {
    id: string;
    name: string;
    issuerId?: string | null;
    issuerName?: string | null;
    issuer?: { id?: string | null; name?: string | null } | null;
    cardData?: { maskedNumber?: string | null; isMultiCard?: boolean | null } | null;
  }) => {
    const option = buildCardAccountOption(account);
    setInlineCreatedAccounts((prev) => {
      const withoutCurrent = prev.filter((existing) => existing.id !== account.id);
      return [...withoutCurrent, option];
    });
  }, []);

  const reset = useCallback(() => {
    setInlineCreatedAccounts([]);
    setCreateCardForIndex(null);
    setIsCreateCardDrawerOpen(false);
    setCreateDraftCardId(null);
    setCreateForFlatSelection(false);
  }, []);

  return {
    inlineCreatedAccounts,
    effectiveCardAccountOptions,
    createCardForIndex,
    isCreateCardDrawerOpen,
    createDraftCardId,
    createForFlatSelection,
    openCreateCardForIndex,
    openCreateCardForDraft,
    openCreateCardForFlatSelection,
    close,
    addInlineAccount,
    reset,
  };
}
