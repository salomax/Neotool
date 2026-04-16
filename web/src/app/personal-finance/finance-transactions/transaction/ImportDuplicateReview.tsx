'use client';

import { useMemo, useState, useCallback, useEffect } from 'react';
import {
  Stack,
  Alert,
  Box,
  Typography,
  IconButton,
  useTheme,
} from '@mui/material';
import { useApolloClient, useMutation } from '@apollo/client/react';
import { useTranslation, i18n } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../i18n';
import {
  TransactionImportDocument,
  type TransactionImportQuery,
  type TransactionImportQueryVariables,
} from '@/lib/graphql/operations/banking/queries.generated';
import {
  ResolveImportDuplicateDocument,
  type ResolveImportDuplicateMutation,
  type ResolveImportDuplicateMutationVariables,
} from '@/lib/graphql/operations/banking/mutations.generated';
import { CurrencyDisplay } from '@/shared/components/ui/CurrencyDisplay';
import { Button } from '@/shared/components/ui/primitives/Button';
import { useToast } from '@/shared/providers';
import { extractErrorMessage } from '@/shared/utils/error';
import CompareArrowsIcon from '@mui/icons-material/CompareArrows';
import ReceiptLongIcon from '@mui/icons-material/ReceiptLong';
import LinkIcon from '@mui/icons-material/Link';
import { DuplicateResolution } from '@/lib/graphql/types/__generated__/graphql';

function resolveLocale() {
  return i18n.language || Intl.DateTimeFormat().resolvedOptions().locale || 'pt-BR';
}

function formatDate(value: unknown): string {
  if (value == null) return '';
  const date = new Date(value as any);
  if (Number.isNaN(date.getTime())) return String(value);

  const locale = resolveLocale();
  return new Intl.DateTimeFormat(locale, { day: '2-digit', month: 'long', year: 'numeric' }).format(date);
}

export interface ImportDuplicateReviewProps {
  jobIds: string[];
  onComplete?: () => void;
  onClose?: () => void;
}

export function ImportDuplicateReview({ jobIds, onComplete, onClose }: ImportDuplicateReviewProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const toast = useToast();
  const theme = useTheme();
  const [resolutions, setResolutions] = useState<Record<string, DuplicateResolution>>({});
  const [resolvedRows, setResolvedRows] = useState<Set<string>>(new Set());
  const [submittingRowKey, setSubmittingRowKey] = useState<string | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const apolloClient = useApolloClient();
  const [jobsData, setJobsData] = useState<Record<string, TransactionImportQuery['transactionImport']>>({});
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<unknown>(null);

  useEffect(() => {
    let cancelled = false;

    // Reset per-open state so we don't carry resolutions between job sets.
    setResolutions({});
    setResolvedRows(new Set());
    setSubmittingRowKey(null);
    setSubmitError(null);

    if (!jobIds || jobIds.length === 0) {
      setJobsData({});
      setLoading(false);
      setError(null);
      return;
    }

    setLoading(true);
    setError(null);
    setJobsData({});

    void Promise.all(
      jobIds.map(async (id) => {
        const result = await apolloClient.query<TransactionImportQuery, TransactionImportQueryVariables>({
          query: TransactionImportDocument,
          variables: { id },
          fetchPolicy: 'network-only',
        });
        return { id, import: result.data?.transactionImport ?? null };
      }),
    )
      .then((results) => {
        if (cancelled) return;
        const map: Record<string, TransactionImportQuery['transactionImport']> = {};
        for (const r of results) {
          if (r.import) map[r.id] = r.import;
        }
        setJobsData(map);
      })
      .catch((err) => {
        if (cancelled) return;
        setError(err);
      })
      .finally(() => {
        if (cancelled) return;
        setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [apolloClient, jobIds]);

  const [resolveDuplicate] = useMutation<ResolveImportDuplicateMutation, ResolveImportDuplicateMutationVariables>(
    ResolveImportDuplicateDocument,
  );

  type ReviewRow = {
    jobId: string;
    jobSource: string;
    rowIndex: number;
    parsed: unknown;
    candidates: Array<{ source: string; date: unknown; description: string; amount: number; currencyCode: string }>;
  };

  const rows = useMemo<ReviewRow[]>(() => {
    const out: ReviewRow[] = [];

    for (const id of jobIds) {
      const job = jobsData[id];
      if (!job) continue;

      for (const cand of job.duplicateCandidates ?? []) {
        out.push({
          jobId: id,
          jobSource: job.source,
          rowIndex: cand.rowIndex,
          parsed: cand.parsed,
          candidates: cand.candidates,
        });
      }
    }

    return out;
  }, [jobIds, jobsData]);

  const count = rows.length;
  const isSubmitting = submittingRowKey != null;

  const rowKeys = useMemo(() => rows.map((r) => `${r.jobId}:${r.rowIndex}`), [rows]);

  const handleResolve = useCallback(
    async (row: ReviewRow, resolution: DuplicateResolution) => {
      if (isSubmitting) return;

      const rowKey = `${row.jobId}:${row.rowIndex}`;
      setSubmittingRowKey(rowKey);
      setSubmitError(null);

      try {
        await resolveDuplicate({
          variables: {
            input: { jobId: row.jobId, rowIndex: row.rowIndex, resolution },
          },
        });

        setResolutions((prev) => ({ ...prev, [rowKey]: resolution }));
        const nextResolvedRows = new Set(resolvedRows);
        nextResolvedRows.add(rowKey);
        setResolvedRows(nextResolvedRows);

        if (rowKeys.length > 0 && nextResolvedRows.size === rowKeys.length) {
          onComplete?.();
          onClose?.();
        }
      } catch (err) {
        const message = extractErrorMessage(err, t('financeTransactions.import.errorStep.message'));
        setSubmitError(message);
        toast.error(message);
      } finally {
        setSubmittingRowKey(null);
      }
    },
    [isSubmitting, onClose, onComplete, resolveDuplicate, resolvedRows, rowKeys, t, toast],
  );

  if (loading) {
    return (
      <Stack spacing={1}>
        <Typography color="text.secondary">{t('financeTransactions.import.processingStep.title')}</Typography>
      </Stack>
    );
  }

  if (error) {
    return (
      <Typography color="error">{t('financeTransactions.import.errorStep.message')}</Typography>
    );
  }

  if (count === 0) {
    return (
      <Stack spacing={2}>
        <Typography color="text.secondary">{t('financeTransactions.duplicateReview.resolved')}</Typography>
        {onClose ? (
          <Box>
            <Button variant="outlined" onClick={onClose}>
              {t('financeTransactions.import.resultStep.close')}
            </Button>
          </Box>
        ) : null}
      </Stack>
    );
  }

  const candidates = rows;
  const isNfceReview = rows[0]?.jobSource === 'NFC_E';

  const importedLabelKey = isNfceReview
    ? 'financeTransactions.duplicateReview.newLabelNfce'
    : 'financeTransactions.duplicateReview.newLabel';
  const useIncomingLabelKey = isNfceReview
    ? 'financeTransactions.duplicateReview.useIncomingLink'
    : 'financeTransactions.duplicateReview.useIncoming';

  return (
    <Stack spacing={2}>
      <Typography variant="body2" color="text.secondary">
        
        {t('financeTransactions.duplicateReview.resolved')}: {resolvedRows.size}/{count}
      </Typography>

      <Stack spacing={2}>
        {candidates.map((cand, index) => {
          const rowIndex = cand.rowIndex;
          const rowKey = `${cand.jobId}:${rowIndex}`;
          const parsed = cand.parsed as any;
          const isResolved = resolvedRows.has(rowKey);
          const existingTx = cand.candidates[0] ?? null;
          const resolution = resolutions[rowKey];

          return (
            <Box key={rowKey} sx={{ position: 'relative' }}>
              <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 1 }}>
                <Box
                  sx={{
                    width: 28,
                    height: 28,
                    borderRadius: '50%',
                    bgcolor: 'primary.main',
                    color: 'primary.contrastText',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontWeight: 700,
                    fontSize: 13,
                    flexShrink: 0,
                  }}
                >
                  #{index + 1}
                </Box>
              </Stack>
              <Stack direction={{ xs: 'column', sm: 'row' }} spacing={0.75} alignItems="stretch">
                <Box
                  sx={{
                    flex: 1,
                    borderRadius: 1,
                    border: '1px solid',
                    borderColor: 'divider',
                    bgcolor: 'action.hover',
                    p: 2,
                    display: 'flex',
                    flexDirection: 'column',
                    minHeight: 150,
                  }}
                >
                  <Stack direction="row" alignItems="center" justifyContent="space-between" spacing={1} sx={{ mb: 1 }}>
                    <Typography variant="overline" color="text.secondary">
                      {t(importedLabelKey)}
                    </Typography>
                    <ReceiptLongIcon fontSize="small" sx={{ color: 'text.secondary' }} />
                  </Stack>
                  <Typography variant="body2" color="text.secondary">
                    {formatDate(parsed.date)}
                  </Typography>
                  <Typography variant="body2" sx={{ fontWeight: 700, mt: 0.5 }}>
                    {parsed.description}
                  </Typography>
                  <Box sx={{ mt: 'auto', display: 'flex', justifyContent: 'flex-end' }}>
                    <CurrencyDisplay
                      value={parsed.amount}
                      currency={parsed.currencyCode}
                      showColor={false}
                      variant="h6"
                      sx={{ fontWeight: 600 }}
                    />
                  </Box>
                </Box>

                <Box
                  sx={{
                    flex: 1,
                    borderRadius: 1,
                    border: '1px solid',
                    borderColor: 'divider',
                    bgcolor: 'background.paper',
                    p: 2,
                    display: 'flex',
                    flexDirection: 'column',
                    minHeight: 150,
                  }}
                >
                  <Stack direction="row" alignItems="center" justifyContent="space-between" spacing={1} sx={{ mb: 1 }}>
                    <Typography variant="overline" color="text.secondary">
                      {t('financeTransactions.duplicateReview.existingLabel')}
                    </Typography>
                  </Stack>
                  {existingTx ? (
                    <>
                      <Typography variant="body2" color="text.secondary">
                        {formatDate(existingTx.date)}
                      </Typography>
                      <Typography variant="body2" sx={{ fontWeight: 700, mt: 0.5 }}>
                        {existingTx.description}
                      </Typography>
                      <Box sx={{ mt: 'auto', display: 'flex', justifyContent: 'flex-end' }}>
                        <CurrencyDisplay
                          value={existingTx.amount}
                          currency={existingTx.currencyCode}
                          showColor={false}
                          variant="h6"
                          sx={{ fontWeight: 600 }}
                        />
                      </Box>
                    </>
                  ) : (
                    <Typography variant="body2" color="text.secondary">
                      {t('financeTransactions.import.errorStep.message')}
                    </Typography>
                  )}
                </Box>
              </Stack>

              <IconButton
                disabled
                size="small"
                sx={{
                  position: 'absolute',
                  left: '50%',
                  top: '50%',
                  transform: 'translate(-50%, -50%)',
                  bgcolor: 'background.paper',
                  border: '1px solid',
                  borderColor: 'divider',
                  boxShadow: theme.shadows[2],
                  zIndex: 1,
                  display: { xs: 'none', sm: 'inline-flex' },
                  opacity: 1,
                  '&.Mui-disabled': {
                    opacity: 1,
                    backgroundColor: theme.palette.background.paper,
                    color: theme.palette.text.secondary,
                  },
                }}
              >
                <CompareArrowsIcon fontSize="small" />
              </IconButton>

              {!isResolved ? (
                <Stack direction="row" spacing={1} justifyContent="flex-end" sx={{ mt: 1 }}>
                  <Button
                    variant="outlined"
                    size="small"
                    onClick={() => handleResolve(cand, DuplicateResolution.KeepBoth)}
                    disabled={isSubmitting}
                    sx={{ textTransform: 'none', borderRadius: 1 }}
                  >
                    {t('financeTransactions.duplicateReview.keepBoth')}
                  </Button>
                  <Button
                    variant="outlined"
                    size="small"
                    onClick={() => handleResolve(cand, DuplicateResolution.UseIncoming)}
                    disabled={isSubmitting}
                    startIcon={isNfceReview ? <LinkIcon fontSize="small" /> : undefined}
                    sx={{ textTransform: 'none', borderRadius: 1 }}
                  >
                    {t(useIncomingLabelKey)}
                  </Button>
                </Stack>
              ) : (
                <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
                  {t('financeTransactions.duplicateReview.resolved')}
                  {resolution ? `: ${t(resolution === DuplicateResolution.KeepBoth ? 'financeTransactions.duplicateReview.keepBoth' : useIncomingLabelKey)}` : ''}
                </Typography>
              )}
            </Box>
          );
        })}
      </Stack>

      {submitError ? (
        <Alert severity="error">{submitError}</Alert>
      ) : null}
    </Stack>
  );
}
