import React, { useState, useMemo } from 'react';
import { useQuery } from '@apollo/client/react';
import i18n from 'i18next';
import { 
  Box, 
  Typography, 
  Paper, 
  Link, 
  Avatar, 
  Stack,
  IconButton,
  List, 
  ListItemButton, 
  ListItemText, 
  ListItemIcon, 
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
  Edit as EditIcon,
  AccountBalance as BankIcon,
  CreditCard as CardIcon,
  AccountBalanceWallet as WalletIcon,
  TrendingUp as TrendingUpIcon,
  Search as SearchIcon,
} from '@mui/icons-material';
import { CardNetworkIcon } from '@/shared/components/ui/icons/CardNetworkIcon';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../i18n';
import { FINANCIAL_ACCOUNTS } from '@/lib/graphql/operations/banking/queries';
import { FinancialAccount, FinancialAccountType } from '@/lib/graphql/types/__generated__/graphql';

// Types
interface FinancialAccountSelectorProps {
  onNewAccount: () => void;
  onEditAccount?: (account: FinancialAccount) => void;
  selectedAccountId?: string | null;
  onSelectAccount: (accountId: string | null) => void;
  accountTypes?: FinancialAccountType[];
  allAccountsLabel?: string;
  consolidatedViewLabel?: string;
  newAccountLabel?: string;
  unknownIssuerLabel?: string;
  searchPlaceholder?: string;
  noAccountsFoundLabel?: string;
}

// Helper to format currency (mock for now if balance missing)
const formatCurrency = (value: number, currency = 'BRL') => {
  const locale = i18n.language || Intl.DateTimeFormat().resolvedOptions().locale || 'pt-BR';
  return new Intl.NumberFormat(locale, {
    style: 'currency',
    currency: currency,
  }).format(value);
};

const getCardLastFourDigits = (account: FinancialAccount) => {
  const raw = account.cardData?.maskedNumber || '';
  const digits = raw.replace(/\D/g, '');
  const last4 = digits.slice(-4);
  return last4.length === 4 ? last4 : null;
};

const isCardAccount = (account: FinancialAccount) => {
  if (account.cardData) return true;
  return account.type === FinancialAccountType.Credit;
};

export function FinancialAccountSelector({ 
  onNewAccount, 
  onEditAccount,
  selectedAccountId, 
  onSelectAccount,
  accountTypes,
  allAccountsLabel,
  consolidatedViewLabel,
  newAccountLabel,
  unknownIssuerLabel,
  searchPlaceholder,
  noAccountsFoundLabel,
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

  const scopedAccounts = useMemo(() => {
    const accounts = data?.financialAccounts || [];
    if (!accountTypes?.length) return accounts;
    return accounts.filter((account) => accountTypes.includes(account.type));
  }, [accountTypes, data?.financialAccounts]);

  const allAccountsText = allAccountsLabel || t('financeTransactions.selector.allAccounts');
  const consolidatedViewText = consolidatedViewLabel || t('financeTransactions.selector.consolidatedView');
  const newAccountText = newAccountLabel || t('financeTransactions.actions.newAccountOrCard');
  const unknownIssuerText = unknownIssuerLabel || t('financeTransactions.selector.unknownIssuer');
  const searchPlaceholderText = searchPlaceholder || t('financeTransactions.selector.searchPlaceholder', 'Search accounts...');
  const noAccountsFoundText = noAccountsFoundLabel || t('financeTransactions.selector.noAccountsFound', 'No accounts found');

  const filteredAccounts = useMemo(() => {
    const accounts = scopedAccounts;
    if (!searchQuery) return accounts;
    const query = searchQuery.toLowerCase();
    return accounts.filter(acc => 
      acc.name.toLowerCase().includes(query) || 
      (acc.issuer?.name || acc.issuerName || '').toLowerCase().includes(query)
    );
  }, [scopedAccounts, searchQuery]);

  // Group Accounts by Issuer
  const groupedAccounts = useMemo<Record<string, FinancialAccount[]>>(() => {
    if (filteredAccounts.length === 0) return {};
    
    return filteredAccounts.reduce<Record<string, FinancialAccount[]>>((acc, account) => {
      const issuerName = account.issuer?.name || account.issuerName || unknownIssuerText;
      if (!acc[issuerName]) {
        acc[issuerName] = [];
      }
      acc[issuerName].push(account);
      return acc;
    }, {});
  }, [filteredAccounts, unknownIssuerText]);

  const allAccounts: FinancialAccount[] = scopedAccounts;
  const hasMultipleAccounts = allAccounts.length > 1;
  const singleAccount = allAccounts.length === 1 ? allAccounts[0] : null;
  
  // Selected Account Logic
  const resolvedSelectedAccountId = selectedAccountId ?? singleAccount?.id ?? null;
  const selectedAccount = resolvedSelectedAccountId
    ? allAccounts.find((a: FinancialAccount) => a.id === resolvedSelectedAccountId) 
    : null;

  // Handlers
  const renderAccountTypeIcon = (type: FinancialAccountType, fontSize?: 'small' | 'medium' | 'large') => {
    if (type === FinancialAccountType.Bank) return <BankIcon fontSize={fontSize} />;
    if (type === FinancialAccountType.Credit || type === FinancialAccountType.Payment) return <CardIcon fontSize={fontSize} />;
    if (type === FinancialAccountType.Investment) return <WalletIcon fontSize={fontSize} />;
    return <BankIcon />;
  };

  const consolidatedViewType = useMemo<'INVESTMENT' | 'BANKING'>(() => {
    if (accountTypes?.length) {
      return accountTypes.every((type) => type === FinancialAccountType.Investment) ? 'INVESTMENT' : 'BANKING';
    }

    if (!allAccounts.length) return 'BANKING';
    return allAccounts.every((account) => account.type === FinancialAccountType.Investment) ? 'INVESTMENT' : 'BANKING';
  }, [accountTypes, allAccounts]);

  const renderConsolidatedViewIcon = (fontSize?: 'small' | 'medium' | 'large') => {
    if (consolidatedViewType === 'INVESTMENT') return <TrendingUpIcon fontSize={fontSize} />;
    return <BankIcon fontSize={fontSize} />;
  };

  const handleClick = (event: React.MouseEvent<HTMLElement>) => {
    if (hasMultipleAccounts || Boolean(onEditAccount)) {
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
          {newAccountText}
        </Typography>
      </Paper>
     );
  }

  // Render Dropdown Trigger (Multiple Accounts)
  const selectedLast4 = selectedAccount ? getCardLastFourDigits(selectedAccount) : null;
  const showSelectedCardDetails = selectedAccount ? isCardAccount(selectedAccount) : false;

  return (
    <Box>
      <Paper
        elevation={0}
        variant="outlined"
        onClick={handleClick}
        data-testid="account-selector"
        sx={{
          p: 2,
          minHeight: 80,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          cursor: hasMultipleAccounts || Boolean(onEditAccount) ? 'pointer' : 'default',
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
                    renderAccountTypeIcon(selectedAccount.type)
                )
             ) : (
                renderConsolidatedViewIcon()
             )}
          </Avatar>
          <Box>
            <Typography variant="subtitle1" fontWeight="bold">
              {selectedAccount ? selectedAccount.name : allAccountsText}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {selectedAccount 
                ? (selectedAccount.issuer?.name || selectedAccount.issuerName) 
                : consolidatedViewText}
            </Typography>
            {showSelectedCardDetails && (
              <Stack direction="row" spacing={0.5} alignItems="center">
                <CardNetworkIcon network={selectedAccount?.cardData?.cardNetwork} size={14} />
                {selectedLast4 && (
                  <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.2 }}>
                    •••• {selectedLast4}
                  </Typography>
                )}
              </Stack>
            )}
          </Box>
        </Box>
        <Stack direction="row" spacing={0.5} alignItems="center">
          {(hasMultipleAccounts || Boolean(onEditAccount)) && (open ? <ExpandLessIcon color="action" /> : <ExpandMoreIcon color="action" />)}
        </Stack>
      </Paper>
      
      <Link
        component="button"
        variant="body1"
        underline="none"
        onClick={onNewAccount}
        data-testid="btn-new-account"
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
          {newAccountText}
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
                selected={!resolvedSelectedAccountId}
                onClick={() => handleSelect(null)}
                sx={{ py: 2 }}
            >
                <ListItemIcon>
                    <Avatar sx={{ bgcolor: 'primary.main', borderRadius: borderRadius }}>
                        {renderConsolidatedViewIcon()}
                    </Avatar>
                </ListItemIcon>
                <ListItemText 
                    primary={allAccountsText}
                    secondary={consolidatedViewText}
                    primaryTypographyProps={{ fontWeight: 'bold' }}
                />
                 {/* Expand icon for "All accounts" acts as collapse? No, just selection */}
                {!resolvedSelectedAccountId && <ExpandLessIcon color="action" />}
            </ListItemButton>

            <Divider />
            
            <Box>
                <TextField 
                    fullWidth
                    size="small"
                    placeholder={searchPlaceholderText}
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
                        {noAccountsFoundText}
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
                                selected={resolvedSelectedAccountId === account.id}
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
                                        {!account.issuer?.logoAsset?.publicUrl && renderAccountTypeIcon(account.type, 'small')}
                                    </Avatar>
                                </ListItemIcon>
                                {(() => {
                                  const last4 = getCardLastFourDigits(account);
                                  const showCardDetails = isCardAccount(account);

                                  return (
                                <ListItemText 
                                      primary={account.name}
                                      secondary={
                                        showCardDetails ? (
                                            <Stack direction="row" spacing={0.5} alignItems="center">
                                              <CardNetworkIcon network={account.cardData?.cardNetwork} size={14} />
                                              {last4 && (
                                                <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.2 }}>
                                                  •••• {last4}
                                                </Typography>
                                              )}
                                            </Stack>
                                        ) : undefined
                                      }
                                      primaryTypographyProps={{ variant: 'body2', fontWeight: 'medium' }}
                                    />
                                  );
                                })()}
                                {onEditAccount && (
                                  <IconButton
                                    aria-label={t('financeTransactions.actions.edit')}
                                    onClick={(e) => {
                                      e.stopPropagation();
                                      handleClose();
                                      onEditAccount(account);
                                    }}
                                    size="small"
                                    sx={{ ml: 'auto' }}
                                  >
                                    <EditIcon fontSize="small" />
                                  </IconButton>
                                )}
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
                {newAccountText}
             </Link>
        </Box>
      </Popover>
    </Box>
  );
}
