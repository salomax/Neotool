import {
  CircularProgress,
  Divider,
  ListItemIcon,
  ListItemText,
  Menu,
  MenuItem,
  alpha,
  useTheme,
} from '@mui/material';
import AutoFixHighIcon from '@mui/icons-material/AutoFixHigh';
import EditIcon from '@mui/icons-material/Edit';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import SwapHorizIcon from '@mui/icons-material/SwapHoriz';
import { memo } from 'react';
import { TransactionType } from '@/lib/graphql/types/__generated__/graphql';
import type { Transaction } from '@/lib/graphql/types/__generated__/graphql';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../i18n';

export const TRANSACTION_ACTIONS_MENU_ID = 'finance-transactions-transaction-actions-menu';

interface TransactionActionMenuProps {
  anchorEl: HTMLElement | null;
  transaction: Transaction | null;
  markingTransferTransactionId: string | null;
  onClose: () => void;
  onEdit: (tx: Transaction) => void;
  onViewAppliedRules: (tx: Transaction) => void;
  onCreateRule: (tx: Transaction) => void;
  onMarkAsTransfer: () => void;
  onDelete: (tx: Transaction) => void;
}

export const TransactionActionMenu = memo(function TransactionActionMenu({
  anchorEl,
  transaction,
  markingTransferTransactionId,
  onClose,
  onEdit,
  onViewAppliedRules,
  onCreateRule,
  onMarkAsTransfer,
  onDelete,
}: TransactionActionMenuProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const theme = useTheme();
  const isMarkingThisTransfer = markingTransferTransactionId === transaction?.id;
  const isBalanceAdjustment = transaction?.type === TransactionType.BalanceAdjustment;
  const isTransfer = transaction?.type === TransactionType.Transfer;

  return (
    <Menu
      id={TRANSACTION_ACTIONS_MENU_ID}
      open={Boolean(anchorEl)}
      anchorEl={anchorEl}
      onClose={onClose}
      anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
      transformOrigin={{ vertical: 'top', horizontal: 'right' }}
      MenuListProps={{ 'aria-labelledby': anchorEl?.id }}
    >
      <MenuItem
        onClick={() => {
          if (!transaction) return;
          onClose();
          onEdit(transaction);
        }}
        disabled={isMarkingThisTransfer}
        data-testid="transaction-action-edit"
      >
        <ListItemIcon><EditIcon fontSize="small" /></ListItemIcon>
        <ListItemText>{t('financeTransactions.transactionTable.rowActions.edit')}</ListItemText>
      </MenuItem>

      {!isBalanceAdjustment && (transaction?.appliedRuleIds?.length ?? 0) > 0 && (
        <MenuItem
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
      )}

      {!isBalanceAdjustment && (
        <MenuItem
          onClick={() => {
            if (!transaction) return;
            onClose();
            onCreateRule(transaction);
          }}
          disabled={isMarkingThisTransfer}
          data-testid="transaction-action-create-rule-from-description"
        >
          <ListItemIcon><AutoFixHighIcon fontSize="small" /></ListItemIcon>
          <ListItemText>{t('financeTransactions.transactionTable.rowActions.createRuleFromDescription')}</ListItemText>
        </MenuItem>
      )}

      {!isBalanceAdjustment && !isTransfer && (
        <MenuItem onClick={onMarkAsTransfer} disabled={isMarkingThisTransfer}>
          <ListItemIcon>
            {isMarkingThisTransfer
              ? <CircularProgress size={18} />
              : <SwapHorizIcon color="info" fontSize="small" />}
          </ListItemIcon>
          <ListItemText>{t('financeTransactions.transactionTable.rowActions.markAsTransfer')}</ListItemText>
        </MenuItem>
      )}

      {!isBalanceAdjustment && <Divider />}

      <MenuItem
        onClick={() => {
          if (!transaction) return;
          onClose();
          onDelete(transaction);
        }}
        data-testid="transaction-action-delete"
        disabled={isMarkingThisTransfer}
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
    </Menu>
  );
});
