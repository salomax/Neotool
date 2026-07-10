'use client';

import { useState, type MouseEvent } from 'react';
import DragIndicatorIcon from '@mui/icons-material/DragIndicator';
import EditIcon from '@mui/icons-material/Edit';
import LinkIcon from '@mui/icons-material/Link';
import TaskAltIcon from '@mui/icons-material/TaskAlt';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import {
  DndContext,
  type DragEndEvent,
  MouseSensor,
  TouchSensor,
  closestCenter,
  useSensor,
  useSensors,
} from '@dnd-kit/core';
import { SortableContext, arrayMove, rectSortingStrategy, useSortable } from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import { alpha } from '@mui/material/styles';
import { Box, IconButton, Link, Paper, Stack, Tooltip, Typography, useMediaQuery, useTheme } from '@mui/material';
import { EmptyState } from '@/shared/components/ui/feedback';
import { FINANCE_TABLE_BODY_FONT_SIZE } from '@/shared/components/ui/data-display/Table';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../i18n';
import type { WishlistItemStatus } from '@/lib/graphql/types/__generated__/graphql';
import { CategoryChip } from '../finance-transactions/category/CategoryChip';
import { CategoryPicker } from '../finance-transactions/category/CategoryPicker';
import type { WishlistItemRow } from './wishlistRowModel';
import {
  formatWishlistCurrency,
  formatWishlistDate,
  hasValidExternalUrl,
} from './wishlistRowModel';

type PurchaseWishlistTableProps = {
  rows: WishlistItemRow[];
  status: WishlistItemStatus;
  viewMode: 'list' | 'grid';
  reorderEnabled: boolean;
  onEdit: (row: WishlistItemRow) => void;
  onDelete: (row: WishlistItemRow) => void;
  onMarkAsPurchased: (row: WishlistItemRow) => void;
  onReorder: (orderedIds: string[]) => void;
  onUpdateCategory: (row: WishlistItemRow, categoryId: string | null) => void | Promise<void>;
  updatingCategoryItemId: string | null;
};

type SortableRowProps = {
  row: WishlistItemRow;
  status: WishlistItemStatus;
  viewMode: 'list' | 'grid';
  reorderEnabled: boolean;
  onEdit: (row: WishlistItemRow) => void;
  onDelete: (row: WishlistItemRow) => void;
  onMarkAsPurchased: (row: WishlistItemRow) => void;
  onOpenCategoryPicker: (event: MouseEvent<HTMLElement>, row: WishlistItemRow) => void;
  updatingCategoryItemId: string | null;
};

function priorityLabel(priority: WishlistItemRow['priority'], t: (key: string) => string): string {
  return t(`purchaseWishlist.priority.${priority}`);
}

function priorityTextColor(priority: WishlistItemRow['priority']): 'error.main' | 'warning.main' | 'success.main' {
  if (priority === 'HIGH') return 'error.main';
  if (priority === 'MEDIUM') return 'warning.main';
  return 'success.main';
}

function SortableWishlistRow({
  row,
  status,
  viewMode,
  reorderEnabled,
  onEdit,
  onDelete,
  onMarkAsPurchased,
  onOpenCategoryPicker,
  updatingCategoryItemId,
}: SortableRowProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('sm'));
  const [imageVisible, setImageVisible] = useState(Boolean(row.productImageUrl));
  const { attributes, listeners, setNodeRef, transform, transition, isDragging, isSorting } = useSortable({
    id: row.id,
    disabled: !reorderEnabled,
  });

  return (
    <Paper
      ref={setNodeRef}
      elevation={0}
      sx={{
        px: { xs: 1.5, sm: 2 },
        py: 1.5,
        border: '1px solid',
        borderColor: isDragging ? 'primary.main' : 'divider',
        bgcolor: isDragging ? alpha(theme.palette.primary.main, 0.06) : 'background.paper',
        transform: CSS.Transform.toString(transform),
        transition: isSorting ? transition : undefined,
        height: '100%',
      }}
    >
      <Stack spacing={1.25} sx={{ height: '100%' }}>
        <Stack direction="row" spacing={1.5} alignItems="stretch" sx={{ flex: 1, minHeight: 0 }}>
          {status === 'ACTIVE' ? (
            <Tooltip title={reorderEnabled ? t('purchaseWishlist.table.dragHandle') : t('purchaseWishlist.table.reorderDisabled')}>
              <Box
                {...(reorderEnabled ? { ...attributes, ...listeners } : {})}
                sx={{
                  mt: 0.25,
                  color: reorderEnabled ? 'text.secondary' : 'text.disabled',
                  cursor: reorderEnabled ? 'grab' : 'default',
                  touchAction: 'none',
                }}
              >
                <DragIndicatorIcon fontSize="small" />
              </Box>
            </Tooltip>
          ) : null}

          {imageVisible && row.productImageUrl ? (
            <Box
              sx={{
                width: { xs: 48, sm: 56 },
                height: { xs: 48, sm: 56 },
                borderRadius: 1.5,
                overflow: 'hidden',
                border: '1px solid',
                borderColor: 'divider',
                bgcolor: 'grey.50',
                flexShrink: 0,
              }}
            >
              <Box
                component="img"
                src={row.productImageUrl}
                alt={row.name}
                onError={() => setImageVisible(false)}
                sx={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block' }}
              />
            </Box>
          ) : null}

          <Box sx={{ minWidth: 0, flex: 1, display: 'flex', flexDirection: 'column' }}>
            <Stack
              direction="row"
              spacing={1}
              alignItems="flex-start"
              justifyContent="space-between"
            >
              <Typography
                variant="body2"
                sx={{
                  fontSize: FINANCE_TABLE_BODY_FONT_SIZE,
                  fontWeight: 400,
                  pr: 1,
                  lineHeight: 1.25,
                  ...(viewMode === 'grid'
                    ? {
                        display: '-webkit-box',
                        WebkitBoxOrient: 'vertical',
                        WebkitLineClamp: 3,
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                      }
                    : {}),
                }}
              >
                {row.name}
              </Typography>
              <Typography
                variant="subtitle2"
                sx={{
                  fontSize: FINANCE_TABLE_BODY_FONT_SIZE,
                  fontWeight: 700,
                  color: 'text.primary',
                  textAlign: 'right',
                  whiteSpace: 'nowrap',
                  lineHeight: 1.25,
                  fontVariantNumeric: 'tabular-nums',
                }}
              >
                {formatWishlistCurrency(row.estimatedPrice, row.currencyCode)}
              </Typography>
            </Stack>

            <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap" sx={{ mt: 1 }}>
              <CategoryChip
                category={row.category}
                disabled={updatingCategoryItemId === row.id}
                loading={updatingCategoryItemId === row.id}
                onClick={(event) => onOpenCategoryPicker(event, row)}
                placeholder={
                  <Typography variant="body2" sx={{ opacity: 0.65 }}>
                    {t('purchaseWishlist.uncategorized')}
                  </Typography>
                }
              />
              <Box
                sx={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: 0.75,
                  px: 0.25,
                  py: 0.5,
                }}
              >
                <Box
                  sx={{
                    width: 10,
                    height: 10,
                    borderRadius: '50%',
                    bgcolor: priorityTextColor(row.priority),
                    flexShrink: 0,
                  }}
                />
                <Typography
                  variant="body2"
                  sx={{
                    fontSize: FINANCE_TABLE_BODY_FONT_SIZE,
                    fontWeight: 700,
                    lineHeight: 1.25,
                    color: priorityTextColor(row.priority),
                  }}
                >
                  {priorityLabel(row.priority, t)}
                </Typography>
              </Box>
              {row.targetDate ? (
                <Typography
                  variant="body2"
                  sx={{
                    fontSize: FINANCE_TABLE_BODY_FONT_SIZE,
                    lineHeight: 1.25,
                    color: 'text.secondary',
                  }}
                >
                  {t('purchaseWishlist.table.targetDate', { date: formatWishlistDate(row.targetDate) })}
                </Typography>
              ) : null}
              {status === 'PURCHASED' && row.purchasedAt ? (
                <Typography
                  variant="body2"
                  sx={{
                    fontSize: FINANCE_TABLE_BODY_FONT_SIZE,
                    lineHeight: 1.25,
                    color: 'text.secondary',
                  }}
                >
                  {t('purchaseWishlist.table.purchasedAt', { date: formatWishlistDate(row.purchasedAt) })}
                </Typography>
              ) : null}
            </Stack>

            {row.notes ? (
              <Typography
                variant="body2"
                color="text.secondary"
                sx={{ mt: 1, fontSize: FINANCE_TABLE_BODY_FONT_SIZE, lineHeight: 1.25 }}
              >
                {row.notes}
              </Typography>
            ) : null}
          </Box>
        </Stack>

        <Stack
          direction="row"
          spacing={0.5}
          alignItems="center"
          justifyContent="space-between"
          sx={{ pt: 0.5, mt: 'auto', minHeight: 32 }}
        >
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
            {hasValidExternalUrl(row.productUrl) ? (
              <Link
                href={row.productUrl ?? '#'}
                target="_blank"
                rel="noreferrer"
                underline="hover"
                color="primary.main"
                sx={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: 0.5,
                  fontSize: FINANCE_TABLE_BODY_FONT_SIZE,
                  lineHeight: 1.25,
                }}
              >
                <LinkIcon fontSize="inherit" />
                {isMobile ? t('purchaseWishlist.table.linkShort') : t('purchaseWishlist.table.openLink')}
              </Link>
            ) : null}
          </Box>

          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.25 }}>
            {status === 'ACTIVE' ? (
              <>
                <Tooltip title={t('purchaseWishlist.actions.markAsPurchased')}>
                  <IconButton size="small" color="primary" onClick={() => onMarkAsPurchased(row)}>
                    <TaskAltIcon fontSize="small" />
                  </IconButton>
                </Tooltip>
              </>
            ) : null}
            <Tooltip title={t('purchaseWishlist.actions.edit')}>
              <IconButton size="small" onClick={() => onEdit(row)}>
                <EditIcon fontSize="small" />
              </IconButton>
            </Tooltip>
            <Tooltip title={t('purchaseWishlist.actions.delete')}>
              <IconButton size="small" color="error" onClick={() => onDelete(row)}>
                <DeleteOutlineIcon fontSize="small" />
              </IconButton>
            </Tooltip>
          </Box>
        </Stack>
      </Stack>
    </Paper>
  );
}

export function PurchaseWishlistTable({
  rows,
  status,
  viewMode,
  reorderEnabled,
  onEdit,
  onDelete,
  onMarkAsPurchased,
  onReorder,
  onUpdateCategory,
  updatingCategoryItemId,
}: PurchaseWishlistTableProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const [categoryPickerAnchorEl, setCategoryPickerAnchorEl] = useState<HTMLElement | null>(null);
  const [categoryPickerRow, setCategoryPickerRow] = useState<WishlistItemRow | null>(null);

  const sensors = useSensors(
    useSensor(MouseSensor, { activationConstraint: { distance: 8 } }),
    useSensor(TouchSensor, { activationConstraint: { delay: 180, tolerance: 6 } }),
  );

  const moveRow = (fromIndex: number, toIndex: number) => {
    if (fromIndex === toIndex) {
      return;
    }

    const orderedIds = arrayMove(
      rows.map((row) => row.id),
      fromIndex,
      toIndex,
    );
    onReorder(orderedIds);
  };

  const handleDragEnd = (event: DragEndEvent) => {
    if (!reorderEnabled || !event.over || event.active.id === event.over.id) {
      return;
    }

    const oldIndex = rows.findIndex((row) => row.id === event.active.id);
    const newIndex = rows.findIndex((row) => row.id === event.over?.id);
    if (oldIndex < 0 || newIndex < 0) {
      return;
    }

    moveRow(oldIndex, newIndex);
  };

  const handleOpenCategoryPicker = (event: MouseEvent<HTMLElement>, row: WishlistItemRow) => {
    setCategoryPickerAnchorEl(event.currentTarget);
    setCategoryPickerRow(row);
  };

  const handleCloseCategoryPicker = () => {
    setCategoryPickerAnchorEl(null);
    setCategoryPickerRow(null);
  };

  const handleSelectCategory = async (categoryId: string | null) => {
    if (!categoryPickerRow) {
      handleCloseCategoryPicker();
      return;
    }

    if ((categoryPickerRow.category?.id ?? null) === categoryId) {
      handleCloseCategoryPicker();
      return;
    }

    const row = categoryPickerRow;
    handleCloseCategoryPicker();
    await onUpdateCategory(row, categoryId);
  };

  if (rows.length === 0) {
    return (
      <EmptyState
        title={
          status === 'ACTIVE'
            ? t('purchaseWishlist.emptyTitle')
            : t('purchaseWishlist.history.emptyTitle')
        }
        description={
          status === 'ACTIVE'
            ? t('purchaseWishlist.emptyDescription')
            : t('purchaseWishlist.history.emptyDescription')
        }
      />
    );
  }

  return (
    <Box
      sx={{
        pt: 2,
      }}
    >
      <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
        <SortableContext items={rows.map((row) => row.id)} strategy={rectSortingStrategy}>
          <Box
            sx={{
              display: 'grid',
              gridTemplateColumns:
                viewMode === 'grid'
                  ? {
                      xs: '1fr',
                      md: 'repeat(3, minmax(0, 1fr))',
                    }
                  : '1fr',
              gap: 1.25,
              alignItems: 'stretch',
            }}
          >
            {rows.map((row) => (
              <SortableWishlistRow
                key={row.id}
                row={row}
                status={status}
                viewMode={viewMode}
                reorderEnabled={reorderEnabled}
                onEdit={onEdit}
                onDelete={onDelete}
                onMarkAsPurchased={onMarkAsPurchased}
                onOpenCategoryPicker={handleOpenCategoryPicker}
                updatingCategoryItemId={updatingCategoryItemId}
              />
            ))}
          </Box>
        </SortableContext>
      </DndContext>

      <CategoryPicker
        value={categoryPickerRow?.category?.id ?? null}
        onChange={(categoryId) => {
          void handleSelectCategory(categoryId);
        }}
        creditDebitType="DEBIT"
        autoClearSelection={false}
        anchorEl={categoryPickerAnchorEl}
        onClose={handleCloseCategoryPicker}
      />
    </Box>
  );
}
