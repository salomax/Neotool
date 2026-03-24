'use client';

import React, { useState, useCallback } from 'react';
import {
  Box,
  Typography,
  Stack,
  Button,
  Card,
  CardContent,
  Chip,
  Collapse,
  Alert,
} from '@mui/material';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import { useQuery, useMutation } from '@apollo/client/react';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../i18n';
import { TRANSACTION_IMPORT } from '@/lib/graphql/operations/banking/queries';
import { RESOLVE_IMPORT_DUPLICATE } from '@/lib/graphql/operations/banking/mutations';
import { useToast } from '@/shared/providers';
import { extractErrorMessage } from '@/shared/utils/error';

type DuplicateResolution = 'KEEP_BOTH' | 'USE_INCOMING' | 'KEEP_EXISTING';

function formatAmount(amount: number, creditDebitType: string, currencyCode: string): string {
  const sign = creditDebitType === 'CREDIT' ? '+' : '-';
  return `${sign} ${currencyCode} ${Math.abs(amount).toFixed(2)}`;
}

const RESOLUTION_LABEL_KEYS: Record<DuplicateResolution, string> = {
  KEEP_BOTH: 'financeTransactions.duplicateReview.keepBoth',
  USE_INCOMING: 'financeTransactions.duplicateReview.useIncoming',
  KEEP_EXISTING: 'financeTransactions.duplicateReview.keepExisting',
};

export interface ImportDuplicateReviewProps {
  jobId: string;
  onComplete?: () => void;
  onClose?: () => void;
}

export function ImportDuplicateReview({ jobId, onComplete, onClose }: ImportDuplicateReviewProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const toast = useToast();
  const [resolutions, setResolutions] = useState<Record<number, DuplicateResolution>>({});
  const [resolvedRows, setResolvedRows] = useState<Set<number>>(new Set());
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const { data, loading, error } = useQuery<{
    transactionImport: {
      id: string;
      status: string;
      duplicateReviewCount: number;
      duplicateCandidates: Array<{
        rowIndex: number;
        parsed: {
          creditDebitType: string;
          amount: number;
          date: string;
          description: string;
          currencyCode: string;
        };
        candidates: Array<{
          id: string;
          date: string;
          description: string;
          amount: number;
          creditDebitType: string;
          currencyCode: string;
          source: string;
        }>;
      }>;
    };
  }>(TRANSACTION_IMPORT, {
    variables: { id: jobId },
    skip: !jobId,
  });

  const [resolveDuplicate] = useMutation<unknown>(RESOLVE_IMPORT_DUPLICATE);

  const job = data?.transactionImport;
  const candidates = React.useMemo(() => job?.duplicateCandidates ?? [], [job?.duplicateCandidates]);
  const count = candidates.length;
  const allResolved = count > 0 && candidates.every((c) => resolutions[c.rowIndex] != null);
  const canFinish = allResolved && !submitting;

  const setResolution = useCallback((rowIndex: number, resolution: DuplicateResolution) => {
    setResolutions((prev) => ({ ...prev, [rowIndex]: resolution }));
    setResolvedRows((prev) => new Set(prev).add(rowIndex));
    setSubmitError(null);
  }, []);

  const handleFinish = useCallback(async () => {
    if (!canFinish || !jobId) return;
    setSubmitting(true);
    setSubmitError(null);
    try {
      for (const c of candidates) {
        const resolution = resolutions[c.rowIndex];
        if (resolution) {
          await resolveDuplicate({
            variables: {
              input: { jobId, rowIndex: c.rowIndex, resolution },
            },
          });
        }
      }
      onComplete?.();
      onClose?.();
    } catch (err) {
      const message = extractErrorMessage(err, t('financeTransactions.import.errorStep.message'));
      setSubmitError(message);
      toast.error(message);
    } finally {
      setSubmitting(false);
    }
  }, [canFinish, jobId, candidates, resolutions, resolveDuplicate, onComplete, onClose, t, toast]);

  if (loading) {
    return (
      <Box sx={{ p: 3 }}>
        <Typography color="text.secondary">{t('financeTransactions.import.processingStep.title')}</Typography>
      </Box>
    );
  }

  if (error || !job) {
    return (
      <Box sx={{ p: 3 }}>
        <Typography color="error">{t('financeTransactions.import.errorStep.message')}</Typography>
      </Box>
    );
  }

  if (count === 0) {
    return (
      <Box sx={{ p: 3 }}>
        <Typography color="text.secondary">{t('financeTransactions.duplicateReview.resolved')}</Typography>
        {onClose && (
          <Button variant="contained" sx={{ mt: 2 }} onClick={onClose}>
            {t('financeTransactions.import.resultStep.close')}
          </Button>
        )}
      </Box>
    );
  }

  return (
    <Box sx={{ p: 3 }}>
      <Typography variant="h6" sx={{ mb: 2 }}>
        {t('financeTransactions.duplicateReview.header', { count })}
      </Typography>
      <Stack spacing={2}>
        {candidates.map((cand) => {
          const resolution = resolutions[cand.rowIndex];
          const isResolved = resolvedRows.has(cand.rowIndex);
          const parsed = cand.parsed;
          const existing = cand.candidates[0];

          return (
            <Card key={cand.rowIndex} variant="outlined" sx={{ overflow: 'hidden' }}>
              <CardContent>
                <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 1 }}>
                  {isResolved && (
                    <CheckCircleIcon color="success" fontSize="small" />
                  )}
                  <Typography variant="subtitle2" color="text.secondary">
                    #{cand.rowIndex + 1}
                  </Typography>
                </Stack>
                <Collapse in={!isResolved}>
                  <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
                    <Card variant="outlined" sx={{ flex: 1, bgcolor: 'action.hover' }}>
                      <CardContent>
                        <Typography variant="caption" color="text.secondary">
                          {t('financeTransactions.duplicateReview.newLabel')}
                        </Typography>
                        <Typography variant="body2">{parsed.description}</Typography>
                        <Typography variant="body2" color="text.secondary">
                          {parsed.date} · {formatAmount(parsed.amount, parsed.creditDebitType, parsed.currencyCode)}
                        </Typography>
                      </CardContent>
                    </Card>
                    <Card variant="outlined" sx={{ flex: 1 }}>
                      <CardContent>
                        <Typography variant="caption" color="text.secondary">
                          {t('financeTransactions.duplicateReview.existingLabel')}
                        </Typography>
                        {existing && (
                          <>
                            <Typography variant="body2">{existing.description}</Typography>
                            <Typography variant="body2" color="text.secondary">
                              {existing.date} · {formatAmount(existing.amount, existing.creditDebitType, existing.currencyCode)}
                            </Typography>
                            <Chip
                              label={existing.source}
                              size="small"
                              sx={{ mt: 0.5 }}
                              variant="outlined"
                            />
                          </>
                        )}
                      </CardContent>
                    </Card>
                  </Stack>
                  <Stack direction="row" flexWrap="wrap" gap={1} sx={{ mt: 2 }}>
                    <Button
                      size="small"
                      variant={resolution === 'KEEP_BOTH' ? 'contained' : 'outlined'}
                      onClick={() => setResolution(cand.rowIndex, 'KEEP_BOTH')}
                    >
                      {t('financeTransactions.duplicateReview.keepBoth')}
                    </Button>
                    <Button
                      size="small"
                      variant={resolution === 'USE_INCOMING' ? 'contained' : 'outlined'}
                      onClick={() => setResolution(cand.rowIndex, 'USE_INCOMING')}
                      color={resolution === 'USE_INCOMING' ? 'warning' : 'primary'}
                    >
                      {t('financeTransactions.duplicateReview.useIncoming')}
                    </Button>
                    <Button
                      size="small"
                      variant={resolution === 'KEEP_EXISTING' ? 'contained' : 'outlined'}
                      onClick={() => setResolution(cand.rowIndex, 'KEEP_EXISTING')}
                    >
                      {t('financeTransactions.duplicateReview.keepExisting')}
                    </Button>
                  </Stack>
                  {resolution === 'USE_INCOMING' && (
                    <Typography variant="caption" color="warning.main" display="block" sx={{ mt: 1 }}>
                      {t('financeTransactions.duplicateReview.useIncomingWarning')}
                    </Typography>
                  )}
                </Collapse>
                <Collapse in={isResolved}>
                  <Typography variant="body2" color="text.secondary">
                    {t('financeTransactions.duplicateReview.resolved')}: {resolution ? t(RESOLUTION_LABEL_KEYS[resolution]) : ''}
                  </Typography>
                </Collapse>
              </CardContent>
            </Card>
          );
        })}
      </Stack>
      {submitError && (
        <Alert severity="error" sx={{ mt: 2 }}>
          {submitError}
        </Alert>
      )}
      <Stack direction="row" spacing={2} sx={{ mt: 3 }} justifyContent="flex-end">
        {onClose && (
          <Button onClick={onClose}>{t('financeTransactions.actions.cancel')}</Button>
        )}
        <Button
          variant="contained"
          onClick={handleFinish}
          disabled={!canFinish}
        >
          {submitting ? t('financeTransactions.actions.saving') : t('financeTransactions.duplicateReview.finishReview')}
        </Button>
      </Stack>
    </Box>
  );
}
