'use client';

import React, { useState, useCallback } from 'react';
import { Button, Popover, TextField, Stack, Box } from '@mui/material';
import QrCode2Icon from '@mui/icons-material/QrCode2';
import { useMutation } from '@apollo/client/react';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../i18n';
import { useToast } from '@/shared/providers';
import { IMPORT_NFCE } from '@/lib/graphql/operations/banking/mutations';

export interface ScanNfceButtonProps {
  selectedAccountId: string | null;
  onImportCompleted?: () => void;
}

function isSefazError(message: string): boolean {
  const lower = message.toLowerCase();
  return (
    lower.includes('sefaz') ||
    lower.includes('timeout') ||
    lower.includes('unavailable') ||
    lower.includes('indisponível') ||
    lower.includes('fetch')
  );
}

export function ScanNfceButton({ selectedAccountId, onImportCompleted }: ScanNfceButtonProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const toast = useToast();
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);
  const [url, setUrl] = useState('');

  const [importNfce, { loading }] = useMutation<{
    importNfce: { import: { id: string; status: string; importedCount: number; skippedCount: number } };
  }>(IMPORT_NFCE);

  const open = Boolean(anchorEl);

  const handleOpen = useCallback((e: React.MouseEvent<HTMLElement>) => {
    setAnchorEl(e.currentTarget);
    setUrl('');
  }, []);

  const handleClose = useCallback(() => {
    setAnchorEl(null);
    setUrl('');
  }, []);

  const handleSubmit = useCallback(async () => {
    const trimmed = url.trim();
    if (!trimmed || !selectedAccountId) return;

    try {
      const result = await importNfce({
        variables: { financialAccountId: selectedAccountId, qrCodeUrl: trimmed },
      });

      const data = result.data?.importNfce?.import;
      handleClose();

      if (data) {
        const imported = data.importedCount ?? 0;
        const skipped = data.skippedCount ?? 0;
        if (skipped >= 1 && imported === 0) {
          toast.info(t('financeTransactions.nfce.alreadyImported'));
        } else {
          toast.success(t('financeTransactions.nfce.success'));
        }
        onImportCompleted?.();
      }
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err);
      if (isSefazError(message)) {
        toast.error(t('financeTransactions.nfce.sefazUnavailable'));
      } else {
        toast.error(t('financeTransactions.nfce.error'));
      }
    }
  }, [url, selectedAccountId, importNfce, handleClose, toast, t, onImportCompleted]);

  return (
    <>
      <Button
        variant="outlined"
        startIcon={<QrCode2Icon />}
        onClick={handleOpen}
        disabled={!selectedAccountId}
        aria-describedby={open ? 'nfce-url-popover' : undefined}
      >
        {t('financeTransactions.nfce.button')}
      </Button>
      <Popover
        id="nfce-url-popover"
        open={open}
        anchorEl={anchorEl}
        onClose={handleClose}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'left' }}
        transformOrigin={{ vertical: 'top', horizontal: 'left' }}
      >
        <Box sx={{ p: 2, minWidth: 320 }}>
          <Stack spacing={2}>
            <TextField
              label={t('financeTransactions.nfce.urlPlaceholder')}
              value={url}
              onChange={(e) => setUrl(e.target.value)}
              fullWidth
              size="small"
              autoFocus
              onKeyDown={(e) => {
                if (e.key === 'Enter') handleSubmit();
              }}
            />
            <Stack direction="row" spacing={1} justifyContent="flex-end">
              <Button onClick={handleClose} size="small">
                {t('financeTransactions.actions.cancel')}
              </Button>
              <Button
                variant="contained"
                onClick={handleSubmit}
                disabled={!url.trim() || loading}
                size="small"
              >
                {loading ? t('financeTransactions.actions.saving') : t('financeTransactions.nfce.submit')}
              </Button>
            </Stack>
          </Stack>
        </Box>
      </Popover>
    </>
  );
}
