'use client';

import React, { useState, useCallback, useRef } from 'react';
import {
  Box,
  Typography,
  Stack,
  Button,
  LinearProgress,
  Alert,
  Paper,
  Avatar,
  List,
  ListItemButton,
  ListItemText,
  CircularProgress,
  Divider,
  alpha,
} from '@mui/material';
import { CloudUpload as UploadIcon, Add as AddIcon, AccountBalance as BankIcon, InsertDriveFile as FileIcon, TipsAndUpdates as TipsAndUpdatesIcon } from '@mui/icons-material';
import { Drawer } from '@/shared/components/ui/layout/Drawer';
import { useMutation, useQuery } from '@apollo/client/react';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../i18n';
import { useImportJobStatus, type ImportStatus, type ImportJobStatusPayload } from '@/lib/hooks/useImportJobStatus';
import { IMPORT_TRANSACTIONS_FILE } from '@/lib/graphql/operations/banking/mutations';
import { ImportDuplicateReview } from './ImportDuplicateReview';
import { FINANCIAL_ACCOUNTS } from '@/lib/graphql/operations/banking/queries';
import { FinancialAccount } from '@/lib/graphql/types/__generated__/graphql';
import Link from '@/shared/components/ui/navigation/Link';
import { cleanErrorMessage, extractErrorCode, extractErrorParameters, extractErrorMessage } from '@/shared/utils/error';
import { publishTransactionsImported } from '../events';

const MAX_FILE_BYTES = 3 * 1024 * 1024; // 3 MB
const ACCEPTED_EXTENSIONS = ['.ofx', '.csv', '.xlsx', '.xls', '.pdf'];
const ACCEPTED_TYPES =
  'application/x-ofx,.ofx,text/csv,.csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,.xlsx,application/vnd.ms-excel,.xls,application/pdf,.pdf';

type Step = 'account' | 'upload' | 'processing' | 'result' | 'review' | 'error';

interface AsyncImportErrorDetails {
  errorCode?: string;
  parameters?: Record<string, unknown>;
  message?: string;
}

function parseAsyncImportErrorDetails(raw: unknown): AsyncImportErrorDetails | null {
  if (typeof raw !== 'string' || raw.trim().length === 0) return null;

  try {
    const parsed = JSON.parse(raw) as Record<string, unknown>;
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return null;

    return {
      errorCode: typeof parsed.errorCode === 'string' ? parsed.errorCode : undefined,
      parameters:
        parsed.parameters && typeof parsed.parameters === 'object' && !Array.isArray(parsed.parameters)
          ? parsed.parameters as Record<string, unknown>
          : undefined,
      message: typeof parsed.message === 'string' ? parsed.message : undefined,
    };
  } catch {
    return null;
  }
}

function resolveAsyncImportError(
  tCommon: (key: string, params?: Record<string, unknown>) => string,
  rawDetails: unknown,
  fallbackMessage: string,
): { message: string; details: string | null } {
  if (typeof rawDetails !== 'string' || rawDetails.trim().length === 0) {
    return { message: fallbackMessage, details: null };
  }

  const parsed = parseAsyncImportErrorDetails(rawDetails);
  if (parsed?.errorCode) {
    const translationKey = `errors.${parsed.errorCode}`;
    const translated = tCommon(translationKey, parsed.parameters);
    if (translated !== translationKey) {
      return { message: translated, details: null };
    }
  }

  if (parsed?.message) {
    return { message: cleanErrorMessage(parsed.message), details: null };
  }

  // rawDetails is JSON but has no recognizable user-facing structure (e.g. array of row errors).
  // Never show raw JSON to end users.
  try {
    JSON.parse(rawDetails);
    return { message: fallbackMessage, details: null };
  } catch {
    // Not JSON — may be a plain-text message worth showing after cleanup.
  }

  return { message: cleanErrorMessage(rawDetails), details: null };
}

function fileToBase64(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      const { result } = reader;
      if (typeof result !== 'string') {
        reject(new Error('Failed to read file content as data URL'));
        return;
      }
      const commaIndex = result.indexOf(',');
      const base64 = commaIndex >= 0 ? result.slice(commaIndex + 1) : result;
      if (!base64) {
        reject(new Error('Failed to extract base64 payload from file'));
        return;
      }
      resolve(base64);
    };
    reader.onerror = () => reject(reader.error);
    reader.readAsDataURL(file);
  });
}

function getExtension(name: string): string {
  const i = name.lastIndexOf('.');
  return i >= 0 ? name.slice(i).toLowerCase() : '';
}

export interface ImportTransactionsDrawerProps {
  open: boolean;
  onClose: () => void;
  selectedAccountId: string | null;
  selectedAccountType?: FinancialAccount['type'] | null;
  onImportCompleted?: () => void;
  onRequestCreateAccount?: (type: 'BANK' | 'CARD') => void;
  /** When set, drawer opens directly on the duplicate review step for these pending review jobs (e.g. from page load). */
  reviewJobIds?: string[] | null;
}

export function ImportTransactionsDrawer({
  open,
  onClose,
  selectedAccountId,
  selectedAccountType,
  onImportCompleted,
  onRequestCreateAccount,
  reviewJobIds,
}: ImportTransactionsDrawerProps) {
  const { t, tCommon } = useTranslation(personalFinanceTranslations);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [step, setStep] = useState<Step>('account');
  const [targetAccountId, setTargetAccountId] = useState<string | null>(selectedAccountId);
  const latestTargetAccountIdRef = useRef<string | null>(null);
  const [file, setFile] = useState<File | null>(null);
  const [fileError, setFileError] = useState<string | null>(null);
  const [jobId, setJobId] = useState<string | null>(null);
  const jobIdRef = useRef<string | null>(null);
  const [reviewJobIdsState, setReviewJobIdsState] = useState<string[] | null>(null);
  const [lastEvent, setLastEvent] = useState<ImportJobStatusPayload | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [errorDetails, setErrorDetails] = useState<string | null>(null);

  const isImportableAccountType = (type: FinancialAccount['type'] | null | undefined) => type === 'BANK';
  const canUseSelectedImportableAccount = !!selectedAccountId && isImportableAccountType(selectedAccountType);

  React.useEffect(() => {
    if (!open) return;
    if (reviewJobIds && reviewJobIds.length > 0) {
      setReviewJobIdsState(reviewJobIds);
      setJobId(null); // review mode doesn't poll upload import status
      setStep('review');
      setTargetAccountId(selectedAccountId);
      setFile(null);
      setFileError(null);
      setLastEvent(null);
      setErrorMessage(null);
      setErrorDetails(null);
      return;
    }
    setReviewJobIdsState(null);
    setTargetAccountId(canUseSelectedImportableAccount ? selectedAccountId : null);
    setStep(canUseSelectedImportableAccount ? 'upload' : 'account');
    setFile(null);
    setFileError(null);
    setJobId(null);
    setLastEvent(null);
    setErrorMessage(null);
    setErrorDetails(null);
  }, [open, reviewJobIds, selectedAccountId, canUseSelectedImportableAccount]);

  React.useEffect(() => {
    latestTargetAccountIdRef.current = targetAccountId ?? selectedAccountId ?? null;
  }, [selectedAccountId, targetAccountId]);

  React.useEffect(() => {
    jobIdRef.current = jobId;
  }, [jobId]);

  const [importFile, { loading: mutating }] = useMutation<{
    importTransactionsFile: { import: { id: string } };
  }>(IMPORT_TRANSACTIONS_FILE);
  const { data: accountsData, loading: accountsLoading } = useQuery<{ financialAccounts: FinancialAccount[] }>(
    FINANCIAL_ACCOUNTS,
    {
      skip: !open || canUseSelectedImportableAccount,
      errorPolicy: 'all',
    },
  );

  React.useEffect(() => {
    if (!open) return;
    if (reviewJobIds && reviewJobIds.length > 0) return;
    if (canUseSelectedImportableAccount) return;
    if (step !== 'account') return;
    if (accountsLoading) return;

    const accounts = accountsData?.financialAccounts ?? [];
    const bankAccounts = accounts.filter((account) => isImportableAccountType(account.type));
    if (bankAccounts.length !== 1) return;

    setTargetAccountId(bankAccounts[0]!.id);
    setStep('upload');
    setFile(null);
    setFileError(null);
  }, [
    open,
    reviewJobIds,
    canUseSelectedImportableAccount,
    step,
    accountsLoading,
    accountsData?.financialAccounts,
  ]);

  const handleClose = useCallback(() => {
    onClose();
    setStep('account');
    setTargetAccountId(canUseSelectedImportableAccount ? selectedAccountId : null);
    setFile(null);
    setFileError(null);
    setJobId(null);
    setReviewJobIdsState(null);
    setLastEvent(null);
    setErrorMessage(null);
    setErrorDetails(null);
  }, [onClose, selectedAccountId, canUseSelectedImportableAccount]);

  useImportJobStatus(jobId, {
    onEvent: setLastEvent,
    onCompleted: () => {
      setStep('result');
      publishTransactionsImported({ source: 'file', financialAccountId: latestTargetAccountIdRef.current });
      onImportCompleted?.();
    },
    onReviewRequired: () => {
      const id = jobIdRef.current;
      setReviewJobIdsState(id ? [id] : []);
      setStep('review');
    },
    onFailed: (details) => {
      const fallbackMessage = t('financeTransactions.import.errorStep.message');
      const resolved = resolveAsyncImportError(tCommon, details, fallbackMessage);
      setErrorMessage(resolved.message);
      setErrorDetails(resolved.details);
      setStep('error');
    },
  });

  const handleAccountSelect = (accountId: string) => {
    setTargetAccountId(accountId);
    setStep('upload');
    setFile(null);
    setFileError(null);
  };

  const handleCancelFromUpload = () => {
    if (canUseSelectedImportableAccount) {
      handleClose();
      return;
    }

    const accounts = accountsData?.financialAccounts ?? [];
    const bankAccounts = accounts.filter((account) => isImportableAccountType(account.type));
    if (bankAccounts.length <= 1) {
      handleClose();
      return;
    }

    setTargetAccountId(null);
    setFile(null);
    setFileError(null);
    setStep('account');
  };

  const handleCreateAccountFromImport = () => {
    handleClose();
    onRequestCreateAccount?.('BANK');
  };

  const validateFile = (f: File): string | null => {
    const ext = getExtension(f.name);
    if (!ACCEPTED_EXTENSIONS.includes(ext)) {
      return t('financeTransactions.import.uploadStep.unsupportedType');
    }
    if (f.size > MAX_FILE_BYTES) {
      return t('financeTransactions.import.uploadStep.fileTooLarge', { maxMb: MAX_FILE_BYTES / (1024 * 1024) });
    }
    return null;
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const f = e.target.files?.[0];
    e.target.value = '';
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

  const handleDragOver = (e: React.DragEvent) => e.preventDefault();

  const handleSubmit = async () => {
    if (!file || !targetAccountId) return;
    setFileError(null);
    try {
      const base64 = await fileToBase64(file);
      const result = await importFile({
        variables: {
          input: {
            financialAccountId: targetAccountId,
            fileName: file.name,
            fileBase64: base64,
          },
        },
      });
      const id = result.data?.importTransactionsFile?.import?.id;
      if (id) {
        setJobId(id);
        setStep('processing');
      } else {
        setErrorMessage(t('financeTransactions.import.errorStep.message'));
        setErrorDetails(null);
        setStep('error');
      }
    } catch (err) {
      const errorCode = extractErrorCode(err);
      const parameters = extractErrorParameters(err);

      if (errorCode) {
        const translationKey = `errors.${errorCode}`;
        const translated = tCommon(translationKey, parameters);
        setErrorMessage(translated !== translationKey ? translated : t('financeTransactions.import.errorStep.message'));
      } else {
        setErrorMessage(extractErrorMessage(err, t('financeTransactions.import.errorStep.message')));
      }
      setErrorDetails(null);
      setStep('error');
    }
  };

  const handleRetry = () => {
    setStep(targetAccountId ? 'upload' : 'account');
    setErrorMessage(null);
    setErrorDetails(null);
    setJobId(null);
    setLastEvent(null);
  };

  const statusLabel = (s: ImportStatus) =>
    t(`financeTransactions.import.status.${s}` as 'financeTransactions.import.status.PENDING');

  if (!open) return null;

  const renderAccountStep = () => {
    const accounts = accountsData?.financialAccounts ?? [];
    const filteredAccounts = accounts.filter((account) => isImportableAccountType(account.type));
    return (
      <Box sx={{ p: 1 }}>
        <Typography variant="body1" color="text.secondary" sx={{ mb: 2 }}>
          {t('financeTransactions.import.accountStep.title')}
        </Typography>
        {accountsLoading ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 3 }}>
            <CircularProgress size={24} />
          </Box>
        ) : filteredAccounts.length === 0 ? (
          <Alert severity="info" sx={{ mb: 2 }}>
            {t('financeTransactions.import.accountStep.noAccounts')}
          </Alert>
        ) : (
          <Paper variant="outlined">
            <List disablePadding>
              {filteredAccounts.map((account) => (
                <ListItemButton key={account.id} onClick={() => handleAccountSelect(account.id)}>
                  <Avatar
                    src={account.issuer?.logoAsset?.publicUrl ?? undefined}
                    sx={{ mr: 2, borderRadius: 1.25, bgcolor: account.themeColor || 'primary.main' }}
                  >
                    <BankIcon />
                  </Avatar>
                  <ListItemText
                    primary={account.name}
                    secondary={account.issuer?.name || account.issuerName}
                  />
                </ListItemButton>
              ))}
            </List>
          </Paper>
        )}
        <Box sx={{ mt: 2 }}>
          <Link 
            component="button"
            variant="body1"
            underline="none"
          sx={{ 
              display: 'flex', 
              alignItems: 'center', 
              gap: 1, 
              fontWeight: 'medium',
              mt: 2,
              width: '100%'
          }}            
            onClick={handleCreateAccountFromImport}>
              <AddIcon fontSize="small" />
              {t('financeTransactions.import.accountStep.createAccount')}
          </Link>
        </Box>
        <Divider sx={{ my: 2 }} />
        <Box sx={{ display: 'flex', justifyContent: 'flex-end' }}>
          <Drawer.CancelButton onClick={handleClose}>
            {t('financeTransactions.actions.cancel')}
          </Drawer.CancelButton>
        </Box>
      </Box>
    );
  };

  const renderUploadStep = () => {
    if (!targetAccountId) {
      return (
        <Box sx={{ p: 1 }}>
          <Alert severity="info">{t('financeTransactions.import.uploadStep.selectAccount')}</Alert>
          <Divider sx={{ my: 2 }} />
          <Drawer.ActionButtons
            cancelText={t('financeTransactions.actions.cancel')}
            submitText={t('financeTransactions.import.button')}
            submittingText={t('financeTransactions.actions.saving')}
            onCancel={handleCancelFromUpload}
            onSubmit={handleSubmit}
            submitDisabled
            submitting={mutating}
            submitTestId="import-transactions-submit-button"
          />
        </Box>
      );
    }
    return (
      <Box sx={{ p: 1 }}>
        <Typography variant="body1" color="text.secondary" sx={{ mb: 2 }}>
          {t('financeTransactions.import.uploadStep.title')}
        </Typography>
        <Alert severity="info" icon={<TipsAndUpdatesIcon fontSize="inherit" />} sx={{ mb: 2 }}>
          {t('financeTransactions.import.uploadStep.recommendation')}
        </Alert>
        <input
          ref={fileInputRef}
          type="file"
          accept={ACCEPTED_TYPES}
          onChange={handleFileChange}
          style={{ display: 'none' }}
          id="import-file-input"
          data-testid="import-transactions-file-input"
        />
        <Box
          onDrop={handleDrop}
          onDragOver={handleDragOver}
          onClick={() => {
            if (fileInputRef.current) {
              fileInputRef.current.value = '';
              fileInputRef.current.click();
            }
          }}
          sx={{
            border: '3px dashed',
            borderColor: fileError ? 'error.main' : 'divider',
            borderRadius: 2,
            p: 4,
            textAlign: 'center',
            bgcolor: file ? 'action.selected' : 'transparent',
            cursor: 'pointer',
            transition: 'all 0.2s',
            '&:hover': {
              bgcolor: (theme) => alpha(theme.palette.primary.main, 0.08),
              borderColor: fileError ? 'error.main' : 'primary.dark',
            },
          }}
        >
          <Stack direction="column" alignItems="center">
            <Box>
              <UploadIcon sx={{ fontSize: 48, color: 'primary.main', mb: 1, display: 'block', mx: 'auto' }} />
              <Typography variant="body2" color="primary.main" sx={{ mb: 1, fontWeight: 'medium' }}>
                {t('financeTransactions.import.uploadStep.dragDrop')}
              </Typography>
              <Typography variant="caption" color="text.disabled">
                {t('financeTransactions.import.uploadStep.acceptedTypes')}
              </Typography>
            </Box>
            <Box>
              <Link 
                  component="span"
                  variant="body1"
                  underline="none"
                  sx={{ mt: 2, cursor: 'pointer' }}>
                {t('financeTransactions.import.uploadStep.title')}
              </Link>
            </Box>
          </Stack>
        </Box>
        {file && (
          <Box sx={{ 
            mt: 2, 
            p: 2, 
            bgcolor: (theme) => alpha(theme.palette.primary.main, 0.04), 
            borderRadius: 1, 
            border: '1px solid', 
            borderColor: (theme) => alpha(theme.palette.primary.main, 0.2) 
          }}>
            <Typography variant="caption" color="primary.main" display="block" gutterBottom sx={{ fontWeight: 'medium' }}>
              {t('financeTransactions.import.uploadStep.fileSelected')}
            </Typography>
            <Stack direction="row" alignItems="center" spacing={1}>
              <FileIcon color="primary" />
              <Typography variant="body2" sx={{ fontWeight: 'medium', color: 'text.primary' }}>{file.name}</Typography>
            </Stack>
          </Box>
        )}
        {fileError && (
          <Alert severity="error" sx={{ mt: 2 }}>{fileError}</Alert>
        )}
        <Divider sx={{ my: 2 }} />
        <Drawer.ActionButtons
          cancelText={t('financeTransactions.actions.cancel')}
          submitText={t('financeTransactions.import.button')}
          submittingText={t('financeTransactions.actions.saving')}
          onCancel={handleCancelFromUpload}
          onSubmit={handleSubmit}
          submitDisabled={!file || mutating || !targetAccountId}
          submitting={mutating}
          submitTestId="import-transactions-submit-button"
        />
      </Box>
    );
  };

  const renderProcessingStep = () => (
    <Box sx={{ p: 1 }}>
      <Typography variant="h6" sx={{ mb: 2 }}>{t('financeTransactions.import.processingStep.title')}</Typography>
      <LinearProgress sx={{ mb: 2 }} />
      <Typography variant="body2" color="text.secondary">
        {t('financeTransactions.import.processingStep.status', {
          status: statusLabel((lastEvent?.status ?? 'PENDING') as ImportStatus),
        })}
      </Typography>
      {lastEvent?.importedCount != null && (
        <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
          {t('financeTransactions.import.processingStep.imported', { count: lastEvent.importedCount })}
        </Typography>
      )}
    </Box>
  );

  const renderResultStep = () => (
    <Box sx={{ p: 1 }}>
      <Typography variant="h6" sx={{ mb: 2 }}>{t('financeTransactions.import.resultStep.title')}</Typography>
      <Alert severity="success" sx={{ mb: 2 }} data-testid="import-result-summary">
        {t('financeTransactions.import.resultStep.summary', {
          imported: lastEvent?.importedCount ?? 0,
          skipped: lastEvent?.skippedCount ?? 0,
        })}
        </Alert>
      <Button variant="contained" fullWidth onClick={handleClose}>
        {t('financeTransactions.import.resultStep.close')}
      </Button>
    </Box>
  );

  const renderReviewStep = () =>
    reviewJobIdsState && reviewJobIdsState.length > 0 ? (
      <ImportDuplicateReview
        jobIds={reviewJobIdsState}
        onComplete={() => {
          publishTransactionsImported({ source: 'file', financialAccountId: latestTargetAccountIdRef.current });
          onImportCompleted?.();
        }}
        onClose={handleClose}
      />
    ) : null;

  const renderErrorStep = () => (
    <Box sx={{ p: 1 }}>
      <Typography variant="h6" sx={{ mb: 2 }}>{t('financeTransactions.import.errorStep.title')}</Typography>
      <Alert severity="error" sx={{ mb: 2 }} data-testid="import-error-message">
        {errorMessage ?? t('financeTransactions.import.errorStep.message')}
        {errorDetails && (
          <Typography variant="caption" component="div" sx={{ mt: 1 }}>{errorDetails}</Typography>
        )}
      </Alert>
      <Stack direction="row" spacing={2}>
        <Button variant="outlined" onClick={handleRetry}>{t('financeTransactions.import.errorStep.retry')}</Button>
        <Button variant="contained" onClick={handleClose}>{t('financeTransactions.actions.cancel')}</Button>
      </Stack>
    </Box>
  );

  const body =
    step === 'account' ? renderAccountStep() :
    step === 'upload' ? renderUploadStep() :
    step === 'processing' ? renderProcessingStep() :
    step === 'result' ? renderResultStep() :
    step === 'review' ? renderReviewStep() :
    renderErrorStep();

  return (
    <Drawer open={open} onClose={handleClose} anchor="right" size="md">
      <Drawer.Header
        title={t('financeTransactions.import.title')}
        showCloseButton
        closeButtonAriaLabel={t('financeTransactions.actions.close')}
      />
      <Drawer.Body>{body}</Drawer.Body>
    </Drawer>
  );
}
