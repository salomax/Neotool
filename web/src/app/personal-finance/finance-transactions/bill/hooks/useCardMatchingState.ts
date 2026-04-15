import { useCallback, useEffect, useMemo, useState } from 'react';
import { useMutation } from '@apollo/client/react';
import { useTheme } from '@mui/material/styles';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../../i18n';
import { CREATE_FINANCIAL_ACCOUNT } from '@/lib/graphql/operations/banking/mutations';
import { type FinancialAccount } from '@/lib/graphql/types/__generated__/graphql';
import { extractErrorMessage } from '@/shared/utils/error';
import {
  buildDraftCardName,
  getDayOfMonthFromDateInput,
  toIssuerKey,
} from '../billImportUtils';
import {
  INITIAL_SHARED_CARD_TEMPLATE,
  type BillDateOverrides,
  type BillStep,
  type CardAccountOption,
  type CardResolution,
  type DraftBucketSummary,
  type DraftCardResolution,
  type FlatTxAssignments,
  type FlatTxInclusions,
  type FlatTxSelections,
  type ReviewCard,
  type SharedCardTemplate,
} from '../billImportTypes';

interface UseCardMatchingStateOptions {
  reviewCards: ReviewCard[];
  flatSourceCard: ReviewCard | null;
  isFlatAssignmentMode: boolean;
  cardAccountOptions: CardAccountOption[];
  cardAccountOptionsById: Map<string, CardAccountOption>;
  issuerOptions: { id: string; name: string }[];
  step: BillStep;
  onNavigateToTxReview: () => void;
}

export function useCardMatchingState({
  reviewCards,
  flatSourceCard,
  isFlatAssignmentMode,
  cardAccountOptions,
  cardAccountOptionsById,
  issuerOptions,
  step,
  onNavigateToTxReview,
}: UseCardMatchingStateOptions) {
  const { t } = useTranslation(personalFinanceTranslations);
  const theme = useTheme();

  const [cardResolutions, setCardResolutions] = useState<CardResolution[]>([]);
  const [draftCards, setDraftCards] = useState<DraftCardResolution[]>([]);
  const [flatTxAssignments, setFlatTxAssignments] = useState<FlatTxAssignments>({});
  const [flatTxSelections, setFlatTxSelections] = useState<FlatTxSelections>({});
  const [flatTxInclusions, setFlatTxInclusions] = useState<FlatTxInclusions>({});
  const [assignmentLastFour, setAssignmentLastFour] = useState('');
  const [sharedCardTemplate, setSharedCardTemplate] = useState<SharedCardTemplate>(INITIAL_SHARED_CARD_TEMPLATE);
  const [billDateOverrides, setBillDateOverrides] = useState<BillDateOverrides>({ closingDate: '', dueDate: '' });
  const [templateError, setTemplateError] = useState<string | null>(null);

  const defaultDraftCardBaseName = useMemo(
    () => t('financeTransactions.billImport.cardMatchingStep.defaultBaseName'),
    [t],
  );

  const [createFinancialAccount, { loading: creatingTemplateCards }] = useMutation<{
    createFinancialAccount: FinancialAccount;
  }>(CREATE_FINANCIAL_ACCOUNT);

  // ── Derived state ──────────────────────────────────────────────────────────

  const selectedIssuerKey = useMemo(() => {
    for (const resolution of cardResolutions) {
      if (!resolution?.financialAccountId) continue;
      const selected = cardAccountOptionsById.get(resolution.financialAccountId);
      if (selected?.issuerKey) return selected.issuerKey;
    }
    for (const draft of draftCards) {
      if (!draft?.financialAccountId) continue;
      const selected = cardAccountOptionsById.get(draft.financialAccountId);
      if (selected?.issuerKey) return selected.issuerKey;
    }
    if (sharedCardTemplate.issuerId && sharedCardTemplate.issuerId !== 'custom') {
      return toIssuerKey(sharedCardTemplate.issuerId, undefined);
    }
    if (sharedCardTemplate.issuerId === 'custom' && sharedCardTemplate.issuerName.trim()) {
      return toIssuerKey(undefined, sharedCardTemplate.issuerName);
    }
    return null;
  }, [cardResolutions, draftCards, cardAccountOptionsById, sharedCardTemplate]);

  const selectedIssuerDefaults = useMemo(() => {
    for (const resolution of cardResolutions) {
      if (!resolution?.financialAccountId) continue;
      const selected = cardAccountOptionsById.get(resolution.financialAccountId);
      if (!selected) continue;
      if (selected.issuerId) return { issuerId: selected.issuerId, issuerName: undefined as string | undefined };
      if (selected.issuerName) return { issuerId: undefined as string | undefined, issuerName: selected.issuerName };
    }
    for (const draft of draftCards) {
      if (!draft?.financialAccountId) continue;
      const selected = cardAccountOptionsById.get(draft.financialAccountId);
      if (!selected) continue;
      if (selected.issuerId) return { issuerId: selected.issuerId, issuerName: undefined as string | undefined };
      if (selected.issuerName) return { issuerId: undefined as string | undefined, issuerName: selected.issuerName };
    }
    if (sharedCardTemplate.issuerId && sharedCardTemplate.issuerId !== 'custom') {
      return { issuerId: sharedCardTemplate.issuerId, issuerName: undefined as string | undefined };
    }
    if (sharedCardTemplate.issuerId === 'custom' && sharedCardTemplate.issuerName.trim()) {
      return { issuerId: undefined as string | undefined, issuerName: sharedCardTemplate.issuerName.trim() };
    }
    return { issuerId: undefined as string | undefined, issuerName: undefined as string | undefined };
  }, [cardResolutions, draftCards, cardAccountOptionsById, sharedCardTemplate]);

  const duplicateDraftLastFourDigits = useMemo(() => {
    const counts = new Map<string, number>();
    draftCards.forEach((draft) => {
      const normalized = draft.lastFourDigits.trim();
      if (normalized.length !== 4) return;
      counts.set(normalized, (counts.get(normalized) ?? 0) + 1);
    });
    return new Set(
      Array.from(counts.entries())
        .filter(([, count]) => count > 1)
        .map(([lastFour]) => lastFour),
    );
  }, [draftCards]);

  const selectedTemplateIssuerLabel = useMemo(() => {
    if (sharedCardTemplate.issuerId === 'custom') return sharedCardTemplate.issuerName.trim();
    return issuerOptions.find((issuer) => issuer.id === sharedCardTemplate.issuerId)?.name ?? '';
  }, [sharedCardTemplate.issuerId, sharedCardTemplate.issuerName, issuerOptions]);

  const templateRequiresBillingCycle = sharedCardTemplate.subtype === 'CREDIT_CARD';
  const templateHasIssuer =
    (sharedCardTemplate.issuerId === 'custom' && sharedCardTemplate.issuerName.trim().length > 0) ||
    (sharedCardTemplate.issuerId !== '' && sharedCardTemplate.issuerId !== 'custom');
  const templateReadyForCreation = templateHasIssuer && !!sharedCardTemplate.subtype;

  const templatePreviewName = buildDraftCardName(
    sharedCardTemplate,
    selectedTemplateIssuerLabel,
    'XXXX',
    defaultDraftCardBaseName,
  );

  const allFlatTransactionsAssigned =
    !!flatSourceCard &&
    flatSourceCard.transactions.length > 0 &&
    flatSourceCard.transactions.some((_, txIndex) => !!flatTxAssignments[txIndex]);

  const allDraftBucketsReady =
    draftCards.length > 0 &&
    draftCards.every(
      (draft) =>
        draft.lastFourDigits.trim().length === 4 ||
        (draft.allowMissingLastFour && draft.lastFourDigits.trim().length === 0),
    ) &&
    duplicateDraftLastFourDigits.size === 0;

  const draftBucketSummaries = useMemo<DraftBucketSummary[]>(() => {
    if (!flatSourceCard) return [];
    return draftCards.map((draft) => {
      const assignedTransactions = flatSourceCard.transactions.filter(
        (_, txIndex) => flatTxAssignments[txIndex] === draft.draftCardId,
      );
      const total = assignedTransactions.reduce(
        (sum, tx) => sum + (tx.creditDebitType === 'CREDIT' ? -tx.amount : tx.amount),
        0,
      );
      return {
        draftCardId: draft.draftCardId,
        label: draft.lastFourDigits.trim()
          ? `•••• ${draft.lastFourDigits.trim()}`
          : t('financeTransactions.billImport.cardMatchingStep.unassignedLastFour'),
        count: assignedTransactions.length,
        total,
      };
    });
  }, [draftCards, flatSourceCard, flatTxAssignments, t]);

  const hasDraftBucketsNeedingCreation = useMemo(() => {
    if (!isFlatAssignmentMode || !flatSourceCard) return false;
    return draftCards.some((draft) => {
      if (draft.financialAccountId) return false;
      const bucket = draftBucketSummaries.find((s) => s.draftCardId === draft.draftCardId);
      return !!bucket && bucket.count > 0;
    });
  }, [draftBucketSummaries, draftCards, flatSourceCard, isFlatAssignmentMode]);

  const allCardsResolved = isFlatAssignmentMode
    ? allDraftBucketsReady &&
      allFlatTransactionsAssigned &&
      (!hasDraftBucketsNeedingCreation || templateReadyForCreation)
    : cardResolutions.length > 0 && cardResolutions.every((r) => r.financialAccountId !== null);

  const canReviewTransactions = isFlatAssignmentMode
    ? allDraftBucketsReady && allFlatTransactionsAssigned
    : allCardsResolved;

  // ── Effects: clear mismatched issuer selections ────────────────────────────

  useEffect(() => {
    if (step !== 'card-matching' || !selectedIssuerKey) return;
    setCardResolutions((prev) => {
      let changed = false;
      const next = prev.map((resolution) => {
        if (!resolution.financialAccountId) return resolution;
        const selected = cardAccountOptionsById.get(resolution.financialAccountId);
        if (selected?.issuerKey === selectedIssuerKey) return resolution;
        changed = true;
        return { ...resolution, financialAccountId: null };
      });
      return changed ? next : prev;
    });
    if (draftCards.length === 0) return;
    setDraftCards((prev) => {
      let changed = false;
      const next = prev.map((draft) => {
        if (!draft.financialAccountId) return draft;
        const selected = cardAccountOptionsById.get(draft.financialAccountId);
        if (selected?.issuerKey === selectedIssuerKey) return draft;
        changed = true;
        return { ...draft, financialAccountId: null };
      });
      return changed ? next : prev;
    });
  }, [step, selectedIssuerKey, cardAccountOptionsById, draftCards.length]);

  // ── Callbacks ──────────────────────────────────────────────────────────────

  const handleTemplateFieldChange = useCallback(
    (field: keyof SharedCardTemplate, value: string) => {
      setSharedCardTemplate((prev) => ({ ...prev, [field]: value }));
      setTemplateError(null);
    },
    [],
  );

  const findExactAccountMatches = useCallback(
    (lastFourDigits: string) => {
      const exactMatches = cardAccountOptions.filter((option) =>
        option.maskedNumber?.endsWith(lastFourDigits),
      );
      if (!selectedIssuerKey) return exactMatches;
      return exactMatches.filter((option) => option.issuerKey === selectedIssuerKey);
    },
    [cardAccountOptions, selectedIssuerKey],
  );

  const getAvailableOptionsForCard = useCallback(
    (card: ReviewCard) => {
      const baseOptions: CardAccountOption[] = card.matchAmbiguous
        ? card.matchCandidates.map((candidate) => {
            const known = cardAccountOptionsById.get(candidate.id);
            return (
              known ?? {
                id: candidate.id,
                name: candidate.name,
                issuerId: null,
                issuerName: null,
                issuerKey: null,
                maskedNumber: null,
              }
            );
          })
        : cardAccountOptions;
      if (!selectedIssuerKey) return baseOptions;
      return baseOptions.filter((option) => option.issuerKey === selectedIssuerKey);
    },
    [cardAccountOptions, cardAccountOptionsById, selectedIssuerKey],
  );

  const assignSelectedTransactionsToBucket = useCallback(
    (lastFourDigits: string | null) => {
      if (!flatSourceCard) return;
      const selectedTransactionIndexes = flatSourceCard.transactions
        .map((_, txIndex) => txIndex)
        .filter((txIndex) => flatTxSelections[txIndex]);
      if (selectedTransactionIndexes.length === 0) return;

      const normalizedLastFour = (lastFourDigits ?? '').replace(/\D/g, '').slice(0, 4);
      if (normalizedLastFour.length !== 4 && lastFourDigits !== null) return;

      const bucketKey = normalizedLastFour || '__NO_LAST4__';
      const existingDraft = draftCards.find((draft) =>
        normalizedLastFour
          ? draft.lastFourDigits.trim() === normalizedLastFour
          : draft.allowMissingLastFour && draft.lastFourDigits.trim().length === 0,
      );

      let nextDraftCardId = existingDraft?.draftCardId;
      let nextDraftCards = draftCards;

      if (!nextDraftCardId) {
        nextDraftCardId = `draft-${crypto.randomUUID()}`;
        nextDraftCards = [
          ...draftCards,
          {
            draftCardId: nextDraftCardId,
            lastFourDigits: normalizedLastFour,
            financialAccountId:
              normalizedLastFour.length === 4 && findExactAccountMatches(normalizedLastFour).length === 1
                ? findExactAccountMatches(normalizedLastFour)[0]!.id
                : null,
            allowMissingLastFour: bucketKey === '__NO_LAST4__',
          },
        ];
        setDraftCards(nextDraftCards);
      }

      setFlatTxAssignments((prev) => {
        const next = { ...prev };
        selectedTransactionIndexes.forEach((txIndex) => { next[txIndex] = nextDraftCardId!; });
        return next;
      });
      setFlatTxSelections((prev) => {
        const next = { ...prev };
        selectedTransactionIndexes.forEach((txIndex) => { next[txIndex] = false; });
        return next;
      });
      if (normalizedLastFour.length === 4) {
        setAssignmentLastFour(normalizedLastFour);
      }
      setTemplateError(null);
    },
    [draftCards, findExactAccountMatches, flatSourceCard, flatTxSelections],
  );

  const materializeFlatAssignmentBuckets = useCallback(async (): Promise<boolean> => {
    const draftsToResolve = draftCards.filter(
      (draft) =>
        !draft.financialAccountId &&
        draftBucketSummaries.some((bucket) => bucket.draftCardId === draft.draftCardId && bucket.count > 0),
    );
    if (draftsToResolve.length === 0) return true;

    if (!templateReadyForCreation) {
      setTemplateError(t('financeTransactions.billImport.cardMatchingStep.templateInvalid'));
      return false;
    }
    if (duplicateDraftLastFourDigits.size > 0) {
      setTemplateError(t('financeTransactions.billImport.cardMatchingStep.duplicateLastFour'));
      return false;
    }

    setTemplateError(null);

    try {
      const createdAccounts: Array<{ draftCardId: string; account: FinancialAccount }> = [];
      const resolvedMatches: Array<{ draftCardId: string; financialAccountId: string }> = [];

      for (const draft of draftsToResolve) {
        const trimmedLastFour = draft.lastFourDigits.trim();
        if (trimmedLastFour.length === 4) {
          const exactMatches = findExactAccountMatches(trimmedLastFour);
          if (exactMatches.length === 1) {
            resolvedMatches.push({ draftCardId: draft.draftCardId, financialAccountId: exactMatches[0]!.id });
            continue;
          }
          if (exactMatches.length > 1) {
            setTemplateError(
              t('financeTransactions.billImport.cardMatchingStep.ambiguousExistingCard', { lastFourDigits: trimmedLastFour }),
            );
            return false;
          }
        }

        const input: Record<string, unknown> = {
          type:
            sharedCardTemplate.subtype === 'CREDIT_CARD' || sharedCardTemplate.subtype === 'CHARGE_CARD'
              ? 'CREDIT'
              : 'PAYMENT',
          subtype: sharedCardTemplate.subtype,
          name: buildDraftCardName(
            sharedCardTemplate,
            selectedTemplateIssuerLabel,
            trimmedLastFour || t('financeTransactions.billImport.cardMatchingStep.noLastFourPlaceholder'),
            defaultDraftCardBaseName,
          ),
          currencyCode: 'BRL',
          themeColor: theme.palette.primary.main,
          includeInNetWorth: true,
          includeInDashboard: true,
          ...(sharedCardTemplate.issuerId === 'custom'
            ? { issuerName: sharedCardTemplate.issuerName.trim() }
            : { issuerId: sharedCardTemplate.issuerId }),
          cardData: {
            cardNetwork: sharedCardTemplate.cardNetwork || undefined,
            cardLevel:
              sharedCardTemplate.subtype === 'CREDIT_CARD' && sharedCardTemplate.cardLevel
                ? sharedCardTemplate.cardLevel
                : undefined,
            maskedNumber: trimmedLastFour || undefined,
            creditLimit: sharedCardTemplate.creditLimit ? Number(sharedCardTemplate.creditLimit) : undefined,
            closingDay: sharedCardTemplate.closingDay ? Number(sharedCardTemplate.closingDay) : undefined,
            dueDay: sharedCardTemplate.dueDay ? Number(sharedCardTemplate.dueDay) : undefined,
          },
        };

        const response = await createFinancialAccount({ variables: { input } });
        const created = response.data?.createFinancialAccount;
        if (!created) throw new Error('createFinancialAccount returned no account');
        createdAccounts.push({ draftCardId: draft.draftCardId, account: created });
      }

      if (createdAccounts.length > 0) {
        // The orchestrator will pick up these new accounts via useInlineCardCreation
        // We need to notify it — handled by returning the accounts list
      }

      setDraftCards((prev) =>
        prev.map((draft) => {
          const resolved = resolvedMatches.find((item) => item.draftCardId === draft.draftCardId);
          if (resolved) return { ...draft, financialAccountId: resolved.financialAccountId };
          const created = createdAccounts.find((item) => item.draftCardId === draft.draftCardId);
          if (created) return { ...draft, financialAccountId: created.account.id };
          return draft;
        }),
      );

      return true;
    } catch (error) {
      setTemplateError(
        extractErrorMessage(error, t('financeTransactions.billImport.cardMatchingStep.templateCreateFailed')),
      );
      return false;
    }
  }, [
    createFinancialAccount,
    defaultDraftCardBaseName,
    draftBucketSummaries,
    draftCards,
    duplicateDraftLastFourDigits.size,
    findExactAccountMatches,
    selectedTemplateIssuerLabel,
    sharedCardTemplate,
    theme,
    t,
    templateReadyForCreation,
  ]);

  const handleFlatReview = useCallback(async () => {
    if (!canReviewTransactions) return;
    if (allCardsResolved) {
      const ok = await materializeFlatAssignmentBuckets();
      if (ok) onNavigateToTxReview();
      return;
    }
    onNavigateToTxReview();
  }, [allCardsResolved, canReviewTransactions, materializeFlatAssignmentBuckets, onNavigateToTxReview]);

  // ── Init from job data ─────────────────────────────────────────────────────

  const initFromJob = useCallback(
    (job: {
      reviewPayload?: {
        reviewMode?: string;
        statementPeriod?: { from?: string; to?: string };
        cards: ReviewCard[];
      };
    }) => {
      const cards = job.reviewPayload?.cards ?? [];
      const isFlatMode = job.reviewPayload?.reviewMode === 'TX_CARD_ASSIGNMENT';

      setCardResolutions(
        cards.map((c) => ({
          reviewCardId: c.reviewCardId,
          financialAccountId: c.matchAmbiguous ? null : (c.matchedFinancialAccountId ?? null),
        })),
      );

      if (isFlatMode) {
        const flatSelections: FlatTxSelections = {};
        const flatInclusions: FlatTxInclusions = {};
        cards[0]?.transactions.forEach((_, txIndex) => {
          flatSelections[txIndex] = false;
          flatInclusions[txIndex] = true;
        });
        setDraftCards([]);
        setFlatTxAssignments({});
        setFlatTxSelections(flatSelections);
        setFlatTxInclusions(flatInclusions);
        setSharedCardTemplate((prev) => ({
          ...prev,
          subtype: prev.subtype || 'CREDIT_CARD',
          closingDay: String(getDayOfMonthFromDateInput(cards[0]?.closingDate) ?? prev.closingDay),
          dueDay: String(getDayOfMonthFromDateInput(cards[0]?.dueDate) ?? prev.dueDay),
        }));
        setTemplateError(null);
      } else {
        setDraftCards([]);
        setFlatTxAssignments({});
        setFlatTxSelections({});
        setFlatTxInclusions({});
      }

      setBillDateOverrides({
        closingDate:
          cards.find((card) => !!card.closingDate)?.closingDate ??
          job.reviewPayload?.statementPeriod?.to ??
          '',
        dueDate: cards.find((card) => !!card.dueDate)?.dueDate ?? '',
      });
    },
    [],
  );

  const reset = useCallback(() => {
    setCardResolutions([]);
    setDraftCards([]);
    setFlatTxAssignments({});
    setFlatTxSelections({});
    setFlatTxInclusions({});
    setAssignmentLastFour('');
    setSharedCardTemplate(INITIAL_SHARED_CARD_TEMPLATE);
    setBillDateOverrides({ closingDate: '', dueDate: '' });
    setTemplateError(null);
  }, []);

  return {
    // State
    cardResolutions,
    setCardResolutions,
    draftCards,
    setDraftCards,
    flatTxAssignments,
    setFlatTxAssignments,
    flatTxSelections,
    setFlatTxSelections,
    flatTxInclusions,
    setFlatTxInclusions,
    assignmentLastFour,
    setAssignmentLastFour,
    sharedCardTemplate,
    billDateOverrides,
    setBillDateOverrides,
    templateError,
    setTemplateError,
    creatingTemplateCards,
    // Derived
    selectedIssuerKey,
    selectedIssuerDefaults,
    duplicateDraftLastFourDigits,
    selectedTemplateIssuerLabel,
    templateRequiresBillingCycle,
    templateHasIssuer,
    templateReadyForCreation,
    templatePreviewName,
    allFlatTransactionsAssigned,
    allDraftBucketsReady,
    draftBucketSummaries,
    hasDraftBucketsNeedingCreation,
    allCardsResolved,
    canReviewTransactions,
    defaultDraftCardBaseName,
    // Callbacks
    handleTemplateFieldChange,
    findExactAccountMatches,
    getAvailableOptionsForCard,
    assignSelectedTransactionsToBucket,
    materializeFlatAssignmentBuckets,
    handleFlatReview,
    initFromJob,
    reset,
  };
}
