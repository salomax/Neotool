'use client';

import React, { useState, useMemo, useCallback } from 'react';
import { useQuery, useMutation } from '@apollo/client/react';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../i18n';
import {
  Box,
  Button,
  Typography,
  Paper,
  Table,
  TableContainer,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions,
  TextField,
  InputAdornment,
  Tabs,
  Tab,
  TableHead,
  TableRow,
  TableCell,
  TableBody,
  alpha,
  useTheme,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import SearchIcon from '@mui/icons-material/Search';

import { ManagementLayout } from '@/shared/components/management/ManagementLayout';
import { ManagementHeaderActions, ManagementHeaderActionButton } from '@/shared/components/management/ManagementHeaderActions';
import { useToast } from '@/shared/providers';
import { FINANCE_TABLE_TYPOGRAPHY_SX } from '@/shared/components/ui/data-display/Table';
import { Label } from '@/shared/components/ui/primitives/Label';

import {
  TRANSACTION_RULES,
} from '@/lib/graphql/operations/banking/queries';
import {
  DELETE_TRANSACTION_RULE,
} from '@/lib/graphql/operations/banking/mutations';

import type { TransactionRule } from './types';
import { TransactionRuleRow } from './TransactionRuleRow';
import { TransactionRuleDrawer } from './TransactionRuleDrawer';

interface TransactionRulesData {
  transactionRules: TransactionRule[];
}

type RulesTab = 'manual' | 'automatic';

export const TransactionRulesManagement: React.FC = () => {
  const theme = useTheme();
  const toast = useToast();
  const { t } = useTranslation(personalFinanceTranslations);

  const [tab, setTab] = useState<RulesTab>('manual');
  const [searchQuery, setSearchQuery] = useState('');
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editingRule, setEditingRule] = useState<TransactionRule | null>(null);
  const [ruleToDelete, setRuleToDelete] = useState<TransactionRule | null>(null);

  const { data, loading, error, refetch } = useQuery<TransactionRulesData>(TRANSACTION_RULES, {
    fetchPolicy: 'cache-and-network',
  });

  const [deleteRule, { loading: deleteLoading }] = useMutation(DELETE_TRANSACTION_RULE, {
    onCompleted: () => {
      toast.success(t('financeTransactions.transactionRules.toast.deleted'));
      setRuleToDelete(null);
      refetch();
    },
    onError: (err) => toast.error(err.message),
  });

  const filteredRules = useMemo(() => {
    const allRules = data?.transactionRules ?? [];
    if (!searchQuery.trim()) return allRules;
    const q = searchQuery.toLowerCase();
    return allRules.filter((r) => r.pattern.toLowerCase().includes(q));
  }, [data?.transactionRules, searchQuery]);

  const explicitRules = useMemo(
    () => filteredRules.filter((r) => !r.isImplicit),
    [filteredRules],
  );
  const implicitRules = useMemo(
    () => filteredRules.filter((r) => r.isImplicit),
    [filteredRules],
  );

  const effectiveTab: RulesTab = implicitRules.length === 0 && tab === 'automatic' ? 'manual' : tab;

  const handleCreate = useCallback(() => {
    setEditingRule(null);
    setDrawerOpen(true);
  }, []);

  const handleEdit = useCallback((rule: TransactionRule) => {
    setEditingRule(rule);
    setDrawerOpen(true);
  }, []);

  const handleDelete = useCallback((rule: TransactionRule) => {
    setRuleToDelete(rule);
  }, []);

  return (
    <ManagementLayout
      error={error}
      onErrorRetry={refetch}
      errorFallbackMessage={t('financeTransactions.transactionRules.errors.load')}
    >
      <ManagementLayout.Header>
        <Box
          sx={{
            display: 'flex',
            alignItems: 'flex-end',
            justifyContent: 'space-between',
            gap: 2,
            mb: 2,
            borderBottom: `1px solid ${theme.palette.divider}`,
          }}
        >
          <Box sx={{ flex: 1, minWidth: 0 }}>
            <Tabs
              value={effectiveTab}
              onChange={(_, next: RulesTab) => {
                setTab(next);
              }}
              sx={{
                '& .MuiTab-root': {
                  textTransform: 'none',
                  fontWeight: theme.typography.button.fontWeight,
                  fontSize: theme.typography.button.fontSize,
                  letterSpacing: theme.typography.button.letterSpacing,
                  lineHeight: theme.typography.button.lineHeight,
                  px: 0,
                  mr: 3,
                  minHeight: 48,
                  minWidth: 'auto',
                },
                '& .MuiTabs-indicator': {
                  height: 3,
                  borderRadius: 2,
                },
              }}
            >
              <Tab value="manual" label={t('financeTransactions.transactionRules.tabs.manual')} />
              <Tab value="automatic" label={t('financeTransactions.transactionRules.tabs.automatic')} />
            </Tabs>
          </Box>
          <ManagementHeaderActions>
            <Box sx={{ mb: 2 }}>
              <TextField
                size="small"
                placeholder={t('financeTransactions.transactionRules.searchPlaceholder')}
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                InputProps={{
                  startAdornment: (
                    <InputAdornment position="start">
                      <SearchIcon fontSize="small" />
                    </InputAdornment>
                  ),
                }}
                sx={{
                  width: 360,
                  maxWidth: '100%',
                  '& .MuiInputBase-root': {
                    height: '51px',
                    borderRadius: `${(theme as any).custom?.radius?.md ?? theme.shape.borderRadius}px`,
                  },
                  '& .MuiInputBase-input': {
                    py: 0,
                  },
                }}
              />
            </Box>
            <ManagementHeaderActionButton
              variant="contained"
              startIcon={<AddIcon />}
              onClick={handleCreate}
              sx={{
                boxShadow: `0 10px 24px ${alpha(theme.palette.primary.main, 0.28)}`,
                '&:hover': {
                  boxShadow: `0 12px 28px ${alpha(theme.palette.primary.main, 0.36)}`,
                },
              }}
            >
              {t('financeTransactions.transactionRules.actions.newRule')}
            </ManagementHeaderActionButton>
          </ManagementHeaderActions>
        </Box>

      </ManagementLayout.Header>

      <ManagementLayout.Content>
        {effectiveTab === 'manual' ? (
          <TableContainer
            component={Paper}
            elevation={0}
            sx={{
              bgcolor: 'transparent',
              background: 'transparent !important',
              border: 'none',
              overflow: 'visible',
              position: 'relative',
            }}
          >
            <Table sx={{ minWidth: 1050, borderCollapse: 'separate', borderSpacing: '0', ...FINANCE_TABLE_TYPOGRAPHY_SX }}>
              <TableHead>
                <TableRow>
                  <TableCell
                    sx={{
                      pl: 2,
                      borderBottom: explicitRules.length > 0 ? 'none' : `1px solid ${theme.palette.divider}`,
                      bgcolor: 'transparent',
                      width: 140,
                    }}
                  >
                    <Label>{t('financeTransactions.transactionRules.columns.context')}</Label>
                  </TableCell>
                  <TableCell
                    sx={{
                      borderBottom: explicitRules.length > 0 ? 'none' : `1px solid ${theme.palette.divider}`,
                      bgcolor: 'transparent',
                      width: 140,
                    }}
                  >
                    <Label>{t('financeTransactions.transactionRules.columns.rule')}</Label>
                  </TableCell>
                  <TableCell
                    sx={{
                      borderBottom: explicitRules.length > 0 ? 'none' : `1px solid ${theme.palette.divider}`,
                      bgcolor: 'transparent',
                    }}
                  >
                    <Label>{t('financeTransactions.transactionRules.columns.matchingText')}</Label>
                  </TableCell>
                  <TableCell
                    sx={{
                      borderBottom: explicitRules.length > 0 ? 'none' : `1px solid ${theme.palette.divider}`,
                      bgcolor: 'transparent',
                      width: 220,
                    }}
                  >
                    <Label>{t('financeTransactions.transactionRules.columns.newDescription')}</Label>
                  </TableCell>
                  <TableCell
                    sx={{
                      borderBottom: explicitRules.length > 0 ? 'none' : `1px solid ${theme.palette.divider}`,
                      bgcolor: 'transparent',
                      width: 180,
                    }}
                  >
                    <Label>{t('financeTransactions.transactionRules.columns.category')}</Label>
                  </TableCell>
                  <TableCell
                    sx={{
                      borderBottom: explicitRules.length > 0 ? 'none' : `1px solid ${theme.palette.divider}`,
                      bgcolor: 'transparent',
                      width: 220,
                    }}
                  >
                    <Label>{t('financeTransactions.transactionRules.columns.tags')}</Label>
                  </TableCell>
                  <TableCell
                    sx={{
                      borderBottom: explicitRules.length > 0 ? 'none' : `1px solid ${theme.palette.divider}`,
                      bgcolor: 'transparent',
                      width: 220,
                    }}
                  >
                    <Label>{t('financeTransactions.transactionRules.columns.merchant')}</Label>
                  </TableCell>
                  <TableCell
                    sx={{
                      borderBottom: explicitRules.length > 0 ? 'none' : `1px solid ${theme.palette.divider}`,
                      bgcolor: 'transparent',
                      width: 120,
                    }}
                  >
                    <Label>{t('financeTransactions.transactionRules.columns.priority')}</Label>
                  </TableCell>
                  <TableCell
                    sx={{
                      px: 4,
                      borderBottom: explicitRules.length > 0 ? 'none' : `1px solid ${theme.palette.divider}`,
                      bgcolor: 'transparent',
                    }}
                  />
                </TableRow>
              </TableHead>
              <TableBody>
                {explicitRules.map((rule) => (
                  <TransactionRuleRow
                    key={rule.id}
                    rule={rule}
                    onEdit={handleEdit}
                    onDelete={handleDelete}
                    renderAs="table"
                    isFirst={explicitRules[0]?.id === rule.id}
                    isLast={explicitRules[explicitRules.length - 1]?.id === rule.id}
                  />
                ))}
                {!loading && explicitRules.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={9}>
                      <Typography variant="body2" color="text.secondary">
                        {t('financeTransactions.transactionRules.emptyState.manual')}
                      </Typography>
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </TableContainer>
        ) : (
          <TableContainer
            component={Paper}
            elevation={0}
            sx={{
              bgcolor: 'transparent',
              background: 'transparent !important',
              border: 'none',
              overflow: 'visible',
              position: 'relative',
            }}
          >
            <Table sx={{ minWidth: 1050, borderCollapse: 'separate', borderSpacing: '0', ...FINANCE_TABLE_TYPOGRAPHY_SX }}>
              <TableHead>
                <TableRow>
                  <TableCell
                    sx={{
                      pl: 2,
                      borderBottom: implicitRules.length > 0 ? 'none' : `1px solid ${theme.palette.divider}`,
                      bgcolor: 'transparent',
                      width: 140,
                    }}
                  >
                    <Label>{t('financeTransactions.transactionRules.columns.context')}</Label>
                  </TableCell>
                  <TableCell
                    sx={{
                      borderBottom: implicitRules.length > 0 ? 'none' : `1px solid ${theme.palette.divider}`,
                      bgcolor: 'transparent',
                      width: 140,
                    }}
                  >
                    <Label>{t('financeTransactions.transactionRules.columns.rule')}</Label>
                  </TableCell>
                  <TableCell
                    sx={{
                      borderBottom: implicitRules.length > 0 ? 'none' : `1px solid ${theme.palette.divider}`,
                      bgcolor: 'transparent',
                    }}
                  >
                    <Label>{t('financeTransactions.transactionRules.columns.matchingText')}</Label>
                  </TableCell>
                  <TableCell
                    sx={{
                      borderBottom: implicitRules.length > 0 ? 'none' : `1px solid ${theme.palette.divider}`,
                      bgcolor: 'transparent',
                      width: 220,
                    }}
                  >
                    <Label>{t('financeTransactions.transactionRules.columns.newDescription')}</Label>
                  </TableCell>
                  <TableCell
                    sx={{
                      borderBottom: implicitRules.length > 0 ? 'none' : `1px solid ${theme.palette.divider}`,
                      bgcolor: 'transparent',
                      width: 180,
                    }}
                  >
                    <Label>{t('financeTransactions.transactionRules.columns.category')}</Label>
                  </TableCell>
                  <TableCell
                    sx={{
                      borderBottom: implicitRules.length > 0 ? 'none' : `1px solid ${theme.palette.divider}`,
                      bgcolor: 'transparent',
                      width: 220,
                    }}
                  >
                    <Label>{t('financeTransactions.transactionRules.columns.tags')}</Label>
                  </TableCell>
                  <TableCell
                    sx={{
                      borderBottom: implicitRules.length > 0 ? 'none' : `1px solid ${theme.palette.divider}`,
                      bgcolor: 'transparent',
                      width: 220,
                    }}
                  >
                    <Label>{t('financeTransactions.transactionRules.columns.merchant')}</Label>
                  </TableCell>
                  <TableCell
                    sx={{
                      borderBottom: implicitRules.length > 0 ? 'none' : `1px solid ${theme.palette.divider}`,
                      bgcolor: 'transparent',
                      width: 120,
                    }}
                  >
                    <Label>{t('financeTransactions.transactionRules.columns.priority')}</Label>
                  </TableCell>
                  <TableCell
                    sx={{
                      px: 4,
                      borderBottom: implicitRules.length > 0 ? 'none' : `1px solid ${theme.palette.divider}`,
                      bgcolor: 'transparent',
                    }}
                  />
                </TableRow>
              </TableHead>
              <TableBody>
                {implicitRules.map((rule) => (
                  <TransactionRuleRow
                    key={rule.id}
                    rule={rule}
                    onEdit={handleEdit}
                    onDelete={handleDelete}
                    renderAs="table"
                    isFirst={implicitRules[0]?.id === rule.id}
                    isLast={implicitRules[implicitRules.length - 1]?.id === rule.id}
                  />
                ))}
                {!loading && implicitRules.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={9}>
                      <Typography variant="body2" color="text.secondary">
                        {t('financeTransactions.transactionRules.emptyState.automatic')}
                      </Typography>
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </TableContainer>
        )}

        <TransactionRuleDrawer
          key={`${drawerOpen ? 'open' : 'closed'}-${editingRule?.id ?? 'new'}`}
          open={drawerOpen}
          rule={editingRule}
          onClose={() => setDrawerOpen(false)}
          onSaved={() => {
            setDrawerOpen(false);
            refetch();
          }}
        />

        <Dialog open={!!ruleToDelete} onClose={() => setRuleToDelete(null)} maxWidth="xs">
          <DialogTitle>{t('financeTransactions.transactionRules.deleteDialog.title')}</DialogTitle>
          <DialogContent>
            <DialogContentText>{t('financeTransactions.transactionRules.deleteDialog.description')}</DialogContentText>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setRuleToDelete(null)}>{t('financeTransactions.actions.cancel')}</Button>
            <Button
              color="error"
              onClick={() => {
                if (ruleToDelete) {
                  deleteRule({ variables: { id: ruleToDelete.id } });
                }
              }}
              disabled={deleteLoading}
            >
              {t('financeTransactions.actions.delete')}
            </Button>
          </DialogActions>
        </Dialog>
      </ManagementLayout.Content>
    </ManagementLayout>
  );
};
