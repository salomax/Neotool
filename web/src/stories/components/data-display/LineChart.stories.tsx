import type { Meta, StoryObj } from '@storybook/react';
import { Box, Stack, Typography } from '@mui/material';
import { LineChart } from '@/shared/components/ui/data-display/LineChart';

const meta: Meta<typeof LineChart> = {
  title: 'Components/Data Display/LineChart',
  component: LineChart,
  parameters: {
    layout: 'padded',
    docs: {
      description: {
        component:
          'Shared line chart component with multi-axis support built on top of Recharts.',
      },
    },
  },
  tags: ['autodocs'],
};

export default meta;
type Story = StoryObj<typeof meta>;

const sampleData = [
  { date: '2023-01-01', revenue: 120000, expenses: 80000, marginPercent: 33 },
  { date: '2023-02-01', revenue: 135000, expenses: 90000, marginPercent: 33.3 },
  { date: '2023-03-01', revenue: 150000, expenses: 95000, marginPercent: 36.7 },
  { date: '2023-04-01', revenue: 160000, expenses: 100000, marginPercent: 37.5 },
];

export const Default: Story = {
  render: () => (
    <Box sx={{ width: '100%', maxWidth: 900 }}>
      <LineChart
        data={sampleData}
        title="Revenue vs Expenses"
        xKey="date"
        xIsDate
        axes={[
          { id: 'left-currency', side: 'left', label: 'Amount (BRL)', format: 'currency' },
          { id: 'right-percent', side: 'right', label: 'Margin %', format: 'percent' },
        ]}
        series={[
          { id: 'revenue', name: 'Revenue', dataKey: 'revenue', yAxisId: 'left-currency' },
          { id: 'expenses', name: 'Expenses', dataKey: 'expenses', yAxisId: 'left-currency' },
          { id: 'margin', name: 'Margin %', dataKey: 'marginPercent', yAxisId: 'right-percent' },
        ]}
      />
    </Box>
  ),
};

export const WithGridAndDots: Story = {
  render: () => (
    <Box sx={{ width: '100%', maxWidth: 900 }}>
      <LineChart
        data={sampleData}
        title="Performance Over Time"
        xKey="date"
        xIsDate
        showGrid
        showDots
        showValueLabels
        axes={[
          { id: 'left-currency', side: 'left', label: 'Amount (BRL)', format: 'currency' },
          { id: 'right-percent', side: 'right', label: 'Margin %', format: 'percent' },
        ]}
        series={[
          { id: 'revenue', name: 'Revenue', dataKey: 'revenue', yAxisId: 'left-currency' },
          { id: 'margin', name: 'Margin %', dataKey: 'marginPercent', yAxisId: 'right-percent' },
        ]}
      />
    </Box>
  ),
};

