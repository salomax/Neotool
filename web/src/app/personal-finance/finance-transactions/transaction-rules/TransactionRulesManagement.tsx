'use client';

import React, { useState, useMemo, useCallback, useEffect, useRef } from 'react';
import { useLazyQuery, useQuery, useMutation } from '@apollo/client/react';
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
  IconButton,
  LinearProgress,
  Stack,
  // Note: Using MUI Box throughout; for scroll container use LayoutBox
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
import ClearIcon from '@mui/icons-material/Clear';
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined';

import { ManagementLayout } from '@/shared/components/management/ManagementLayout';
import { ManagementHeaderActions, ManagementHeaderActionButton } from '@/shared/components/management/ManagementHeaderActions';
import { useToast } from '@/shared/providers';
import { extractErrorMessage } from '@/shared/utils/error';
import { FINANCE_TABLE_TYPOGRAPHY_SX } from '@/shared/components/ui/data-display/Table';
import { Label } from '@/shared/components/ui/primitives/Label';

import {
  TRANSACTION_RULES,
  TRANSACTION_RULES_BY_IDS,
  TRANSACTION_RULE_HISTORICAL_MATCH_COUNT,
} from '@/lib/graphql/operations/banking/queries';
import {
  DELETE_TRANSACTION_RULE,
  SET_SYSTEM_TRANSACTION_RULE_ENABLED,
  APPLY_TRANSACTION_RULE_TO_HISTORICAL,
} from '@/lib/graphql/operations/banking/mutations';

import type { TransactionRule } from './types';
import { TransactionRuleRow } from './TransactionRuleRow';
import { TransactionRuleDrawer } from './TransactionRuleDrawer';
import { ApplyRuleToHistoricalDialog } from '../transaction/ApplyRuleToHistoricalDialog';

interface TransactionRulesData {
  transactionRules: {
    edges: Array<{
      node: TransactionRule;
      cursor: string;
    }>;
    pageInfo: {
      hasNextPage: boolean;
      hasPreviousPage: boolean;
      startCursor?: string | null;
      endCursor?: string | null;
    };
    totalCount?: number | null;
  };
}

interface TransactionRulesVariables {
  isSystem?: boolean;
  search?: string;
  first?: number;
  after?: string | null;
}

type RulesTab = 'manual' | 'automatic';
const RULES_PAGE_SIZE = 30;

export interface TransactionRulesManagementProps {
  onOpenHelp?: () => void;
  initialTab?: RulesTab;
  initialEditRuleId?: string | null;
}

interface TransactionRulesByIdsData {
  transactionRulesByIds: TransactionRule[];
}

interface TransactionRulesByIdsVariables {
  ids: string[];
}

export const TransactionRulesManagement: React.FC<TransactionRulesManagementProps> = ({
  onOpenHelp,
  initialTab,
  initialEditRuleId,
}) => {
  const theme = useTheme();
  const toast = useToast();
  const { t } = useTranslation(personalFinanceTranslations);

  const [tab, setTab] = useState<RulesTab>(initialTab ?? 'manual');
  const [searchQuery, setSearchQuery] = useState('');
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editingRule, setEditingRule] = useState<TransactionRule | null>(null);
  const [applyRuleToHistoryPrompt, setApplyRuleToHistoryPrompt] = useState<{ ruleId: string; matchCount: number } | null>(
    null,
  );
  const [ruleToDelete, setRuleToDelete] = useState<TransactionRule | null>(null);
  const [isFetchingMore, setIsFetchingMore] = useState(false);
  const isFetchingMoreRef = useRef(false);
  const loaderRef = useRef<HTMLDivElement | null>(null);
  const tableContainerRef = useRef<HTMLDivElement | null>(null);
  const tableHeadRef = useRef<HTMLTableSectionElement | null>(null);
  const [progressTop, setProgressTop] = useState<number>(0);
  const [deepLinkClosed, setDeepLinkClosed] = useState(false);
  const deepLinkErrorNotifiedRef = useRef(false);

  const isSystemFilter = tab === 'automatic';
  const normalizedSearchQuery = searchQuery.trim();
  const { data, loading, error, refetch, fetchMore } = useQuery<TransactionRulesData, TransactionRulesVariables>(TRANSACTION_RULES, {
    fetchPolicy: 'cache-and-network',
    variables: {
      isSystem: isSystemFilter,
      search: normalizedSearchQuery.length > 0 ? normalizedSearchQuery : undefined,
      first: RULES_PAGE_SIZE,
      after: null,
    },
  });

  const normalizedEditRuleId = initialEditRuleId?.trim() ? initialEditRuleId.trim() : null;
  const {
    data: deepLinkRuleData,
    error: deepLinkRuleError,
  } = useQuery<TransactionRulesByIdsData, TransactionRulesByIdsVariables>(TRANSACTION_RULES_BY_IDS, {
    variables: normalizedEditRuleId ? { ids: [normalizedEditRuleId] } : { ids: [] },
    skip: !normalizedEditRuleId,
    fetchPolicy: 'cache-first',
  });

  useEffect(() => {
    if (!normalizedEditRuleId) return;
    if (!deepLinkRuleError) return;
    if (deepLinkErrorNotifiedRef.current) return;

    deepLinkErrorNotifiedRef.current = true;
    toast.error(extractErrorMessage(deepLinkRuleError, t('financeTransactions.transactionRules.errors.load')));
  }, [deepLinkRuleError, normalizedEditRuleId, t, toast]);

  const deepLinkRule = deepLinkRuleData?.transactionRulesByIds?.[0] ?? null;
  const deepLinkOpen = !!normalizedEditRuleId && !deepLinkClosed && !!deepLinkRule;
  const effectiveDrawerOpen = drawerOpen || deepLinkOpen;
  const effectiveRule = drawerOpen ? editingRule : deepLinkOpen ? deepLinkRule : null;
  const isCreatingRule = effectiveDrawerOpen && !effectiveRule;

  const [loadRuleHistoricalMatchCount] = useLazyQuery<{ transactionRuleHistoricalMatchCount: number }>(
    TRANSACTION_RULE_HISTORICAL_MATCH_COUNT,
    { fetchPolicy: 'no-cache' },
  );
  const [applyTransactionRuleToHistorical, { loading: applyingRuleToHistory }] = useMutation(
    APPLY_TRANSACTION_RULE_TO_HISTORICAL,
  );

  const [deleteRule, { loading: deleteLoading }] = useMutation(DELETE_TRANSACTION_RULE, {
    onCompleted: () => {
      toast.success(t('financeTransactions.transactionRules.toast.deleted'));
      setRuleToDelete(null);
      refetch();
    },
    onError: (err) =>
      toast.error(
        extractErrorMessage(err, t('financeTransactions.transactionRules.errors.delete')),
      ),
  });

  const [setSystemRuleEnabled] = useMutation(SET_SYSTEM_TRANSACTION_RULE_ENABLED, {
    onCompleted: () => {
      toast.success(t('financeTransactions.transactionRules.toast.updated'));
      refetch();
    },
    onError: (err) =>
      toast.error(
        extractErrorMessage(err, t('financeTransactions.transactionRules.errors.toggleSystemRule')),
      ),
  });

  const filteredRules = useMemo(
    () => data?.transactionRules?.edges?.map((edge) => edge.node) ?? [],
    [data?.transactionRules?.edges],
  );

  const hasMore = data?.transactionRules?.pageInfo?.hasNextPage ?? false;
  const endCursor = data?.transactionRules?.pageInfo?.endCursor ?? null;
  const showHeaderProgress = loading || isFetchingMore;
  
  const updateProgressTop = useCallback(() => {
    const c = tableContainerRef.current;
    const h = tableHeadRef.current;
    if (!c || !h) return;
    const cRect = c.getBoundingClientRect();
    const hRect = h.getBoundingClientRect();
    const top = Math.max(0, Math.round(hRect.bottom - cRect.top - 1));
    setProgressTop(top);
  }, []);

  useEffect(() => {
    updateProgressTop();
    window.addEventListener('resize', updateProgressTop);

    const c = tableContainerRef.current;
    const h = tableHeadRef.current;
    const ResizeObserverImpl = typeof ResizeObserver === 'undefined' ? null : ResizeObserver;
    const ro =
      ResizeObserverImpl && (c || h)
        ? new ResizeObserverImpl(() => {
            updateProgressTop();
          })
        : null;

    if (ro) {
      if (c) ro.observe(c);
      if (h) ro.observe(h);
    }

    return () => {
      window.removeEventListener('resize', updateProgressTop);
      ro?.disconnect();
    };
  }, [tab, updateProgressTop]);

  useEffect(() => {
    const id = window.requestAnimationFrame(() => updateProgressTop());
    return () => window.cancelAnimationFrame(id);
  }, [tab, updateProgressTop]);

  const handleLoadMore = useCallback(() => {
    if (!hasMore || !endCursor) return;
    if (loading || isFetchingMoreRef.current) return;

    isFetchingMoreRef.current = true;
    setIsFetchingMore(true);

    fetchMore({
      variables: {
        isSystem: isSystemFilter,
        search: normalizedSearchQuery.length > 0 ? normalizedSearchQuery : undefined,
        first: RULES_PAGE_SIZE,
        after: endCursor,
      },
      updateQuery: (previousQueryResult, { fetchMoreResult }) => {
        if (!fetchMoreResult?.transactionRules) return previousQueryResult;

        const previousEdges = previousQueryResult.transactionRules?.edges ?? [];
        const incomingEdges = fetchMoreResult.transactionRules.edges ?? [];

        const seenIds = new Set<string>();
        const mergedEdges = [...previousEdges, ...incomingEdges].filter((edge) => {
          const id = edge?.node?.id;
          if (!id) return false;
          if (seenIds.has(id)) return false;
          seenIds.add(id);
          return true;
        });

        return {
          ...previousQueryResult,
          transactionRules: {
            ...previousQueryResult.transactionRules,
            ...fetchMoreResult.transactionRules,
            edges: mergedEdges,
            pageInfo: fetchMoreResult.transactionRules.pageInfo,
          },
        };
      },
    }).finally(() => {
      isFetchingMoreRef.current = false;
      setIsFetchingMore(false);
    });
  }, [endCursor, fetchMore, hasMore, isSystemFilter, loading, normalizedSearchQuery]);

  useEffect(() => {
    const currentLoader = loaderRef.current;
    if (!currentLoader) return;

    const observer = new IntersectionObserver(
      (entries) => {
        const entry = entries[0];
        if (entry?.isIntersecting && hasMore && !loading) {
          handleLoadMore();
        }
      },
      { threshold: 0, rootMargin: '200px 0px' },
    );

    observer.observe(currentLoader);
    return () => observer.disconnect();
  }, [handleLoadMore, hasMore, loading]);

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
        <Box sx={{ px: 3, mb: 2 }}>
          <ManagementHeaderActions>
            <Box sx={{ flex: 1, minWidth: 240, mb: 2 }}>
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
                    endAdornment: searchQuery.trim() ? (
                      <InputAdornment position="end">
                        <IconButton
                          size="small"
                          onMouseDown={(e) => e.preventDefault()}
                          onClick={() => setSearchQuery('')}
                          aria-label={t('financeTransactions.transactionRules.actions.clearSearchAriaLabel')}
                        >
                          <ClearIcon fontSize="small" />
                        </IconButton>
                      </InputAdornment>
                    ) : undefined,
                  }}
                  sx={{
                  width: '100%',
                    maxWidth: 520,
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
              variant="outlined"
              startIcon={<InfoOutlinedIcon />}
              onClick={() => onOpenHelp?.()}
            >
              {t('financeTransactions.transactionRules.helpBanner.title')}
            </ManagementHeaderActionButton>
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
          <Stack
            direction="row"
            spacing={2}
            sx={{ width: '100%', mt: 1 }}
            alignItems="flex-end"
            flexWrap="wrap"
          >
            <Box sx={{ flex: 1, minWidth: 240 }}>
              <Tabs
                value={tab}
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
          </Stack>
          <Box sx={{ borderBottom: `1px solid ${theme.palette.divider}` }} />
        </Box>

      </ManagementLayout.Header>

      <ManagementLayout.Content>
        <Box sx={{ height: '100%', minHeight: 0, overflowY: 'visible' }}>
          {tab === 'manual' ? (
            <Box sx={{ px: 3 }}>
            <TableContainer
              ref={tableContainerRef}
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
              <Box
                sx={{
                  position: 'absolute',
                  left: 0,
                  right: 0,
                  top: `${progressTop}px`,
                  height: 4,
                  pointerEvents: 'none',
                  zIndex: 2,
                }}
              >
                <LinearProgress
                  sx={{
                    height: '100%',
                    opacity: showHeaderProgress ? 1 : 0,
                    transition: 'opacity 150ms linear',
                  }}
                />
              </Box>
              <Table sx={{ minWidth: 1050, borderCollapse: 'separate', borderSpacing: '0', ...FINANCE_TABLE_TYPOGRAPHY_SX }}>
                <TableHead ref={tableHeadRef}>
                  <TableRow>
                    <TableCell
                      sx={{
                        pl: 2,
                        borderBottom: filteredRules.length > 0 ? 'none' : `1px solid ${theme.palette.divider}`,
                        bgcolor: 'transparent',
                        width: 140,
                      }}
                    >
                      <Label>{t('financeTransactions.transactionRules.columns.context')}</Label>
                    </TableCell>
                    <TableCell
                      sx={{
                        borderBottom: filteredRules.length > 0 ? 'none' : `1px solid ${theme.palette.divider}`,
                        bgcolor: 'transparent',
                        width: 140,
                      }}
                    >
                      <Label>{t('financeTransactions.transactionRules.columns.rule')}</Label>
                    </TableCell>
                    <TableCell
                      sx={{
                        borderBottom: filteredRules.length > 0 ? 'none' : `1px solid ${theme.palette.divider}`,
                        bgcolor: 'transparent',
                      }}
                    >
                      <Label>{t('financeTransactions.transactionRules.columns.matchingText')}</Label>
                    </TableCell>
                    <TableCell
                      sx={{
                        borderBottom: filteredRules.length > 0 ? 'none' : `1px solid ${theme.palette.divider}`,
                        bgcolor: 'transparent',
                        width: 220,
                      }}
                    >
                      <Label>{t('financeTransactions.transactionRules.columns.newDescription')}</Label>
                    </TableCell>
                    <TableCell
                      sx={{
                        borderBottom: filteredRules.length > 0 ? 'none' : `1px solid ${theme.palette.divider}`,
                        bgcolor: 'transparent',
                        width: 180,
                      }}
                    >
                      <Label>{t('financeTransactions.transactionRules.columns.category')}</Label>
                    </TableCell>
                    <TableCell
                      sx={{
                        borderBottom: filteredRules.length > 0 ? 'none' : `1px solid ${theme.palette.divider}`,
                        bgcolor: 'transparent',
                        width: 220,
                      }}
                    >
                      <Label>{t('financeTransactions.transactionRules.columns.tags')}</Label>
                    </TableCell>
                    <TableCell
                      sx={{
                        borderBottom: filteredRules.length > 0 ? 'none' : `1px solid ${theme.palette.divider}`,
                        bgcolor: 'transparent',
                        width: 220,
                      }}
                    >
                      <Label>{t('financeTransactions.transactionRules.columns.merchant')}</Label>
                    </TableCell>
                    <TableCell
                      sx={{
                        borderBottom: filteredRules.length > 0 ? 'none' : `1px solid ${theme.palette.divider}`,
                        bgcolor: 'transparent',
                        width: 80,
                      }}
                    >
                      <Label>{t('financeTransactions.transactionRules.columns.priority')}</Label>
                    </TableCell>
                    <TableCell
                      sx={{
                        px: 4,
                        borderBottom: filteredRules.length > 0 ? 'none' : `1px solid ${theme.palette.divider}`,
                        bgcolor: 'transparent',
                      }}
                    />
                  </TableRow>
                </TableHead>
                <TableBody>
                  {filteredRules.map((rule) => (
                    <TransactionRuleRow
                      key={rule.id}
                      rule={rule}
                      onEdit={handleEdit}
                      onDelete={handleDelete}
                      renderAs="table"
                      showActions
                      isFirst={filteredRules[0]?.id === rule.id}
                      isLast={filteredRules[filteredRules.length - 1]?.id === rule.id}
                      disabledChipLabel={t('financeTransactions.transactionRules.status.disabled')}
                    />
                  ))}
                  {!loading && filteredRules.length === 0 && (
                    <TableRow>
                    <TableCell colSpan={9} align="center" sx={{ py: 6 }}>
                        <Typography variant="body2" color="text.secondary">
                          {t('financeTransactions.transactionRules.emptyState.manual')}
                        </Typography>
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
              <Box ref={loaderRef} sx={{ height: 1 }} />
              {isFetchingMore && (
                <Box sx={{ py: 2, textAlign: 'center' }}>
                  <Typography variant="body2" color="text.secondary">
                    {t('financeTransactions.transactionRules.loadingMore')}
                  </Typography>
                </Box>
              )}
            </TableContainer>
            </Box>
          ) : (
            <Box sx={{ px: 3 }}>
            <TableContainer
              ref={tableContainerRef}
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
              <Box
                sx={{
                  position: 'absolute',
                  left: 0,
                  right: 0,
                  top: `${progressTop}px`,
                  height: 4,
                  pointerEvents: 'none',
                  zIndex: 2,
                }}
              >
                <LinearProgress
                  sx={{
                    height: '100%',
                    opacity: showHeaderProgress ? 1 : 0,
                    transition: 'opacity 150ms linear',
                  }}
                />
              </Box>
              <Table sx={{ minWidth: 1050, borderCollapse: 'separate', borderSpacing: '0', ...FINANCE_TABLE_TYPOGRAPHY_SX }}>
                <TableHead ref={tableHeadRef}>
                  <TableRow>
                    <TableCell
                      sx={{
                        pl: 2,
                        borderBottom: filteredRules.length > 0 ? 'none' : `1px solid ${theme.palette.divider}`,
                        bgcolor: 'transparent',
                        width: 140,
                      }}
                    >
                      <Label>{t('financeTransactions.transactionRules.columns.context')}</Label>
                    </TableCell>
                    <TableCell
                      sx={{
                        borderBottom: filteredRules.length > 0 ? 'none' : `1px solid ${theme.palette.divider}`,
                        bgcolor: 'transparent',
                        width: 140,
                      }}
                    >
                      <Label>{t('financeTransactions.transactionRules.columns.rule')}</Label>
                    </TableCell>
                    <TableCell
                      sx={{
                        borderBottom: filteredRules.length > 0 ? 'none' : `1px solid ${theme.palette.divider}`,
                        bgcolor: 'transparent',
                      }}
                    >
                      <Label>{t('financeTransactions.transactionRules.columns.matchingText')}</Label>
                    </TableCell>
                    <TableCell
                      sx={{
                        borderBottom: filteredRules.length > 0 ? 'none' : `1px solid ${theme.palette.divider}`,
                        bgcolor: 'transparent',
                        width: 220,
                      }}
                    >
                      <Label>{t('financeTransactions.transactionRules.columns.newDescription')}</Label>
                    </TableCell>
                    <TableCell
                      sx={{
                        borderBottom: filteredRules.length > 0 ? 'none' : `1px solid ${theme.palette.divider}`,
                        bgcolor: 'transparent',
                        width: 180,
                      }}
                    >
                      <Label>{t('financeTransactions.transactionRules.columns.category')}</Label>
                    </TableCell>
                    <TableCell
                      sx={{
                        borderBottom: filteredRules.length > 0 ? 'none' : `1px solid ${theme.palette.divider}`,
                        bgcolor: 'transparent',
                        width: 220,
                      }}
                    >
                      <Label>{t('financeTransactions.transactionRules.columns.tags')}</Label>
                    </TableCell>
                    <TableCell
                      sx={{
                        borderBottom: filteredRules.length > 0 ? 'none' : `1px solid ${theme.palette.divider}`,
                        bgcolor: 'transparent',
                        width: 220,
                      }}
                    >
                      <Label>{t('financeTransactions.transactionRules.columns.merchant')}</Label>
                    </TableCell>
                    <TableCell
                      sx={{
                        borderBottom: filteredRules.length > 0 ? 'none' : `1px solid ${theme.palette.divider}`,
                        bgcolor: 'transparent',
                        width: 80,
                      }}
                    >
                      <Label>{t('financeTransactions.transactionRules.columns.priority')}</Label>
                    </TableCell>
                    <TableCell
                      sx={{
                        px: 4,
                        borderBottom: filteredRules.length > 0 ? 'none' : `1px solid ${theme.palette.divider}`,
                        bgcolor: 'transparent',
                      }}
                    />
                  </TableRow>
                </TableHead>
                <TableBody>
                  {filteredRules.map((rule) => (
                    <TransactionRuleRow
                      key={rule.id}
                      rule={rule}
                      onEdit={handleEdit}
                      onDelete={handleDelete}
                      renderAs="table"
                      showActions
                      onToggleSystemRuleEnabled={(targetRule, enabled) => {
                        setSystemRuleEnabled({ variables: { id: targetRule.id, enabled } });
                      }}
                      disableSystemRuleLabel={t('financeTransactions.transactionRules.actions.disableAutomaticRule')}
                      enableSystemRuleLabel={t('financeTransactions.transactionRules.actions.enableAutomaticRule')}
                      disabledChipLabel={t('financeTransactions.transactionRules.status.disabled')}
                      isFirst={filteredRules[0]?.id === rule.id}
                      isLast={filteredRules[filteredRules.length - 1]?.id === rule.id}
                    />
                  ))}
                  {!loading && filteredRules.length === 0 && (
                    <TableRow>
                    <TableCell colSpan={9} align="center" sx={{ py: 6 }}>
                        <Typography variant="body2" color="text.secondary">
                          {t('financeTransactions.transactionRules.emptyState.automatic')}
                        </Typography>
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
              <Box ref={loaderRef} sx={{ height: 1 }} />
              {isFetchingMore && (
                <Box sx={{ py: 2, textAlign: 'center' }}>
                  <Typography variant="body2" color="text.secondary">
                    {t('financeTransactions.transactionRules.loadingMore')}
                  </Typography>
                </Box>
              )}
            </TableContainer>
            </Box>
          )}
        </Box>

        <TransactionRuleDrawer
          key={`${effectiveDrawerOpen ? 'open' : 'closed'}-${effectiveRule?.id ?? 'new'}`}
          open={effectiveDrawerOpen}
          rule={effectiveRule}
          onClose={() => {
            if (!drawerOpen && deepLinkOpen) {
              setDeepLinkClosed(true);
              return;
            }
            setDrawerOpen(false);
          }}
          onSaved={(savedRule) => {
            if (!drawerOpen && deepLinkOpen) {
              setDeepLinkClosed(true);
            } else {
              setDrawerOpen(false);
            }
            setEditingRule(null);
            refetch();
            if (!isCreatingRule) return;
            void (async () => {
              try {
                const result = await loadRuleHistoricalMatchCount({ variables: { id: savedRule.id } });
                const matchCount = result.data?.transactionRuleHistoricalMatchCount ?? 0;
                if (matchCount > 0) {
                  setApplyRuleToHistoryPrompt({ ruleId: savedRule.id, matchCount });
                }
              } catch (err) {
                toast.error(extractErrorMessage(err, t('financeTransactions.applyRuleToHistoryPrompt.toastError')));
              }
            })();
          }}
        />

        <ApplyRuleToHistoricalDialog
          open={Boolean(applyRuleToHistoryPrompt)}
          matchCount={applyRuleToHistoryPrompt?.matchCount ?? 0}
          loading={applyingRuleToHistory}
          onClose={() => setApplyRuleToHistoryPrompt(null)}
          onConfirm={() => {
            const payload = applyRuleToHistoryPrompt;
            if (!payload) return;
            void (async () => {
              try {
                const res = await applyTransactionRuleToHistorical({ variables: { id: payload.ruleId } });
                const affected = (res.data as any)?.applyTransactionRuleToHistorical?.affectedCount as number | undefined;
                toast.success(
                  t('financeTransactions.applyRuleToHistoryPrompt.toastSuccess', {
                    count: typeof affected === 'number' ? affected : payload.matchCount,
                  }),
                );
                setApplyRuleToHistoryPrompt(null);
              } catch (err) {
                toast.error(extractErrorMessage(err, t('financeTransactions.applyRuleToHistoryPrompt.toastError')));
              }
            })();
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
