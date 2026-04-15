'use client';

import { useMemo, useState } from 'react';
import {
  Alert,
  alpha,
  Box,
  Button,
  IconButton,
  InputAdornment,
  ListItemIcon,
  Menu,
  MenuItem,
  Stack,
  TextField,
  useMediaQuery,
  useTheme,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import ArrowDropDownIcon from '@mui/icons-material/ArrowDropDown';
import AccountBalanceIcon from '@mui/icons-material/AccountBalance';
import CreditCardIcon from '@mui/icons-material/CreditCard';
import AutoFixHighIcon from '@mui/icons-material/AutoFixHigh';
import QrCode2Icon from '@mui/icons-material/QrCode2';
import SearchIcon from '@mui/icons-material/Search';
import ClearIcon from '@mui/icons-material/Clear';
import Link from 'next/link';
import { UploadIcon } from '@/shared/ui/mui-imports';
import { extractErrorMessage } from '@/shared/utils/error';
import { personalFinanceTranslations } from '../i18n';
import { useTranslation } from '@/shared/i18n';
import { MonthYearCarousel } from './summary/MonthYearCarousel';
import { BillCarousel } from './bill/BillCarousel';
import { CreditCardBill } from './account/useAccountDisplayMode';

type ActionButtonsRevealSxOptions = {
  expandedWidth: number;
  expandedSelector?: string;
  notExpandedSelector?: string;
  hasEndIcon?: boolean;
};

interface ImportAlertState {
  error?: unknown;
  accountId: string | null;
  onRetry: () => void;
  reviewRequiredJob: { id: string; duplicateReviewCount: number } | null;
  onOpenReviewJob: (jobId: string) => void;
}

interface NavigationState {
  isCalendarMode: boolean;
  selectedMonth: number;
  selectedYear: number;
  onDateChange: (month: number, year: number) => void;
  bills: CreditCardBill[];
  selectedBillId: string | null;
  onSelectBill: (billId: string) => void;
}

interface TransactionActions {
  onOpenNfceImport: () => void;
  onOpenImportTransactions: () => void;
  onOpenImportBill: () => void;
  onCreateTransaction: () => void;
  isCreating: boolean;
}

interface FinanceTransactionsHeaderBarProps {
  importAlerts: ImportAlertState;
  navigation: NavigationState;
  searchQuery: string;
  onSearchQueryChange: (query: string) => void;
  actions: TransactionActions;
}

export function FinanceTransactionsHeaderBar({
  importAlerts: { error: importsError, accountId: effectiveSelectedAccountId, onRetry: onRetryImports, reviewRequiredJob, onOpenReviewJob },
  navigation: { isCalendarMode, selectedMonth, selectedYear, onDateChange, bills, selectedBillId, onSelectBill },
  searchQuery,
  onSearchQueryChange,
  actions: { onOpenNfceImport, onOpenImportTransactions, onOpenImportBill, onCreateTransaction, isCreating: isCreatingTransaction },
}: FinanceTransactionsHeaderBarProps) {
  const { t, tCommon } = useTranslation(personalFinanceTranslations);
  const theme = useTheme();
  const actionButtonsRevealEnabled = useMediaQuery('(min-width:750px)');
  const [importMenuAnchor, setImportMenuAnchor] = useState<null | HTMLElement>(null);
  const normalizedSearchQuery = searchQuery.trim();

  const actionButtonsRevealCollapsedWidth = 90;
  const actionButtonsRevealExpandedWidth = 168;
  const actionButtonsRevealSearchExpandedWidth = 320;
  const actionButtonsRevealCollapsedPx = 1;
  const actionButtonsRevealExpandedPx = 0.75;
  const actionButtonsBorderRadiusPx = `${(theme as any).custom?.radius?.button ?? theme.shape.borderRadius}px`;
  const actionButtonsOutlinedBorderColor = alpha(theme.palette.primary.main, 0.5);
  const actionButtonsRevealWidthDurationMs = 180;
  const actionButtonsRevealLabelDurationMs = 260;
  const actionButtonsRevealLabelOpacityDurationMs = 180;
  const actionButtonsRevealLabelDelayMs = 160;
  const actionButtonsRevealButtonTransition = `width ${actionButtonsRevealWidthDurationMs}ms ease, padding ${actionButtonsRevealWidthDurationMs}ms ease`;
  const actionButtonsRevealBgTransition = `background-color ${actionButtonsRevealWidthDurationMs}ms ease`;
  const actionButtonsRevealStartIconTransition = `margin-right ${actionButtonsRevealWidthDurationMs}ms ease`;
  const actionButtonsRevealEndIconTransition = `margin-left ${actionButtonsRevealWidthDurationMs}ms ease`;
  const actionButtonsRevealLabelHiddenTransition = `max-width ${actionButtonsRevealLabelDurationMs}ms ease 0ms, opacity ${actionButtonsRevealLabelOpacityDurationMs}ms ease 0ms, transform ${actionButtonsRevealLabelDurationMs}ms ease 0ms`;
  const actionButtonsRevealLabelShownTransition = `max-width ${actionButtonsRevealLabelDurationMs}ms ease ${actionButtonsRevealLabelDelayMs}ms, opacity ${actionButtonsRevealLabelOpacityDurationMs}ms ease ${actionButtonsRevealLabelDelayMs}ms, transform ${actionButtonsRevealLabelDurationMs}ms ease ${actionButtonsRevealLabelDelayMs}ms`;

  const getActionButtonsRevealSx = useMemo(
    () =>
      ({ expandedWidth, expandedSelector, notExpandedSelector, hasEndIcon }: ActionButtonsRevealSxOptions) => {
        const revealLabelShownSelector = `&:hover .revealLabel, &:focus-visible .revealLabel${
          expandedSelector ? `, ${expandedSelector} .revealLabel` : ''
        }`;
        const startIconExpandedSelector = `&:hover .MuiButton-startIcon, &:focus-visible .MuiButton-startIcon${
          expandedSelector ? `, ${expandedSelector} .MuiButton-startIcon` : ''
        }`;
        const endIconExpandedSelector = `&:hover .MuiButton-endIcon, &:focus-visible .MuiButton-endIcon${
          expandedSelector ? `, ${expandedSelector} .MuiButton-endIcon` : ''
        }`;
        const startIconCollapsedSelector = `&:not(:hover):not(:focus-visible)${
          notExpandedSelector ?? ''
        } .MuiButton-startIcon`;
        const endIconCollapsedSelector = `&:not(:hover):not(:focus-visible)${
          notExpandedSelector ?? ''
        } .MuiButton-endIcon`;
        const expandedStateSelector = `&:hover, &:focus-visible${expandedSelector ? `, ${expandedSelector}` : ''}`;

        return {
          width: actionButtonsRevealCollapsedWidth,
          px: actionButtonsRevealCollapsedPx,
          transition: actionButtonsRevealButtonTransition,
          '& .revealLabel': {
            display: 'inline-block',
            overflow: 'hidden',
            whiteSpace: 'nowrap',
            maxWidth: 0,
            opacity: 0,
            transform: 'translateX(-8px)',
            transition: actionButtonsRevealLabelHiddenTransition,
          },
          '& .MuiButton-startIcon': {
            marginLeft: 0,
            marginRight: 0.5,
            transition: actionButtonsRevealStartIconTransition,
          },
          ...(hasEndIcon
            ? {
                '& .MuiButton-endIcon': {
                  marginLeft: 0.5,
                  marginRight: 0,
                  transition: actionButtonsRevealEndIconTransition,
                },
              }
            : null),
          [revealLabelShownSelector]: {
            transition: actionButtonsRevealLabelShownTransition,
            maxWidth: 200,
            opacity: 1,
            transform: 'translateX(0)',
          },
          [startIconExpandedSelector]: {
            marginRight: 1,
          },
          ...(hasEndIcon
            ? {
                [endIconExpandedSelector]: {
                  marginLeft: 1,
                },
              }
            : null),
          [startIconCollapsedSelector]: {
            marginRight: 0,
          },
          ...(hasEndIcon
            ? {
                [endIconCollapsedSelector]: {
                  marginLeft: 0,
                },
              }
            : null),
          [expandedStateSelector]: {
            width: expandedWidth,
            px: actionButtonsRevealExpandedPx,
          },
        };
      },
    [
      actionButtonsRevealButtonTransition,
      actionButtonsRevealCollapsedPx,
      actionButtonsRevealCollapsedWidth,
      actionButtonsRevealEndIconTransition,
      actionButtonsRevealExpandedPx,
      actionButtonsRevealLabelHiddenTransition,
      actionButtonsRevealLabelShownTransition,
      actionButtonsRevealStartIconTransition,
    ],
  );

  return (
    <>
      {importsError && effectiveSelectedAccountId && (
        <Alert
          severity="warning"
          sx={{ width: '100%', mb: 2 }}
          action={
            <Button size="small" onClick={onRetryImports}>
              {tCommon('cardGrid.retry')}
            </Button>
          }
        >
          {extractErrorMessage(importsError, tCommon('errors.default'))}
        </Alert>
      )}
      {reviewRequiredJob && (
        <Alert
          severity="info"
          sx={{ width: '100%', mb: 2 }}
          action={
            <Button size="small" onClick={() => onOpenReviewJob(reviewRequiredJob.id)}>
              {t('financeTransactions.duplicateReview.bannerAction')}
            </Button>
          }
        >
          {t('financeTransactions.duplicateReview.header', {
            count: reviewRequiredJob.duplicateReviewCount ?? 1,
          })}
        </Alert>
      )}
      <Stack
        direction="row"
        spacing={2}
        sx={{ width: '100%', mb: 2, mt: { xs: 0, md: -1.5 }, minHeight: 50 }}
        alignItems="flex-end"
        flexWrap="wrap"
      >
        <Box sx={{ flex: 1, minWidth: 200 }}>
          {isCalendarMode ? (
            <MonthYearCarousel selectedMonth={selectedMonth} selectedYear={selectedYear} onChange={onDateChange} />
          ) : (
            <BillCarousel
              bills={bills}
              selectedBillId={selectedBillId}
              onSelectBill={onSelectBill}
            />
          )}
        </Box>
        <Box sx={{ flex: '0 0 auto' }}>
          <TextField
            data-has-value={!!normalizedSearchQuery}
            placeholder={t('financeTransactions.transactionRules.searchPlaceholder')}
            value={searchQuery}
            onChange={(event) => onSearchQueryChange(event.target.value)}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <SearchIcon fontSize="small" color="primary" />
                </InputAdornment>
              ),
              endAdornment: normalizedSearchQuery ? (
                <InputAdornment position="end">
                  <IconButton
                    size="small"
                    onMouseDown={(event) => event.preventDefault()}
                    onClick={() => onSearchQueryChange('')}
                    aria-label={t('financeTransactions.transactionRules.actions.clearSearchAriaLabel')}
                  >
                    <ClearIcon fontSize="small" />
                  </IconButton>
                </InputAdornment>
              ) : undefined,
            }}
            sx={{
              width: actionButtonsRevealCollapsedWidth,
              transition: actionButtonsRevealButtonTransition,
              '& .MuiOutlinedInput-root': {
                height: '48px',
                minHeight: '48px',
                boxSizing: 'border-box',
                borderRadius: actionButtonsBorderRadiusPx,
                transition: `${actionButtonsRevealButtonTransition}, ${actionButtonsRevealBgTransition}`,
                px: actionButtonsRevealCollapsedPx,
                justifyContent: 'center',
                backgroundColor: 'transparent',
                '& .MuiOutlinedInput-notchedOutline': {
                  borderColor: actionButtonsOutlinedBorderColor,
                  borderWidth: 1,
                  borderRadius: actionButtonsBorderRadiusPx,
                },
              },
              '& .MuiInputBase-input': {
                py: 0,
                display: 'inline-block',
                overflow: 'hidden',
                whiteSpace: 'nowrap',
                maxWidth: 0,
                opacity: 0,
                transform: 'translateX(-8px)',
                transition: actionButtonsRevealLabelHiddenTransition,
              },
              '& .MuiInputAdornment-root': {
                height: '100%',
                display: 'flex',
                alignItems: 'center',
              },
              '& .MuiInputAdornment-positionStart': {
                flex: 1,
                mr: '0 !important',
                ml: 3,
                justifyContent: 'center',
                transition: actionButtonsRevealStartIconTransition,
              },
              '& .MuiInputAdornment-positionEnd': {
                ml: 0,
                transition: actionButtonsRevealEndIconTransition,
                opacity: 0,
                maxWidth: 0,
                overflow: 'hidden',
              },
              '&:hover .MuiInputBase-input, &:focus-within .MuiInputBase-input, &[data-has-value="true"] .MuiInputBase-input': {
                transition: actionButtonsRevealLabelShownTransition,
                maxWidth: 240,
                opacity: 1,
                transform: 'translateX(0)',
              },
              '&:hover .MuiInputAdornment-positionStart, &:focus-within .MuiInputAdornment-positionStart, &[data-has-value="true"] .MuiInputAdornment-positionStart': {
                mr: 1,
                pr: 1,
                ml: 1,
                flex: '0 0 auto',
                justifyContent: 'flex-start',
              },
              '&:hover .MuiInputAdornment-positionEnd, &:focus-within .MuiInputAdornment-positionEnd, &[data-has-value="true"] .MuiInputAdornment-positionEnd': {
                ml: -3,
                opacity: 1,
                maxWidth: 240,
              },
              '&:hover .MuiOutlinedInput-root, &:focus-within .MuiOutlinedInput-root, &[data-has-value="true"] .MuiOutlinedInput-root': {
                px: actionButtonsRevealExpandedPx,
                justifyContent: 'flex-start',
                '& .MuiOutlinedInput-notchedOutline': {
                  borderColor: theme.palette.primary.main,
                  borderWidth: 1,
                },
              },
              '&:hover, &:focus-within, &[data-has-value="true"]': {
                width: actionButtonsRevealSearchExpandedWidth,
              },
            }}
          />
        </Box>
        {actionButtonsRevealEnabled ? (
          <Button
            component={Link}
            href="/personal-finance/finance-transactions/transaction-rules"
            variant="outlined"
            startIcon={<AutoFixHighIcon fontSize="small" />}
            sx={getActionButtonsRevealSx({ expandedWidth: actionButtonsRevealExpandedWidth })}
          >
            <Box component="span" className="revealLabel">
              {t('financeTransactions.actions.automation')}
            </Box>
          </Button>
        ) : (
          <Button
            component={Link}
            href="/personal-finance/finance-transactions/transaction-rules"
            variant="outlined"
            startIcon={<AutoFixHighIcon fontSize="small" />}
          >
            {t('financeTransactions.actions.automation')}
          </Button>
        )}
        {actionButtonsRevealEnabled ? (
          <Button
            variant="outlined"
            startIcon={<UploadIcon />}
            endIcon={<ArrowDropDownIcon />}
            onClick={(e) => setImportMenuAnchor(e.currentTarget)}
            aria-haspopup="menu"
            aria-expanded={!!importMenuAnchor}
            aria-controls={importMenuAnchor ? 'finance-transactions-import-menu' : undefined}
            data-open={!!importMenuAnchor}
            data-testid="btn-open-import-drawer"
            sx={getActionButtonsRevealSx({
              expandedWidth: actionButtonsRevealExpandedWidth,
              hasEndIcon: true,
              expandedSelector: '&[data-open="true"]',
              notExpandedSelector: ':not([data-open="true"])',
            })}
          >
            <Box component="span" className="revealLabel">
              {t('financeTransactions.import.button')}
            </Box>
          </Button>
        ) : (
          <Button
            variant="outlined"
            startIcon={<UploadIcon />}
            endIcon={<ArrowDropDownIcon />}
            onClick={(e) => setImportMenuAnchor(e.currentTarget)}
            aria-haspopup="menu"
            aria-expanded={!!importMenuAnchor}
            aria-controls={importMenuAnchor ? 'finance-transactions-import-menu' : undefined}
            data-testid="btn-open-import-drawer"
          >
            {t('financeTransactions.import.button')}
          </Button>
        )}
        <Menu
          id="finance-transactions-import-menu"
          anchorEl={importMenuAnchor}
          open={!!importMenuAnchor}
          onClose={() => setImportMenuAnchor(null)}
          anchorOrigin={{ vertical: 'bottom', horizontal: 'left' }}
        >
          <MenuItem
            onClick={() => {
              setImportMenuAnchor(null);
              onOpenNfceImport();
            }}
          >
            <ListItemIcon>
              <QrCode2Icon fontSize="small" />
            </ListItemIcon>
            {t('financeTransactions.nfce.button')}
          </MenuItem>
          <MenuItem
            onClick={() => {
              setImportMenuAnchor(null);
              onOpenImportTransactions();
            }}
          >
            <ListItemIcon>
              <AccountBalanceIcon fontSize="small" />
            </ListItemIcon>
            {t('financeTransactions.import.menu.file')}
          </MenuItem>
          <MenuItem
            onClick={() => {
              setImportMenuAnchor(null);
              onOpenImportBill();
            }}
          >
            <ListItemIcon>
              <CreditCardIcon fontSize="small" />
            </ListItemIcon>
            {t('financeTransactions.import.menu.cardBill')}
          </MenuItem>
        </Menu>
        <Button
          sx={{
            boxShadow: `0 10px 24px ${alpha(theme.palette.primary.main, 0.28)}`,
            '&:hover': {
              boxShadow: `0 12px 28px ${alpha(theme.palette.primary.main, 0.36)}`,
            },
          }}
          variant="contained"
          startIcon={<AddIcon />}
          onClick={onCreateTransaction}
          disabled={isCreatingTransaction}
          data-testid="btn-new-transaction"
        >
          {t('financeTransactions.quickAdd')}
        </Button>
      </Stack>
    </>
  );
}
