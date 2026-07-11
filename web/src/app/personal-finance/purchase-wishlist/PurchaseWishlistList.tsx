'use client';
import AppsIcon from '@mui/icons-material/Apps';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import ViewAgendaIcon from '@mui/icons-material/ViewAgenda';
import {
  Box,
  Chip,
  Collapse,
  Container,
  Fade,
  IconButton,
  Stack,
  Tab,
  Tabs,
  Tooltip,
  Typography,
} from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { useMutation, useQuery } from '@apollo/client/react';
import { useEffect, useMemo, useState } from 'react';
import { ConfirmationDialog, LoadingState, QueryErrorState } from '@/shared/components/ui/feedback';
import { SidebarLayout } from '@/shared/components/ui/layout/SidebarLayout';
import { Page } from '@/shared/components/ui/layout';
import { useMutationWithRefetch } from '@/shared/hooks/mutations';
import { useScopedToggleFilter } from '../finance-transactions/hooks/useScopedToggleFilter';
import { useToast } from '@/shared/providers';
import { useTranslation } from '@/shared/i18n';
import { extractErrorMessage } from '@/shared/utils/error';
import { personalFinanceTranslations } from '../i18n';
import {
  CreatePurchaseWishlistItemDocument,
  DeletePurchaseWishlistItemDocument,
  MarkPurchaseWishlistItemAsPurchasedDocument,
  PurchaseWishlistActiveItemsDocument,
  PurchaseWishlistPurchasedItemsDocument,
  PurchaseWishlistItemWithTransactionDocument,
  ReorderPurchaseWishlistItemsDocument,
  UpdatePurchaseWishlistItemDocument,
  type PurchaseWishlistActiveItemsQuery,
  type PurchaseWishlistPurchasedItemsQuery,
} from './purchaseWishlistOperations.generated';
import type { CreateTransactionInput, CreateWishlistItemInput, UpdateWishlistItemInput } from '@/lib/graphql/types/__generated__/graphql';
import type {
  TransactionCreateSubmitInput,
  TransactionSuccessPayload,
} from '../finance-transactions/transaction/transactionFormShared';
import { WishlistItemStatus } from '@/lib/graphql/types/__generated__/graphql';
import { PurchaseWishlistForm } from './PurchaseWishlistForm';
import { PurchaseDecisionDialog } from './PurchaseDecisionDialog';
import { PurchaseWishlistSummaryPanel } from './PurchaseWishlistSummaryPanel';
import { PurchaseWishlistTable } from './PurchaseWishlistTable';
import { PurchaseWishlistTransactionDrawer } from './PurchaseWishlistTransactionDrawer';
import { PurchaseWishlistToolbar } from './PurchaseWishlistToolbar';
import {
  applyWishlistOrder,
  filterWishlistItems,
  formatWishlistCurrency,
  type WishlistCategoryFilterSelection,
  sortWishlistItems,
  type WishlistItemRow,
} from './wishlistRowModel';
import { WishlistItemPriority } from '@/lib/graphql/types/__generated__/graphql';

type WishlistTab = 'active' | 'history';
type PurchaseWishlistViewMode = 'list' | 'grid';
const PURCHASE_WISHLIST_VIEW_MODE_STORAGE_KEY = 'personal-finance.purchase-wishlist.view-mode';

function readPurchaseWishlistViewMode(): PurchaseWishlistViewMode | null {
  if (typeof window === 'undefined') {
    return null;
  }

  try {
    const stored = window.localStorage.getItem(PURCHASE_WISHLIST_VIEW_MODE_STORAGE_KEY);
    return stored === 'grid' || stored === 'list' ? stored : null;
  } catch {
    return null;
  }
}

const wishlistCategoryFilterEquals = (
  left: WishlistCategoryFilterSelection,
  right: WishlistCategoryFilterSelection,
) => left.categoryId === right.categoryId && left.uncategorized === right.uncategorized;

export function PurchaseWishlistList() {
  const { t, tCommon } = useTranslation(personalFinanceTranslations);
  const toast = useToast();
  const theme = useTheme();
  const [tab, setTab] = useState<WishlistTab>('active');
  const [searchQuery, setSearchQuery] = useState('');
  const [viewMode, setViewMode] = useState<PurchaseWishlistViewMode>('list');
  const [optimisticActiveOrder, setOptimisticActiveOrder] = useState<string[] | null>(null);
  const [updatingCategoryItemId, setUpdatingCategoryItemId] = useState<string | null>(null);
  const [editingItem, setEditingItem] = useState<WishlistItemRow | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [rowToDelete, setRowToDelete] = useState<WishlistItemRow | null>(null);
  const [rowToPurchase, setRowToPurchase] = useState<WishlistItemRow | null>(null);
  const [rowToPurchaseWithTransaction, setRowToPurchaseWithTransaction] = useState<WishlistItemRow | null>(null);
  const priorityFilter = useScopedToggleFilter<WishlistItemPriority>('purchase-wishlist');
  const categoryFilter = useScopedToggleFilter<WishlistCategoryFilterSelection>(
    'purchase-wishlist',
    wishlistCategoryFilterEquals,
  );

  const activeQuery = useQuery<PurchaseWishlistActiveItemsQuery>(PurchaseWishlistActiveItemsDocument, {
    fetchPolicy: 'cache-and-network',
  });
  const purchasedQuery = useQuery<PurchaseWishlistPurchasedItemsQuery>(PurchaseWishlistPurchasedItemsDocument, {
    fetchPolicy: 'cache-and-network',
  });

  const activeServerRows = useMemo(
    () => sortWishlistItems(activeQuery.data?.wishlistItems ?? []),
    [activeQuery.data?.wishlistItems],
  );
  const activeRows = useMemo(
    () => applyWishlistOrder(activeServerRows, optimisticActiveOrder),
    [activeServerRows, optimisticActiveOrder],
  );
  const purchasedRows = useMemo(
    () => sortWishlistItems(purchasedQuery.data?.wishlistItems ?? []),
    [purchasedQuery.data?.wishlistItems],
  );

  useEffect(() => {
    const persistedViewMode = readPurchaseWishlistViewMode();
    if (persistedViewMode) {
      setViewMode(persistedViewMode);
    }
  }, []);

  useEffect(() => {
    if (typeof window === 'undefined') {
      return;
    }

    try {
      window.localStorage.setItem(PURCHASE_WISHLIST_VIEW_MODE_STORAGE_KEY, viewMode);
    } catch {
      // Ignore storage failures such as private mode or quota exhaustion.
    }
  }, [viewMode]);

  useEffect(() => {
    if (!optimisticActiveOrder) {
      return;
    }

    const serverIds = activeServerRows.map((row) => row.id);
    const sameLength = serverIds.length === optimisticActiveOrder.length;
    const sameOrder = sameLength && serverIds.every((id, index) => id === optimisticActiveOrder[index]);
    const sameSet =
      sameLength && serverIds.every((id) => optimisticActiveOrder.includes(id));

    if (sameOrder || !sameSet) {
      setOptimisticActiveOrder(null);
    }
  }, [activeServerRows, optimisticActiveOrder]);

  const activeSummaryPriorityFilter = tab === 'active' ? priorityFilter.value : null;
  const activeSummaryCategoryFilter = tab === 'active' ? categoryFilter.value : null;

  const filteredActiveRows = useMemo(
    () =>
      filterWishlistItems(activeRows, searchQuery, {
        priority: activeSummaryPriorityFilter,
        category: activeSummaryCategoryFilter,
      }),
    [activeRows, activeSummaryCategoryFilter, activeSummaryPriorityFilter, searchQuery],
  );

  const filteredPurchasedRows = useMemo(
    () => filterWishlistItems(purchasedRows, searchQuery),
    [purchasedRows, searchQuery],
  );

  const summaryFiltersOpen = Boolean(activeSummaryPriorityFilter || activeSummaryCategoryFilter);
  const reorderEnabled = tab === 'active' && searchQuery.trim().length === 0 && !summaryFiltersOpen;
  const visibleRows = tab === 'active' ? filteredActiveRows : filteredPurchasedRows;

  const categoryFilterMeta = useMemo(() => {
    if (!categoryFilter.value) {
      return null;
    }

    if (categoryFilter.value.uncategorized) {
      return {
        name: t('purchaseWishlist.uncategorized'),
        color: theme.palette.grey[600],
      };
    }

    const match = activeRows.find((row) => row.category?.id === categoryFilter.value?.categoryId)?.category;
    return {
      name: match?.name ?? categoryFilter.value.categoryId,
      color: match?.color ?? theme.palette.primary.main,
    };
  }, [activeRows, categoryFilter.value, t, theme]);

  const priorityAccent =
    priorityFilter.value === WishlistItemPriority.High
      ? theme.palette.error.main
      : priorityFilter.value === WishlistItemPriority.Medium
        ? theme.palette.warning.main
        : priorityFilter.value === WishlistItemPriority.Low
          ? theme.palette.success.main
          : theme.palette.primary.main;

  const refreshAll = async () => {
    await Promise.all([activeQuery.refetch(), purchasedQuery.refetch()]);
  };

  const [createMutation, createState] = useMutation(CreatePurchaseWishlistItemDocument);
  const [updateMutation, updateState] = useMutation(UpdatePurchaseWishlistItemDocument);
  const [reorderMutation] = useMutation(ReorderPurchaseWishlistItemsDocument);
  const [purchaseMutation, purchaseState] = useMutation(MarkPurchaseWishlistItemAsPurchasedDocument);
  const [purchaseWithTransactionMutation] = useMutation(PurchaseWishlistItemWithTransactionDocument);
  const [deleteMutation, deleteState] = useMutation(DeletePurchaseWishlistItemDocument);

  const { executeMutation } = useMutationWithRefetch({
    onRefetch: () => {
      void refreshAll();
    },
    errorMessage: t('purchaseWishlist.errors.mutationFailed'),
  });

  const handleCreate = async (input: CreateWishlistItemInput | UpdateWishlistItemInput) => {
    try {
      await executeMutation(
        createMutation,
        { input: input as CreateWishlistItemInput },
        'purchase-wishlist-create',
      );
      toast.success(t('purchaseWishlist.toast.created'));
      setCreateOpen(false);
    } catch (error) {
      toast.error(extractErrorMessage(error));
      throw error;
    }
  };

  const handleUpdate = async (input: CreateWishlistItemInput | UpdateWishlistItemInput) => {
    if (!editingItem) {
      return;
    }

    try {
      await executeMutation(
        updateMutation,
        { id: editingItem.id, input: input as UpdateWishlistItemInput },
        `purchase-wishlist-update-${editingItem.id}`,
      );
      toast.success(t('purchaseWishlist.toast.updated'));
      setEditingItem(null);
    } catch (error) {
      toast.error(extractErrorMessage(error));
      throw error;
    }
  };

  const handleUpdateCategory = async (row: WishlistItemRow, categoryId: string | null) => {
    setUpdatingCategoryItemId(row.id);

    try {
      await executeMutation(
        updateMutation,
        {
          id: row.id,
          input: { categoryId },
        },
        `purchase-wishlist-update-category-${row.id}-${categoryId ?? 'uncategorized'}`,
      );
      toast.success(t('purchaseWishlist.toast.updated'));
    } catch (error) {
      toast.error(extractErrorMessage(error));
    } finally {
      setUpdatingCategoryItemId(null);
    }
  };

  const handleDelete = async () => {
    if (!rowToDelete) {
      return;
    }

    try {
      await executeMutation(
        deleteMutation,
        { id: rowToDelete.id },
        `purchase-wishlist-delete-${rowToDelete.id}`,
      );
      toast.success(t('purchaseWishlist.toast.deleted'));
      setRowToDelete(null);
    } catch (error) {
      toast.error(extractErrorMessage(error));
    }
  };

  const handleMarkAsPurchased = async () => {
    if (!rowToPurchase) {
      return;
    }

    try {
      await executeMutation(
        purchaseMutation,
        { id: rowToPurchase.id },
        `purchase-wishlist-purchased-${rowToPurchase.id}`,
      );
      toast.success(t('purchaseWishlist.toast.purchased'));
      setRowToPurchase(null);
      setTab('history');
    } catch (error) {
      toast.error(extractErrorMessage(error));
    }
  };

  const handleCreateTransactionFromWishlist = async (
    input: TransactionCreateSubmitInput,
  ): Promise<TransactionSuccessPayload | void> => {
    if (!rowToPurchaseWithTransaction) {
      return;
    }

    try {
      const result = await executeMutation(
        purchaseWithTransactionMutation,
        {
          id: rowToPurchaseWithTransaction.id,
          input: input as CreateTransactionInput,
        },
        `purchase-wishlist-purchase-with-transaction-${rowToPurchaseWithTransaction.id}`,
      );
      const createdTransaction = result.data?.purchaseWishlistItemWithTransaction?.transaction;
      toast.success(t('purchaseWishlist.toast.purchasedWithTransaction'));
      setRowToPurchaseWithTransaction(null);
      setTab('history');

      return createdTransaction?.id
        ? {
            action: 'create',
            transactionId: createdTransaction.id,
            date: String(createdTransaction.date),
          }
        : undefined;
    } catch (error) {
      toast.error(extractErrorMessage(error));
      throw error;
    }
  };

  const handleReorder = async (orderedIds: string[]) => {
    setOptimisticActiveOrder(orderedIds);
    try {
      await executeMutation(
        reorderMutation,
        { orderedIds },
        `purchase-wishlist-reorder-${orderedIds.join(',')}`,
      );
      toast.success(t('purchaseWishlist.toast.reordered'));
    } catch (error) {
      setOptimisticActiveOrder(null);
      toast.error(extractErrorMessage(error));
    }
  };

  const summaryPanel = (
    <Box sx={{ p: 3, pb: 2, height: '100%' }}>
      <PurchaseWishlistSummaryPanel
        rows={activeRows}
        loading={activeQuery.loading && activeRows.length === 0}
        error={activeQuery.error}
        activePriorityFilter={activeSummaryPriorityFilter}
        onPriorityClick={tab === 'active' ? priorityFilter.toggle : undefined}
        activeCategoryFilter={activeSummaryCategoryFilter}
        onCategoryClick={tab === 'active' ? categoryFilter.toggle : undefined}
        onRetry={() => {
          void refreshAll();
        }}
      />
    </Box>
  );

  const activeTabCount = activeRows.length;
  const mobileTrigger = (
    <Box
      sx={{
        px: 3,
        py: 1.5,
        display: 'flex',
        alignItems: 'flex-start',
        justifyContent: 'space-between',
        gap: 2,
      }}
    >
      <Box sx={{ minWidth: 0, flex: 1 }}>
        <Typography variant="subtitle2" sx={{ fontWeight: 700, color: 'text.primary', lineHeight: 1.2 }}>
          {tCommon('routes.purchaseWishlist')}
        </Typography>
        <Typography
          variant="body2"
          sx={{
            color: 'text.secondary',
            mt: 0.25,
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          }}
        >
          {t('purchaseWishlist.mobileTrigger.summary', {
            count: activeTabCount,
            amount: formatWishlistCurrency(
              activeRows.reduce((sum, row) => sum + (row.estimatedPrice ?? 0), 0),
            ),
          })}
        </Typography>
      </Box>
      <ExpandMoreIcon sx={{ color: 'text.secondary', flexShrink: 0 }} />
    </Box>
  );

  const currentError = tab === 'active' ? activeQuery.error : purchasedQuery.error;
  const currentLoading = tab === 'active' ? activeQuery.loading : purchasedQuery.loading;
  const currentRows = tab === 'active' ? activeRows : purchasedRows;
  const mainContent = (
    <Box>
      {currentError ? (
        <QueryErrorState
          error={currentError}
          onRetry={() => {
            void refreshAll();
          }}
        />
      ) : null}
      {!currentError ? <LoadingState isLoading={currentLoading && currentRows.length === 0} minHeight={180} /> : null}

      {!currentError && !(currentLoading && currentRows.length === 0) ? (
        <>
          <PurchaseWishlistToolbar
            searchQuery={searchQuery}
            onSearchQueryChange={setSearchQuery}
            onCreate={() => setCreateOpen(true)}
            createLabel={t('purchaseWishlist.newButton')}
            searchPlaceholder={t('purchaseWishlist.searchPlaceholder')}
            clearSearchAriaLabel={t('purchaseWishlist.actions.clearSearchAriaLabel')}
          />

          <Collapse
            in={summaryFiltersOpen}
            timeout={{ enter: 320, exit: 240 }}
            unmountOnExit
            easing={{
              enter: theme.transitions.easing.easeOut,
              exit: theme.transitions.easing.sharp,
            }}
            sx={{ width: '100%' }}
          >
            <Fade
              in={summaryFiltersOpen}
              timeout={{ enter: 260, exit: 180 }}
              easing={{
                enter: theme.transitions.easing.easeOut,
                exit: theme.transitions.easing.sharp,
              }}
            >
              <Box
                sx={{
                  display: 'flex',
                  flexWrap: 'wrap',
                  gap: 1,
                  mb: 2,
                  pt: 0.25,
                }}
              >
                {activeSummaryPriorityFilter ? (
                  <Chip
                    variant="outlined"
                    label={`${t('purchaseWishlist.form.priority')}: ${t(`purchaseWishlist.priority.${activeSummaryPriorityFilter}`)}`}
                    onDelete={priorityFilter.clear}
                    sx={{
                      bgcolor: alpha(priorityAccent, 0.12),
                      borderColor: alpha(priorityAccent, 0.4),
                      color: priorityAccent,
                      '& .MuiChip-deleteIcon': {
                        color: alpha(priorityAccent, 0.75),
                        '&:hover': { color: priorityAccent },
                      },
                    }}
                  />
                ) : null}
                {activeSummaryCategoryFilter ? (
                  <Chip
                    variant="outlined"
                    label={`${t('purchaseWishlist.form.category')}: ${categoryFilterMeta?.name ?? t('purchaseWishlist.uncategorized')}`}
                    onDelete={categoryFilter.clear}
                    sx={{
                      bgcolor: alpha(categoryFilterMeta?.color ?? theme.palette.primary.main, 0.12),
                      borderColor: alpha(categoryFilterMeta?.color ?? theme.palette.primary.main, 0.4),
                      color: categoryFilterMeta?.color ?? theme.palette.primary.main,
                      '& .MuiChip-deleteIcon': {
                        color: alpha(categoryFilterMeta?.color ?? theme.palette.primary.main, 0.75),
                        '&:hover': { color: categoryFilterMeta?.color ?? theme.palette.primary.main },
                      },
                    }}
                  />
                ) : null}
              </Box>
            </Fade>
          </Collapse>

          <Stack
            sx={{
              mb: 2,
              borderBottom: '1px solid',
              borderColor: 'divider',
            }}
            direction="row"
            alignItems="center"
            justifyContent="space-between"
            spacing={2}
          >
            <Tabs
              value={tab}
              onChange={(_, nextValue: WishlistTab) => setTab(nextValue)}
              sx={{
                minHeight: 0,
                '& .MuiTabs-indicator': { backgroundColor: 'primary.main' },
              }}
            >
              <Tab
                value="active"
                label={t('purchaseWishlist.tabs.active', { count: filteredActiveRows.length })}
              />
              <Tab
                value="history"
                label={t('purchaseWishlist.tabs.history', { count: filteredPurchasedRows.length })}
              />
            </Tabs>

            <Stack direction="row" spacing={0.5} sx={{ flexShrink: 0, pb: 0.5 }}>
              <Tooltip title={t('purchaseWishlist.viewMode.list')}>
                <IconButton
                  size="small"
                  color={viewMode === 'list' ? 'primary' : 'default'}
                  onClick={() => setViewMode('list')}
                  aria-label={t('purchaseWishlist.viewMode.list')}
                  aria-pressed={viewMode === 'list'}
                >
                  <ViewAgendaIcon fontSize="small" />
                </IconButton>
              </Tooltip>
              <Tooltip title={t('purchaseWishlist.viewMode.grid')}>
                <IconButton
                  size="small"
                  color={viewMode === 'grid' ? 'primary' : 'default'}
                  onClick={() => setViewMode('grid')}
                  aria-label={t('purchaseWishlist.viewMode.grid')}
                  aria-pressed={viewMode === 'grid'}
                >
                  <AppsIcon fontSize="small" />
                </IconButton>
              </Tooltip>
            </Stack>
          </Stack>

          <PurchaseWishlistTable
            rows={visibleRows}
            status={tab === 'active' ? WishlistItemStatus.Active : WishlistItemStatus.Purchased}
            viewMode={viewMode}
            reorderEnabled={reorderEnabled}
            onEdit={setEditingItem}
            onDelete={setRowToDelete}
            onMarkAsPurchased={setRowToPurchase}
            onReorder={(orderedIds) => void handleReorder(orderedIds)}
            onUpdateCategory={(row, categoryId) => void handleUpdateCategory(row, categoryId)}
            updatingCategoryItemId={updatingCategoryItemId}
          />
        </>
      ) : null}
    </Box>
  );

  return (
    <>
      <SidebarLayout
        side="left"
        size="sm"
        fullHeight
        mobileVariant="bottom-sheet"
        mobileTrigger={mobileTrigger}
        sidebar={summaryPanel}
      >
        <Page>
          <Container
            maxWidth="xl"
            disableGutters
            sx={{
              pt: { xs: 0, md: 4 },
              pb: { xs: 0, md: 4 },
              px: { xs: 0, md: 3 },
              minWidth: 0,
              overflowX: 'clip',
            }}
          >
            {mainContent}
          </Container>
        </Page>
      </SidebarLayout>

      {createOpen ? (
        <PurchaseWishlistForm
          key="purchase-wishlist-create"
          open
          onClose={() => setCreateOpen(false)}
          onSubmit={handleCreate}
          saving={createState.loading}
          error={createState.error}
        />
      ) : null}

      {editingItem ? (
        <PurchaseWishlistForm
          key={editingItem.id}
          open
          item={editingItem}
          onClose={() => setEditingItem(null)}
          onSubmit={handleUpdate}
          saving={updateState.loading}
          error={updateState.error}
        />
      ) : null}

      <PurchaseDecisionDialog
        row={rowToPurchase}
        loading={purchaseState.loading}
        onClose={() => setRowToPurchase(null)}
        onConfirmPurchase={() => void handleMarkAsPurchased()}
        onConfirmPurchaseWithTransaction={() => {
          if (!rowToPurchase) {
            return;
          }

          setRowToPurchaseWithTransaction(rowToPurchase);
          setRowToPurchase(null);
        }}
      />

      <PurchaseWishlistTransactionDrawer
        row={rowToPurchaseWithTransaction}
        onClose={() => setRowToPurchaseWithTransaction(null)}
        onSuccess={() => {
          setTab('history');
        }}
        onRefresh={() => {
          void refreshAll();
        }}
        onCreateSubmit={handleCreateTransactionFromWishlist}
      />

      <ConfirmationDialog
        open={Boolean(rowToDelete)}
        onClose={() => setRowToDelete(null)}
        onConfirm={() => void handleDelete()}
        loading={deleteState.loading}
        title={t('purchaseWishlist.deleteDialog.title')}
        message={t('purchaseWishlist.deleteDialog.message', { name: rowToDelete?.name ?? '' })}
        confirmText={t('actions.delete')}
        cancelText={t('actions.cancel')}
      />
    </>
  );
}
