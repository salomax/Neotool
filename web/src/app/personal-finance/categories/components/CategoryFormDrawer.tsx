'use client';

import React, { useState, useEffect } from 'react';
import {
  Drawer,
  Typography,
  TextField,
  MenuItem,
  Stack,
  Button,
  Box,
  IconButton,
  useTheme,
  alpha,
} from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../i18n';
import { CategoryIcon } from '../../finance-transactions/category/CategoryIcon';
import { CATEGORY_ICON_NAMES, CATEGORY_COLOR_PALETTE } from '../constants';
import type { TransactionCategory } from '@/lib/graphql/types/__generated__/graphql';
import { CategoryApplicableTo } from '@/lib/graphql/types/__generated__/graphql';
import { DrawerActionButtons } from '@/shared/components/ui/layout/Drawer';

export interface CategoryFormValues {
  name: string;
  applicableTo: CategoryApplicableTo;
  parentId: string | null;
  icon: string | null;
  color: string | null;
}

const DEFAULT_VALUES: CategoryFormValues = {
  name: '',
  applicableTo: CategoryApplicableTo.Expense,
  parentId: null,
  icon: 'help_outline',
  color: CATEGORY_COLOR_PALETTE[0] ?? null,
};

interface CategoryFormDrawerProps {
  open: boolean;
  onClose: () => void;
  onSubmit: (values: CategoryFormValues) => Promise<void>;
  initialValues?: CategoryFormValues | null;
  /** When editing, parent and applicableTo are immutable. */
  isEdit?: boolean;
  /** Root categories for parent selector (create only). */
  rootCategories: TransactionCategory[];
  loading?: boolean;
}

export function CategoryFormDrawer({
  open,
  onClose,
  onSubmit,
  initialValues,
  isEdit = false,
  rootCategories,
  loading = false,
}: CategoryFormDrawerProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const theme = useTheme();
  const [values, setValues] = useState<CategoryFormValues>(DEFAULT_VALUES);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (open) {
      if (initialValues) {
        setValues(initialValues);
      } else {
        setValues(DEFAULT_VALUES);
      }
    }
  }, [open, initialValues]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!values.name.trim()) return;
    setSubmitting(true);
    try {
      await onSubmit(values);
      onClose();
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Drawer
      anchor="right"
      open={open}
      onClose={onClose}
      PaperProps={{
        sx: { width: { xs: '100%', sm: 400 } },
      }}
    >
      <Box sx={{ p: 2, display: 'flex', flexDirection: 'column', height: '100%' }}>
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 2 }}>
          <Typography variant="h6">
            {isEdit ? t('categories.form.editTitle') : t('categories.newCategory')}
          </Typography>
          <IconButton onClick={onClose} size="small" aria-label={t('actions.cancel')}>
            <CloseIcon />
          </IconButton>
        </Box>

        <form id="category-form" onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', flex: 1 }}>
          <Stack spacing={2}>
            <TextField
              label={t('categories.form.name')}
              placeholder={t('categories.form.namePlaceholder')}
              value={values.name}
              onChange={(e) => setValues((v) => ({ ...v, name: e.target.value }))}
              required
              fullWidth
              autoFocus
            />

            <TextField
              select
              label={t('categories.form.type')}
              value={values.applicableTo}
              onChange={(e) =>
                setValues((v) => ({ ...v, applicableTo: e.target.value as CategoryApplicableTo }))
              }
              fullWidth
              disabled={isEdit}
            >
              <MenuItem value={CategoryApplicableTo.Expense}>
                {t('categories.applicableTo.EXPENSE')}
              </MenuItem>
              <MenuItem value={CategoryApplicableTo.Income}>
                {t('categories.applicableTo.INCOME')}
              </MenuItem>
              <MenuItem value={CategoryApplicableTo.Any}>
                {t('categories.applicableTo.ANY')}
              </MenuItem>
              <MenuItem value={CategoryApplicableTo.Transfer}>
                {t('categories.applicableTo.TRANSFER')}
              </MenuItem>
            </TextField>

            {!isEdit && (
              <TextField
                select
                label={t('categories.form.parent')}
                value={values.parentId ?? ''}
                onChange={(e) =>
                  setValues((v) => ({
                    ...v,
                    parentId: e.target.value ? (e.target.value as string) : null,
                  }))
                }
                fullWidth
              >
                <MenuItem value="">{t('categories.form.parentNone')}</MenuItem>
                {rootCategories.map((c) => (
                  <MenuItem key={c.id} value={c.id}>
                    {c.name}
                  </MenuItem>
                ))}
              </TextField>
            )}

            <Box>
              <Typography variant="subtitle2" color="text.secondary" sx={{ mb: 1 }}>
                {t('categories.form.icon')}
              </Typography>
              <Box
                sx={{
                  display: 'flex',
                  flexWrap: 'wrap',
                  gap: 0.5,
                  maxHeight: 140,
                  overflow: 'auto',
                }}
              >
                {CATEGORY_ICON_NAMES.map((iconName) => (
                  <IconButton
                    key={iconName}
                    size="small"
                    onClick={() => setValues((v) => ({ ...v, icon: iconName }))}
                    sx={{
                      bgcolor:
                        values.icon === iconName
                          ? alpha(theme.palette.primary.main, 0.12)
                          : 'transparent',
                    }}
                    aria-label={iconName}
                  >
                    <CategoryIcon
                      iconName={iconName}
                      color={values.color ?? undefined}
                      size={24}
                    />
                  </IconButton>
                ))}
              </Box>
            </Box>

            <Box>
              <Typography variant="subtitle2" color="text.secondary" sx={{ mb: 1 }}>
                {t('categories.form.color')}
              </Typography>
              <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
                {CATEGORY_COLOR_PALETTE.map((hex) => (
                  <IconButton
                    key={hex}
                    size="small"
                    onClick={() => setValues((v) => ({ ...v, color: hex }))}
                    sx={{
                      width: 32,
                      height: 32,
                      bgcolor: hex,
                      border: values.color === hex ? 3 : 0,
                      borderColor: 'primary.main',
                      '&:hover': { bgcolor: hex, opacity: 0.9 },
                    }}
                    aria-label={hex}
                  />
                ))}
              </Box>
            </Box>
          </Stack>

          <Box sx={{ mt: 'auto', pt: 2 }}>
            <DrawerActionButtons
              cancelText={t('actions.cancel')}
              submitText={t('actions.save')}
              submittingText={t('financeTransactions.actions.saving')}
              onCancel={onClose}
              submitForm="category-form"
              cancelDisabled={submitting || loading}
              submitDisabled={!values.name.trim() || submitting || loading}
              submitting={submitting || loading}
            />
          </Box>
        </form>
      </Box>
    </Drawer>
  );
}
