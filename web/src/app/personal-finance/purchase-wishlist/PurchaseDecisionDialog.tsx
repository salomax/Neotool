'use client';

import ReceiptLongRoundedIcon from '@mui/icons-material/ReceiptLongRounded';
import ShoppingBagRoundedIcon from '@mui/icons-material/ShoppingBagRounded';
import TaskAltRoundedIcon from '@mui/icons-material/TaskAltRounded';
import { alpha, useTheme } from '@mui/material/styles';
import { Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, Stack, Typography } from '@mui/material';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../i18n';
import type { WishlistItemRow } from './wishlistRowModel';

type PurchaseDecisionDialogProps = {
  row: WishlistItemRow | null;
  loading?: boolean;
  onClose: () => void;
  onConfirmPurchase: () => void;
  onConfirmPurchaseWithTransaction: () => void;
};

export function PurchaseDecisionDialog({
  row,
  loading = false,
  onClose,
  onConfirmPurchase,
  onConfirmPurchaseWithTransaction,
}: PurchaseDecisionDialogProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const theme = useTheme();

  return (
    <Dialog
      open={Boolean(row)}
      onClose={loading ? undefined : onClose}
      fullWidth
      maxWidth="sm"
    >
      <DialogTitle sx={{ px: 3, pb: 1.5, pt: 3 }}>
        <Stack direction="row" spacing={1.5} alignItems="center">
          <Box
            sx={{
              width: 44,
              height: 44,
              borderRadius: '14px',
              display: 'grid',
              placeItems: 'center',
              bgcolor: alpha(theme.palette.primary.main, 0.1),
              color: 'primary.main',
            }}
          >
            <ShoppingBagRoundedIcon fontSize="small" />
          </Box>
          <Stack spacing={0.5}>
            <Typography variant="h6">{t('purchaseWishlist.purchaseDialog.title')}</Typography>
            <Typography variant="body2" color="text.secondary">
              {t('purchaseWishlist.purchaseDialog.description')}
            </Typography>
          </Stack>
        </Stack>
      </DialogTitle>
      <DialogContent sx={{ px: 3, pb: 2 }}>
        <Stack spacing={2}>
          <Typography variant="body1">
            {t('purchaseWishlist.purchaseDialog.message', { name: row?.name ?? '' })}
          </Typography>
          <Stack spacing={1.25}>
            <Button
              variant="contained"
              color="inherit"
              onClick={onConfirmPurchase}
              disabled={loading}
              sx={{
                justifyContent: 'flex-start',
                textAlign: 'left',
                px: 2,
                py: 1.75,
                borderRadius: 2.5,
                bgcolor: alpha(theme.palette.text.primary, 0.06),
                color: 'text.primary',
                border: `1px solid ${alpha(theme.palette.text.primary, 0.08)}`,
                boxShadow: 'none',
                '&:hover': {
                  bgcolor: alpha(theme.palette.text.primary, 0.1),
                  boxShadow: 'none',
                },
              }}
              startIcon={<TaskAltRoundedIcon />}
            >
              <Stack spacing={0.25} alignItems="flex-start">
                <Typography variant="body1" fontWeight={700}>
                  {t('purchaseWishlist.purchaseDialog.confirm')}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  {t('purchaseWishlist.purchaseDialog.confirmHelper')}
                </Typography>
              </Stack>
            </Button>
            <Button
              variant="contained"
              onClick={onConfirmPurchaseWithTransaction}
              disabled={loading}
              sx={{
                justifyContent: 'flex-start',
                textAlign: 'left',
                px: 2,
                py: 1.75,
                borderRadius: 2.5,
                boxShadow: 'none',
                '&:hover': {
                  boxShadow: 'none',
                },
              }}
              startIcon={<ReceiptLongRoundedIcon />}
            >
              <Stack spacing={0.25} alignItems="flex-start">
                <Typography variant="body1" fontWeight={700}>
                  {t('purchaseWishlist.purchaseDialog.confirmWithTransaction')}
                </Typography>
                <Typography variant="body2" sx={{ color: alpha(theme.palette.common.white, 0.82) }}>
                  {t('purchaseWishlist.purchaseDialog.confirmWithTransactionHelper')}
                </Typography>
              </Stack>
            </Button>
          </Stack>
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 3, pt: 0 }}>
        <Button variant="outlined" onClick={onClose} disabled={loading}>
          {t('actions.cancel')}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
