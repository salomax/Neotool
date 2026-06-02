'use client';

import React from 'react';
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Divider,
  Stack,
  Typography,
  alpha,
} from '@mui/material';
import { CloudUpload as UploadIcon, InsertDriveFile as FileIcon } from '@mui/icons-material';
import { useMutation } from '@apollo/client/react';
import { Drawer } from '@/shared/components/ui/layout/Drawer';
import {
  CONFIRM_INVESTMENT_IMPORT,
  SUBMIT_INVESTMENT_IMPORT,
} from '@/lib/graphql/operations/banking/investmentImportOperations';
import { extractErrorMessage } from '@/shared/utils/error';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../i18n';
import {
  ConfirmInvestmentImportInput,
  InvestmentImportPreview,
  InvestmentImportReview,
} from './InvestmentImportPreview';

type DrawerStep = 'upload' | 'preview' | 'result' | 'error';

const MAX_FILE_BYTES = 10 * 1024 * 1024;
const ACCEPTED_TYPES =
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,.xlsx';

interface SubmitInvestmentImportResult {
  submitInvestmentImport: {
    id: string;
    status: string;
    fileName?: string | null;
    reviewPayload?: InvestmentImportReview | null;
    errorMessage?: string | null;
  };
}

interface ConfirmInvestmentImportResult {
  confirmInvestmentImport: {
    id: string;
    status: string;
    errorMessage?: string | null;
  };
}

interface ImportInvestmentsDrawerProps {
  open: boolean;
  onClose: () => void;
  onImportCompleted: () => void;
}

function getExtension(name: string): string {
  const idx = name.lastIndexOf('.');
  return idx >= 0 ? name.slice(idx).toLowerCase() : '';
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

export function ImportInvestmentsDrawer({ open, onClose, onImportCompleted }: ImportInvestmentsDrawerProps) {
  const { t } = useTranslation(personalFinanceTranslations);

  const fileInputRef = React.useRef<HTMLInputElement>(null);

  const [step, setStep] = React.useState<DrawerStep>('upload');
  const [file, setFile] = React.useState<File | null>(null);
  const [fileError, setFileError] = React.useState<string | null>(null);
  const [jobId, setJobId] = React.useState<string | null>(null);
  const [review, setReview] = React.useState<InvestmentImportReview | null>(null);
  const [errorMessage, setErrorMessage] = React.useState<string | null>(null);
  const [confirmError, setConfirmError] = React.useState<string | null>(null);
  const [importedCount, setImportedCount] = React.useState(0);

  const [submitInvestmentImport, { loading: submitting }] = useMutation<SubmitInvestmentImportResult>(
    SUBMIT_INVESTMENT_IMPORT,
  );
  const [confirmInvestmentImport, { loading: confirming }] = useMutation<ConfirmInvestmentImportResult>(
    CONFIRM_INVESTMENT_IMPORT,
  );

  const reset = React.useCallback(() => {
    setStep('upload');
    setFile(null);
    setFileError(null);
    setJobId(null);
    setReview(null);
    setErrorMessage(null);
    setConfirmError(null);
    setImportedCount(0);
  }, []);

  React.useEffect(() => {
    if (!open) return;
    reset();
  }, [open, reset]);

  const processFile = React.useCallback(
    (selectedFile: File) => {
      const extension = getExtension(selectedFile.name);
      if (extension !== '.xlsx') {
        setFileError(t('investments.import.drawer.onlyXlsx'));
        setFile(null);
        return;
      }

      if (selectedFile.size > MAX_FILE_BYTES) {
        setFileError(t('investments.import.drawer.maxSize'));
        setFile(null);
        return;
      }

      setFile(selectedFile);
      setFileError(null);
    },
    [t],
  );

  const handleFileChange = React.useCallback(
    (event: React.ChangeEvent<HTMLInputElement>) => {
      const selectedFile = event.target.files?.[0] ?? null;
      event.target.value = '';
      if (!selectedFile) return;
      processFile(selectedFile);
    },
    [processFile],
  );

  const handleDrop = React.useCallback(
    (event: React.DragEvent<HTMLDivElement>) => {
      event.preventDefault();
      const selectedFile = event.dataTransfer.files?.[0] ?? null;
      if (!selectedFile) return;
      processFile(selectedFile);
    },
    [processFile],
  );

  const handleSubmit = React.useCallback(async () => {
    if (!file) return;

    try {
      const base64 = await fileToBase64(file);
      const response = await submitInvestmentImport({
        variables: {
          input: {
            fileName: file.name,
            fileBase64: base64,
          },
        },
      });

      const payload = response.data?.submitInvestmentImport;
      if (!payload?.id || !payload.reviewPayload) {
        setErrorMessage(payload?.errorMessage ?? t('investments.import.drawer.genericError'));
        setStep('error');
        return;
      }

      setJobId(payload.id);
      setReview(payload.reviewPayload);
      setStep('preview');
    } catch (error) {
      setErrorMessage(extractErrorMessage(error, t('investments.import.drawer.genericError')));
      setStep('error');
    }
  }, [file, submitInvestmentImport, t]);

  const handleConfirm = React.useCallback(
    async (input: ConfirmInvestmentImportInput, rowCount: number) => {
      try {
        setConfirmError(null);
        const response = await confirmInvestmentImport({
          variables: { input },
        });

        const status = response.data?.confirmInvestmentImport?.status;
        if (status !== 'DONE') {
          setConfirmError(response.data?.confirmInvestmentImport?.errorMessage ?? t('investments.import.drawer.genericError'));
          return;
        }

        setImportedCount(rowCount);
        setStep('result');
      } catch (error) {
        setConfirmError(extractErrorMessage(error, t('investments.import.drawer.genericError')));
      }
    },
    [confirmInvestmentImport, t],
  );

  const handleCloseAfterResult = React.useCallback(() => {
    onImportCompleted();
    onClose();
  }, [onClose, onImportCompleted]);

  const title =
    step === 'upload'
      ? t('investments.import.drawer.titleUpload')
      : step === 'preview'
      ? t('investments.import.drawer.titlePreview')
      : step === 'result'
      ? t('investments.import.drawer.titleResult')
      : t('investments.import.drawer.titleError');

  return (
    <Drawer open={open} onClose={onClose} anchor="right" size="md">
      <Drawer.Header title={title} />
      <Drawer.Body>
        {step === 'upload' ? (
          <Stack spacing={2} sx={{ p: 1 }}>
            <Typography variant="body2" color="text.secondary">
              {t('investments.import.drawer.uploadHint')}
            </Typography>
            <input
              ref={fileInputRef}
              type="file"
              accept={ACCEPTED_TYPES}
              style={{ display: 'none' }}
              onChange={handleFileChange}
            />

            <Box
              onDrop={handleDrop}
              onDragOver={(event) => event.preventDefault()}
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
                cursor: 'pointer',
                bgcolor: file ? 'action.selected' : 'transparent',
                transition: 'all 0.2s',
                '&:hover': {
                  bgcolor: (theme) => alpha(theme.palette.primary.main, 0.08),
                  borderColor: fileError ? 'error.main' : 'primary.main',
                },
              }}
            >
              <UploadIcon sx={{ fontSize: 48, color: 'primary.main', mb: 1, display: 'block', mx: 'auto' }} />
              <Typography variant="body2" color="primary.main" fontWeight="medium" sx={{ mb: 0.5 }}>
                {t('investments.import.drawer.dragDrop')}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                {t('investments.import.drawer.dragDropSubtitle')}
              </Typography>
            </Box>

            {file ? (
              <Box
                sx={{
                  p: 2,
                  bgcolor: (theme) => alpha(theme.palette.primary.main, 0.04),
                  borderRadius: 1,
                  border: '1px solid',
                  borderColor: (theme) => alpha(theme.palette.primary.main, 0.2),
                }}
              >
                <Stack direction="row" spacing={1} alignItems="center">
                  <FileIcon fontSize="small" color="primary" />
                  <Typography variant="body2" fontWeight={600}>
                    {file.name}
                  </Typography>
                </Stack>
              </Box>
            ) : null}

            {fileError ? <Alert severity="error">{fileError}</Alert> : null}
          </Stack>
        ) : null}

        {step === 'preview' && review && jobId ? (
          <InvestmentImportPreview
            jobId={jobId}
            review={review}
            onConfirm={handleConfirm}
            confirmLoading={confirming}
            confirmError={confirmError}
          />
        ) : null}

        {step === 'result' ? (
          <Stack spacing={2} sx={{ p: 1 }}>
            <Alert severity="success">
              {t('investments.import.drawer.successMessage', { count: importedCount })}
            </Alert>
          </Stack>
        ) : null}

        {step === 'error' ? (
          <Stack spacing={2} sx={{ p: 1 }}>
            <Alert severity="error">{errorMessage ?? t('investments.import.drawer.genericError')}</Alert>
          </Stack>
        ) : null}
      </Drawer.Body>

      <Drawer.Footer>
        <Divider sx={{ mb: 2 }} />
        {step === 'upload' ? (
          <Drawer.ActionButtons
            cancelText={t('financeTransactions.actions.cancel')}
            submitText={t('investments.import.drawer.submitUpload')}
            submittingText={t('investments.import.drawer.submittingUpload')}
            onCancel={onClose}
            onSubmit={() => void handleSubmit()}
            submitDisabled={!file || submitting}
            submitting={submitting}
          />
        ) : null}

        {step === 'result' ? (
          <Button fullWidth variant="contained" onClick={handleCloseAfterResult}>
            {t('investments.import.drawer.close')}
          </Button>
        ) : null}

        {step === 'error' ? (
          <Stack direction="row" spacing={2}>
            <Button
              variant="outlined"
              onClick={() => {
                setErrorMessage(null);
                setStep('upload');
              }}
            >
              {t('investments.import.drawer.retry')}
            </Button>
            <Button variant="contained" onClick={onClose}>
              {t('investments.import.drawer.close')}
            </Button>
          </Stack>
        ) : null}

        {step === 'preview' && confirming ? (
          <Stack direction="row" spacing={1} alignItems="center" justifyContent="flex-end">
            <CircularProgress size={16} />
            <Typography variant="caption">{t('investments.import.drawer.confirming')}</Typography>
          </Stack>
        ) : null}
      </Drawer.Footer>
    </Drawer>
  );
}
