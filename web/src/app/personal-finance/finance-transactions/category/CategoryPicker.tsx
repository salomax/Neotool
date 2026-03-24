'use client';

import React, { useMemo, useState, useCallback } from 'react';
import {
  Alert,
  Box,
  TextField,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Typography,
  InputAdornment,
  alpha,
  useTheme,
  Button,
  CircularProgress,
  IconButton,
  Link,
  Popover,
  Stack,
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import AddIcon from '@mui/icons-material/Add';
import CloseIcon from '@mui/icons-material/Close';
import CheckIcon from '@mui/icons-material/Check';
import WarningAmberRoundedIcon from '@mui/icons-material/WarningAmberRounded';
import { useQuery, useMutation } from '@apollo/client/react';
import { extractErrorMessage } from '@/shared/utils/error';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../i18n';
import { TRANSACTION_CATEGORIES } from '@/lib/graphql/operations/banking/queries';
import {
  CREATE_TRANSACTION_CATEGORY,
  DELETE_TRANSACTION_CATEGORY,
} from '@/lib/graphql/operations/banking/mutations';
import type {
  CreateTransactionCategoryMutation,
  DeleteTransactionCategoryMutation,
} from '@/lib/graphql/operations/banking/mutations.generated';
import type { TransactionCategory } from '@/lib/graphql/types/__generated__/graphql';
import { CategoryApplicableTo } from '@/lib/graphql/types/__generated__/graphql';
import { CategoryIcon } from './CategoryIcon';
import {
  CATEGORY_ICON_NAMES,
  CATEGORY_COLOR_PALETTE,
} from './categoryIconMap';

export type CategoryPickerView = 'browse' | 'create' | 'confirm' | 'deleteConfirm';

export interface CategoryPickerProps {
  value: string | null;
  onChange: (categoryId: string | null) => void;
  creditDebitType: 'CREDIT' | 'DEBIT' | 'TRANSFER';
  anchorEl: HTMLElement | null;
  onClose: () => void;
}

const CATEGORY_NAME_COLLATOR = new Intl.Collator(undefined, {
  sensitivity: 'base',
  numeric: true,
});

function compareCategoryForPicker(a: TransactionCategory, b: TransactionCategory) {
  const aSystem = a.isSystem === true;
  const bSystem = b.isSystem === true;
  if (aSystem !== bSystem) return aSystem ? 1 : -1;

  if (aSystem && bSystem) {
    const byOrder = a.sortOrder - b.sortOrder;
    if (byOrder !== 0) return byOrder;
  }

  const byName = CATEGORY_NAME_COLLATOR.compare(a.name, b.name);
  if (byName !== 0) return byName;

  return a.sortOrder - b.sortOrder;
}

function buildTree(categories: TransactionCategory[]) {
  const roots = categories
    .filter((c) => !c.parentId)
    .sort(compareCategoryForPicker);
  const byParent = categories.reduce<Record<string, TransactionCategory[]>>(
    (acc, c) => {
      const key = c.parentId ?? 'root';
      if (!acc[key]) acc[key] = [];
      acc[key].push(c);
      return acc;
    },
    {}
  );
  Object.values(byParent).forEach((children) => children.sort(compareCategoryForPicker));
  return { roots, byParent };
}

const CONFIRM_DURATION_MS = 1500;

export function CategoryPicker({
  value,
  onChange,
  creditDebitType,
  anchorEl,
  onClose,
}: CategoryPickerProps) {
  const theme = useTheme();
  const { t } = useTranslation(personalFinanceTranslations);
  const [view, setView] = useState<CategoryPickerView>('browse');
  const [searchQuery, setSearchQuery] = useState('');
  const [createName, setCreateName] = useState('');
  const [createIcon, setCreateIcon] = useState<string | null>('help_outline');
  const [createColor, setCreateColor] = useState<string | null>(
    CATEGORY_COLOR_PALETTE[0] ?? null
  );
  const [confirmCreatedName, setConfirmCreatedName] = useState<string | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<{
    category: TransactionCategory;
    children: TransactionCategory[];
  } | null>(null);

  const { data } = useQuery<{ transactionCategories: TransactionCategory[] }>(
    TRANSACTION_CATEGORIES,
    { fetchPolicy: 'cache-first' }
  );
  const [createCategory, { loading: createLoading, error: createError, reset: resetCreateState }] =
    useMutation<CreateTransactionCategoryMutation>(CREATE_TRANSACTION_CATEGORY, {
      refetchQueries: [{ query: TRANSACTION_CATEGORIES }],
    });
  const [deleteCategory, { loading: deleteLoading }] = useMutation<DeleteTransactionCategoryMutation>(
    DELETE_TRANSACTION_CATEGORY,
    { refetchQueries: [{ query: TRANSACTION_CATEGORIES }] }
  );
  const [deleteError, setDeleteError] = useState<unknown>(null);

  const { roots, byParent } = useMemo(() => {
    const allCategories = data?.transactionCategories ?? [];
    const mainApplicableTo =
      creditDebitType === 'DEBIT'
        ? CategoryApplicableTo.Expense
        : creditDebitType === 'CREDIT'
          ? CategoryApplicableTo.Income
          : CategoryApplicableTo.Transfer;
    const allowedApplicableTo = new Set<CategoryApplicableTo>(
      mainApplicableTo === CategoryApplicableTo.Transfer
        ? [CategoryApplicableTo.Transfer, CategoryApplicableTo.Any]
        : [mainApplicableTo, CategoryApplicableTo.Any]
    );
    const typeFiltered = allCategories.filter((c) => allowedApplicableTo.has(c.applicableTo));
    const idSet = new Set(typeFiltered.map((c) => c.id));
    const nonOrphaned = typeFiltered.filter((c) => !c.parentId || idSet.has(c.parentId));
    return buildTree(nonOrphaned);
  }, [data?.transactionCategories, creditDebitType]);

  const prevCreditDebitType = React.useRef(creditDebitType);
  React.useEffect(() => {
    if (prevCreditDebitType.current === creditDebitType) return;
    prevCreditDebitType.current = creditDebitType;
    if (value !== null) onChange(null);
  }, [creditDebitType, value, onChange]);

  React.useEffect(() => {
    if (value === null) return;
    const allCategories = data?.transactionCategories ?? [];
    const selected = allCategories.find((c) => c.id === value);
    if (!selected) return;
    const expectedApplicableTo =
      creditDebitType === 'DEBIT'
        ? CategoryApplicableTo.Expense
        : creditDebitType === 'CREDIT'
          ? CategoryApplicableTo.Income
          : CategoryApplicableTo.Transfer;
    const allowedApplicableTo = new Set<CategoryApplicableTo>(
      expectedApplicableTo === CategoryApplicableTo.Transfer
        ? [CategoryApplicableTo.Transfer, CategoryApplicableTo.Any]
        : [expectedApplicableTo, CategoryApplicableTo.Any]
    );
    if (!allowedApplicableTo.has(selected.applicableTo)) onChange(null);
  }, [value, data?.transactionCategories, creditDebitType, onChange]);

  const searchLower = searchQuery.trim().toLowerCase();
  const filteredChildren = useMemo(() => {
    if (!searchLower) return byParent;
    const out: Record<string, TransactionCategory[]> = {};
    roots.forEach((r) => {
      const id = r.id;
      if (!id) return;
      const rootMatches = r.name.toLowerCase().includes(searchLower);
      const allChildren = byParent[id] ?? [];
      const children = rootMatches
        ? allChildren
        : allChildren.filter((c) => c.name.toLowerCase().includes(searchLower));
      if (children.length > 0) out[id] = [...children].sort(compareCategoryForPicker);
    });
    return out;
  }, [roots, byParent, searchLower]);

  const filteredRoots = useMemo(() => {
    if (!searchLower) return roots;
    return roots.filter((r) => {
      if (r.name.toLowerCase().includes(searchLower)) return true;
      const id = r.id;
      if (!id) return false;
      return (filteredChildren[id]?.length ?? 0) > 0;
    });
  }, [roots, filteredChildren, searchLower]);


  const handleSelect = useCallback(
    (id: string | null) => {
      onChange(id);
      onClose();
    },
    [onChange, onClose]
  );

  const handleOpenCreate = useCallback(() => {
    setCreateName('');
    setCreateIcon('help_outline');
    setCreateColor(CATEGORY_COLOR_PALETTE[0] ?? null);
    resetCreateState();
    setView('create');
  }, [resetCreateState]);

  const handleBackToBrowse = useCallback(() => {
    setView('browse');
    setConfirmCreatedName(null);
    setDeleteTarget(null);
  }, []);

  const handleCreateSubmit = useCallback(async () => {
    if (!createName.trim()) return;
    const applicableTo =
      creditDebitType === 'DEBIT'
        ? CategoryApplicableTo.Expense
        : creditDebitType === 'CREDIT'
          ? CategoryApplicableTo.Income
          : CategoryApplicableTo.Transfer;
    try {
      const { data: result } = await createCategory({
        variables: {
          input: {
            name: createName.trim(),
            applicableTo,
            parentId: null,
            icon: createIcon ?? undefined,
            color: createColor ?? undefined,
          },
        },
      });
      const newCategory = result?.createTransactionCategory;
      if (newCategory) {
        onChange(newCategory.id);
        setConfirmCreatedName(newCategory.name);
        setView('confirm');
      }
    } catch {
      // Network or other errors: Apollo sets error state, shown in UI
    }
  }, [createName, createIcon, createColor, creditDebitType, createCategory, onChange]);

  React.useEffect(() => {
    if (view === 'confirm' && confirmCreatedName) {
      const t = setTimeout(() => onClose(), CONFIRM_DURATION_MS);
      return () => clearTimeout(t);
    }
  }, [view, confirmCreatedName, onClose]);

  const handleDeleteClick = useCallback(
    (category: TransactionCategory) => (e: React.MouseEvent) => {
      e.stopPropagation();
      const children = byParent[category.id] ?? [];
      setDeleteTarget({ category, children });
      setView('deleteConfirm');
    },
    [byParent]
  );

  React.useEffect(() => {
    if (anchorEl) {
      setView('browse');
      setDeleteTarget(null);
      setConfirmCreatedName(null);
    }
  }, [anchorEl]);

  const handleDeleteConfirm = useCallback(async () => {
    if (!deleteTarget) return;
    setDeleteError(null);
    try {
      await deleteCategory({ variables: { id: deleteTarget.category.id } });
      setDeleteTarget(null);
      setView('browse');
    } catch (err) {
      setDeleteError(err);
    }
  }, [deleteTarget, deleteCategory]);

  const handleDeleteCancel = useCallback(() => {
    setDeleteTarget(null);
    setDeleteError(null);
    setView('browse');
  }, []);

  if (!anchorEl) return null;

  const open = Boolean(anchorEl);

  const renderBrowse = () => (
    <>
      <Box sx={{ p: 1, borderBottom: 1, borderColor: 'divider', flexShrink: 0 }}>
        <TextField
          fullWidth
          size="small"
          placeholder={t('financeTransactions.categoryPicker.searchPlaceholder')}
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          InputProps={{
            startAdornment: (
              <InputAdornment position="start">
                <SearchIcon fontSize="small" color="action" />
              </InputAdornment>
            ),
            disableUnderline: true,
            sx: { '& fieldset': { border: 'none' } },
          }}
          variant="standard"
          autoFocus
        />
      </Box>
      <List sx={{ flex: 1, minHeight: 0, overflow: 'auto', py: 0 }}>
        <ListItemButton
          onClick={() => handleSelect(null)}
          selected={value === null}
          sx={{ borderBottom: 1, borderColor: 'divider' }}
        >
          <ListItemIcon sx={{ minWidth: 40 }}>
            <CategoryIcon iconName="help_outline" color={theme.palette.text.secondary} size={22} />
          </ListItemIcon>
          <ListItemText primary={t('financeTransactions.categoryPicker.noCategory')} primaryTypographyProps={{ variant: 'body2' }} />
        </ListItemButton>

        {filteredRoots.map((root) => {
          const children = filteredChildren[root.id!] ?? [];
          return (
            <React.Fragment key={root.id}>
              <Box
                component="div"
                sx={{
                  display: 'flex',
                  alignItems: 'stretch',
                  bgcolor: alpha(theme.palette.primary.main, 0.06),
                  '&:hover .delete-btn': { opacity: 1 },
                }}
              >
                <ListItemButton
                  onClick={() => handleSelect(root.id)}
                  selected={value === root.id}
                  sx={{ flex: 1, borderRadius: 0 }}
                >
                  <ListItemIcon sx={{ minWidth: 40 }}>
                    <CategoryIcon
                      iconName={root.icon}
                      color={root.color ?? undefined}
                      size={22}
                    />
                  </ListItemIcon>
                  <ListItemText
                    primary={root.name}
                    primaryTypographyProps={{ variant: 'body2', fontWeight: 600 }}
                  />
                </ListItemButton>
                {!root.isSystem && (
                  <IconButton
                    className="delete-btn"
                    size="small"
                    onClick={handleDeleteClick(root)}
                    sx={{ opacity: 0, mr: 0.5, alignSelf: 'center' }}
                    aria-label={t('financeTransactions.categoryPicker.deleteAriaLabel')}
                  >
                    <CloseIcon fontSize="small" />
                  </IconButton>
                )}
              </Box>
              {children.map((child) => (
                <Box
                  key={child.id}
                  sx={{
                    display: 'flex',
                    alignItems: 'center',
                    '&:hover .delete-btn': { opacity: 1 },
                  }}
                >
                  <ListItemButton
                    onClick={() => handleSelect(child.id)}
                    selected={value === child.id}
                    sx={{ flex: 1, pl: 4 }}
                  >
                    <ListItemIcon sx={{ minWidth: 40 }}>
                      <CategoryIcon
                        iconName={child.icon}
                        color={child.color ?? undefined}
                        size={22}
                      />
                    </ListItemIcon>
                    <ListItemText primary={child.name} primaryTypographyProps={{ variant: 'body2' }} />
                  </ListItemButton>
                  {!child.isSystem && (
                    <IconButton
                      className="delete-btn"
                      size="small"
                      onClick={handleDeleteClick(child)}
                      sx={{ opacity: 0 }}
                      aria-label={t('financeTransactions.categoryPicker.deleteAriaLabel')}
                    >
                      <CloseIcon fontSize="small" />
                    </IconButton>
                  )}
                </Box>
              ))}
            </React.Fragment>
          );
        })}

        {filteredRoots.length === 0 && (
          <ListItemButton disabled>
            <ListItemText
              primary={t('financeTransactions.categoryPicker.noResults')}
              primaryTypographyProps={{
                variant: 'body2',
                color: 'text.secondary',
                textAlign: 'center',
              }}
            />
          </ListItemButton>
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
        >
          <AddIcon fontSize="small" />
          {t('financeTransactions.categoryPicker.newCategory')}
        </Link>
      </Box>
    </>
  );

  const renderCreate = () => (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%', overflow: 'hidden' }}>
      {/* Header */}
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
          size="small"
          onClick={handleBackToBrowse}
          aria-label={t('financeTransactions.categoryPicker.backAriaLabel')}
          sx={{ mr: 0.5 }}
        >
          <ArrowBackIcon fontSize="small" />
        </IconButton>
        <Typography variant="subtitle1" fontWeight={600}>
          {t('financeTransactions.categoryPicker.newCategory')}
        </Typography>
      </Stack>

      {/* Form */}
      <Stack
        component="form"
        onSubmit={(e: React.FormEvent) => {
          e.preventDefault();
          handleCreateSubmit();
        }}
        sx={{
          flex: 1,
          overflow: 'auto',
          p: 2.5,
          gap: 2.5,
        }}
      >
        {createError && (
          <Alert severity="error" onClose={() => resetCreateState()} sx={{ py: 0 }}>
            {extractErrorMessage(createError)}
          </Alert>
        )}
        <TextField
          label={t('financeTransactions.categoryPicker.nameLabel')}
          placeholder={t('financeTransactions.categoryPicker.namePlaceholder')}
          value={createName}
          onChange={(e) => setCreateName(e.target.value)}
          required
          fullWidth
          autoFocus
          size="small"
          variant="outlined"
        />

        <Box>
          <Box
            sx={{
              display: 'flex',
              flexWrap: 'wrap',
              justifyContent: 'center',
              gap: 1,
              maxHeight: 144,
              overflow: 'auto',
              border: 1,
              borderColor: 'divider',
              borderRadius: 1,
              bgcolor: createColor ? alpha(createColor, 0.08) : theme.palette.action.hover,
            }}
          >
            {CATEGORY_ICON_NAMES.map((iconName) => (
              <IconButton
                key={iconName}
                size="small"
                onClick={() => setCreateIcon(iconName)}
                sx={{
                  width: 32,
                  height: 32,
                  bgcolor:
                    createIcon === iconName
                      ? alpha(createColor ?? theme.palette.primary.main, 0.14)
                      : 'transparent',
                  border:
                    createIcon === iconName
                      ? `2px solid ${createColor ?? theme.palette.primary.main}`
                      : '2px solid transparent',
                  '&:hover': {
                    bgcolor: alpha(createColor ?? theme.palette.primary.main, 0.08),
                  },
                }}
                aria-label={iconName}
              >
                <CategoryIcon iconName={iconName} color={createColor ?? undefined} size={22} />
              </IconButton>
            ))}
          </Box>
        </Box>

        <Box>
          <Stack direction="row" flexWrap="wrap" gap={.4} useFlexGap>
            {CATEGORY_COLOR_PALETTE.map((hex) => (
              <IconButton
                key={hex}
                size="small"
                onClick={() => setCreateColor(hex)}
                sx={{
                  width: 32,
                  height: 32,
                  flexShrink: 0,
                  bgcolor: hex,
                  borderRadius: 1,
                  border: createColor === hex ? 3 : 1,
                  borderColor: createColor === hex ? 'primary.main' : 'divider',
                  boxSizing: 'border-box',
                  '&:hover': {
                    bgcolor: alpha(hex, 0.85),
                    borderColor: alpha(hex, 0.8),
                    borderWidth: 2,
                  },
                  '&.Mui-focusVisible': {
                    bgcolor: alpha(hex, 0.85),
                  },
                }}
                aria-label={hex}
              >
                {createColor === hex && (
                  <CheckIcon sx={{ color: 'white', fontSize: 18, filter: 'drop-shadow(0 0 1px rgba(0,0,0,0.5))' }} />
                )}
              </IconButton>
            ))}
          </Stack>
        </Box>
      </Stack>

      {/* Footer */}
      <Box sx={{ borderTop: 1, borderColor: 'divider' }}>
        <Button
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
      <CheckIcon sx={{ fontSize: 48, color: 'success.main', mb: 1 }} />
      <Typography variant="body1">
        {t('financeTransactions.categoryPicker.created', { name: confirmCreatedName ?? '' })}
      </Typography>
    </Box>
  );

  const renderDeleteConfirm = () => {
    if (!deleteTarget) return null;
    const { category, children } = deleteTarget;
    const hasChildren = children.length > 0;
    return (
      <Box sx={{ p: 3, textAlign: 'center' }}>
        <Box
          sx={{
            width: 56,
            height: 56,
            borderRadius: 1,
            bgcolor: alpha(theme.palette.error.main, 0.08),
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            mx: 'auto',
            mb: 2,
          }}
        >
          <WarningAmberRoundedIcon sx={{ fontSize: 34, color: theme.palette.error.main }} />
        </Box>

        <Typography variant="h6" sx={{ fontWeight: 700, mb: 1 }}>
          {t('financeTransactions.categoryPicker.deleteTitle', { name: category.name })}
        </Typography>

        <Typography variant="body2" color="text.secondary" sx={{ mb: hasChildren ? 1.5 : 3 }}>
          {t('financeTransactions.categoryPicker.deleteDescription')}
        </Typography>

        {deleteError ? (
          <Alert severity="error" sx={{ mb: 3, textAlign: 'left' }}>
            {extractErrorMessage(deleteError)}
          </Alert>
        ) : null}

        {hasChildren ? (
          <Box
            sx={{
              textAlign: 'left',
              bgcolor: alpha(theme.palette.text.primary, 0.03),
              border: 1,
              borderColor: 'divider',
              borderRadius: 2,
              px: 2,
              py: 1.5,
              mb: 3,
            }}
          >
            <Typography variant="body2" sx={{ fontWeight: 600, mb: 1 }}>
              {t('financeTransactions.categoryPicker.deleteChildrenTitle')}
            </Typography>
            <Box component="ul" sx={{ m: 0, pl: 2 }}>
              {children.map((c) => (
                <li key={c.id}>
                  <Typography variant="body2" color="text.secondary">
                    {c.name}
                  </Typography>
                </li>
              ))}
            </Box>
          </Box>
        ) : null}

        <Box sx={{ display: 'flex', gap: 2 }}>
          <Button
            onClick={handleDeleteCancel}
            variant="contained"
            sx={{
              flex: 1,
              height: 48,
              borderRadius: 1,
              bgcolor: alpha(theme.palette.text.primary, 0.06),
              color: 'text.primary',
              boxShadow: 'none',
              textTransform: 'none',
              fontWeight: 700,
              '&:hover': {
                bgcolor: alpha(theme.palette.text.primary, 0.1),
                boxShadow: 'none',
              },
            }}
          >
            {t('financeTransactions.actions.cancel')}
          </Button>
          <Button
            variant="contained"
            color="error"
            onClick={handleDeleteConfirm}
            sx={{
              flex: 1,
              height: 48,
              borderRadius: 1,
              boxShadow: `0px 8px 8px ${alpha(theme.palette.error.main, 0.15)}`,
              textTransform: 'none',
              fontWeight: 700,
              '&:hover': {
                boxShadow: 'none',
              },
            }}
            disabled={deleteLoading}
            startIcon={deleteLoading ? <CircularProgress size={18} color="inherit" /> : undefined}
          >
            {t('financeTransactions.actions.delete')}
          </Button>
        </Box>
      </Box>
    );
  };

  const content =
    view === 'browse'
      ? renderBrowse()
      : view === 'create'
        ? renderCreate()
        : view === 'confirm'
          ? renderConfirm()
          : renderDeleteConfirm();

  const isCreateView = view === 'create';

  return (
    <Popover
      open={open}
      anchorEl={anchorEl}
      onClose={onClose}
      anchorOrigin={{ vertical: 'bottom', horizontal: 'left' }}
      transformOrigin={{ vertical: 'top', horizontal: 'left' }}
      PaperProps={{
        sx: {
          width: isCreateView ? 320 : 320,
          maxHeight: isCreateView ? 440 : 440,
          height: isCreateView ? 440 : undefined,
          display: 'flex',
          flexDirection: 'column',
          minHeight: isCreateView ? 440 : 440,
        },
      }}
    >
      <Box sx={{ display: 'flex', flexDirection: 'column', minHeight: 0, flex: 1 }}>
        {content}
      </Box>
    </Popover>
  );
}
