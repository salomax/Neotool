'use client';

import React, { useCallback, useState } from 'react';
import {
  Box,
  Chip,
  IconButton,
  ListItem,
  Menu,
  MenuItem,
  Divider,
  ListItemIcon,
  ListItemText,
  Tooltip,
  Typography,
  alpha,
  useTheme,
  TableRow,
  TableCell,
} from '@mui/material';
import EditIcon from '@mui/icons-material/Edit';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import DragHandleIcon from '@mui/icons-material/DragHandle';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import { CategoryApplicableTo, PatternType } from '@/lib/graphql/types/__generated__/graphql';
import { Logo } from '@/shared/components/ui/primitives';
import type { TransactionRule } from './types';

const PATTERN_TYPE_LABELS: Record<PatternType, string> = {
  [PatternType.Contains]: 'Contém',
  [PatternType.StartsWith]: 'Começa com',
  [PatternType.Exact]: 'Exato',
  [PatternType.Regex]: 'Regex',
};

const APPLICABLE_TO_LABELS: Record<CategoryApplicableTo, string> = {
  [CategoryApplicableTo.Any]: 'Qualquer',
  [CategoryApplicableTo.Income]: 'Receita',
  [CategoryApplicableTo.Expense]: 'Despesa',
  [CategoryApplicableTo.Transfer]: 'Transferência',
};

const TAG_COLORS = [
  '#F44336',
  '#E91E63',
  '#9C27B0',
  '#673AB7',
  '#3F51B5',
  '#2196F3',
  '#03A9F4',
  '#00BCD4',
  '#009688',
  '#4CAF50',
  '#8BC34A',
  '#CDDC39',
  '#FFEB3B',
  '#FFC107',
  '#FF9800',
  '#FF5722',
  '#795548',
  '#607D8B',
];

function stringToColor(value: string) {
  let hash = 0;
  for (let i = 0; i < value.length; i++) {
    hash = value.charCodeAt(i) + ((hash << 5) - hash);
  }
  const index = Math.abs(hash % TAG_COLORS.length);
  return TAG_COLORS[index]!;
}

function outlinedChipSx(theme: any, mainColor: string) {
  return {
    fontSize: '0.7rem',
    height: 20,
    borderColor: alpha(mainColor, 0.4),
    color: mainColor,
    backgroundColor: alpha(mainColor, 0.08),
  } as const;
}

function contextChipSx(theme: any, applicableTo: CategoryApplicableTo) {
  if (applicableTo === CategoryApplicableTo.Income) return outlinedChipSx(theme, theme.palette.success.main);
  if (applicableTo === CategoryApplicableTo.Expense) return outlinedChipSx(theme, theme.palette.error.main);
  if (applicableTo === CategoryApplicableTo.Transfer) return outlinedChipSx(theme, theme.palette.info.main);
  return outlinedChipSx(theme, theme.palette.text.secondary);
}

function ruleChipSx(theme: any, patternType: PatternType) {
  if (patternType === PatternType.Contains) return outlinedChipSx(theme, theme.palette.info.main);
  if (patternType === PatternType.StartsWith) return outlinedChipSx(theme, theme.palette.warning.main);
  if (patternType === PatternType.Exact) return outlinedChipSx(theme, theme.palette.primary.main);
  return outlinedChipSx(theme, theme.palette.secondary.main);
}

interface TransactionRuleRowProps {
  rule: TransactionRule;
  draggable?: boolean;
  onEdit: (rule: TransactionRule) => void;
  onDelete: (rule: TransactionRule) => void;
  renderAs?: 'list' | 'table';
  isFirst?: boolean;
  isLast?: boolean;
}

export const TransactionRuleRow: React.FC<TransactionRuleRowProps> = ({
  rule,
  draggable = false,
  onEdit,
  onDelete,
  renderAs = 'list',
  isFirst = false,
  isLast = false,
}) => {
  const theme = useTheme();
  const [actionMenuAnchorEl, setActionMenuAnchorEl] = useState<HTMLElement | null>(null);
  const isActionMenuOpen = Boolean(actionMenuAnchorEl);
  const actionMenuId = `transaction-rule-actions-menu-${rule.id}`;
  const actionButtonId = `transaction-rule-actions-button-${rule.id}`;

  const closeActionMenu = useCallback(() => {
    setActionMenuAnchorEl(null);
  }, []);

  const handleEditClick = useCallback(() => {
    closeActionMenu();
    onEdit(rule);
  }, [closeActionMenu, onEdit, rule]);

  const handleDeleteClick = useCallback(() => {
    closeActionMenu();
    onDelete(rule);
  }, [closeActionMenu, onDelete, rule]);

  if (renderAs === 'table') {
    return (
      <TableRow
        hover
        sx={{
          '&:hover .rule-actions': { opacity: 1 },
          '&:hover': { bgcolor: alpha(theme.palette.primary.main, 0.04) },
          '& .MuiTableCell-root': {
            bgcolor: 'background.paper',
            borderColor: 'transparent',
          },
          '& .MuiTableCell-root:first-child': {
            borderTopLeftRadius: isFirst ? '12px' : 0,
            borderBottomLeftRadius: isLast ? '12px' : 0,
          },
          '& .MuiTableCell-root:last-child': {
            borderTopRightRadius: isFirst ? '12px' : 0,
            borderBottomRightRadius: isLast ? '12px' : 0,
          },
        }}
      >
        <TableCell sx={{ pl: 2 }}>
          <Chip
            label={APPLICABLE_TO_LABELS[rule.applicableTo as CategoryApplicableTo]}
            size="small"
            variant="outlined"
            sx={contextChipSx(theme, rule.applicableTo as CategoryApplicableTo)}
          />
        </TableCell>

        <TableCell>
          <Chip
            label={PATTERN_TYPE_LABELS[rule.patternType as PatternType]}
            size="small"
            variant="outlined"
            sx={ruleChipSx(theme, rule.patternType as PatternType)}
          />
        </TableCell>

        <TableCell>
          <Typography
            variant="body2"
            color="text.secondary"
            sx={{
              fontStyle: 'italic',
              display: '-webkit-box',
              WebkitLineClamp: 3,
              WebkitBoxOrient: 'vertical',
              overflow: 'hidden',
            }}
            title={rule.pattern}
          >
            &quot;{rule.pattern}&quot;
          </Typography>
        </TableCell>

        <TableCell>
          {rule.descriptionOverride ? (
            <Typography
              variant="body2"
              color="text.secondary"
              sx={{
                fontStyle: 'italic',
                display: '-webkit-box',
                WebkitLineClamp: 3,
                WebkitBoxOrient: 'vertical',
                overflow: 'hidden',
              }}
              title={rule.descriptionOverride}
            >
              &quot;{rule.descriptionOverride}&quot;
            </Typography>
          ) : (
            <Typography variant="body2" color="text.disabled">
              —
            </Typography>
          )}
        </TableCell>

        <TableCell>
          {rule.category ? (
            <Chip
              label={rule.category.name}
              size="small"
              sx={{
                bgcolor: rule.category.color ? alpha(rule.category.color, 0.12) : undefined,
                borderColor: rule.category.color ? alpha(rule.category.color, 0.4) : undefined,
                color: rule.category.color ?? undefined,
                fontWeight: 600,
                fontSize: '0.75rem',
              }}
              variant="outlined"
            />
          ) : null}
        </TableCell>

        <TableCell>
          <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.75, alignItems: 'center' }}>
            {rule.tagsOverride?.map((tag: string) => {
              const color = stringToColor(tag);
              return (
                <Chip
                  key={tag}
                  label={`#${tag}`}
                  size="small"
                  variant="filled"
                  sx={{
                    fontSize: '0.7rem',
                    height: 20,
                    bgcolor: alpha(color, 0.05),
                    color: color,
                    borderRadius: '.3rem',
                  }}
                />
              );
            })}
          </Box>
        </TableCell>

        <TableCell>
          {rule.merchant ? (
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <Logo
                src={rule.merchant.logoAsset?.publicUrl ?? undefined}
                alt={rule.merchant.name}
                size="xs"
              />
              <Typography variant="body2">{rule.merchant.name}</Typography>
            </Box>
          ) : null}
        </TableCell>

        <TableCell>
          <Typography variant="body2" color="text.secondary" sx={{ fontVariantNumeric: 'tabular-nums' }}>
            {rule.priority}
          </Typography>
        </TableCell>

        <TableCell
          align="center"
          padding="none"
          sx={{
            width: 30,
            maxWidth: 30,
          }}
        >
          <IconButton
            size="small"
            sx={{ p: 0.5 }}
            onClick={(e) => setActionMenuAnchorEl(e.currentTarget)}
            id={actionButtonId}
            aria-label="Ações da regra"
            aria-haspopup="menu"
            aria-expanded={isActionMenuOpen ? 'true' : undefined}
            aria-controls={isActionMenuOpen ? actionMenuId : undefined}
          >
            <MoreVertIcon fontSize="small" />
          </IconButton>

          <Menu
            id={actionMenuId}
            open={isActionMenuOpen}
            anchorEl={actionMenuAnchorEl}
            onClose={closeActionMenu}
            anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
            transformOrigin={{ vertical: 'top', horizontal: 'right' }}
            MenuListProps={{
              'aria-labelledby': actionButtonId,
            }}
          >
            <MenuItem onClick={handleEditClick}>
              <ListItemIcon>
                <EditIcon fontSize="small" />
              </ListItemIcon>
              <ListItemText>Editar</ListItemText>
            </MenuItem>
            <Divider />
            <MenuItem
              onClick={handleDeleteClick}
              sx={{
                color: 'error.main',
                '&:hover': {
                  bgcolor: alpha(theme.palette.error.main, 0.08),
                },
                '&.Mui-focusVisible': {
                  bgcolor: alpha(theme.palette.error.main, 0.08),
                },
              }}
            >
              <ListItemIcon sx={{ color: 'inherit' }}>
                <DeleteOutlineIcon fontSize="small" />
              </ListItemIcon>
              <ListItemText>Excluir</ListItemText>
            </MenuItem>
          </Menu>
        </TableCell>
      </TableRow>
    );
  }

  return (
    <ListItem
      disablePadding
      sx={{
        borderBottom: `1px solid ${theme.palette.divider}`,
        '&:last-child': { borderBottom: 'none' },
        '&:hover .rule-actions': { opacity: 1 },
        '&:hover': { bgcolor: alpha(theme.palette.primary.main, 0.04) },
        px: 2,
        py: 1.5,
        gap: 1.5,
        display: 'flex',
        alignItems: 'flex-start',
      }}
    >
      {draggable ? (
        <Box
          sx={{
            cursor: 'grab',
            color: theme.palette.text.disabled,
            display: 'flex',
            alignItems: 'center',
            pt: 0.25,
            flexShrink: 0,
            '&:active': { cursor: 'grabbing' },
          }}
        >
          <DragHandleIcon fontSize="small" />
        </Box>
      ) : (
        <Box sx={{ width: 20, flexShrink: 0 }} />
      )}

      <Box sx={{ flex: 1, minWidth: 0 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
          <Typography variant="body2" fontWeight={600} sx={{ fontFamily: 'monospace' }}>
            &quot;{rule.pattern}&quot;
          </Typography>
          <Chip
            label={PATTERN_TYPE_LABELS[rule.patternType as PatternType]}
            size="small"
            variant="outlined"
            sx={ruleChipSx(theme, rule.patternType as PatternType)}
          />
          {rule.applicableTo !== CategoryApplicableTo.Any && (
            <Chip
              label={APPLICABLE_TO_LABELS[rule.applicableTo as CategoryApplicableTo]}
              size="small"
              variant="outlined"
              sx={contextChipSx(theme, rule.applicableTo as CategoryApplicableTo)}
            />
          )}
        </Box>
      </Box>

      <Box sx={{ flex: 1, minWidth: 0, display: 'flex', flexWrap: 'wrap', gap: 0.75, alignItems: 'center' }}>
        {rule.category && (
          <Chip
            label={rule.category.name}
            size="small"
            sx={{
              bgcolor: rule.category.color ? alpha(rule.category.color, 0.12) : undefined,
              borderColor: rule.category.color ? alpha(rule.category.color, 0.4) : undefined,
              color: rule.category.color ?? undefined,
              fontWeight: 600,
              fontSize: '0.75rem',
            }}
            variant="outlined"
          />
        )}
        {rule.merchant && (
          <Chip
            label={rule.merchant.name}
            size="small"
            variant="outlined"
            sx={{ fontSize: '0.75rem' }}
          />
        )}
        {rule.descriptionOverride && (
          <Typography variant="caption" color="text.secondary" sx={{ fontStyle: 'italic' }}>
            → &quot;{rule.descriptionOverride}&quot;
          </Typography>
        )}
        {rule.tagsOverride?.map((tag: string) => {
          const color = stringToColor(tag);
          return (
            <Chip
              key={tag}
              label={`#${tag}`}
              size="small"
              variant="filled"
              sx={{
                fontSize: '0.7rem',
                height: 20,
                bgcolor: alpha(color, 0.05),
                color: color,
                borderRadius: '.3rem',
              }}
            />
          );
        })}
        {rule.matchCount > 0 && (
          <Chip
            label={`${rule.matchCount}×`}
            size="small"
            variant="outlined"
            sx={{ fontSize: '0.7rem', height: 20, color: 'text.secondary', borderColor: 'divider' }}
          />
        )}
      </Box>

      <Box
        className="rule-actions"
        sx={{
          display: 'flex',
          gap: 0.5,
          opacity: 0.4,
          transition: 'opacity 0.15s',
          flexShrink: 0,
        }}
      >
        <Tooltip title="Ações">
          <IconButton
            size="small"
            onClick={(e) => setActionMenuAnchorEl(e.currentTarget)}
            id={actionButtonId}
            aria-label="Ações da regra"
            aria-haspopup="menu"
            aria-expanded={isActionMenuOpen ? 'true' : undefined}
            aria-controls={isActionMenuOpen ? actionMenuId : undefined}
          >
            <MoreVertIcon fontSize="small" />
          </IconButton>
        </Tooltip>

        <Menu
          id={actionMenuId}
          open={isActionMenuOpen}
          anchorEl={actionMenuAnchorEl}
          onClose={closeActionMenu}
          anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
          transformOrigin={{ vertical: 'top', horizontal: 'right' }}
          MenuListProps={{
            'aria-labelledby': actionButtonId,
          }}
        >
          <MenuItem onClick={handleEditClick}>
            <ListItemIcon>
              <EditIcon fontSize="small" />
            </ListItemIcon>
            <ListItemText>Editar</ListItemText>
          </MenuItem>
          <Divider />
          <MenuItem
            onClick={handleDeleteClick}
            sx={{
              color: 'error.main',
              '&:hover': {
                bgcolor: alpha(theme.palette.error.main, 0.08),
              },
              '&.Mui-focusVisible': {
                bgcolor: alpha(theme.palette.error.main, 0.08),
              },
            }}
          >
            <ListItemIcon sx={{ color: 'inherit' }}>
              <DeleteOutlineIcon fontSize="small" />
            </ListItemIcon>
            <ListItemText>Excluir</ListItemText>
          </MenuItem>
        </Menu>
      </Box>
    </ListItem>
  );
};
