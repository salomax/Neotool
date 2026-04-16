'use client';

import React, { useCallback, useEffect, useRef, useState } from 'react';
import jsQR from 'jsqr';
import {
  Alert,
  alpha,
  Box,
  Button,
  CircularProgress,
  Divider,
  IconButton,
  Paper,
  Stack,
  Tab,
  Tabs,
  TextField,
  Typography,
} from '@mui/material';
import { ArrowBack as ArrowBackIcon, Close as CloseIcon, CloudUpload as UploadIcon } from '@mui/icons-material';
import { useMutation, useQuery } from '@apollo/client/react';
import { Drawer } from '@/shared/components/ui/layout/Drawer';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../i18n';
import { IMPORT_NFCE } from '@/lib/graphql/operations/banking/mutations';
import { ImportDuplicateReview } from './ImportDuplicateReview';
import { extractErrorCode, extractErrorMessage, extractErrorParameters } from '@/shared/utils/error';
import { FinancialAccountsDocument, type FinancialAccountsQuery } from '@/lib/graphql/operations/banking/queries.generated';
import { IssuerSelection } from '../../investments/components/IssuerSelection';

const ACCEPTED_IMAGE_EXTENSIONS = ['.jpg', '.jpeg', '.png'];
const ACCEPTED_IMAGE_TYPES = 'image/jpeg,image/png,.jpg,.jpeg,.png';
const SEFAZ_DOMAINS = [
  'nfce.fazenda.sp.gov.br',
  'dfe-portal.svrs.rs.gov.br',
  'nfe.fazenda.gov.br',
];

type Step = 'account' | 'input' | 'processing' | 'result' | 'review' | 'error';
type EntryTab = 'upload' | 'url';
type ResultKind = 'success' | 'info';

interface ImportNfceMutationData {
  importNfce: {
    import: {
      id: string;
      status: 'COMPLETED' | 'REVIEW_REQUIRED' | string;
      importedCount: number | null;
      skippedCount: number | null;
      completedAt: string | null;
    };
  };
}

interface ImportNfceMutationVariables {
  financialAccountId: string;
  qrCodeUrl: string;
}

export interface NfceImportDrawerProps {
  open: boolean;
  onClose: () => void;
  selectedAccountId: string | null;
  onImportCompleted?: () => void;
}

type CanvasSource = HTMLImageElement | ImageBitmap;

type BarcodeDetectorSource = HTMLImageElement | HTMLCanvasElement | HTMLVideoElement | ImageBitmap;

interface DetectedBarcodeLike {
  rawValue?: string;
}

interface BarcodeDetectorLike {
  detect(source: BarcodeDetectorSource): Promise<DetectedBarcodeLike[]>;
}

interface BarcodeDetectorCtorLike {
  new (options?: { formats?: string[] }): BarcodeDetectorLike;
}

function getExtension(name: string): string {
  const index = name.lastIndexOf('.');
  return index >= 0 ? name.slice(index).toLowerCase() : '';
}

function isValidImageFile(file: File): boolean {
  return file.type.startsWith('image/') || ACCEPTED_IMAGE_EXTENSIONS.includes(getExtension(file.name));
}

function isValidSefazUrl(url: string): boolean {
  try {
    const { hostname } = new URL(url);
    return SEFAZ_DOMAINS.some((domain) => hostname === domain || hostname.endsWith(`.${domain}`));
  } catch {
    return false;
  }
}

function readFileAsDataUrl(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      if (typeof reader.result !== 'string') {
        reject(new Error('Failed to read file as data URL'));
        return;
      }
      resolve(reader.result);
    };
    reader.onerror = () => reject(reader.error ?? new Error('Failed to read image'));
    reader.readAsDataURL(file);
  });
}

function loadImage(src: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const image = new Image();
    image.onload = () => resolve(image);
    image.onerror = () => reject(new Error('Failed to load image'));
    image.src = src;
  });
}

async function loadCanvasSource(file: File): Promise<CanvasSource> {
  if (typeof createImageBitmap === 'function') {
    try {
      return await createImageBitmap(file, { imageOrientation: 'from-image' });
    } catch {
      // Fall through to HTMLImageElement loading when ImageBitmap is unavailable.
    }
  }

  const dataUrl = await readFileAsDataUrl(file);
  return loadImage(dataUrl);
}

function getSourceDimensions(source: CanvasSource): { width: number; height: number } {
  if (source instanceof ImageBitmap) {
    return { width: source.width, height: source.height };
  }

  return {
    width: source.naturalWidth || source.width,
    height: source.naturalHeight || source.height,
  };
}

function renderSourceToCanvas(source: CanvasSource, maxDimension: number, rotation: 0 | 90 | 180 | 270): HTMLCanvasElement {
  const { width, height } = getSourceDimensions(source);
  const scale = Math.min(1, maxDimension / Math.max(width, height));
  const targetWidth = Math.max(1, Math.round(width * scale));
  const targetHeight = Math.max(1, Math.round(height * scale));
  const canvas = document.createElement('canvas');
  const isQuarterTurn = rotation === 90 || rotation === 270;
  canvas.width = isQuarterTurn ? targetHeight : targetWidth;
  canvas.height = isQuarterTurn ? targetWidth : targetHeight;
  const context = canvas.getContext('2d', { willReadFrequently: true });
  if (!context) {
    throw new Error('Canvas context unavailable');
  }

  switch (rotation) {
    case 90:
      context.translate(canvas.width, 0);
      context.rotate(Math.PI / 2);
      break;
    case 180:
      context.translate(canvas.width, canvas.height);
      context.rotate(Math.PI);
      break;
    case 270:
      context.translate(0, canvas.height);
      context.rotate(-Math.PI / 2);
      break;
    default:
      break;
  }

  context.drawImage(source, 0, 0, targetWidth, targetHeight);
  return canvas;
}

function getBarcodeDetector(): BarcodeDetectorCtorLike | null {
  if (typeof window === 'undefined') {
    return null;
  }

  return (window as Window & { BarcodeDetector?: BarcodeDetectorCtorLike }).BarcodeDetector ?? null;
}

async function decodeWithBarcodeDetector(canvas: HTMLCanvasElement): Promise<string | null> {
  const BarcodeDetector = getBarcodeDetector();
  if (!BarcodeDetector) {
    return null;
  }

  try {
    const detector = new BarcodeDetector({ formats: ['qr_code'] });
    const barcodes = await detector.detect(canvas);
    return barcodes[0]?.rawValue?.trim() ?? null;
  } catch {
    return null;
  }
}

function decodeWithJsQr(canvas: HTMLCanvasElement): string | null {
  const context = canvas.getContext('2d', { willReadFrequently: true });
  if (!context) {
    return null;
  }

  const imageData = context.getImageData(0, 0, canvas.width, canvas.height);
  const decoded = jsQR(imageData.data, imageData.width, imageData.height, {
    inversionAttempts: 'attemptBoth',
  });
  return decoded?.data?.trim() ?? null;
}

async function decodeQrCodeFromImage(file: File): Promise<string | null> {
  const source = await loadCanvasSource(file);
  const rotations: Array<0 | 90 | 180 | 270> = [0, 90, 180, 270];
  const maxDimensions = [2048, 1536, 1024];

  for (const maxDimension of maxDimensions) {
    for (const rotation of rotations) {
      const canvas = renderSourceToCanvas(source, maxDimension, rotation);
      const nativeDecoded = await decodeWithBarcodeDetector(canvas);
      if (nativeDecoded) {
        return nativeDecoded;
      }

      const jsQrDecoded = decodeWithJsQr(canvas);
      if (jsQrDecoded) {
        return jsQrDecoded;
      }
    }
  }

  return null;
}

export function NfceImportDrawer({
  open,
  onClose,
  selectedAccountId,
  onImportCompleted,
}: NfceImportDrawerProps) {
  const { t, tCommon } = useTranslation(personalFinanceTranslations);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [step, setStep] = useState<Step>(selectedAccountId ? 'input' : 'account');
  const [targetAccountId, setTargetAccountId] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<EntryTab>('upload');
  const [selectedImage, setSelectedImage] = useState<File | null>(null);
  const [qrCodeUrl, setQrCodeUrl] = useState('');
  const [inputError, setInputError] = useState<string | null>(null);
  const [jobId, setJobId] = useState<string | null>(null);
  const [resultKind, setResultKind] = useState<ResultKind>('success');
  const [resultMessage, setResultMessage] = useState<string | null>(null);
  const [shouldRefreshOnClose, setShouldRefreshOnClose] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const [importNfce] = useMutation<ImportNfceMutationData, ImportNfceMutationVariables>(IMPORT_NFCE);

  const { data: accountsData, loading: accountsLoading, error: accountsError } = useQuery<FinancialAccountsQuery>(
    FinancialAccountsDocument,
    {
      skip: !open || Boolean(selectedAccountId),
      errorPolicy: 'all',
    },
  );

  const resolvedFinancialAccountId = selectedAccountId ?? targetAccountId;
  const shouldShowAccountSelectionStep = !selectedAccountId && !resolvedFinancialAccountId;
  const previousOpenRef = useRef(false);

  const resetState = useCallback(() => {
    setStep(selectedAccountId ? 'input' : 'account');
    setTargetAccountId(null);
    setActiveTab('upload');
    setSelectedImage(null);
    setQrCodeUrl('');
    setInputError(null);
    setJobId(null);
    setResultKind('success');
    setResultMessage(null);
    setShouldRefreshOnClose(false);
    setErrorMessage(null);
  }, [selectedAccountId]);

  useEffect(() => {
    if (!open) {
      previousOpenRef.current = false;
      return;
    }
    if (previousOpenRef.current) return;
    previousOpenRef.current = true;

    setStep(selectedAccountId ? 'input' : 'account');
    setTargetAccountId(null);
    setActiveTab('upload');
    setSelectedImage(null);
    setQrCodeUrl('');
    setInputError(null);
    setJobId(null);
    setResultKind('success');
    setResultMessage(null);
    setShouldRefreshOnClose(false);
    setErrorMessage(null);
  }, [open, selectedAccountId]);

  const handleClose = useCallback(() => {
    if (shouldRefreshOnClose) {
      onImportCompleted?.();
    }
    resetState();
    onClose();
  }, [onClose, onImportCompleted, resetState, shouldRefreshOnClose]);

  const handleRetry = useCallback(() => {
    setStep(resolvedFinancialAccountId ? 'input' : 'account');
    setErrorMessage(null);
  }, [resolvedFinancialAccountId]);

  const handleUploadFile = useCallback(
    async (file: File) => {
      setSelectedImage(file);
      setInputError(null);
      setQrCodeUrl('');

      if (!isValidImageFile(file)) {
        setInputError(t('financeTransactions.import.uploadStep.unsupportedType'));
        return;
      }

      try {
        const decoded = await decodeQrCodeFromImage(file);
        if (!decoded) {
          setInputError(t('financeTransactions.nfce.qrNotFound'));
          return;
        }
        if (!isValidSefazUrl(decoded)) {
          setInputError(t('financeTransactions.nfce.invalidUrl'));
          return;
        }
        setQrCodeUrl(decoded);
      } catch {
        setInputError(t('financeTransactions.nfce.qrNotFound'));
      }
    },
    [t],
  );

  const handleFileChange = useCallback(
    async (event: React.ChangeEvent<HTMLInputElement>) => {
      const file = event.target.files?.[0];
      event.target.value = '';
      if (!file) return;
      await handleUploadFile(file);
    },
    [handleUploadFile],
  );

  const handleDrop = useCallback(
    async (event: React.DragEvent) => {
      event.preventDefault();
      const file = event.dataTransfer.files?.[0];
      if (!file) return;
      await handleUploadFile(file);
    },
    [handleUploadFile],
  );

  const handleSubmit = useCallback(
    async (overrideUrl?: string) => {
      if (!resolvedFinancialAccountId) return;
      const nextUrl = (overrideUrl ?? qrCodeUrl).trim();

      if (!nextUrl || !isValidSefazUrl(nextUrl)) {
        setInputError(t('financeTransactions.nfce.invalidUrl'));
        return;
      }

      setInputError(null);
      setStep('processing');
      setShouldRefreshOnClose(false);

      const importStartedAt = new Date();
      try {
        const result = await importNfce({
          variables: {
            financialAccountId: resolvedFinancialAccountId,
            qrCodeUrl: nextUrl,
          },
        });

        const payload = result.data?.importNfce?.import;
        if (!payload?.id) {
          setErrorMessage(t('financeTransactions.nfce.error'));
          setStep('error');
          return;
        }

        setJobId(payload.id);

        if (payload.status === 'REVIEW_REQUIRED') {
          setStep('review');
          return;
        }

        const isAlreadyImported =
          ((payload.importedCount ?? 0) === 0 && (payload.skippedCount ?? 0) > 0) ||
          (payload.completedAt != null && new Date(payload.completedAt) < importStartedAt);

        if (isAlreadyImported) {
          setResultKind('info');
          setResultMessage(t('financeTransactions.nfce.alreadyImported'));
          setShouldRefreshOnClose(false);
        } else {
          setResultKind('success');
          setResultMessage(t('financeTransactions.nfce.success'));
          setShouldRefreshOnClose(true);
        }
        setStep('result');
      } catch (error) {
        const errorCode = extractErrorCode(error);
        const errorParameters = extractErrorParameters(error);

        if (errorCode === 'NFCE_ALREADY_IMPORTED') {
          setResultKind('info');
          setResultMessage(t('financeTransactions.nfce.alreadyImported'));
          setShouldRefreshOnClose(false);
          setStep('result');
          return;
        }

        if (errorCode === 'SEFAZ_UNAVAILABLE') {
          setErrorMessage(t('financeTransactions.nfce.sefazUnavailable'));
          setStep('error');
          return;
        }

        if (errorCode) {
          const translationKey = `errors.${errorCode}`;
          const translated = tCommon(translationKey, errorParameters);
          setErrorMessage(
            translated !== translationKey
              ? translated
              : extractErrorMessage(error, t('financeTransactions.nfce.error')),
          );
        } else {
          setErrorMessage(extractErrorMessage(error, t('financeTransactions.nfce.error')));
        }
        setStep('error');
      }
    },
    [importNfce, qrCodeUrl, resolvedFinancialAccountId, t, tCommon],
  );

  const renderAccountStep = () => {
    if (accountsLoading) {
      return (
        <Stack spacing={2} alignItems="center" justifyContent="center" sx={{ minHeight: 260 }}>
          <CircularProgress />
        </Stack>
      );
    }

    const accounts = accountsData?.financialAccounts ?? [];
    const errorFallback = t('financeTransactions.nfce.error');

    return (
      <Stack spacing={2}>
        {accountsError ? (
          <Alert severity="error">{extractErrorMessage(accountsError, errorFallback)}</Alert>
        ) : null}
        <IssuerSelection
          financialAccounts={accounts}
          description={t('financeTransactions.import.accountStep.title')}
          emptyState={t('financeTransactions.import.accountStep.noAccounts')}
          onSelect={(accountId) => {
            setTargetAccountId(accountId);
            setStep('input');
          }}
          dataTestIdPrefix="nfce-import-account-"
        />
      </Stack>
    );
  };

  const renderInputStep = () => (
    <Box sx={{ p: 1 }}>
      <Tabs value={activeTab} onChange={(_, value) => setActiveTab(value)} sx={{ mb: 2 }}>
        <Tab value="upload" label={t('financeTransactions.nfce.uploadTab')} />
        <Tab value="url" label={t('financeTransactions.nfce.urlTab')} />
      </Tabs>

      {activeTab === 'upload' ? (
        <Stack spacing={2}>
          <input
            ref={fileInputRef}
            type="file"
            accept={ACCEPTED_IMAGE_TYPES}
            onChange={handleFileChange}
            style={{ display: 'none' }}
          />
          <Paper
            variant="outlined"
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
              borderColor: inputError ? 'error.main' : 'divider',
              borderRadius: 2,
              p: 4,
              textAlign: 'center',
              cursor: 'pointer',
              transition: 'all 0.2s',
              bgcolor: selectedImage ? 'action.selected' : 'transparent',
              '&:hover': {
                bgcolor: (theme) => alpha(theme.palette.primary.main, 0.08),
                borderColor: inputError ? 'error.main' : 'primary.dark',
              },
            }}
          >
            <UploadIcon sx={{ fontSize: 48, color: 'primary.main', mb: 1 }} />
            <Typography variant="body1" color="text.secondary">
              {t('financeTransactions.nfce.uploadHint')}
            </Typography>
          </Paper>

          {selectedImage && (
            <Box
              sx={{
                p: 2,
                borderRadius: 1,
                border: '1px solid',
                borderColor: (theme) => alpha(theme.palette.primary.main, 0.2),
                bgcolor: (theme) => alpha(theme.palette.primary.main, 0.04),
              }}
            >
              <Typography variant="caption" color="primary.main" display="block" gutterBottom sx={{ fontWeight: 'medium' }}>
                {t('financeTransactions.import.uploadStep.fileSelected')}
              </Typography>
              <Typography variant="body2">{selectedImage.name}</Typography>
            </Box>
          )}

          {qrCodeUrl && !inputError && (
            <TextField
              label={t('financeTransactions.nfce.urlPlaceholder')}
              value={qrCodeUrl}
              fullWidth
              size="small"
              InputProps={{ readOnly: true }}
            />
          )}
        </Stack>
      ) : (
        <TextField
          label={t('financeTransactions.nfce.urlPlaceholder')}
          placeholder={t('financeTransactions.nfce.urlPlaceholder')}
          value={qrCodeUrl}
          onChange={(event) => {
            setQrCodeUrl(event.target.value);
            setInputError(null);
          }}
          fullWidth
          size="small"
        />
      )}

      {inputError && (
        <Alert severity="error" sx={{ mt: 2 }}>
          {inputError}
        </Alert>
      )}

      <Divider sx={{ my: 2 }} />

      <Drawer.ActionButtons
        cancelText={t('financeTransactions.actions.cancel')}
        submitText={t('financeTransactions.nfce.submit')}
        submittingText={t('financeTransactions.actions.saving')}
        onCancel={handleClose}
        onSubmit={() => handleSubmit()}
        submitDisabled={!resolvedFinancialAccountId || !qrCodeUrl.trim()}
      />
    </Box>
  );

  const renderProcessingStep = () => (
    <Stack spacing={2} alignItems="center" justifyContent="center" sx={{ minHeight: 260, p: 2 }}>
      <CircularProgress />
      <Typography variant="body1" color="text.secondary">
        {t('financeTransactions.nfce.processing')}
      </Typography>
    </Stack>
  );

  const renderResultStep = () => (
    <Box sx={{ p: 1 }}>
      <Alert severity={resultKind} sx={{ mb: 2 }}>
        {resultMessage}
      </Alert>
      <Button variant="contained" fullWidth onClick={handleClose}>
        {t('financeTransactions.import.resultStep.close')}
      </Button>
    </Box>
  );

  const renderReviewStep = () =>
    jobId ? (
      <ImportDuplicateReview
        jobIds={[jobId]}
        onComplete={() => {
          onImportCompleted?.();
          onClose();
        }}
        onClose={onClose}
      />
    ) : null;

  const renderErrorStep = () => (
    <Box sx={{ p: 1 }}>
      <Alert severity="error" sx={{ mb: 2 }}>
        {errorMessage}
      </Alert>
      <Stack direction="row" spacing={2} justifyContent="flex-end">
        <Button onClick={handleClose}>{t('financeTransactions.actions.cancel')}</Button>
        <Button variant="contained" onClick={handleRetry}>
          {t('financeTransactions.import.errorStep.retry')}
        </Button>
      </Stack>
    </Box>
  );

  return (
    <Drawer open={open} onClose={handleClose} anchor="right" size="md" ariaLabel={t('financeTransactions.nfce.drawerTitle')}>
      {shouldShowAccountSelectionStep ? (
        <Drawer.Header
          title={t('financeTransactions.nfce.drawerTitle')}
          showCloseButton
          closeButtonAriaLabel={t('financeTransactions.actions.close')}
        />
      ) : (
        <Drawer.Header>
          <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', width: '100%' }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, minWidth: 0 }}>
              {!selectedAccountId ? (
                <IconButton
                  onClick={() => {
                    setTargetAccountId(null);
                    setStep('account');
                    setActiveTab('upload');
                    setSelectedImage(null);
                    setQrCodeUrl('');
                    setInputError(null);
                    setJobId(null);
                    setResultKind('success');
                    setResultMessage(null);
                    setShouldRefreshOnClose(false);
                    setErrorMessage(null);
                  }}
                  size="small"
                  aria-label={t('financeTransactions.actions.back')}
                >
                  <ArrowBackIcon />
                </IconButton>
              ) : null}
              <Typography
                variant="h5"
                component="h2"
                fontWeight={400}
                noWrap
                sx={{ letterSpacing: '0.05em', lineHeight: 1.15, color: 'action.active' }}
              >
                {t('financeTransactions.nfce.drawerTitle').toLocaleUpperCase(
                  Intl.DateTimeFormat().resolvedOptions().locale,
                )}
              </Typography>
            </Box>
            <IconButton onClick={handleClose} size="small" aria-label={t('financeTransactions.actions.close')}>
              <CloseIcon />
            </IconButton>
          </Box>
        </Drawer.Header>
      )}
      <Drawer.Body>
        {step === 'account' && renderAccountStep()}
        {step === 'input' && renderInputStep()}
        {step === 'processing' && renderProcessingStep()}
        {step === 'result' && renderResultStep()}
        {step === 'review' && renderReviewStep()}
        {step === 'error' && renderErrorStep()}
      </Drawer.Body>
    </Drawer>
  );
}
