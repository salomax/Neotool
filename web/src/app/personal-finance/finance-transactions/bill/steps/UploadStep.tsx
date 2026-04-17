import { Alert, Box, Button, CircularProgress, Divider, Stack, TextField, Typography, alpha } from '@mui/material';
import { CloudUpload as UploadIcon, InsertDriveFile as FileIcon } from '@mui/icons-material';
import { memo, type ChangeEvent, type DragEvent, type RefObject } from 'react';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../../i18n';
import { ACCEPTED_TYPES, MAX_FILE_BYTES } from '../billImportTypes';

interface UploadStepProps {
  fileInputRef: RefObject<HTMLInputElement>;
  file: File | null;
  fileError: string | null;
  isEncryptedPdf: boolean;
  pdfPassword: string;
  submitting: boolean;
  onFileChange: (e: ChangeEvent<HTMLInputElement>) => void;
  onDrop: (e: DragEvent) => void;
  onPdfPasswordChange: (value: string) => void;
  onSubmit: () => void;
  onCancel: () => void;
}

export const UploadStep = memo(function UploadStep({
  fileInputRef,
  file,
  fileError,
  isEncryptedPdf,
  pdfPassword,
  submitting,
  onFileChange,
  onDrop,
  onPdfPasswordChange,
  onSubmit,
  onCancel,
}: UploadStepProps) {
  const { t } = useTranslation(personalFinanceTranslations);

  return (
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
        onChange={onFileChange}
        style={{ display: 'none' }}
      />

      <Box
        onDrop={onDrop}
        onDragOver={(e) => e.preventDefault()}
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
        <Box
          sx={{
            mt: 2,
            p: 2,
            bgcolor: (t) => alpha(t.palette.primary.main, 0.04),
            borderRadius: 1,
            border: '1px solid',
            borderColor: (t) => alpha(t.palette.primary.main, 0.2),
          }}
        >
          <Stack direction="row" alignItems="center" spacing={1}>
            <FileIcon color="primary" fontSize="small" />
            <Typography variant="body2" fontWeight="medium">{file.name}</Typography>
          </Stack>
        </Box>
      )}

      {file && isEncryptedPdf && (
        <Stack spacing={1.25} sx={{ mt: 2 }}>
          <Alert severity="warning">
            {t('financeTransactions.billImport.uploadStep.encryptedDetected')}
          </Alert>
          <TextField
            fullWidth
            type="password"
            size="medium"
            autoComplete="new-password"
            name="pdf-password"
            label={t('financeTransactions.billImport.uploadStep.passwordLabel')}
            value={pdfPassword}
            onChange={(e) => onPdfPasswordChange(e.target.value)}
          />
        </Stack>
      )}

      {fileError && <Alert severity="error" sx={{ mt: 2 }}>{fileError}</Alert>}

      <Divider sx={{ my: 3 }} />
      <Stack direction="row" spacing={2} justifyContent="flex-end">
        <Button variant="outlined" onClick={onCancel}>
          {t('financeTransactions.actions.cancel')}
        </Button>
        <Button
          variant="contained"
          onClick={onSubmit}
          disabled={!file || submitting || (isEncryptedPdf && !pdfPassword.trim())}
          startIcon={submitting ? <CircularProgress size={18} color="inherit" /> : null}
        >
          {submitting
            ? t('financeTransactions.billImport.uploadStep.submitting')
            : t('financeTransactions.billImport.uploadStep.submit')}
        </Button>
      </Stack>
    </Box>
  );
});
