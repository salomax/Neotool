import {
  Box,
  CircularProgress,
  Divider,
  ListItemIcon,
  ListItemText,
  Menu,
  MenuItem,
  alpha,
  useTheme,
} from '@mui/material';
import useMediaQuery from '@mui/material/useMediaQuery';
import AutoFixHighIcon from '@mui/icons-material/AutoFixHigh';
import EditIcon from '@mui/icons-material/Edit';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import SwapHorizIcon from '@mui/icons-material/SwapHoriz';
import CreditCardIcon from '@mui/icons-material/CreditCard';
import CreditCardOffIcon from '@mui/icons-material/CreditCardOff';
import RepeatIcon from '@mui/icons-material/Repeat';
import { memo } from 'react';
import { TransactionType } from '@/lib/graphql/types/__generated__/graphql';
import type { Transaction } from '@/lib/graphql/types/__generated__/graphql';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../i18n';
import { Drawer } from '@/shared/components/ui/layout/Drawer';

export const TRANSACTION_ACTIONS_MENU_ID = 'finance-transactions-transaction-actions-menu';

interface TransactionActionMenuProps {
  anchorEl: HTMLElement | null;
  transaction: Transaction | null;
  markingTransferTransactionId: string | null;
  markingBillPaymentTransactionId: string | null;
  onClose: () => void;
  onEdit: (tx: Transaction) => void;
  onViewAppliedRules: (tx: Transaction) => void;
  onCreateRule: (tx: Transaction) => void;
  recurringPaymentSuggestion?: { id: string; name: string; nextDueDate: string } | null;
  onLinkRecurringPayment?: (tx: Transaction, recurringPaymentId: string) => void;
  onCreateRecurringPayment: (tx: Transaction) => void;
  onMarkAsTransfer: () => void;
  onMarkAsBillPayment: () => void;
  onUnmarkBillPayment: () => void;
  onDelete: (tx: Transaction) => void;
}

export const TransactionActionMenu = memo(function TransactionActionMenu({
  anchorEl,
  transaction,
  markingTransferTransactionId,
  markingBillPaymentTransactionId,
  onClose,
  onEdit,
  onViewAppliedRules,
  onCreateRule,
  recurringPaymentSuggestion,
  onLinkRecurringPayment,
  onCreateRecurringPayment,
  onMarkAsTransfer,
  onMarkAsBillPayment,
  onUnmarkBillPayment,
  onDelete,
}: TransactionActionMenuProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('md'));
  const isMarkingThisTransfer = markingTransferTransactionId === transaction?.id;
  const isMarkingThisBillPayment = markingBillPaymentTransactionId === transaction?.id;
  const isMarkingAny = isMarkingThisTransfer || isMarkingThisBillPayment;
  const isBalanceAdjustment = transaction?.type === TransactionType.BalanceAdjustment;
  const isTransfer = transaction?.type === TransactionType.Transfer;
  const isBillPayment = transaction?.type === TransactionType.BillPayment;

  const isOpen = Boolean(anchorEl);

  const editAction = (
    <MenuItem
      key="transaction-action-edit"
      onClick={() => {
        if (!transaction) return;
        onClose();
        onEdit(transaction);
      }}
      disabled={isMarkingAny}
      data-testid="transaction-action-edit"
    >
      <ListItemIcon><EditIcon fontSize="small" /></ListItemIcon>
      <ListItemText>{t('financeTransactions.transactionTable.rowActions.edit')}</ListItemText>
    </MenuItem>
  );

  const appliedRulesAction =
    !isBalanceAdjustment && (transaction?.appliedRuleIds?.length ?? 0) > 0 ? (
      <MenuItem
        key="transaction-action-view-applied-rules"
        onClick={() => {
          if (!transaction) return;
          onClose();
          onViewAppliedRules(transaction);
        }}
        data-testid="transaction-action-view-applied-rules"
      >
        <ListItemIcon><AutoFixHighIcon fontSize="small" /></ListItemIcon>
        <ListItemText>{t('financeTransactions.transactionTable.rowActions.viewAppliedRules')}</ListItemText>
      </MenuItem>
    ) : null;

  const createRuleAction = !isBalanceAdjustment ? (
    <MenuItem
      key="transaction-action-create-rule"
      onClick={() => {
        if (!transaction) return;
        onClose();
        onCreateRule(transaction);
      }}
      disabled={isMarkingAny}
      data-testid="transaction-action-create-rule-from-description"
    >
      <ListItemIcon><AutoFixHighIcon fontSize="small" /></ListItemIcon>
      <ListItemText>{t('financeTransactions.transactionTable.rowActions.createRuleFromDescription')}</ListItemText>
    </MenuItem>
  ) : null;

  const linkRecurringAction =
    !isBalanceAdjustment && !isTransfer && recurringPaymentSuggestion ? (
      <MenuItem
        key="transaction-action-link-recurring"
        onClick={() => {
          if (!transaction) return;
          onClose();
          onLinkRecurringPayment?.(transaction, recurringPaymentSuggestion.id);
        }}
        data-testid="transaction-action-link-recurring-suggestion"
        sx={{ color: 'primary.main' }}
      >
        <ListItemIcon><RepeatIcon fontSize="small" color="primary" /></ListItemIcon>
        <ListItemText
          primary={t('recurringPayments.suggestion.link', { name: recurringPaymentSuggestion.name })}
          secondary={t('recurringPayments.suggestion.nextDue', { date: recurringPaymentSuggestion.nextDueDate })}
        />
      </MenuItem>
    ) : null;

  const createRecurringAction =
    !isBalanceAdjustment && !isTransfer ? (
      <MenuItem
        key="transaction-action-create-recurring"
        onClick={() => {
          if (!transaction) return;
          onClose();
          onCreateRecurringPayment(transaction);
        }}
        disabled={isMarkingAny}
        data-testid="transaction-action-create-recurring"
      >
        <ListItemIcon><RepeatIcon fontSize="small" /></ListItemIcon>
        <ListItemText>{t('financeTransactions.transactionTable.rowActions.createRecurringPayment')}</ListItemText>
      </MenuItem>
    ) : null;

  const transferAction =
    !isBalanceAdjustment && !isTransfer && !isBillPayment ? (
      <MenuItem key="transaction-action-mark-transfer" onClick={onMarkAsTransfer} disabled={isMarkingThisTransfer}>
        <ListItemIcon>
          {isMarkingThisTransfer
            ? <CircularProgress size={18} />
            : <SwapHorizIcon color="info" fontSize="small" />}
        </ListItemIcon>
        <ListItemText>{t('financeTransactions.transactionTable.rowActions.markAsTransfer')}</ListItemText>
      </MenuItem>
    ) : null;

  const billPaymentAction =
    !isBalanceAdjustment && !isBillPayment && !isTransfer ? (
      <MenuItem key="transaction-action-mark-bill-payment" onClick={onMarkAsBillPayment} disabled={isMarkingThisBillPayment}>
        <ListItemIcon>
          {isMarkingThisBillPayment
            ? <CircularProgress size={18} />
            : <CreditCardOffIcon color="warning" fontSize="small" />}
        </ListItemIcon>
        <ListItemText>{t('financeTransactions.transactionTable.rowActions.markAsBillPayment')}</ListItemText>
      </MenuItem>
    ) : null;

  const unmarkBillPaymentAction =
    !isBalanceAdjustment && isBillPayment ? (
      <MenuItem
        key="transaction-action-unmark-bill-payment"
        onClick={onUnmarkBillPayment}
        disabled={isMarkingThisBillPayment}
        data-testid="transaction-action-unmark-bill-payment"
      >
        <ListItemIcon>
          {isMarkingThisBillPayment
            ? <CircularProgress size={18} />
            : <CreditCardIcon color="info" fontSize="small" />}
        </ListItemIcon>
        <ListItemText>{t('financeTransactions.transactionTable.rowActions.unmarkBillPayment')}</ListItemText>
      </MenuItem>
    ) : null;

  const deleteAction = (
    <MenuItem
      key="transaction-action-delete"
      onClick={() => {
        if (!transaction) return;
        onClose();
        onDelete(transaction);
      }}
      data-testid="transaction-action-delete"
      disabled={isMarkingAny}
      sx={{
        color: 'error.main',
        '&:hover': { bgcolor: alpha(theme.palette.error.main, 0.08) },
        '&.Mui-focusVisible': { bgcolor: alpha(theme.palette.error.main, 0.08) },
      }}
    >
      <ListItemIcon sx={{ color: 'inherit' }}>
        <DeleteOutlineIcon fontSize="small" />
      </ListItemIcon>
      <ListItemText>{t('financeTransactions.transactionTable.rowActions.remove')}</ListItemText>
    </MenuItem>
  );

  const actionItems = [
    editAction,
    appliedRulesAction,
    createRuleAction,
    linkRecurringAction,
    createRecurringAction,
    transferAction,
    billPaymentAction,
    unmarkBillPaymentAction,
    !isBalanceAdjustment ? <Divider key="transaction-action-divider" /> : null,
    deleteAction,
  ].filter(Boolean);

  if (isMobile) {
    return (
      <Drawer
        open={isOpen}
        onClose={onClose}
        anchor="bottom"
        variant="temporary"
        ariaLabel={t('financeTransactions.transactionTable.rowActions.ariaLabel')}
        sx={{
          zIndex: (drawerTheme) => drawerTheme.zIndex.drawer + 2,
        }}
      >
        <Drawer.Header
          title={t('financeTransactions.transactionTable.rowActions.ariaLabel')}
          closeButtonAriaLabel={t('financeTransactions.actions.close')}
        />
        <Drawer.Body>
          <Box sx={{ mx: -3, my: -2 }}>
            {actionItems}
          </Box>
        </Drawer.Body>
      </Drawer>
    );
  }

  return (
    <Menu
      id={TRANSACTION_ACTIONS_MENU_ID}
      open={isOpen}
      anchorEl={anchorEl}
      onClose={onClose}
      anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
      transformOrigin={{ vertical: 'top', horizontal: 'right' }}
      MenuListProps={{ 'aria-labelledby': anchorEl?.id }}
    >
      {actionItems}
    </Menu>
  );
});
