import React from 'react';
import { WidgetDefinition } from './types';
import { NetWorthWidget } from './widgets/NetWorthWidget';
import { TransactionHistoryWidget } from './widgets/TransactionHistoryWidget';
import { SpendingBreakdownWidget } from './widgets/SpendingBreakdownWidget';
import AccountBalanceIcon from '@mui/icons-material/AccountBalance';
import ReceiptLongIcon from '@mui/icons-material/ReceiptLong';
import PieChartIcon from '@mui/icons-material/PieChart';

export const AVAILABLE_WIDGETS: WidgetDefinition[] = [
  {
    type: 'net-worth',
    defaultTitle: 'Net Worth',
    description: 'Displays your total net worth based on linked accounts.',
    component: NetWorthWidget,
    defaultSize: { 
      w: { xs: 12, md: 4 }, 
      h: { xs: 2, md: 2 } 
    },
    icon: <AccountBalanceIcon />,
  },
  {
    type: 'transaction-history',
    defaultTitle: 'Recent Transactions',
    description: 'Shows the latest transactions from all accounts.',
    component: TransactionHistoryWidget,
    defaultSize: { 
      w: { xs: 12, md: 8 }, 
      h: { xs: 4, md: 4 } 
    },
    icon: <ReceiptLongIcon />,
  },
  {
    type: 'spending-breakdown',
    defaultTitle: 'Spending Breakdown',
    description: 'Visualizes your spending by category.',
    component: SpendingBreakdownWidget,
    defaultSize: { 
      w: { xs: 12, md: 6 }, 
      h: { xs: 4, md: 4 } 
    },
    icon: <PieChartIcon />,
  },
];

export const getWidgetDefinition = (type: string): WidgetDefinition | undefined => {
  return AVAILABLE_WIDGETS.find(w => w.type === type);
};
