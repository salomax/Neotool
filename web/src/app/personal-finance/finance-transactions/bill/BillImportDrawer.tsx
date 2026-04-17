'use client';

import React, { useCallback, useMemo } from 'react';
import { useMutation, useQuery } from '@apollo/client/react';
import { useTranslation, i18n } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../i18n';
import { CONFIRM_BILL_IMPORT } from '@/lib/graphql/operations/banking/mutations';
import { BILL_IMPORT_JOB, FINANCIAL_ACCOUNTS, ISSUERS, TRANSACTION_CATEGORIES } from '@/lib/graphql/operations/banking/queries';
import { type FinancialAccount, type Issuer, type TransactionCategory } from '@/lib/graphql/types/__generated__/graphql';
import { Drawer } from '@/shared/components/ui/layout/Drawer';
import { CreateFinancialAccountDrawer } from '../account/CreateFinancialAccountDrawer';
import { extractErrorCode, extractErrorMessage } from '@/shared/utils/error';
import { publishTransactionsImported } from '../events';
import {
  buildCardAccountOption,
  getDayOfMonthFromDateInput,
  getMoneyLocale,
  resolveFriendlyImportErrorMessage,
  toIssuerKey,
  txSelectionKey,
} from './billImportUtils';
import {
  CardNetworkValues,
  CardSubtypeValues,
  CardLevelValues,
  POLL_INTERVAL_MS,
  type BillStep,
  type ReviewCard,
  type ReviewCardForTxReview,
} from './billImportTypes';
import { useFileUpload } from './hooks/useFileUpload';
import { useCardMatchingState } from './hooks/useCardMatchingState';
import { useTxReviewState } from './hooks/useTxReviewState';
import { useInlineCardCreation } from './hooks/useInlineCardCreation';
import { UploadStep } from './steps/UploadStep';
import { ProcessingStep } from './steps/ProcessingStep';
import { FlatAssignmentStep } from './steps/FlatAssignmentStep';
import { CardBucketMatchingStep } from './steps/CardBucketMatchingStep';
import { TxReviewStep } from './steps/TxReviewStep';
import { ResultStep } from './steps/ResultStep';
import { ErrorStep } from './steps/ErrorStep';

export interface BillImportDrawerProps {
  open: boolean;
  onClose: () => void;
  onImportCompleted?: () => void;
}

export function BillImportDrawer({ open, onClose, onImportCompleted }: BillImportDrawerProps) {
  const { t } = useTranslation(personalFinanceTranslations);

  const [step, setStep] = React.useState<BillStep>('upload');
  const [jobId, setJobId] = React.useState<string | null>(null);
  const [errorMessage, setErrorMessage] = React.useState<string | null>(null);

  const moneyLocale = getMoneyLocale(i18n.language);

  // ── Queries ────────────────────────────────────────────────────────────────

  const { data: jobData, stopPolling, startPolling } = useQuery<{
    billImportJob: {
      id: string;
      status: string;
      errorCode?: string | null;
      errorMessage?: string | null;
      reviewPayload?: {
        bankName?: string;
        reviewMode?: 'CARD_BUCKET_MATCHING' | 'TX_CARD_ASSIGNMENT';
        statementPeriod?: { from?: string; to?: string };
        cards: ReviewCard[];
      };
    };
  }>(BILL_IMPORT_JOB, {
    variables: { jobId },
    skip: !open || !jobId,
    fetchPolicy: 'network-only',
    pollInterval: step === 'processing' ? POLL_INTERVAL_MS : 0,
  });

  const { data: accountsData, loading: accountsLoading } = useQuery<{ financialAccounts: FinancialAccount[] }>(
    FINANCIAL_ACCOUNTS,
    { skip: !open, errorPolicy: 'all' },
  );

  const { data: issuersData, loading: issuersLoading } = useQuery<{ issuers: Issuer[] }>(ISSUERS, {
    skip: !open,
    errorPolicy: 'all',
  });

  const { data: categoriesData } = useQuery<{ transactionCategories: TransactionCategory[] }>(
    TRANSACTION_CATEGORIES,
    { skip: !open, fetchPolicy: 'cache-first' },
  );

  const allCategories = categoriesData?.transactionCategories ?? [];

  const reviewCards: ReviewCard[] = useMemo(
    () => jobData?.billImportJob?.reviewPayload?.cards ?? [],
    [jobData?.billImportJob?.reviewPayload?.cards],
  );
  const reviewMode = jobData?.billImportJob?.reviewPayload?.reviewMode ?? 'CARD_BUCKET_MATCHING';

  const flatSourceCard = reviewMode === 'TX_CARD_ASSIGNMENT' && reviewCards.length > 0
    ? reviewCards[0]!
    : null;
  const isFlatAssignmentMode = !!flatSourceCard;

  // ── Base card account options (without inline-created) ────────────────────

  const baseCardAccountOptions = useMemo(
    () =>
      (accountsData?.financialAccounts ?? [])
        .filter((a) => a.type === 'CREDIT')
        .map(buildCardAccountOption),
    [accountsData?.financialAccounts],
  );

  // ── Inline card creation ───────────────────────────────────────────────────

  const inlineCardCreation = useInlineCardCreation({ baseCardAccountOptions });
  const {
    effectiveCardAccountOptions: cardAccountOptions,
    createCardForIndex,
    isCreateCardDrawerOpen,
    createDraftCardId,
    createForFlatSelection,
  } = inlineCardCreation;

  const cardAccountOptionsById = useMemo(
    () => new Map(cardAccountOptions.map((a) => [a.id, a])),
    [cardAccountOptions],
  );

  // ── Issuer options ─────────────────────────────────────────────────────────

  const issuerOptions = useMemo(
    () => (issuersData?.issuers ?? []).map((issuer) => ({ id: issuer.id, name: issuer.name })),
    [issuersData?.issuers],
  );

  // ── TX review state ────────────────────────────────────────────────────────

  const txReview = useTxReviewState();

  // ── Card matching state ────────────────────────────────────────────────────

  const handleNavigateToTxReview = useCallback(() => setStep('tx-review'), []);

  const cardMatching = useCardMatchingState({
    reviewCards,
    flatSourceCard,
    isFlatAssignmentMode,
    cardAccountOptions,
    cardAccountOptionsById,
    issuerOptions,
    step,
    onNavigateToTxReview: handleNavigateToTxReview,
  });

  // ── File upload ────────────────────────────────────────────────────────────

  const handleSubmitSuccess = useCallback((id: string) => {
    setJobId(id);
    startPolling(POLL_INTERVAL_MS);
    setStep('processing');
  }, [startPolling]);

  const handleSubmitError = useCallback((message: string) => {
    setErrorMessage(message);
    setStep('error');
  }, []);

  const fileUpload = useFileUpload({
    onSubmitSuccess: handleSubmitSuccess,
    onSubmitError: handleSubmitError,
  });

  // ── Confirm mutation ───────────────────────────────────────────────────────

  const [confirmImport, { loading: confirming }] = useMutation<{
    confirmBillImport: { id: string; status: string; errorCode?: string | null; errorMessage?: string | null };
  }>(CONFIRM_BILL_IMPORT);

  // ── Reset ──────────────────────────────────────────────────────────────────

  const resetAll = useCallback(() => {
    fileUpload.reset();
    cardMatching.reset();
    txReview.reset();
    inlineCardCreation.reset();
    setStep('upload');
    setJobId(null);
    setErrorMessage(null);
  }, [fileUpload, cardMatching, txReview, inlineCardCreation]);

  // Reset when drawer opens
  React.useEffect(() => {
    if (!open) return;
    resetAll();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  const resetToUploadStep = useCallback(() => {
    stopPolling();
    cardMatching.reset();
    txReview.reset();
    fileUpload.resetError();
    setStep('upload');
    setJobId(null);
    setErrorMessage(null);
  }, [stopPolling, cardMatching, txReview, fileUpload]);

  const handleClose = useCallback(() => {
    stopPolling();
    onClose();
  }, [onClose, stopPolling]);

  // ── React to job status changes during polling ─────────────────────────────

  React.useEffect(() => {
    const job = jobData?.billImportJob;
    if (!job) return;
    if (!jobId || job.id !== jobId) return;
    if (step !== 'processing') return;

    const status = job.status;
    if (status === 'AWAITING_REVIEW') {
      stopPolling();
      setStep((currentStep) => {
        if (currentStep !== 'processing') return currentStep;
        cardMatching.initFromJob(job as Parameters<typeof cardMatching.initFromJob>[0]);
        txReview.initFromReviewCards(
          job.reviewPayload?.cards ?? [],
          job.reviewPayload?.reviewMode === 'TX_CARD_ASSIGNMENT',
        );
        return 'card-matching';
      });
    } else if (status === 'FAILED') {
      stopPolling();
      setErrorMessage(resolveFriendlyImportErrorMessage(t, job.errorCode, job.errorMessage));
      setStep('error');
    }
  }, [jobData, jobId, step, stopPolling, t, cardMatching, txReview]);

  // ── Status label ───────────────────────────────────────────────────────────

  const statusLabel = useCallback(
    (status: string) => {
      switch (status) {
        case 'PENDING':
        case 'EXTRACTING':
        case 'LLM_PROCESSING':
        case 'EXTRACTED':
        case 'IMPORTING':
        case 'AWAITING_REVIEW':
        case 'DONE':
        case 'FAILED':
          return t(`financeTransactions.billImport.status.${status}`);
        default:
          return status;
      }
    },
    [t],
  );

  const { selectedIssuerKey, sharedCardTemplate } = cardMatching;

  // ── handleConfirm ─────────────────────────────────────────────────────────

  const handleConfirm = useCallback(async () => {
    if (!jobId || !cardMatching.allCardsResolved) return;

    const confirmCards = isFlatAssignmentMode && flatSourceCard
      ? cardMatching.draftCards
          .map((draft) => ({
            reviewCardId: flatSourceCard.reviewCardId,
            financialAccountId: draft.financialAccountId,
            transactions: flatSourceCard.transactions
              .map((tx, txIndex) => ({ tx, txIndex }))
              .filter(({ txIndex }) => cardMatching.flatTxAssignments[txIndex] === draft.draftCardId)
              .map(({ tx, txIndex }) => {
                const categoryKey = `flat-${txIndex}`;
                const categoryState = txReview.txCategorySelections[categoryKey];
                return {
                  date: tx.date ?? '',
                  description: tx.description,
                  amount: tx.amount,
                  creditDebitType: tx.creditDebitType,
                  include: cardMatching.flatTxInclusions[txIndex] ?? true,
                  isBillPayment: !!tx.isBillPayment,
                  categoryId: categoryState?.categoryId ?? undefined,
                  categorySource: categoryState?.categorySource ?? undefined,
                  installment: tx.installment
                    ? { current: tx.installment.current, total: tx.installment.total }
                    : null,
                };
              }),
          }))
          .filter((draft) => !!draft.financialAccountId && draft.transactions.length > 0)
      : reviewCards.map((card, ci) => ({
          reviewCardId: card.reviewCardId,
          financialAccountId: cardMatching.cardResolutions[ci]!.financialAccountId,
          transactions: card.transactions.map((tx, ti) => {
            const categoryKey = txSelectionKey(card.reviewCardId, ti);
            const categoryState = txReview.txCategorySelections[categoryKey];
            return {
              date: tx.date ?? '',
              description: tx.description,
              amount: tx.amount,
              creditDebitType: tx.creditDebitType,
              include: txReview.txSelections[categoryKey] ?? true,
              isBillPayment: !!tx.isBillPayment,
              categoryId: categoryState?.categoryId ?? undefined,
              categorySource: categoryState?.categorySource ?? undefined,
              installment: tx.installment
                ? { current: tx.installment.current, total: tx.installment.total }
                : null,
            };
          }),
        }));

    try {
      const result = await confirmImport({
        variables: {
          input: {
            jobId,
            closingDate: cardMatching.billDateOverrides.closingDate || undefined,
            dueDate: cardMatching.billDateOverrides.dueDate || undefined,
            cards: confirmCards,
          },
        },
      });
      const status = result.data?.confirmBillImport?.status;
      if (status === 'DONE' || status === 'COMPLETED') {
        setStep('result');
        publishTransactionsImported({ source: 'bill' });
        onImportCompleted?.();
      } else {
        setErrorMessage(
          resolveFriendlyImportErrorMessage(
            t,
            result.data?.confirmBillImport?.errorCode,
            result.data?.confirmBillImport?.errorMessage,
          ),
        );
        setStep('error');
      }
    } catch (err) {
      const defaultMessage = resolveFriendlyImportErrorMessage(t);
      setErrorMessage(extractErrorCode(err) ? extractErrorMessage(err, defaultMessage) : defaultMessage);
      setStep('error');
    }
  }, [
    jobId,
    cardMatching,
    txReview,
    isFlatAssignmentMode,
    flatSourceCard,
    reviewCards,
    confirmImport,
    t,
    onImportCompleted,
  ]);

  // ── Derived values for result step ────────────────────────────────────────

  const resultCounts = useMemo(() => {
    const total = isFlatAssignmentMode && flatSourceCard
      ? flatSourceCard.transactions.length
      : reviewCards.reduce((sum, c) => sum + c.transactions.length, 0);
    const included = isFlatAssignmentMode
      ? Object.entries(cardMatching.flatTxAssignments).filter(
          ([txIndex, draftCardId]) => !!draftCardId && (cardMatching.flatTxInclusions[Number(txIndex)] ?? true),
        ).length
      : Object.values(txReview.txSelections).filter(Boolean).length;
    return { total, included };
  }, [isFlatAssignmentMode, flatSourceCard, reviewCards, cardMatching.flatTxAssignments, cardMatching.flatTxInclusions, txReview.txSelections]);

  const includedCount = isFlatAssignmentMode
    ? Object.entries(cardMatching.flatTxAssignments).filter(
        ([txIndex, draftCardId]) => !!draftCardId && (cardMatching.flatTxInclusions[Number(txIndex)] ?? true),
      ).length
    : Object.values(txReview.txSelections).filter(Boolean).length;

  // ── Cards for TxReviewStep ────────────────────────────────────────────────

  const txReviewCards = useMemo<ReviewCardForTxReview[]>(() => {
    if (isFlatAssignmentMode && flatSourceCard) {
      return cardMatching.draftCards.map((draft) => ({
        reviewCardId: draft.draftCardId,
        lastFourDigits: draft.lastFourDigits,
        financialAccountId: draft.financialAccountId,
        transactions: flatSourceCard.transactions
          .map((tx, txIndex) => ({ ...tx, __sourceTxIndex: txIndex }))
          .filter((tx) => cardMatching.flatTxAssignments[tx.__sourceTxIndex] === draft.draftCardId),
      }));
    }
    return reviewCards.map((card, ci) => ({
      reviewCardId: card.reviewCardId,
      lastFourDigits: card.lastFourDigits,
      financialAccountId: cardMatching.cardResolutions[ci]?.financialAccountId ?? null,
      transactions: card.transactions,
    }));
  }, [isFlatAssignmentMode, flatSourceCard, reviewCards, cardMatching]);

  // ── CreateFinancialAccountDrawer derived props ─────────────────────────────

  const createCardLastFour =
    createDraftCardId !== null
      ? (cardMatching.draftCards.find((d) => d.draftCardId === createDraftCardId)?.lastFourDigits || undefined)
      : createCardForIndex !== null
        ? (reviewCards[createCardForIndex]?.lastFourDigits ?? undefined)
        : undefined;

  const createCardClosingDay =
    createDraftCardId !== null
      ? (sharedCardTemplate.closingDay
          ? Number(sharedCardTemplate.closingDay)
          : getDayOfMonthFromDateInput(flatSourceCard?.closingDate))
      : createCardForIndex !== null
        ? getDayOfMonthFromDateInput(reviewCards[createCardForIndex]?.closingDate)
        : undefined;

  const createCardDueDay =
    createDraftCardId !== null
      ? (sharedCardTemplate.dueDay
          ? Number(sharedCardTemplate.dueDay)
          : getDayOfMonthFromDateInput(flatSourceCard?.dueDate))
      : createCardForIndex !== null
        ? getDayOfMonthFromDateInput(reviewCards[createCardForIndex]?.dueDate)
        : undefined;

  // ── Step titles ────────────────────────────────────────────────────────────

  const stepTitles: Record<BillStep, string> = {
    upload: t('financeTransactions.billImport.steps.uploadTitle'),
    processing: t('financeTransactions.billImport.steps.processingTitle'),
    'card-matching': isFlatAssignmentMode
      ? t('financeTransactions.billImport.steps.assignmentTitle')
      : t('financeTransactions.billImport.steps.cardMatchingTitle'),
    'tx-review': t('financeTransactions.billImport.steps.txReviewTitle'),
    result: t('financeTransactions.billImport.steps.resultTitle'),
    error: t('financeTransactions.billImport.steps.errorTitle'),
  };

  if (!open) return null;

  // ── Render ─────────────────────────────────────────────────────────────────

  const body = (() => {
    switch (step) {
      case 'upload':
        return (
          <UploadStep
            fileInputRef={fileUpload.fileInputRef}
            file={fileUpload.file}
            fileError={fileUpload.fileError}
            isEncryptedPdf={fileUpload.isEncryptedPdf}
            pdfPassword={fileUpload.pdfPassword}
            submitting={fileUpload.submitting}
            onFileChange={fileUpload.handleFileChange}
            onDrop={fileUpload.handleDrop}
            onPdfPasswordChange={fileUpload.setPdfPassword}
            onSubmit={fileUpload.handleSubmitFile}
            onCancel={handleClose}
          />
        );

      case 'processing':
        return (
          <ProcessingStep
            jobStatus={jobData?.billImportJob?.status}
            statusLabel={statusLabel}
          />
        );

      case 'card-matching':
        if (isFlatAssignmentMode && flatSourceCard) {
          const selectedFlatCardOption = cardMatching.flatSelectedFinancialAccountId
            ? cardAccountOptionsById.get(cardMatching.flatSelectedFinancialAccountId)
            : undefined;
          return (
            <FlatAssignmentStep
              flatSourceCard={flatSourceCard}
              flatTxAssignments={cardMatching.flatTxAssignments}
              flatTxSelections={cardMatching.flatTxSelections}
              draftCards={cardMatching.draftCards}
              assignmentLastFour={cardMatching.assignmentLastFour}
              selectedCardAccountId={cardMatching.flatSelectedFinancialAccountId}
              selectedCardIsMultiCard={selectedFlatCardOption?.isMultiCard ?? false}
              onSelectCardAccount={cardMatching.selectFlatFinancialAccount}
              onCreateCardAccount={inlineCardCreation.openCreateCardForFlatSelection}
              onAssignSelectedTransactionsToExistingAccount={cardMatching.assignSelectedTransactionsToExistingAccount}
              onSelectAllTx={() => {
                if (!flatSourceCard) return;
                const next: typeof cardMatching.flatTxSelections = {};
                flatSourceCard.transactions.forEach((_, txIndex) => { next[txIndex] = true; });
                cardMatching.setFlatTxSelections(next);
              }}
              onAssignmentLastFourChange={cardMatching.setAssignmentLastFour}
              onSetFlatTxSelections={cardMatching.setFlatTxSelections}
            />
          );
        }
        return (
          <CardBucketMatchingStep
            reviewCards={reviewCards}
            cardResolutions={cardMatching.cardResolutions}
            cardAccountOptionsById={cardAccountOptionsById}
            selectedIssuerKey={selectedIssuerKey}
            allCardsResolved={cardMatching.allCardsResolved}
            getAvailableOptionsForCard={cardMatching.getAvailableOptionsForCard}
            onResolveCard={(ci, accountId) => {
              cardMatching.setCardResolutions((prev) => {
                if (!prev[ci]) return prev;
                const updated = [...prev];
                updated[ci] = { ...prev[ci]!, financialAccountId: accountId };
                return updated;
              });
            }}
            onCreateCardForIndex={inlineCardCreation.openCreateCardForIndex}
            onContinue={() => setStep('tx-review')}
            onCancel={handleClose}
          />
        );

      case 'tx-review':
        return (
          <TxReviewStep
            cards={txReviewCards}
            txSelections={txReview.txSelections}
            flatTxInclusions={cardMatching.flatTxInclusions}
            flatTxAssignments={cardMatching.flatTxAssignments}
            txCategorySelections={txReview.txCategorySelections}
            billDateOverrides={cardMatching.billDateOverrides}
            isFlatAssignmentMode={isFlatAssignmentMode}
            allCardsResolved={cardMatching.allCardsResolved}
            allCategories={allCategories}
            cardAccountOptions={cardAccountOptions}
            confirming={confirming}
            includedCount={includedCount}
            onSetTxSelections={txReview.setTxSelections}
            onSetFlatTxInclusions={cardMatching.setFlatTxInclusions}
            onSetBillDateOverrides={cardMatching.setBillDateOverrides}
            onUpdateCategory={txReview.updateCategory}
            onConfirm={handleConfirm}
            onBack={() => setStep('card-matching')}
          />
        );

      case 'result':
        return <ResultStep included={resultCounts.included} total={resultCounts.total} onClose={handleClose} />;

      case 'error':
        return <ErrorStep errorMessage={errorMessage} onRetry={resetToUploadStep} onClose={handleClose} />;
    }
  })();

  const footer =
    step === 'card-matching' && isFlatAssignmentMode ? (
      <Drawer.ActionButtons
        cancelText={t('financeTransactions.actions.cancel')}
        submitText={t('financeTransactions.billImport.cardMatchingStep.reviewTransactions')}
        onCancel={handleClose}
        onSubmit={cardMatching.handleFlatReview}
        submitDisabled={!cardMatching.canReviewTransactions || cardMatching.creatingTemplateCards}
        submitting={cardMatching.creatingTemplateCards}
      />
    ) : null;

  return (
    <>
      <Drawer open={open} onClose={handleClose} anchor="right" size="md">
        <Drawer.Header
          title={stepTitles[step]}
          showCloseButton
          closeButtonAriaLabel={t('financeTransactions.actions.close')}
        />
        <Drawer.Body>{body}</Drawer.Body>
        {footer && <Drawer.Footer>{footer}</Drawer.Footer>}
      </Drawer>

      <CreateFinancialAccountDrawer
        open={isCreateCardDrawerOpen}
        initialType="CARD"
        initialMaskedNumber={createCardLastFour}
        initialClosingDay={createCardClosingDay}
        initialDueDay={createCardDueDay}
        initialIssuerId={cardMatching.selectedIssuerDefaults.issuerId}
        initialIssuerName={cardMatching.selectedIssuerDefaults.issuerName}
        onClose={inlineCardCreation.close}
        onCreated={(account) => {
          if (createForFlatSelection) {
            inlineCardCreation.addInlineAccount({
              id: account.id,
              name: account.name,
              issuerId: account.issuerId,
              issuerName: account.issuerName,
            });
            cardMatching.selectFlatFinancialAccount(account.id);
            inlineCardCreation.close();
            return;
          }

          inlineCardCreation.addInlineAccount({
            id: account.id,
            name: account.name,
            issuerId: account.issuerId,
            issuerName: account.issuerName,
            cardData: { maskedNumber: createCardLastFour ?? null },
          });

          const createdIssuerKey = toIssuerKey(account.issuerId, account.issuerName);
          const canAssociate = !selectedIssuerKey || createdIssuerKey === selectedIssuerKey;

          if (createDraftCardId !== null) {
            cardMatching.setDraftCards((prev) =>
              prev.map((draft) =>
                draft.draftCardId === createDraftCardId
                  ? {
                      ...draft,
                      financialAccountId: canAssociate ? account.id : null,
                      lastFourDigits: draft.lastFourDigits || createCardLastFour || '',
                    }
                  : draft,
              ),
            );
          } else if (createCardForIndex !== null) {
            cardMatching.setCardResolutions((prev) => {
              const base = reviewCards.map((c, idx) => {
                const existing = prev[idx];
                return {
                  reviewCardId: c.reviewCardId,
                  financialAccountId:
                    existing?.financialAccountId ??
                    (c.matchAmbiguous ? null : (c.matchedFinancialAccountId ?? null)),
                };
              });
              if (!base[createCardForIndex]) return prev;
              base[createCardForIndex] = {
                ...base[createCardForIndex]!,
                financialAccountId: canAssociate ? account.id : null,
              };
              return base;
            });
          }
          inlineCardCreation.close();
        }}
      />
    </>
  );
}
