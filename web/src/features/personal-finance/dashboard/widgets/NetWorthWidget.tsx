import React from 'react';
import { WidgetWrapper } from '../components/WidgetWrapper';
import { WidgetProps } from '../types';
import { Box, Typography, Skeleton } from '@mui/material';
import AccountBalanceIcon from '@mui/icons-material/AccountBalance';
import { useDashboardStore } from '../store';

export const NetWorthWidget: React.FC<WidgetProps> = ({ widget, dragHandleProps }) => {
  const { isEditMode, removeWidget, toggleWidgetVisibility } = useDashboardStore();
  
  // Placeholder for actual data fetching
  const loading = false;
  const netWorth = 125000.50;

  const formatCurrency = (value: number) => {
    return new Intl.NumberFormat('pt-BR', {
      style: 'currency',
      currency: 'BRL',
    }).format(value);
  };

  return (
    <WidgetWrapper
      title={widget.title || "Net Worth"}
      icon={<AccountBalanceIcon />}
      isEditMode={isEditMode}
      onRemove={() => removeWidget(widget.id)}
      onRefresh={() => console.log('Refresh Net Worth')}
      dragHandleProps={dragHandleProps}
    >
      <Box sx={{ p: 3, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '100%' }}>
        <Typography variant="subtitle1" color="text.secondary" gutterBottom>
          Total Net Worth
        </Typography>
        {loading ? (
          <Skeleton variant="text" width={200} height={60} />
        ) : (
          <Typography variant="h3" component="div" sx={{ fontWeight: 'bold', color: 'primary.main' }}>
            {formatCurrency(netWorth)}
          </Typography>
        )}
        <Typography variant="caption" color="success.main" sx={{ mt: 1 }}>
          +2.5% this month
        </Typography>
      </Box>
    </WidgetWrapper>
  );
};
