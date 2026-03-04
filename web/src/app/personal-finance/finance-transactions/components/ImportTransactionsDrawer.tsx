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

const MAX_FILE_BYTES = 3 * 1024 * 1024; // 3 MB
const ACCEPTED_EXTENSIONS = ['.ofx', '.csv', '.xlsx', '.xls'];
const ACCEPTED_TYPES =
  'application/x-ofx,.ofx,text/csv,.csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,.xlsx,application/vnd.ms-excel,.xls';

type Step = 'account' | 'upload' | 'processing' | 'result' | 'review' | 'error';

function fileToBase64(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      const result = reader.result as string;
      const base64 = result.includes(',') ? result.split(',')[1]! : result;
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
  onImportCompleted?: () => void;
  onRequestCreateAccount?: () => void;
  /** When set, drawer opens directly on the duplicate review step for this job (e.g. from page load). */
  reviewJobId?: string | null;
}

export function ImportTransactionsDrawer({
  open,
  onClose,
  selectedAccountId,
  onImportCompleted,
  onRequestCreateAccount,
  reviewJobId,
}: ImportTransactionsDrawerProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [step, setStep] = useState<Step>('account');
  const [targetAccountId, setTargetAccountId] = useState<string | null>(selectedAccountId);
  const [file, setFile] = useState<File | null>(null);
  const [fileError, setFileError] = useState<string | null>(null);
  const [jobId, setJobId] = useState<string | null>(null);
  const [lastEvent, setLastEvent] = useState<ImportJobStatusPayload | null>(null);
  const [errorDetails, setErrorDetails] = useState<unknown>(null);

  React.useEffect(() => {
    if (!open) return;
    if (reviewJobId) {
      setJobId(reviewJobId);
      setStep('review');
      return;
    }
    setTargetAccountId(selectedAccountId ?? null);
    setStep(selectedAccountId ? 'upload' : 'account');
    setFile(null);
    setFileError(null);
    setJobId(null);
    setLastEvent(null);
    setErrorDetails(null);
  }, [open, reviewJobId, selectedAccountId]);

  const [importFile, { loading: mutating, error: mutateError }] = useMutation<{
    importTransactionsFile: { import: { id: string } };
  }>(IMPORT_TRANSACTIONS_FILE);
  const { data: accountsData, loading: accountsLoading } = useQuery<{ financialAccounts: FinancialAccount[] }>(
    FINANCIAL_ACCOUNTS,
    {
      skip: !open || !!selectedAccountId,
      errorPolicy: 'all',
    },
  );

  const handleClose = useCallback(() => {
    onClose();
    setStep('account');
    setTargetAccountId(selectedAccountId ?? null);
    setFile(null);
    setFileError(null);
    setJobId(null);
    setLastEvent(null);
    setErrorDetails(null);
  }, [onClose, selectedAccountId]);

  useImportJobStatus(jobId, {
    onEvent: setLastEvent,
    onCompleted: () => {
      setStep('result');
      onImportCompleted?.();
    },
    onReviewRequired: () => {
      setStep('review');
    },
    onFailed: (details) => {
      setErrorDetails(details);
      setStep('error');
    },
  });

  const handleAccountSelect = (accountId: string) => {
    setTargetAccountId(accountId);
    setStep('upload');
    setFile(null);
    setFileError(null);
  };

  const handleCreateAccountFromImport = () => {
    handleClose();
    onRequestCreateAccount?.();
  };

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
    setFileError(validateFile(f));
    setFile(validateFile(f) ? null : f);
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    const f = e.dataTransfer.files?.[0];
    if (!f) return;
    setFileError(validateFile(f));
    setFile(validateFile(f) ? null : f);
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
        setStep('error');
      }
    } catch {
      setStep('error');
    }
  };

  const handleRetry = () => {
    setStep(targetAccountId ? 'upload' : 'account');
    setErrorDetails(null);
    setJobId(null);
    setLastEvent(null);
  };

  const statusLabel = (s: ImportStatus) =>
    t(`financeTransactions.import.status.${s}` as 'financeTransactions.import.status.PENDING');

  if (!open) return null;

  const renderAccountStep = () => {
    const accounts = accountsData?.financialAccounts ?? [];
    return (
      <Box sx={{ p: 1 }}>
        <Typography variant="body1" color="text.secondary" sx={{ mb: 2 }}>
          {t('financeTransactions.import.accountStep.title')}
        </Typography>
        {accountsLoading ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 3 }}>
            <CircularProgress size={24} />
          </Box>
        ) : accounts.length === 0 ? (
          <Alert severity="info" sx={{ mb: 2 }}>
            {t('financeTransactions.import.accountStep.noAccounts')}
          </Alert>
        ) : (
          <Paper variant="outlined">
            <List disablePadding>
              {accounts.map((account) => (
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
              {t('financeTransactions.import.accountStep.createAccountOrCard')}
          </Link>
        </Box>
        <Divider sx={{ my: 3 }} />
        <Stack direction="row" spacing={2} justifyContent="flex-end" sx={{ mt: 3 }}>
          <Button variant="outlined" onClick={handleClose}>
            {t('financeTransactions.actions.cancel')}
          </Button>
        </Stack>
      </Box>
    );
  };

  const renderUploadStep = () => {
    if (!targetAccountId) {
      return (
        <Box sx={{ p: 1 }}>
          <Alert severity="info">{t('financeTransactions.import.uploadStep.selectAccount')}</Alert>
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
        />
        <Box
          onDrop={handleDrop}
          onDragOver={handleDragOver}
          onClick={() => fileInputRef.current?.click()}
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
        <Divider sx={{ my: 3 }} />
        <Stack direction="row" spacing={2} sx={{ mt: 3 }} justifyContent="flex-end">
          <Button variant="outlined" onClick={() => (selectedAccountId ? handleClose() : setStep('account'))}>
            {t('financeTransactions.actions.cancel')}
          </Button>
          <Button
            variant="contained"
            onClick={handleSubmit}
            disabled={!file || mutating}
            startIcon={mutating ? <CircularProgress size={20} color="inherit" /> : null}
          >
            {mutating ? t('financeTransactions.actions.saving') : t('financeTransactions.import.button')}
          </Button>
        </Stack>
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
      <Alert severity="success" sx={{ mb: 2 }}>
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
    jobId ? (
      <ImportDuplicateReview
        jobId={jobId}
        onComplete={onImportCompleted}
        onClose={handleClose}
      />
    ) : null;

  const renderErrorStep = () => (
    <Box sx={{ p: 1 }}>
      <Typography variant="h6" sx={{ mb: 2 }}>{t('financeTransactions.import.errorStep.title')}</Typography>
      <Alert severity="error" sx={{ mb: 2 }}>
        {t('financeTransactions.import.errorStep.message')}
        {errorDetails != null && typeof errorDetails === 'string' && (
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
      />
      <Drawer.Body>{body}</Drawer.Body>
    </Drawer>
  );
}
