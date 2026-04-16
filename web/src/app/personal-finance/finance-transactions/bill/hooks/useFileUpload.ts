import React, { useCallback, useRef, useState } from 'react';
import { useMutation } from '@apollo/client/react';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../../i18n';
import { SUBMIT_BILL_IMPORT } from '@/lib/graphql/operations/banking/mutations';
import { extractErrorCode, extractErrorMessage } from '@/shared/utils/error';
import {
  detectEncryptedPdf,
  fileToBase64,
  getExtension,
  resolveFriendlyImportErrorMessage,
  validateFile,
} from '../billImportUtils';

interface UseFileUploadOptions {
  onSubmitSuccess: (jobId: string) => void;
  onSubmitError: (message: string) => void;
}

export function useFileUpload({ onSubmitSuccess, onSubmitError }: UseFileUploadOptions) {
  const { t } = useTranslation(personalFinanceTranslations);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [file, setFile] = useState<File | null>(null);
  const [fileError, setFileError] = useState<string | null>(null);
  const [isEncryptedPdf, setIsEncryptedPdf] = useState(false);
  const [pdfPassword, setPdfPassword] = useState('');

  const [submitImport, { loading: submitting }] = useMutation<{
    submitBillImport: { id: string; status: string; errorCode?: string | null; errorMessage?: string | null };
  }>(SUBMIT_BILL_IMPORT);

  const reset = useCallback(() => {
    setFile(null);
    setFileError(null);
    setIsEncryptedPdf(false);
    setPdfPassword('');
  }, []);

  const resetError = useCallback(() => {
    setFileError(null);
  }, []);

  const processFile = useCallback(
    async (f: File) => {
      setPdfPassword('');
      setIsEncryptedPdf(false);
      const err = validateFile(f, t);
      if (err) {
        setFileError(err);
        setFile(null);
        setIsEncryptedPdf(false);
        return;
      }
      try {
        const ext = getExtension(f.name);
        const encrypted = ext === '.pdf' ? await detectEncryptedPdf(f) : false;
        setIsEncryptedPdf(encrypted);
        if (!encrypted) setPdfPassword('');
        setFileError(null);
        setFile(f);
      } catch (error) {
        setFileError(extractErrorMessage(error, 'Failed to read file'));
        setFile(null);
        setIsEncryptedPdf(false);
        setPdfPassword('');
      }
    },
    [t],
  );

  const handleFileChange = useCallback(
    async (e: React.ChangeEvent<HTMLInputElement>) => {
      const f = e.target.files?.[0];
      e.target.value = '';
      if (!f) return;
      await processFile(f);
    },
    [processFile],
  );

  const handleDrop = useCallback(
    async (e: React.DragEvent) => {
      e.preventDefault();
      const f = e.dataTransfer.files?.[0];
      if (!f) return;
      await processFile(f);
    },
    [processFile],
  );

  const handleSubmitFile = useCallback(async () => {
    if (!file) return;
    if (isEncryptedPdf && !pdfPassword.trim()) {
      setFileError(t('financeTransactions.billImport.uploadStep.passwordRequired'));
      return;
    }
    try {
      const base64 = await fileToBase64(file);
      const result = await submitImport({
        variables: {
          input: {
            fileName: file.name,
            fileBase64: base64,
            importType: 'CREDIT_CARD_BILL',
            password: isEncryptedPdf ? pdfPassword.trim() : undefined,
          },
        },
      });
      const id = result.data?.submitBillImport?.id;
      if (id) {
        onSubmitSuccess(id);
      } else {
        onSubmitError(resolveFriendlyImportErrorMessage(t));
      }
    } catch (err) {
      if (extractErrorCode(err) === 'PDF_PASSWORD_REQUIRED') {
        setIsEncryptedPdf(true);
        setPdfPassword('');
        setFileError(t('financeTransactions.billImport.uploadStep.passwordInvalid'));
        return;
      }
      const defaultMessage = resolveFriendlyImportErrorMessage(t);
      onSubmitError(extractErrorCode(err) ? extractErrorMessage(err, defaultMessage) : defaultMessage);
    }
  }, [file, isEncryptedPdf, pdfPassword, submitImport, t, onSubmitSuccess, onSubmitError]);

  return {
    fileInputRef,
    file,
    fileError,
    isEncryptedPdf,
    pdfPassword,
    setPdfPassword: useCallback((v: string) => {
      setPdfPassword(v);
      setFileError(null);
    }, []),
    submitting,
    handleFileChange,
    handleDrop,
    handleSubmitFile,
    reset,
    resetError,
  };
}
