'use client';

import { useLazyQuery, useQuery } from '@apollo/client/react';
import dayjs, { type Dayjs } from 'dayjs';
import { alpha, Alert, Box, CircularProgress, InputAdornment, MenuItem, Stack, TextField, Typography } from '@mui/material';
import type { Theme } from '@mui/material/styles';
import { useEffect, useMemo, useRef, useState } from 'react';
import { Drawer } from '@/shared/components/ui/layout/Drawer';
import { BaseMoneyInput } from '@/shared/components/ui/forms/form/MoneyInput';
import {
  APP_DATE_PICKER_VIEWS,
  appDatePickerDisplayFormatFromLanguageTag,
  mergeAppDatePickerSlotProps,
} from '@/shared/components/ui/forms/form';
import { DatePicker } from '@/shared/ui/mui-imports';
import { extractErrorMessage } from '@/shared/utils/error';
import { i18n, useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../i18n';
import { TRANSACTION_CATEGORIES } from '@/lib/graphql/operations/banking/queries';
import { CategorySelect } from '../finance-transactions/category/CategorySelect';
import {
  PurchaseWishlistProductPreviewDocument,
  type PurchaseWishlistProductPreviewQuery,
  type PurchaseWishlistProductPreviewQueryVariables,
} from './purchaseWishlistOperations.generated';
import {
  type TransactionCategory,
  CreateWishlistItemInput,
  UpdateWishlistItemInput,
  WishlistItemPriority,
} from '@/lib/graphql/types/__generated__/graphql';
import type { ChangeEvent } from 'react';
import type { WishlistItemRow } from './wishlistRowModel';

type PurchaseWishlistFormProps = {
  open: boolean;
  item?: WishlistItemRow | null;
  saving?: boolean;
  error?: unknown;
  onClose: () => void;
  onSubmit: (input: CreateWishlistItemInput | UpdateWishlistItemInput) => Promise<void> | void;
};

type FormState = {
  name: string;
  estimatedPrice: number | undefined;
  categoryId: string | null;
  priority: WishlistItemPriority;
  notes: string;
  targetDate: Dayjs | null;
  productUrl: string;
  productImageUrl: string | null;
};

type AutoFilledField = 'name' | 'estimatedPrice';

function emptyFormState(): FormState {
  return {
    name: '',
    estimatedPrice: undefined,
    categoryId: null,
    priority: WishlistItemPriority.Medium,
    notes: '',
    targetDate: null,
    productUrl: '',
    productImageUrl: null,
  };
}

function isValidUrl(value: string): boolean {
  try {
    const parsed = new URL(value);
    return parsed.protocol === 'http:' || parsed.protocol === 'https:';
  } catch {
    return false;
  }
}

function resolvedPreviewUrl(productUrl?: string | null, productImageUrl?: string | null): string | null {
  if (!productUrl?.trim() || !productImageUrl?.trim()) {
    return null;
  }
  return productUrl.trim();
}

export function PurchaseWishlistForm({
  open,
  item,
  saving = false,
  error,
  onClose,
  onSubmit,
}: PurchaseWishlistFormProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const minTargetDate = useMemo(() => dayjs().add(1, 'day').startOf('day'), []);
  const maxTargetDate = useMemo(() => dayjs().add(10, 'year').endOf('day'), []);
  const autoFillTimeoutRef = useRef<number | null>(null);
  const previewDebounceTimeoutRef = useRef<number | null>(null);
  const previewRequestIdRef = useRef(0);
  const { data: categoriesData } = useQuery<{ transactionCategories: TransactionCategory[] }>(TRANSACTION_CATEGORIES, {
    skip: !open,
  });
  const initialState = useMemo<FormState>(
    () =>
      item
        ? {
            name: item.name,
            estimatedPrice: item.estimatedPrice ?? undefined,
            categoryId: item.category?.id ?? item.categoryId ?? null,
            priority: item.priority,
            notes: item.notes ?? '',
            targetDate: typeof item.targetDate === 'string' ? dayjs(item.targetDate) : null,
            productUrl: item.productUrl ?? '',
            productImageUrl: item.productImageUrl ?? null,
          }
        : emptyFormState(),
    [item],
  );
  const itemResetKey = item?.id ?? 'new';
  const [state, setState] = useState<FormState>(initialState);
  const [attemptedSubmit, setAttemptedSubmit] = useState(false);
  const [previewImageUrl, setPreviewImageUrl] = useState<string | null>(null);
  const [highlightedFields, setHighlightedFields] = useState<AutoFilledField[]>([]);
  const [lastPreviewUrl, setLastPreviewUrl] = useState<string | null>(
    resolvedPreviewUrl(item?.productUrl, item?.productImageUrl),
  );
  const [prevItemResetKey, setPrevItemResetKey] = useState(itemResetKey);

  if (itemResetKey !== prevItemResetKey) {
    setPrevItemResetKey(itemResetKey);
    setState(initialState);
    setAttemptedSubmit(false);
    setPreviewImageUrl(item?.productImageUrl ?? null);
    setHighlightedFields([]);
    setLastPreviewUrl(resolvedPreviewUrl(item?.productUrl, item?.productImageUrl));
  }

  const normalizedProductUrl = state.productUrl.trim();
  const productUrlIsValid = Boolean(normalizedProductUrl) && isValidUrl(normalizedProductUrl);
  const displayedPreviewImageUrl = productUrlIsValid ? previewImageUrl : null;
  const [fetchPreview, { loading: previewLoading }] = useLazyQuery<
    PurchaseWishlistProductPreviewQuery,
    PurchaseWishlistProductPreviewQueryVariables
  >(PurchaseWishlistProductPreviewDocument, {
    fetchPolicy: 'no-cache',
  });
  const filteredCategories = useMemo(
    () =>
      (categoriesData?.transactionCategories ?? []).filter(
        (category) => category.applicableTo === 'EXPENSE' || category.applicableTo === 'ANY',
      ),
    [categoriesData?.transactionCategories],
  );

  const urlError = useMemo(() => {
    const normalized = state.productUrl.trim();
    if (!normalized) {
      return null;
    }

    return isValidUrl(normalized) ? null : t('purchaseWishlist.form.validation.invalidUrl');
  }, [state.productUrl, t]);

  const nameError = attemptedSubmit && !state.name.trim() ? t('purchaseWishlist.form.validation.nameRequired') : null;
  const targetDateError = useMemo(() => {
    if (!state.targetDate) {
      return null;
    }

    if (state.targetDate.isBefore(minTargetDate, 'day')) {
      return t('purchaseWishlist.form.validation.targetDateMin');
    }

    if (state.targetDate.isAfter(maxTargetDate, 'day')) {
      return t('purchaseWishlist.form.validation.targetDateMax');
    }

    return null;
  }, [maxTargetDate, minTargetDate, state.targetDate, t]);

  useEffect(
    () => () => {
      if (autoFillTimeoutRef.current) {
        window.clearTimeout(autoFillTimeoutRef.current);
      }
      if (previewDebounceTimeoutRef.current) {
        window.clearTimeout(previewDebounceTimeoutRef.current);
      }
    },
    [],
  );

  const applyFieldHighlights = (fields: AutoFilledField[]) => {
    if (fields.length === 0) {
      return;
    }

    setHighlightedFields(fields);
    if (autoFillTimeoutRef.current) {
      window.clearTimeout(autoFillTimeoutRef.current);
    }
    autoFillTimeoutRef.current = window.setTimeout(() => {
      setHighlightedFields([]);
      autoFillTimeoutRef.current = null;
    }, 1800);
  };

  const handleChange =
    <K extends keyof FormState>(field: K) =>
    (event: ChangeEvent<HTMLInputElement>) => {
      if (field === 'productUrl') {
        setPreviewImageUrl(null);
        setLastPreviewUrl(null);
        setState((current) => ({ ...current, productUrl: event.target.value, productImageUrl: null }));
        return;
      }
      setState((current) => ({ ...current, [field]: event.target.value }));
    };

  useEffect(() => {
    if (!open || !productUrlIsValid) {
      return undefined;
    }

    if (normalizedProductUrl === lastPreviewUrl) {
      return undefined;
    }

    if (previewDebounceTimeoutRef.current) {
      window.clearTimeout(previewDebounceTimeoutRef.current);
    }

    previewDebounceTimeoutRef.current = window.setTimeout(() => {
      const requestId = previewRequestIdRef.current + 1;
      previewRequestIdRef.current = requestId;

      void fetchPreview({ variables: { url: normalizedProductUrl } }).then(({ data }) => {
        if (previewRequestIdRef.current !== requestId) {
          return;
        }

        const preview = data?.wishlistProductMetadataPreview;
        setLastPreviewUrl(normalizedProductUrl);
        setPreviewImageUrl(preview?.imageUrl ?? null);

        if (!preview) {
          setState((current) => ({ ...current, productImageUrl: null }));
          return;
        }

        const autoFilledFields: AutoFilledField[] = [];
        setState((current) => {
          const nextState = { ...current };
          nextState.productImageUrl = preview.imageUrl ?? null;
          if (!current.name.trim() && preview.name?.trim()) {
            nextState.name = preview.name.trim();
            autoFilledFields.push('name');
          }
          if (current.estimatedPrice == null && typeof preview.estimatedPrice === 'number') {
            nextState.estimatedPrice = preview.estimatedPrice;
            autoFilledFields.push('estimatedPrice');
          }
          return nextState;
        });
        applyFieldHighlights(autoFilledFields);
      });
    }, 700);

    return () => {
      if (previewDebounceTimeoutRef.current) {
        window.clearTimeout(previewDebounceTimeoutRef.current);
        previewDebounceTimeoutRef.current = null;
      }
    };
  }, [fetchPreview, lastPreviewUrl, normalizedProductUrl, open, productUrlIsValid]);

  const fieldHighlightSx = (field: AutoFilledField) =>
    highlightedFields.includes(field)
      ? (theme: Theme) => ({
          '& .MuiOutlinedInput-root': {
            backgroundColor: alpha(theme.palette.primary.main, 0.06),
            transition: 'background-color 180ms ease, box-shadow 180ms ease',
            '& .MuiOutlinedInput-notchedOutline': {
              borderColor: 'primary.main',
              borderWidth: 2,
            },
          },
        })
      : undefined;

  const handleSubmit = async () => {
    setAttemptedSubmit(true);

    if (!state.name.trim() || urlError || targetDateError) {
      return;
    }

    const payload = {
      name: state.name.trim(),
      estimatedPrice: typeof state.estimatedPrice === 'number' ? state.estimatedPrice : null,
      categoryId: state.categoryId,
      priority: state.priority,
      notes: state.notes.trim() || null,
      targetDate: state.targetDate?.format('YYYY-MM-DD') ?? null,
      productUrl: normalizedProductUrl || null,
      productImageUrl: productUrlIsValid ? state.productImageUrl : null,
    };

    await onSubmit(payload);
  };

  return (
    <Drawer open={open} onClose={onClose} anchor="right" size="md">
      <Drawer.Header
        title={item ? t('purchaseWishlist.form.drawerTitleEdit') : t('purchaseWishlist.form.drawerTitleNew')}
        closeButtonAriaLabel={t('actions.close')}
      />
      <Drawer.Body>
        <Stack spacing={2}>
          {error ? <Alert severity="error">{extractErrorMessage(error)}</Alert> : null}

          <Box sx={{ width: '100%' }}>
            <TextField
              label={t('purchaseWishlist.form.productUrl')}
              value={state.productUrl}
              onChange={handleChange('productUrl')}
              error={Boolean(urlError)}
              helperText={
                urlError
                  ? urlError
                  : previewLoading
                    ? t('purchaseWishlist.form.productUrlLoading')
                    : t('purchaseWishlist.form.productUrlHelp')
              }
              fullWidth
              InputProps={{
                endAdornment: previewLoading ? (
                  <InputAdornment position="end">
                    <CircularProgress size={16} />
                  </InputAdornment>
                ) : undefined,
              }}
            />

            {displayedPreviewImageUrl ? (
              <Box
                sx={{
                  mt: 1.25,
                  borderRadius: 2,
                  overflow: 'hidden',
                  border: '1px solid',
                  borderColor: 'divider',
                  width: 120,
                  height: 120,
                  backgroundColor: 'grey.50',
                }}
              >
                <Box
                  component="img"
                  src={displayedPreviewImageUrl}
                  alt={state.name || t('purchaseWishlist.form.productPreviewAlt')}
                  sx={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block' }}
                />
              </Box>
            ) : null}
          </Box>

          <TextField
            label={t('purchaseWishlist.form.name')}
            value={state.name}
            onChange={handleChange('name')}
            error={Boolean(nameError)}
            helperText={nameError}
            required
            fullWidth
            sx={fieldHighlightSx('name')}
          />

          <Box sx={fieldHighlightSx('estimatedPrice')}>
            <BaseMoneyInput
              label={t('purchaseWishlist.form.estimatedPrice')}
              value={state.estimatedPrice}
              onChange={(value) => setState((current) => ({ ...current, estimatedPrice: value as number | undefined }))}
              locale={i18n.language}
              currency="BRL"
              fullWidth
            />
          </Box>

          <Box sx={{ width: '100%' }}>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 0.75 }}>
              {t('purchaseWishlist.form.category')}
            </Typography>
            <CategorySelect
              value={state.categoryId}
              onChange={(categoryId) => setState((current) => ({ ...current, categoryId }))}
              categories={filteredCategories}
              creditDebitType="DEBIT"
              placeholder={t('financeTransactions.category.label')}
              variant="field"
            />
          </Box>

          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ alignItems: 'flex-start' }}>
            <Box sx={{ flex: '1 1 0', minWidth: 0, width: '100%' }}>
              <DatePicker
                format={appDatePickerDisplayFormatFromLanguageTag(i18n.language)}
                views={[...APP_DATE_PICKER_VIEWS]}
                value={state.targetDate}
                onChange={(targetDate) => setState((current) => ({ ...current, targetDate }))}
                minDate={minTargetDate}
                maxDate={maxTargetDate}
                enableAccessibleFieldDOMStructure={false}
                slotProps={mergeAppDatePickerSlotProps({
                  textField: {
                    label: t('purchaseWishlist.form.targetDate'),
                    fullWidth: true,
                    size: 'medium',
                    error: Boolean(targetDateError),
                    helperText: targetDateError ?? undefined,
                  },
                })}
              />
            </Box>

            <Box sx={{ flex: '1 1 0', minWidth: 0, width: '100%' }}>
              <TextField
                select
                label={t('purchaseWishlist.form.priority')}
                value={state.priority}
                onChange={handleChange('priority')}
                fullWidth
              >
                {(['HIGH', 'MEDIUM', 'LOW'] as const).map((priority) => (
                  <MenuItem key={priority} value={priority}>
                    {t(`purchaseWishlist.priority.${priority}`)}
                  </MenuItem>
                ))}
              </TextField>
            </Box>
          </Stack>

          <TextField
            label={t('purchaseWishlist.form.notes')}
            value={state.notes}
            onChange={handleChange('notes')}
            multiline
            minRows={4}
            fullWidth
          />
        </Stack>
      </Drawer.Body>
      <Drawer.Footer>
        <Box sx={{ display: 'flex', justifyContent: 'flex-end' }}>
          <Drawer.ActionButtons
            cancelText={t('actions.cancel')}
            submitText={t('actions.save')}
            onCancel={onClose}
            onSubmit={() => void handleSubmit()}
            submitting={saving}
          />
        </Box>
      </Drawer.Footer>
    </Drawer>
  );
}
