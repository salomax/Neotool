import React from 'react';
import { WidgetWrapper } from '../components/WidgetWrapper';
import { WidgetProps } from '../types';
import { Box, Typography, LinearProgress, Stack } from '@mui/material';
import PieChartIcon from '@mui/icons-material/PieChart';
import { useDashboardStore } from '../store';

// Mock data
const SPENDING_CATEGORIES = [
  { name: 'Housing', amount: 2500, total: 5000, color: 'primary.main' },
  { name: 'Food', amount: 1200, total: 5000, color: 'secondary.main' },
  { name: 'Transport', amount: 800, total: 5000, color: 'warning.main' },
  { name: 'Entertainment', amount: 500, total: 5000, color: 'info.main' },
];

export const SpendingBreakdownWidget: React.FC<WidgetProps> = ({ widget, dragHandleProps }) => {
  const { isEditMode, removeWidget } = useDashboardStore();

  const formatCurrency = (value: number) => {
    return new Intl.NumberFormat('pt-BR', {
      style: 'currency',
      currency: 'BRL',
    }).format(value);
  };

  return (
    <WidgetWrapper
      title={widget.title || "Spending Breakdown"}
      icon={<PieChartIcon />}
      isEditMode={isEditMode}
      onRemove={() => removeWidget(widget.id)}
      onRefresh={() => console.log('Refresh Spending')}
      dragHandleProps={dragHandleProps}
    >
      <Box sx={{ p: 2 }}>
        <Stack spacing={3}>
          {SPENDING_CATEGORIES.map((category) => (
            <Box key={category.name}>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
                <Typography variant="body2" fontWeight="medium">{category.name}</Typography>
                <Typography variant="body2">{formatCurrency(category.amount)}</Typography>
              </Box>
              <LinearProgress 
                variant="determinate" 
                value={(category.amount / category.total) * 100} 
                sx={{ 
                  height: 8, 
                  borderRadius: 4,
                  bgcolor: 'action.hover',
                  '& .MuiLinearProgress-bar': {
                    bgcolor: category.color
                  }
                }}
              />
            </Box>
          ))}
        </Stack>
      </Box>
    </WidgetWrapper>
  );
};
