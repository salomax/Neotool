'use client';

import React, { useState, useRef, useCallback } from 'react';
import {
  Box,
  Typography,
  Stack,
  Button,
  Alert,
  LinearProgress,
  CircularProgress,
  Divider,
  Paper,
  List,
  ListItem,
  ListItemText,
  ListItemAvatar,
  Avatar,
  Chip,
  Checkbox,
  Select,
  MenuItem as SelectMenuItem,
  FormControl,
  InputLabel,
  alpha,
} from '@mui/material';
import {
  CloudUpload as UploadIcon,
  CreditCard as CardIcon,
  CheckCircle as CheckIcon,
  InsertDriveFile as FileIcon,
  HelpOutline as UnmatchedIcon,
  Add as AddIcon,
  SwapHoriz as TransferIcon,
} from '@mui/icons-material';
import { Drawer } from '@/shared/components/ui/layout/Drawer';
import { useMutation, useQuery } from '@apollo/client/react';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../i18n';
import { SUBMIT_PDF_BILL_IMPORT, CONFIRM_PDF_BILL_IMPORT } from '@/lib/graphql/operations/banking/mutations';
import { PDF_IMPORT_JOB, FINANCIAL_ACCOUNTS } from '@/lib/graphql/operations/banking/queries';
import { FinancialAccount } from '@/lib/graphql/types/__generated__/graphql';
import { CreateFinancialAccountDrawer } from './CreateFinancialAccountDrawer';
import { extractErrorCode, extractErrorMessage } from '@/shared/utils/error';

const MAX_FILE_BYTES = 3 * 1024 * 1024; // 3 MB
const ACCEPTED_EXTENSIONS = ['.pdf', '.csv'];
const ACCEPTED_TYPES = 'application/pdf,.pdf,text/csv,.csv';
const POLL_INTERVAL_MS = 2000;

type BillStep = 'upload' | 'processing' | 'card-matching' | 'tx-review' | 'result' | 'error';

/** Per-card resolution: which financialAccountId to associate with each extracted card. */
interface CardResolution {
  lastFourDigits: string;
  financialAccountId: string | null;
}

/** Per-transaction selection state (include/exclude). Key: `${cardIndex}-${txIndex}` */
type TxSelections = Record<string, boolean>;

function fileToBase64(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      const result = reader.result as string;
      resolve(result.includes(',') ? result.split(',')[1]! : result);
    };
    reader.onerror = () => reject(reader.error);
    reader.readAsDataURL(file);
  });
}

function getExtension(name: string): string {
  const i = name.lastIndexOf('.');
  return i >= 0 ? name.slice(i).toLowerCase() : '';
}

function formatTxDisplayDate(date: string | null | undefined): string {
  if (!date) return '-';

  const dateOnlyMatch = /^(\d{4})-(\d{2})-(\d{2})$/.exec(date);
  const dateObj = dateOnlyMatch
    ? new Date(Number(dateOnlyMatch[1]), Number(dateOnlyMatch[2]) - 1, Number(dateOnlyMatch[3]))
    : new Date(date);

  if (Number.isNaN(dateObj.getTime())) return date;

  return new Intl.DateTimeFormat(undefined, { dateStyle: 'short' }).format(dateObj);
}

function resolveFriendlyImportErrorMessage(
  t: (key: string) => string,
  errorCode?: string | null,
  errorMessage?: string | null,
): string {
  const normalizedCode = (errorCode ?? '').toLowerCase();
  const normalizedMessage = (errorMessage ?? '').toLowerCase();

  if (
    normalizedCode === 'assistant_pdf_reader_failed' ||
    normalizedMessage.includes('llm_provider_error') ||
    normalizedMessage.includes('high demand')
  ) {
    return t('financeTransactions.billImport.errorStep.processingUnavailable');
  }

  return t('financeTransactions.billImport.errorStep.generic');
}

export interface ImportBillDrawerProps {
  open: boolean;
  onClose: () => void;
  onImportCompleted?: () => void;
}

export function ImportBillDrawer({
  open,
  onClose,
  onImportCompleted,
}: ImportBillDrawerProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [step, setStep] = useState<BillStep>('upload');
  const [file, setFile] = useState<File | null>(null);
  const [fileError, setFileError] = useState<string | null>(null);
  const [jobId, setJobId] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [cardResolutions, setCardResolutions] = useState<CardResolution[]>([]);
  const [txSelections, setTxSelections] = useState<TxSelections>({});

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

  // Sub-flow: inline card creation
  const [createCardForIndex, setCreateCardForIndex] = useState<number | null>(null);
  const [isCreateCardDrawerOpen, setIsCreateCardDrawerOpen] = useState(false);
  // Accounts created inline during this import session — merged with cardAccounts
  // to guarantee they appear in the Select even before Apollo refetch completes.
  const [inlineCreatedAccounts, setInlineCreatedAccounts] = useState<Array<{ id: string; name: string }>>([]);

  // Reset all state when drawer opens
  React.useEffect(() => {
    if (!open) return;
    setStep('upload');
    setFile(null);
    setFileError(null);
    setJobId(null);
    setErrorMessage(null);
    setCardResolutions([]);
    setTxSelections({});
    setInlineCreatedAccounts([]);
  }, [open]);

  const [submitImport, { loading: submitting }] = useMutation<{
    submitPdfBillImport: { id: string; status: string; errorCode?: string | null; errorMessage?: string | null };
  }>(SUBMIT_PDF_BILL_IMPORT);

  const [confirmImport, { loading: confirming }] = useMutation<{
    confirmPdfBillImport: { id: string; status: string; errorCode?: string | null; errorMessage?: string | null };
  }>(CONFIRM_PDF_BILL_IMPORT);

  // Poll job status while in processing step
  const { data: jobData, stopPolling, startPolling } = useQuery<{
    pdfImportJob: {
      id: string;
      status: string;
      errorCode?: string | null;
      errorMessage?: string | null;
      reviewPayload?: {
        bankName?: string;
        extractionConfidence: number;
        needsReview: boolean;
        warnings: string[];
        cards: Array<{
          lastFourDigits: string;
          holderName?: string;
          computedTotal: number;
          declaredTotal: number;
          totalMatchesDeclared: boolean;
          matchedFinancialAccountId?: string;
          matchAmbiguous: boolean;
          closingDate?: string;
          dueDate?: string;
          matchCandidates: Array<{ id: string; name: string }>;
          transactions: Array<{
            date?: string;
            description: string;
            amount: number;
            creditDebitType: string;
            confidence: number;
            isBillPayment?: boolean;
            installment?: { current: number; total: number };
          }>;
        }>;
      };
    };
  }>(PDF_IMPORT_JOB, {
    variables: { jobId },
    skip: !open || !jobId,
    fetchPolicy: 'network-only',
    pollInterval: step === 'processing' ? POLL_INTERVAL_MS : 0,
  });

  const reviewCards = React.useMemo(
    () => jobData?.pdfImportJob?.reviewPayload?.cards ?? [],
    [jobData?.pdfImportJob?.reviewPayload?.cards],
  );

  // React to job status changes during polling
  React.useEffect(() => {
    const job = jobData?.pdfImportJob;
    if (!job) return;

    const status = job.status;
    if (status === 'AWAITING_REVIEW') {
      stopPolling();
      // Only initialise resolutions when transitioning from processing — never overwrite later steps
      setStep((currentStep) => {
        if (currentStep !== 'processing') return currentStep;
        const cards = job.reviewPayload?.cards ?? [];
        setCardResolutions(
          cards.map((c) => ({
            lastFourDigits: c.lastFourDigits,
            financialAccountId: c.matchAmbiguous ? null : (c.matchedFinancialAccountId ?? null),
          })),
        );
        const selections: TxSelections = {};
        cards.forEach((card, ci) => {
          card.transactions.forEach((_, ti) => {
            selections[`${ci}-${ti}`] = true;
          });
        });
        setTxSelections(selections);
        return 'card-matching';
      });
    } else if (status === 'FAILED') {
      stopPolling();
      setErrorMessage(resolveFriendlyImportErrorMessage(t, job.errorCode, job.errorMessage));
      setStep('error');
    }
  }, [jobData, stopPolling, t]);

  const { data: accountsData } = useQuery<{ financialAccounts: FinancialAccount[] }>(
    FINANCIAL_ACCOUNTS,
    { skip: !open, errorPolicy: 'all' },
  );

  const cardAccountOptions: Array<{ id: string; name: string }> = [
    ...(accountsData?.financialAccounts ?? [])
      .filter((a) => a.type === 'CREDIT')
      .map((a) => ({ id: a.id, name: a.name })),
    // Merge accounts created inline this session that may not be in Apollo cache yet
    ...inlineCreatedAccounts.filter(
      (ia) => !(accountsData?.financialAccounts ?? []).some((a) => a.id === ia.id),
    ),
  ];

  const handleClose = useCallback(() => {
    stopPolling();
    onClose();
  }, [onClose, stopPolling]);

  const validateFile = (f: File): string | null => {
    const ext = getExtension(f.name);
    if (!ACCEPTED_EXTENSIONS.includes(ext)) {
      return t('financeTransactions.import.uploadStep.unsupportedType');
    }
    if (f.size > MAX_FILE_BYTES) {
      return t('financeTransactions.import.uploadStep.fileTooLarge');
    }
    return null;
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const f = e.target.files?.[0];
    if (!f) return;
    const err = validateFile(f);
    setFileError(err);
    setFile(err ? null : f);
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    const f = e.dataTransfer.files?.[0];
    if (!f) return;
    const err = validateFile(f);
    setFileError(err);
    setFile(err ? null : f);
  };

  const handleSubmitFile = async () => {
    if (!file) return;
    try {
      const base64 = await fileToBase64(file);
      const result = await submitImport({
        variables: { input: { fileName: file.name, fileBase64: base64, importType: 'CREDIT_CARD_BILL' } },
      });
      const id = result.data?.submitPdfBillImport?.id;
      if (id) {
        setJobId(id);
        startPolling(POLL_INTERVAL_MS);
        setStep('processing');
      } else {
        setErrorMessage(resolveFriendlyImportErrorMessage(t));
        setStep('error');
      }
    } catch (err) {
      const defaultMessage = resolveFriendlyImportErrorMessage(t);
      setErrorMessage(extractErrorCode(err) ? extractErrorMessage(err, defaultMessage) : defaultMessage);
      setStep('error');
    }
  };

  const allCardsResolved = cardResolutions.length > 0 && cardResolutions.every((r) => r.financialAccountId !== null);

  const handleConfirm = async () => {
    if (!jobId || !allCardsResolved) return;
    const cards = reviewCards;
    try {
      const result = await confirmImport({
        variables: {
          input: {
            jobId,
            cards: cards.map((card, ci) => ({
              lastFourDigits: card.lastFourDigits,
              financialAccountId: cardResolutions[ci]!.financialAccountId,
              transactions: card.transactions.map((tx, ti) => ({
                date: tx.date ?? '',
                description: tx.description,
                amount: tx.amount,
                creditDebitType: tx.creditDebitType,
                include: txSelections[`${ci}-${ti}`] ?? true,
                isBillPayment: !!tx.isBillPayment,
              })),
            })),
          },
        },
      });
      const status = result.data?.confirmPdfBillImport?.status;
      if (status === 'DONE' || status === 'COMPLETED') {
        setStep('result');
        onImportCompleted?.();
      } else {
        setErrorMessage(
          resolveFriendlyImportErrorMessage(
            t,
            result.data?.confirmPdfBillImport?.errorCode,
            result.data?.confirmPdfBillImport?.errorMessage,
          ),
        );
        setStep('error');
      }
    } catch (err) {
      const defaultMessage = resolveFriendlyImportErrorMessage(t);
      setErrorMessage(extractErrorCode(err) ? extractErrorMessage(err, defaultMessage) : defaultMessage);
      setStep('error');
    }
  };

  // ─── Step renderers ────────────────────────────────────────────────────────

  const renderUpload = () => (
    <Box sx={{ p: 1 }}>
      <Typography variant="body1" color="text.secondary" sx={{ mb: 2 }}>
        {t('financeTransactions.billImport.uploadStep.prompt')}
      </Typography>
      <Alert severity="info" sx={{ mb: 2 }}>
        {t('financeTransactions.billImport.uploadStep.recommendation')}
      </Alert>
      <input
        ref={fileInputRef}
        type="file"
        accept={ACCEPTED_TYPES}
        onChange={handleFileChange}
        style={{ display: 'none' }}
      />
      <Box
        onDrop={handleDrop}
        onDragOver={(e) => e.preventDefault()}
        onClick={() => fileInputRef.current?.click()}
        sx={{
          border: '3px dashed',
          borderColor: fileError ? 'error.main' : 'divider',
          borderRadius: 2,
          p: 4,
          textAlign: 'center',
          cursor: 'pointer',
          bgcolor: file ? 'action.selected' : 'transparent',
          transition: 'all 0.2s',
          '&:hover': {
            bgcolor: (theme) => alpha(theme.palette.primary.main, 0.08),
            borderColor: fileError ? 'error.main' : 'primary.dark',
          },
        }}
      >
        <UploadIcon sx={{ fontSize: 48, color: 'primary.main', mb: 1, display: 'block', mx: 'auto' }} />
        <Typography variant="body2" color="primary.main" fontWeight="medium" sx={{ mb: 0.5 }}>
          {t('financeTransactions.billImport.uploadStep.dragDrop')}
        </Typography>
        <Typography variant="caption" color="text.disabled">
          {t('financeTransactions.billImport.uploadStep.acceptedHint', { maxMb: MAX_FILE_BYTES / (1024 * 1024) })}
        </Typography>
      </Box>

      {file && (
        <Box sx={{ mt: 2, p: 2, bgcolor: (t) => alpha(t.palette.primary.main, 0.04), borderRadius: 1, border: '1px solid', borderColor: (t) => alpha(t.palette.primary.main, 0.2) }}>
          <Stack direction="row" alignItems="center" spacing={1}>
            <FileIcon color="primary" fontSize="small" />
            <Typography variant="body2" fontWeight="medium">{file.name}</Typography>
          </Stack>
        </Box>
      )}

      {fileError && <Alert severity="error" sx={{ mt: 2 }}>{fileError}</Alert>}

      <Divider sx={{ my: 3 }} />
      <Stack direction="row" spacing={2} justifyContent="flex-end">
        <Button variant="outlined" onClick={handleClose}>{t('financeTransactions.actions.cancel')}</Button>
        <Button
          variant="contained"
          onClick={handleSubmitFile}
          disabled={!file || submitting}
          startIcon={submitting ? <CircularProgress size={18} color="inherit" /> : null}
        >
          {submitting ? t('financeTransactions.billImport.uploadStep.submitting') : t('financeTransactions.billImport.uploadStep.submit')}
        </Button>
      </Stack>
    </Box>
  );

  const renderProcessing = () => (
    <Box sx={{ p: 1 }}>
      <Typography variant="h6" sx={{ mb: 2 }}>{t('financeTransactions.billImport.processingStep.title')}</Typography>
      <LinearProgress sx={{ mb: 2 }} />
      <Typography variant="body2" color="text.secondary">
        {t('financeTransactions.billImport.processingStep.description')}
      </Typography>
      {jobData?.pdfImportJob?.status && (
        <Typography variant="caption" color="text.disabled" display="block" sx={{ mt: 1 }}>
          {t('financeTransactions.billImport.processingStep.status', { status: statusLabel(jobData.pdfImportJob.status) })}
        </Typography>
      )}
    </Box>
  );

  const renderCardMatching = () => {
    const cards = reviewCards;
    return (
      <Box sx={{ p: 1 }}>
        <Typography variant="body1" color="text.secondary" sx={{ mb: 2 }}>
          {t('financeTransactions.billImport.cardMatchingStep.description')}
        </Typography>

        {cards.map((card, ci) => {
          const resolution = cardResolutions[ci];
          const isResolved = !!resolution?.financialAccountId;
          const needsUserInput = !isResolved;

          return (
            <Paper variant="outlined" key={`${card.lastFourDigits}-${ci}`} sx={{ mb: 2, p: 2 }}>
              <Stack direction="row" alignItems="center" spacing={1.5} sx={{ mb: 1.5 }}>
                <Avatar sx={{ bgcolor: 'primary.light', width: 36, height: 36 }}>
                  <CardIcon fontSize="small" />
                </Avatar>
                <Box sx={{ flex: 1 }}>
                  <Typography variant="body1" fontWeight="medium">
                    •••• {card.lastFourDigits}
                  </Typography>
                  {card.holderName && (
                    <Typography variant="caption" color="text.secondary">{card.holderName}</Typography>
                  )}
                </Box>
                <Chip
                  size="small"
                  icon={isResolved ? <CheckIcon /> : <UnmatchedIcon />}
                  label={
                    isResolved
                      ? t('financeTransactions.billImport.cardMatchingStep.status.associated')
                      : t('financeTransactions.billImport.cardMatchingStep.status.pending')
                  }
                  color={isResolved ? 'success' : 'warning'}
                  variant="outlined"
                />
              </Stack>

              {card.matchAmbiguous && (
                <Alert severity="warning" sx={{ mb: 1.5 }}>
                  {t('financeTransactions.billImport.cardMatchingStep.ambiguousWarning', { lastFourDigits: card.lastFourDigits })}
                </Alert>
              )}

              <FormControl fullWidth size="small">
                <InputLabel>{t('financeTransactions.billImport.cardMatchingStep.cardAccountLabel')}</InputLabel>
                <Select
                  label={t('financeTransactions.billImport.cardMatchingStep.cardAccountLabel')}
                  value={resolution?.financialAccountId ?? ''}
                  onChange={(e) => {
                    const val = e.target.value as string;
                    setCardResolutions((prev) => {
                      if (!prev[ci]) return prev;
                      const updated = [...prev];
                      updated[ci] = { ...prev[ci]!, financialAccountId: val || null };
                      return updated;
                    });
                  }}
                >
                  <SelectMenuItem value="" disabled>
                    <em>{t('financeTransactions.billImport.cardMatchingStep.cardAccountPlaceholder')}</em>
                  </SelectMenuItem>
                  {/* Pre-populate with candidates if ambiguous, else all card accounts */}
                  {(card.matchAmbiguous ? card.matchCandidates : cardAccountOptions).map((opt: { id: string; name: string }) => (
                    <SelectMenuItem key={opt.id} value={opt.id}>
                      {opt.name}
                    </SelectMenuItem>
                  ))}
                </Select>
              </FormControl>

              {needsUserInput && !card.matchAmbiguous && (
                <Button
                  size="small"
                  startIcon={<AddIcon />}
                  sx={{ mt: 1 }}
                  onClick={() => {
                    setCreateCardForIndex(ci);
                    setIsCreateCardDrawerOpen(true);
                  }}
                >
                  {t('financeTransactions.billImport.cardMatchingStep.createNewCard')}
                </Button>
              )}

              <Typography variant="caption" color="text.secondary" display="block" sx={{ mt: 1 }}>
                {t('financeTransactions.billImport.cardMatchingStep.txSummary', {
                  count: card.transactions.length,
                  total: card.computedTotal.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' }),
                })}
              </Typography>
            </Paper>
          );
        })}

        <Divider sx={{ my: 3 }} />
        <Stack direction="row" spacing={2} justifyContent="flex-end">
          <Button variant="outlined" onClick={handleClose}>{t('financeTransactions.actions.cancel')}</Button>
          <Button
            variant="contained"
            disabled={!allCardsResolved}
            onClick={() => setStep('tx-review')}
          >
            {t('financeTransactions.billImport.cardMatchingStep.reviewTransactions')}
          </Button>
        </Stack>
      </Box>
    );
  };

  const renderTxReview = () => {
    const cards = reviewCards;
    const warnings = jobData?.pdfImportJob?.reviewPayload?.warnings ?? [];
    const includedCount = Object.values(txSelections).filter(Boolean).length;

    return (
      <Box sx={{ p: 1 }}>
        <Typography variant="body1" color="text.secondary" sx={{ mb: 2 }}>
          {t('financeTransactions.billImport.txReviewStep.description')}
        </Typography>

        {warnings.length > 0 && (
          <Alert severity="warning" sx={{ mb: 2 }}>
            {warnings.join(' ')}
          </Alert>
        )}

        {cards.map((card, ci) => {
          const account = cardAccountOptions.find((a) => a.id === cardResolutions[ci]?.financialAccountId);
          return (
            <Box key={`${card.lastFourDigits}-${ci}`} sx={{ mb: 3 }}>
              <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 1 }}>
                <CardIcon fontSize="small" color="action" />
                <Typography variant="subtitle2">
                  •••• {card.lastFourDigits}
                  {account && (
                    <Typography component="span" variant="caption" color="text.secondary" sx={{ ml: 1 }}>
                      — {account.name}
                    </Typography>
                  )}
                </Typography>
              </Stack>

              <Paper variant="outlined">
                <List disablePadding dense>
                  {card.transactions.map((tx, ti) => {
                    const key = `${ci}-${ti}`;
                    const included = txSelections[key] ?? true;
                    const isDebit = tx.creditDebitType === 'DEBIT';
                    const isBillPayment = !!tx.isBillPayment;

                    return (
                      <React.Fragment key={key}>
                        {ti > 0 && <Divider />}
                        <ListItem
                          sx={{ opacity: included ? 1 : 0.4, py: 0.75 }}
                          secondaryAction={
                            <Checkbox
                              edge="end"
                              checked={included}
                              onChange={(e) =>
                                setTxSelections((prev) => ({ ...prev, [key]: e.target.checked }))
                              }
                              size="small"
                            />
                          }
                        >
                          <ListItemAvatar sx={{ minWidth: 36 }}>
                            {isBillPayment ? (
                              <TransferIcon fontSize="small" color="info" />
                            ) : (
                              <Box
                                sx={{
                                  width: 8,
                                  height: 8,
                                  borderRadius: '50%',
                                  bgcolor: isDebit ? 'error.main' : 'success.main',
                                  mt: 0.5,
                                }}
                              />
                            )}
                          </ListItemAvatar>
                          <ListItemText
                            primary={
                              <Stack direction="row" alignItems="center" spacing={0.5}>
                                <Typography variant="body2" noWrap sx={{ flex: 1 }}>
                                  {tx.description}
                                </Typography>
                                {isBillPayment && (
                                  <Chip label={t('financeTransactions.billImport.txReviewStep.paymentChip')} size="small" color="info" variant="outlined" sx={{ fontSize: 10 }} />
                                )}
                                {tx.installment && (
                                  <Chip
                                    label={`${tx.installment.current}/${tx.installment.total}`}
                                    size="small"
                                    variant="outlined"
                                    sx={{ fontSize: 10 }}
                                  />
                                )}
                              </Stack>
                            }
                            secondary={
                              <Stack direction="row" spacing={1} alignItems="center">
                                <Typography variant="caption" color="text.secondary">
                                  {formatTxDisplayDate(tx.date)}
                                </Typography>
                                <Typography
                                  variant="caption"
                                  fontWeight="medium"
                                  color={isDebit ? 'error.main' : 'success.main'}
                                >
                                  {isDebit ? '- ' : '+ '}
                                  {tx.amount.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })}
                                </Typography>
                              </Stack>
                            }
                          />
                        </ListItem>
                      </React.Fragment>
                    );
                  })}
                </List>
              </Paper>
            </Box>
          );
        })}

        <Divider sx={{ my: 3 }} />
        <Stack direction="row" spacing={2} justifyContent="flex-end">
          <Button variant="outlined" onClick={() => setStep('card-matching')}>{t('financeTransactions.billImport.common.back')}</Button>
          <Button
            variant="contained"
            onClick={handleConfirm}
            disabled={confirming || includedCount === 0}
            startIcon={confirming ? <CircularProgress size={18} color="inherit" /> : null}
          >
            {confirming
              ? t('financeTransactions.billImport.txReviewStep.confirming')
              : t('financeTransactions.billImport.txReviewStep.confirmButton', { count: includedCount })}
          </Button>
        </Stack>
      </Box>
    );
  };

  const renderResult = () => {
    const cards = reviewCards;
    const total = cards.reduce((sum, c) => sum + c.transactions.length, 0);
    const included = Object.values(txSelections).filter(Boolean).length;

    return (
      <Box sx={{ p: 1 }}>
        <Alert severity="success" sx={{ mb: 2 }}>
          {t('financeTransactions.billImport.resultStep.success', { included, total })}
        </Alert>
        <Button variant="contained" fullWidth onClick={handleClose}>
          {t('financeTransactions.billImport.common.close')}
        </Button>
      </Box>
    );
  };

  const renderError = () => (
    <Box sx={{ p: 1 }}>
      <Typography variant="h6" sx={{ mb: 2 }}>{t('financeTransactions.billImport.errorStep.title')}</Typography>
      <Alert severity="error" sx={{ mb: 2 }}>
        {errorMessage ?? t('financeTransactions.billImport.errorStep.generic')}
      </Alert>
      <Stack direction="row" spacing={2}>
        <Button variant="outlined" onClick={() => { setStep('upload'); setErrorMessage(null); setJobId(null); }}>
          {t('financeTransactions.billImport.errorStep.retry')}
        </Button>
        <Button variant="contained" onClick={handleClose}>{t('financeTransactions.billImport.common.close')}</Button>
      </Stack>
    </Box>
  );

  const stepTitles: Record<BillStep, string> = {
    upload: t('financeTransactions.billImport.steps.uploadTitle'),
    processing: t('financeTransactions.billImport.steps.processingTitle'),
    'card-matching': t('financeTransactions.billImport.steps.cardMatchingTitle'),
    'tx-review': t('financeTransactions.billImport.steps.txReviewTitle'),
    result: t('financeTransactions.billImport.steps.resultTitle'),
    error: t('financeTransactions.billImport.steps.errorTitle'),
  };

  if (!open) return null;

  const body =
    step === 'upload' ? renderUpload() :
    step === 'processing' ? renderProcessing() :
    step === 'card-matching' ? renderCardMatching() :
    step === 'tx-review' ? renderTxReview() :
    step === 'result' ? renderResult() :
    renderError();

  const createCardLastFour =
    createCardForIndex !== null
      ? (reviewCards[createCardForIndex]?.lastFourDigits ?? undefined)
      : undefined;

  return (
    <>
      <Drawer open={open} onClose={handleClose} anchor="right" size="md">
        <Drawer.Header title={stepTitles[step]} showCloseButton />
        <Drawer.Body>{body}</Drawer.Body>
      </Drawer>

      <CreateFinancialAccountDrawer
        open={isCreateCardDrawerOpen}
        initialType="CARD"
        initialMaskedNumber={createCardLastFour}
        onClose={() => {
          setIsCreateCardDrawerOpen(false);
          setCreateCardForIndex(null);
        }}
        onCreated={(account) => {
          setInlineCreatedAccounts((prev) => [...prev, account]);
          if (createCardForIndex !== null) {
            setCardResolutions((prev) => {
              const cards = reviewCards;
              const base = cards.map((c, idx) => {
                const existing = prev[idx];
                return {
                  lastFourDigits: c.lastFourDigits,
                  financialAccountId:
                    existing?.financialAccountId ?? (c.matchAmbiguous ? null : (c.matchedFinancialAccountId ?? null)),
                };
              });

              if (!base[createCardForIndex]) return prev;
              base[createCardForIndex] = { ...base[createCardForIndex], financialAccountId: account.id };
              return base;
            });
          }
          setIsCreateCardDrawerOpen(false);
          setCreateCardForIndex(null);
        }}
      />
    </>
  );
}
