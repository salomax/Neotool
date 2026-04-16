'use client';

import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Alert,
  Avatar,
  Box,
  Button,
  Chip,
  CircularProgress,
  InputAdornment,
  List,
  ListItemAvatar,
  ListItemButton,
  ListItemText,
  Link,
  Popover,
  Stack,
  TextField,
  Typography,
  alpha,
  useTheme,
  IconButton,
} from '@mui/material';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import SearchIcon from '@mui/icons-material/Search';
import BlockIcon from '@mui/icons-material/Block';
import AddIcon from '@mui/icons-material/Add';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import CheckIcon from '@mui/icons-material/Check';
import { useLazyQuery, useMutation } from '@apollo/client/react';
import { useDebounce } from '@/shared/hooks/ui';
import { MERCHANTS, MERCHANT_LOGO_PREVIEW } from '@/lib/graphql/operations/banking/queries';
import { CREATE_MERCHANT } from '@/lib/graphql/operations/banking/mutations';
import { useTranslation } from '@/shared/i18n';
import { extractErrorMessage } from '@/shared/utils/error';
import { personalFinanceTranslations } from '../../i18n';
import type { Merchant } from '@/lib/graphql/types/__generated__/graphql';

type MerchantPickerView = 'browse' | 'create' | 'confirm';

type MerchantChipData = {
  id: string;
  name: string;
  domain?: string | null;
  logoAsset?: { publicUrl?: string | null } | null;
};

interface MerchantConnectionData {
  merchants: {
    edges: Array<{
      cursor: string;
      node: Merchant;
    }>;
    pageInfo: {
      hasNextPage: boolean;
      hasPreviousPage: boolean;
      startCursor: string | null;
      endCursor: string | null;
    };
    totalCount: number;
  };
}

interface MerchantsVariables {
  search?: string;
  after?: string;
  first?: number;
}

interface CreateMerchantData {
  createMerchant: Merchant;
}

interface CreateMerchantVariables {
  input: {
    name: string;
    website?: string;
  };
}

interface MerchantLogoPreviewData {
  merchantLogoPreview: {
    id: string;
    publicUrl: string | null;
  } | null;
}

interface MerchantLogoPreviewVariables {
  domain: string;
}

interface MerchantSelectProps {
  value: string | null;
  /** Pass `initialValues.merchant` so the chip renders without fetching the full list. */
  initialMerchant?: MerchantChipData | null;
  onChange: (merchantId: string | null) => void;
  placeholder?: string;
}

const PAGE_SIZE = 20;
const CONFIRM_DURATION_MS = 1500;

export function normalizeMerchantWebsiteDomain(raw: string): string | null {
  // Keep this normalization aligned with MerchantWebsiteNormalizer on backend.
  const input = raw.trim();
  if (!input) return null;
  const withoutScheme = input.replace(/^[a-zA-Z][a-zA-Z0-9+.-]*:\/\//, '');
  const host = withoutScheme.split(/[/?#]/, 1)[0]?.trim().replace(/\.$/, '').toLowerCase().replace(/^www\./, '');
  if (!host) return null;
  if (host.length > 200) return null;
  if (!/^(?!-)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,63}$/.test(host)) return null;
  return host;
}

export function MerchantSelect({ value, initialMerchant, onChange, placeholder }: MerchantSelectProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const theme = useTheme();
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);
  const [view, setView] = useState<MerchantPickerView>('browse');
  const [searchInput, setSearchInput] = useState('');
  const [createName, setCreateName] = useState('');
  const [createWebsite, setCreateWebsite] = useState('');
  const [confirmName, setConfirmName] = useState<string | null>(null);
  const [createdMerchant, setCreatedMerchant] = useState<MerchantChipData | null>(null);
  const [loadingMore, setLoadingMore] = useState(false);
  const [logoPreviewUrl, setLogoPreviewUrl] = useState<string | null>(null);
  const browseListRef = useRef<HTMLUListElement | null>(null);
  const infiniteScrollSentinelRef = useRef<HTMLDivElement | null>(null);
  const lastFetchCursorRef = useRef<string | null>(null);

  const debouncedSearch = useDebounce(searchInput, 300);
  const debouncedWebsite = useDebounce(createWebsite, 500);
  const normalizedSearch = debouncedSearch.trim() || undefined;
  const normalizedPreviewDomain = useMemo(
    () => normalizeMerchantWebsiteDomain(debouncedWebsite),
    [debouncedWebsite],
  );
  const [loadLogoPreview] = useLazyQuery<MerchantLogoPreviewData, MerchantLogoPreviewVariables>(
    MERCHANT_LOGO_PREVIEW,
    { fetchPolicy: 'no-cache' },
  );

  const [loadMerchants, { data, loading, fetchMore, called }] = useLazyQuery<MerchantConnectionData, MerchantsVariables>(
    MERCHANTS,
    { notifyOnNetworkStatusChange: true, fetchPolicy: 'network-only' },
  );

  const [createMerchant, { loading: createLoading, error: createError, reset: resetCreateState }] = useMutation<
    CreateMerchantData,
    CreateMerchantVariables
  >(CREATE_MERCHANT);

  const open = Boolean(anchorEl);
  const resolvedPlaceholder = placeholder ?? t('financeTransactions.merchant');
  const connection = data?.merchants;
  const merchants = useMemo(() => connection?.edges.map((edge) => edge.node) ?? [], [connection?.edges]);
  const totalCount = connection?.totalCount ?? 0;
  const pageInfo = connection?.pageInfo;
  const isInitialBrowseLoading = !called || (loading && merchants.length === 0);

  const selectedMerchant = useMemo<MerchantChipData | Merchant | null>(() => {
    if (!value) return null;
    return (
      merchants.find((m) => m.id === value) ??
      (createdMerchant?.id === value ? createdMerchant : null) ??
      (initialMerchant?.id === value ? initialMerchant : null)
    );
  }, [value, merchants, createdMerchant, initialMerchant]);

  const handleLoadMore = useCallback(async () => {
    if (!fetchMore || !pageInfo?.hasNextPage || !pageInfo.endCursor) return;
    setLoadingMore(true);
    try {
      await fetchMore({
        variables: {
          first: PAGE_SIZE,
          search: normalizedSearch,
          after: pageInfo.endCursor,
        },
        updateQuery: (previousResult, { fetchMoreResult }) => {
          if (!fetchMoreResult?.merchants) return previousResult;
          return {
            merchants: {
              ...fetchMoreResult.merchants,
              edges: [...previousResult.merchants.edges, ...fetchMoreResult.merchants.edges],
            },
          };
        },
      });
    } finally {
      setLoadingMore(false);
    }
  }, [fetchMore, normalizedSearch, pageInfo?.endCursor, pageInfo?.hasNextPage]);

  useEffect(() => {
    if (!open || view !== 'browse') return;
    loadMerchants({
      variables: { first: PAGE_SIZE, search: normalizedSearch },
    });
  }, [open, view, normalizedSearch, loadMerchants]);

  useEffect(() => {
    lastFetchCursorRef.current = null;
  }, [normalizedSearch, open, view]);

  useEffect(() => {
    if (!open || view !== 'browse') return;
    if (!pageInfo?.hasNextPage || !pageInfo.endCursor) return;
    if (loadingMore || loading) return;

    const root = browseListRef.current;
    const sentinel = infiniteScrollSentinelRef.current;
    if (!root || !sentinel) return;

    const observer = new IntersectionObserver(
      (entries) => {
        const entry = entries[0];
        if (!entry?.isIntersecting) return;
        if (!pageInfo?.hasNextPage || !pageInfo.endCursor) return;
        if (loadingMore || loading) return;

        if (lastFetchCursorRef.current === pageInfo.endCursor) return;
        lastFetchCursorRef.current = pageInfo.endCursor;
        void handleLoadMore();
      },
      { root, rootMargin: '80px' },
    );

    observer.observe(sentinel);
    return () => observer.disconnect();
  }, [open, view, pageInfo?.endCursor, pageInfo?.hasNextPage, loadingMore, loading, handleLoadMore]);

  useEffect(() => {
    if (view === 'confirm' && confirmName) {
      const timeoutId = setTimeout(() => setAnchorEl(null), CONFIRM_DURATION_MS);
      return () => clearTimeout(timeoutId);
    }
  }, [view, confirmName]);

  useEffect(() => {
    if (!open) {
      setView('browse');
      setSearchInput('');
      setCreateName('');
      setCreateWebsite('');
      setLogoPreviewUrl(null);
      setConfirmName(null);
      resetCreateState();
    }
  }, [open, resetCreateState]);

  useEffect(() => {
    if (view !== 'create') return;
    if (!normalizedPreviewDomain) {
      setLogoPreviewUrl(null);
      return;
    }
    let cancelled = false;
    void loadLogoPreview({ variables: { domain: normalizedPreviewDomain } })
      .then((result) => {
        if (cancelled) return;
        setLogoPreviewUrl(result.data?.merchantLogoPreview?.publicUrl ?? null);
      })
      .catch(() => {
        if (cancelled) return;
        setLogoPreviewUrl(null);
      });
    return () => {
      cancelled = true;
    };
  }, [loadLogoPreview, normalizedPreviewDomain, view]);

  const handleOpen = useCallback((event: React.MouseEvent<HTMLElement>) => {
    setAnchorEl(event.currentTarget);
  }, []);

  const handleClose = useCallback(() => {
    setAnchorEl(null);
  }, []);

  const handleSelect = useCallback(
    (merchantId: string | null) => {
      onChange(merchantId);
      handleClose();
    },
    [handleClose, onChange],
  );

  const handleBackToBrowse = useCallback(() => {
    setView('browse');
    setConfirmName(null);
    resetCreateState();
  }, [resetCreateState]);

  const handleOpenCreate = useCallback(() => {
    setCreateName('');
    setCreateWebsite('');
    setView('create');
    resetCreateState();
  }, [resetCreateState]);

  const handleCreateSubmit = useCallback(async () => {
    const trimmedName = createName.trim();
    if (!trimmedName) return;

    const websiteInput = createWebsite.trim();

    try {
      const result = await createMerchant({
        variables: {
          input: {
            name: trimmedName,
            website: websiteInput || undefined,
          },
        },
      });
      const merchant = result.data?.createMerchant;
      if (!merchant) return;

      setCreatedMerchant(merchant);
      onChange(merchant.id);
      setConfirmName(merchant.name);
      setView('confirm');
      loadMerchants({
        variables: { first: PAGE_SIZE, search: normalizedSearch },
      });
    } catch {
      // handled by Apollo error state
    }
  }, [createMerchant, createName, createWebsite, loadMerchants, normalizedSearch, onChange]);

  const renderBrowse = () => (
    <Box sx={{ display: 'flex', flexDirection: 'column', flex: 1, minHeight: 0 }}>
      <Box sx={{ p: 1, borderBottom: 1, borderColor: 'divider', flexShrink: 0 }}>
        <TextField
          fullWidth
          autoFocus
          value={searchInput}
          onChange={(event) => setSearchInput(event.target.value)}
          placeholder={t('financeTransactions.merchantPicker.searchPlaceholder')}
          inputProps={{ 'data-testid': 'merchant-picker-search-input' }}
          InputProps={{
            startAdornment: (
              <InputAdornment position="start">
                <SearchIcon fontSize="small" />
              </InputAdornment>
            ),
            disableUnderline: true,
            sx: { '& fieldset': { border: 'none' } },
          }}
          variant="standard"
        />
      </Box>

      <List ref={browseListRef} dense disablePadding sx={{ overflow: 'auto', flex: 1, minHeight: 0 }}>
        {isInitialBrowseLoading ? (
          <Box sx={{ py: 3, display: 'flex', justifyContent: 'center' }}>
            <CircularProgress size={20} />
          </Box>
        ) : (
          <>
            <ListItemButton onClick={() => handleSelect(null)} selected={!value} sx={{ gap: 1, py: 0.75 }}>
              <BlockIcon fontSize="small" sx={{ color: 'text.disabled', ml: 0.5 }} />
              <ListItemText
                primary={
                  <Typography variant="body2" color="text.secondary">
                    {t('financeTransactions.noMerchant')}
                  </Typography>
                }
              />
            </ListItemButton>

            {merchants.map((merchant) => (
              <ListItemButton
                key={merchant.id}
                onClick={() => handleSelect(merchant.id)}
                selected={merchant.id === value}
                sx={{ gap: 0.5, py: 0.75 }}
              >
                <ListItemAvatar sx={{ minWidth: 36 }}>
                  <Avatar
                    src={merchant.logoAsset?.publicUrl ?? undefined}
                    sx={{ width: 24, height: 24, borderRadius: '.2rem', fontSize: '0.7rem' }}
                  >
                    {merchant.name.charAt(0)}
                  </Avatar>
                </ListItemAvatar>
                <ListItemText primary={<Typography variant="body2">{merchant.name}</Typography>} />
              </ListItemButton>
            ))}

            {merchants.length === 0 && (
              <Box sx={{ py: 2, textAlign: 'center' }}>
                <Typography variant="body2" color="text.secondary">
                  {t('financeTransactions.merchantPicker.noResults')}
                </Typography>
              </Box>
            )}

            {pageInfo?.hasNextPage ? (
              <Box sx={{ px: 1.5, py: 1, display: 'flex', justifyContent: 'center' }}>
                {loadingMore ? <CircularProgress size={18} /> : null}
              </Box>
            ) : null}

            <Box ref={infiniteScrollSentinelRef} sx={{ height: 1 }} />

            {totalCount > 0 ? (
              <Box sx={{ px: 1.5, pb: 0.5 }}>
                <Typography variant="caption" color="text.secondary">
                  {t('financeTransactions.merchantPicker.resultCount', {
                    shown: merchants.length,
                    total: totalCount,
                  })}
                </Typography>
              </Box>
            ) : null}
          </>
        )}
      </List>

      <Box
        component="footer"
        sx={{
          borderTop: 1,
          borderColor: 'divider',
          flexShrink: 0,
          p: 1,
          height: 48,
          bgcolor: 'background.paper',
        }}
      >
        <Link
          component="button"
          onClick={handleOpenCreate}
          underline="none"
          sx={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: 0.5,
            cursor: 'pointer',
          }}
          data-testid="merchant-picker-create-button"
        >
          <AddIcon fontSize="small" />
          {t('financeTransactions.merchantPicker.createMerchant')}
        </Link>
      </Box>
    </Box>
  );

  const renderCreate = () => (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <Stack
        direction="row"
        alignItems="center"
        spacing={1}
        sx={{
          p: 1,
          borderBottom: 1,
          borderColor: 'divider',
          bgcolor: alpha(theme.palette.primary.main, 0.04),
        }}
      >
        <IconButton
          data-testid="merchant-picker-back-button"
          size="small"
          onClick={handleBackToBrowse}
          aria-label={t('financeTransactions.actions.back')}
          sx={{ mr: 0.5 }}
        >
          <ArrowBackIcon fontSize="small" />
        </IconButton>
        <Typography variant="subtitle1" fontWeight={600}>
          {t('financeTransactions.merchantPicker.createMerchant')}
        </Typography>
      </Stack>

      <Stack
        component="form"
        onSubmit={(event) => {
          event.preventDefault();
          void handleCreateSubmit();
        }}
        sx={{ p: 2, gap: 2, flex: 1 }}
      >
        {createError ? (
          <Alert severity="error" sx={{ py: 0 }}>
            {extractErrorMessage(createError)}
          </Alert>
        ) : null}

        <TextField
          required
          size="small"
          value={createName}
          onChange={(event) => setCreateName(event.target.value)}
          label={t('financeTransactions.merchantPicker.nameLabel')}
          placeholder={t('financeTransactions.merchantPicker.namePlaceholder')}
          inputProps={{ maxLength: 100, 'data-testid': 'merchant-picker-name-input' }}
          autoFocus
        />

        <TextField
          size="small"
          value={createWebsite}
          onChange={(event) => setCreateWebsite(event.target.value)}
          label={t('financeTransactions.merchantPicker.websiteLabel')}
          placeholder={t('financeTransactions.merchantPicker.websitePlaceholder')}
          inputProps={{ maxLength: 200, 'data-testid': 'merchant-picker-website-input' }}
        />

        <Box
          sx={{
            border: 1,
            borderColor: 'divider',
            borderRadius: 1.25,
            p: 1.25,
            display: 'flex',
            alignItems: 'center',
            gap: 1,
            bgcolor: alpha(theme.palette.text.primary, 0.02),
          }}
          data-testid="merchant-picker-logo-preview"
        >
          <Avatar src={logoPreviewUrl ?? undefined} sx={{ width: 28, height: 28, borderRadius: '.35rem' }}>
            {(createName.trim()[0] ?? '?').toUpperCase()}
          </Avatar>
          <Box sx={{ minWidth: 0 }}>
            <Typography variant="caption" color="text.secondary">
              {t('financeTransactions.merchantPicker.logoPreview')}
            </Typography>
            <Typography variant="body2" noWrap>
              {normalizedPreviewDomain ?? t('financeTransactions.merchantPicker.noWebsite')}
            </Typography>
          </Box>
        </Box>
      </Stack>

      {/* Footer */}
      <Box sx={{ borderTop: 1, borderColor: 'divider' }}>
        <Button
          data-testid="merchant-picker-save-btn"
          fullWidth
          variant="contained"
          onClick={handleCreateSubmit}
          disabled={!createName.trim() || createLoading}
          startIcon={createLoading ? <CircularProgress size={18} color="inherit" /> : undefined}
          sx={{ height: 48, borderRadius: 0 }}
        >
          {createLoading ? t('financeTransactions.actions.saving') : t('financeTransactions.actions.save')}
        </Button>
      </Box>
    </Box>
  );

  const renderConfirm = () => (
    <Box sx={{ p: 3, textAlign: 'center' }}>
      <CheckIcon sx={{ fontSize: 46, color: 'success.main', mb: 1 }} />
      <Typography variant="body1">
        {t('financeTransactions.merchantPicker.created', { name: confirmName ?? '' })}
      </Typography>
    </Box>
  );

  return (
    <>
      <Box
        onClick={handleOpen}
        sx={{ cursor: 'pointer', display: 'flex', alignItems: 'center', width: '100%', minWidth: 0 }}
        data-testid="merchant-select-trigger"
      >
        {selectedMerchant ? (
          <Chip
            avatar={
              <Avatar
                src={selectedMerchant.logoAsset?.publicUrl ?? undefined}
                sx={{ width: 20, height: 20, borderRadius: '.2rem', fontSize: '0.65rem' }}
              >
                {selectedMerchant.name.charAt(0)}
              </Avatar>
            }
            label={
              <Typography
                variant="body2"
                noWrap
                sx={{ maxWidth: '18ch', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}
              >
                {selectedMerchant.name}
              </Typography>
            }
            sx={{
              maxWidth: '100%',
              borderRadius: 1,
              bgcolor: 'action.selected',
              '& .MuiChip-label': { minWidth: 0, maxWidth: '100%', overflow: 'hidden' },
              '&:hover': { bgcolor: 'action.hover' },
            }}
            onDelete={handleOpen}
            deleteIcon={<ExpandMoreIcon sx={{ color: 'inherit !important' }} />}
          />
        ) : (
          <Chip
            label={resolvedPlaceholder}
            variant="outlined"
            sx={{
              maxWidth: '100%',
              minWidth: 0,
              borderRadius: 1,
              borderStyle: 'dashed',
              color: 'text.secondary',
              '& .MuiChip-label': { minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' },
              '&:hover': {
                borderColor: 'primary.main',
                color: 'primary.main',
                bgcolor: alpha(theme.palette.primary.main, 0.05),
              },
            }}
            onDelete={handleOpen}
            deleteIcon={<ExpandMoreIcon />}
          />
        )}
      </Box>

      <Popover
        open={open}
        anchorEl={anchorEl}
        onClose={handleClose}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'left' }}
        transformOrigin={{ vertical: 'top', horizontal: 'left' }}
        PaperProps={{
          sx: {
            width: 320,
            maxHeight: 430,
            minHeight: 320,
            display: 'flex',
            flexDirection: 'column',
          },
        }}
      >
        <Box sx={{ display: 'flex', flexDirection: 'column', flex: 1, minHeight: 0 }}>
          {view === 'browse' ? renderBrowse() : view === 'create' ? renderCreate() : renderConfirm()}
        </Box>
      </Popover>
    </>
  );
}
