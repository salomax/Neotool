import React from 'react';
import { WidgetWrapper } from '../components/WidgetWrapper';
import { WidgetProps } from '../types';
import { Box, List, ListItem, ListItemText, ListItemAvatar, Avatar, Typography, Button, Divider } from '@mui/material';
import ReceiptLongIcon from '@mui/icons-material/ReceiptLong';
import ShoppingBagIcon from '@mui/icons-material/ShoppingBag';
import RestaurantIcon from '@mui/icons-material/Restaurant';
import DirectionsCarIcon from '@mui/icons-material/DirectionsCar';
import { useDashboardStore } from '../store';
import Link from 'next/link';

// Mock data
const RECENT_TRANSACTIONS = [
  { id: '1', title: 'Supermarket', date: 'Today', amount: -250.00, category: 'Groceries', icon: <ShoppingBagIcon /> },
  { id: '2', title: 'Uber', date: 'Yesterday', amount: -45.90, category: 'Transport', icon: <DirectionsCarIcon /> },
  { id: '3', title: 'Restaurant XYZ', date: 'Yesterday', amount: -120.50, category: 'Dining', icon: <RestaurantIcon /> },
  { id: '4', title: 'Salary', date: '01/10/2023', amount: 5000.00, category: 'Income', icon: <ReceiptLongIcon /> },
];

export const TransactionHistoryWidget: React.FC<WidgetProps> = ({ widget, dragHandleProps }) => {
  const { isEditMode, removeWidget } = useDashboardStore();

  const formatCurrency = (value: number) => {
    return new Intl.NumberFormat('pt-BR', {
      style: 'currency',
      currency: 'BRL',
    }).format(value);
  };

  return (
    <WidgetWrapper
      title={widget.title || "Recent Transactions"}
      icon={<ReceiptLongIcon />}
      isEditMode={isEditMode}
      onRemove={() => removeWidget(widget.id)}
      onRefresh={() => console.log('Refresh Transactions')}
      dragHandleProps={dragHandleProps}
    >
      <List sx={{ width: '100%', bgcolor: 'background.paper' }}>
        {RECENT_TRANSACTIONS.map((transaction, index) => (
          <React.Fragment key={transaction.id}>
            <ListItem alignItems="flex-start">
              <ListItemAvatar>
                <Avatar sx={{ bgcolor: transaction.amount > 0 ? 'success.light' : 'primary.light' }}>
                  {transaction.icon}
                </Avatar>
              </ListItemAvatar>
              <ListItemText
                primary={transaction.title}
                secondary={
                  <React.Fragment>
                    <Typography
                      component="span"
                      variant="body2"
                      color="text.primary"
                    >
                      {transaction.category}
                    </Typography>
                    {` — ${transaction.date}`}
                  </React.Fragment>
                }
              />
              <Typography
                variant="body2"
                sx={{ 
                  fontWeight: 'bold', 
                  color: transaction.amount > 0 ? 'success.main' : 'text.primary',
                  alignSelf: 'center'
                }}
              >
                {formatCurrency(transaction.amount)}
              </Typography>
            </ListItem>
            {index < RECENT_TRANSACTIONS.length - 1 && <Divider variant="inset" component="li" />}
          </React.Fragment>
        ))}
      </List>
      <Box sx={{ p: 2, textAlign: 'center', borderTop: 1, borderColor: 'divider' }}>
        <Button component={Link} href="/personal-finance/finance-transactions" size="small">
          View All Transactions
        </Button>
      </Box>
    </WidgetWrapper>
  );
};
