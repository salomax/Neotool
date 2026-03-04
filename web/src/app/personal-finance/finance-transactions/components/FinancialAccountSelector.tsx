import React, { useState, useMemo } from 'react';
import { useQuery } from '@apollo/client/react';
import { 
  Box, 
  Typography, 
  Paper, 
  Link, 
  Avatar, 
  List, 
  ListItemButton, 
  ListItemText, 
  ListItemIcon, 
  Collapse, 
  Popover,
  Divider,
  useTheme,
  Skeleton,
  TextField,
  InputAdornment,
} from '@mui/material';
import { 
  KeyboardArrowDown as ExpandMoreIcon, 
  KeyboardArrowUp as ExpandLessIcon, 
  Add as AddIcon,
  AccountBalance as BankIcon,
  CreditCard as CardIcon,
  AccountBalanceWallet as WalletIcon,
  Search as SearchIcon,
} from '@mui/icons-material';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../i18n';
import { FINANCIAL_ACCOUNTS } from '@/lib/graphql/operations/banking/queries';
import { FinancialAccount, FinancialAccountType } from '@/lib/graphql/types/__generated__/graphql';

// Types
interface FinancialAccountSelectorProps {
  onNewAccount: () => void;
  selectedAccountId?: string | null;
  onSelectAccount: (accountId: string | null) => void;
}

// Helper to format currency (mock for now if balance missing)
const formatCurrency = (value: number, currency = 'BRL') => {
  return new Intl.NumberFormat('pt-BR', {
    style: 'currency',
    currency: currency,
  }).format(value);
};

export function FinancialAccountSelector({ 
  onNewAccount, 
  selectedAccountId, 
  onSelectAccount 
}: FinancialAccountSelectorProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const theme = useTheme();
  const borderRadius = (theme as any).custom?.radius?.lg ? `${(theme as any).custom.radius.lg}px` : '12px';
  
  // State for Popover
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);
  const open = Boolean(anchorEl);
  const [searchQuery, setSearchQuery] = useState('');

  // Data Fetching
  const { data, loading } = useQuery<{ financialAccounts: FinancialAccount[] }>(FINANCIAL_ACCOUNTS, {
    errorPolicy: 'all',
  });

  const filteredAccounts = useMemo(() => {
    const accounts = data?.financialAccounts || [];
    if (!searchQuery) return accounts;
    const query = searchQuery.toLowerCase();
    return accounts.filter(acc => 
      acc.name.toLowerCase().includes(query) || 
      (acc.issuer?.name || acc.issuerName || '').toLowerCase().includes(query)
    );
  }, [data?.financialAccounts, searchQuery]);

  // Group Accounts by Issuer
  const groupedAccounts = useMemo<Record<string, FinancialAccount[]>>(() => {
    if (filteredAccounts.length === 0) return {};
    
    return filteredAccounts.reduce<Record<string, FinancialAccount[]>>((acc, account) => {
      const issuerName = account.issuer?.name || account.issuerName || t('financeTransactions.selector.unknownIssuer');
      if (!acc[issuerName]) {
        acc[issuerName] = [];
      }
      acc[issuerName].push(account);
      return acc;
    }, {});
  }, [filteredAccounts, t]);

  const allAccounts: FinancialAccount[] = data?.financialAccounts || [];
  const hasMultipleAccounts = allAccounts.length > 1;
  const singleAccount = allAccounts.length === 1 ? allAccounts[0] : null;
  
  // Selected Account Logic
  const selectedAccount = selectedAccountId 
    ? allAccounts.find((a: FinancialAccount) => a.id === selectedAccountId) 
    : null;

  // Handlers
  const handleClick = (event: React.MouseEvent<HTMLElement>) => {
    // Only open dropdown if multiple accounts
    if (hasMultipleAccounts) {
      setAnchorEl(event.currentTarget);
    }
  };

  const handleClose = () => {
    setAnchorEl(null);
    setTimeout(() => setSearchQuery(''), 300);
  };

  const handleSelect = (id: string | null) => {
    onSelectAccount(id);
    handleClose();
  };

  // Loading State
  if (loading) {
    return <Skeleton variant="rectangular" width="100%" height={72} sx={{ borderRadius: 2 }} />;
  }

  // Render Single Account View (No Dropdown)
  if (!hasMultipleAccounts && singleAccount) {
    return (
      <Box>
        <Paper
          elevation={0}
          variant="outlined"
          sx={{
            p: 2,
            display: 'flex',
            alignItems: 'center',
            gap: 2,
            borderRadius: 2,
            bgcolor: 'background.paper',
          }}
        >
          <Avatar 
            src={singleAccount.issuer?.logoAsset?.publicUrl || undefined}
            alt={singleAccount.name}
            sx={{ 
                bgcolor: singleAccount.issuer?.logoAsset?.publicUrl ? 'transparent' : (singleAccount.themeColor || 'primary.main'),
                borderRadius: borderRadius
            }}
          >
            {!singleAccount.issuer?.logoAsset?.publicUrl && (singleAccount.type === FinancialAccountType.Bank ? <BankIcon /> : <CardIcon />)}
          </Avatar>
          <Box>
            <Typography variant="subtitle1" fontWeight="bold">
              {singleAccount.name}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {singleAccount.issuer?.name || singleAccount.issuerName}
            </Typography>
          </Box>
        </Paper>
        
        <Link
          component="button"
          variant="body1"
          underline="none"
          onClick={onNewAccount}
          sx={{ 
              display: 'flex', 
              alignItems: 'center', 
              gap: 1, 
              fontWeight: 'medium',
              mt: 2,
              justifyContent: 'center',
              width: '100%'
          }}
        >
          <AddIcon fontSize="small" />
          {t('financeTransactions.actions.newAccountOrCard')}
        </Link>
      </Box>
    );
  }

  // Render Empty State (No accounts) -> Show Create Link directly?
  // Or just the "All Accounts" view but empty?
  // If no accounts, maybe just show the create button as before?
  // The requirement says "if the user has just one financial account... if user add more...".
  // Implicitly handles 0 accounts?
  if (allAccounts.length === 0) {
     return (
        <Paper
        elevation={0}
        variant="outlined"
        sx={{
          p: 2,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          gap: 1,
          borderRadius: 2,
          cursor: 'pointer',
          bgcolor: 'background.paper',
          '&:hover': { bgcolor: 'action.hover' }
        }}
        onClick={onNewAccount}
      >
        <AddIcon color="primary" />
        <Typography color="primary" fontWeight="medium">
          {t('financeTransactions.actions.newAccountOrCard')}
        </Typography>
      </Paper>
     );
  }

  // Render Dropdown Trigger (Multiple Accounts)
  return (
    <Box>
      <Paper
        elevation={0}
        variant="outlined"
        onClick={handleClick}
        sx={{
          p: 2,
          minHeight: 80,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          cursor: 'pointer',
          borderRadius: 2,
          bgcolor: open ? 'action.selected' : 'background.paper',
          borderColor: open ? 'primary.main' : 'divider',
          transition: 'all 0.2s',
          '&:hover': {
            borderColor: 'primary.main',
            bgcolor: 'action.hover'
          }
        }}
      >
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
          <Avatar 
            src={selectedAccount?.issuer?.logoAsset?.publicUrl || undefined}
            sx={{ 
                bgcolor: selectedAccount?.issuer?.logoAsset?.publicUrl ? 'transparent' : (selectedAccount?.themeColor || 'primary.main'),
                borderRadius: borderRadius
            }}
          >
             {selectedAccount ? (
                !selectedAccount.issuer?.logoAsset?.publicUrl && (
                    selectedAccount.type === FinancialAccountType.Bank ? <BankIcon /> : <CardIcon />
                )
             ) : (
                <BankIcon />
             )}
          </Avatar>
          <Box>
            <Typography variant="subtitle1" fontWeight="bold">
              {selectedAccount ? selectedAccount.name : t('financeTransactions.selector.allAccounts')}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {selectedAccount 
                ? (selectedAccount.issuer?.name || selectedAccount.issuerName) 
                : t('financeTransactions.selector.consolidatedView')}
            </Typography>
          </Box>
        </Box>
        {open ? <ExpandLessIcon color="action" /> : <ExpandMoreIcon color="action" />}
      </Paper>
      
      <Link
        component="button"
        variant="body1"
        underline="none"
        onClick={onNewAccount}
        sx={{ 
            display: 'flex', 
            alignItems: 'center', 
            gap: 1, 
            fontWeight: 'medium',
            mt: 2,
            justifyContent: 'center',
            width: '100%'
        }}
      >
        <AddIcon fontSize="small" />
        {t('financeTransactions.actions.newAccountOrCard')}
      </Link>

      <Popover
        open={open}
        anchorEl={anchorEl}
        onClose={handleClose}
        anchorOrigin={{
          vertical: 'bottom',
          horizontal: 'left',
        }}
        transformOrigin={{
          vertical: 'top',
          horizontal: 'left',
        }}
        PaperProps={{
          sx: {
            width: anchorEl?.offsetWidth, // Match trigger width
            mt: 1,
            maxHeight: 500, // Max height
            display: 'flex',
            flexDirection: 'column',
            overflow: 'hidden', // Handle internal scrolling
            borderRadius: 2,
            boxShadow: theme.shadows[3]
          }
        }}
      >
        <Box sx={{ overflowY: 'auto', flex: 1 }}>
            {/* "All Accounts" Option */}
            <ListItemButton 
                selected={!selectedAccountId}
                onClick={() => handleSelect(null)}
                sx={{ py: 2 }}
            >
                <ListItemIcon>
                    <Avatar sx={{ bgcolor: 'primary.main', borderRadius: borderRadius }}>
                        <BankIcon />
                    </Avatar>
                </ListItemIcon>
                <ListItemText 
                    primary={t('financeTransactions.selector.allAccounts')}
                    secondary={t('financeTransactions.selector.consolidatedView')}
                    primaryTypographyProps={{ fontWeight: 'bold' }}
                />
                 {/* Expand icon for "All accounts" acts as collapse? No, just selection */}
                {!selectedAccountId && <ExpandLessIcon color="action" />}
            </ListItemButton>

            <Divider />
            
            <Box>
                <TextField 
                    fullWidth
                    size="small"
                    placeholder={t('financeTransactions.selector.searchPlaceholder', 'Search accounts...')}
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    sx={{ 
                        '& .MuiOutlinedInput-notchedOutline': { border: 'none' } 
                    }}
                    InputProps={{
                        startAdornment: (
                            <InputAdornment position="start">
                                <SearchIcon color="action" fontSize="small" />
                            </InputAdornment>
                        )
                    }}
                />
            </Box>

            <Divider sx={{ mb: 1 }}/>

            {/* No Results */}
            {searchQuery && filteredAccounts.length === 0 && (
                <Box sx={{ p: 2, textAlign: 'center' }}>
                    <Typography variant="body2" color="text.secondary">
                        {t('financeTransactions.selector.noAccountsFound', 'No accounts found')}
                    </Typography>
                </Box>
            )}

            {/* Grouped Accounts List */}
            {Object.entries(groupedAccounts).map(([issuerName, accounts]) => (
                <Box key={issuerName}>
                    <Typography 
                        variant="caption" 
                        color="text.secondary" 
                        sx={{ px: 2, py: 1, display: 'block', fontWeight: 'bold', textTransform: 'uppercase' }}
                    >
                        {issuerName}
                    </Typography>
                    <List disablePadding>
                        {accounts.map((account) => (
                            <ListItemButton 
                                key={account.id}
                                selected={selectedAccountId === account.id}
                                onClick={() => handleSelect(account.id)}
                                sx={{ pl: 2 }}
                            >
                                <ListItemIcon>
                                    <Avatar 
                                        src={account.issuer?.logoAsset?.publicUrl || undefined}
                                        sx={{ 
                                            width: 32, 
                                            height: 32, 
                                            bgcolor: account.issuer?.logoAsset?.publicUrl ? 'transparent' : (account.themeColor || 'grey.300'),
                                            fontSize: '1rem',
                                            borderRadius: borderRadius
                                        }}
                                    >
                                        {!account.issuer?.logoAsset?.publicUrl && (account.type === FinancialAccountType.Bank ? <BankIcon fontSize="small" /> : <CardIcon fontSize="small" />)}
                                    </Avatar>
                                </ListItemIcon>
                                <ListItemText 
                                    primary={account.name}
                                    secondary={
                                        // Mock balance display
                                        account.cardData?.availableCreditLimit 
                                            ? formatCurrency(account.cardData.availableCreditLimit, account.currencyCode)
                                            : formatCurrency(0, account.currencyCode)
                                    }
                                    primaryTypographyProps={{ variant: 'body2', fontWeight: 'medium' }}
                                    secondaryTypographyProps={{ 
                                        variant: 'body2', 
                                        color: account.cardData?.availableCreditLimit ? 'success.main' : 'text.secondary' 
                                    }}
                                />
                            </ListItemButton>
                        ))}
                    </List>
                </Box>
            ))}
        </Box>

        {/* Footer: Create Link */}
        <Box sx={{ p: 2, borderTop: 1, borderColor: 'divider', bgcolor: 'background.paper' }}>
             <Link
                component="button"
                variant="body1"
                underline="none"
                onClick={() => {
                    handleClose();
                    onNewAccount();
                }}
                sx={{ 
                    display: 'flex', 
                    alignItems: 'center', 
                    gap: 1, 
                    fontWeight: 'medium',
                    width: '100%',
                    justifyContent: 'center'
                }}
             >
                <AddIcon fontSize="small" />
                {t('financeTransactions.actions.newAccountOrCard')}
             </Link>
        </Box>
      </Popover>
    </Box>
  );
}
