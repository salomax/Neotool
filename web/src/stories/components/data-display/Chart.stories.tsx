import type { Meta, StoryObj } from '@storybook/react';
import { Chart } from '@/shared/components/ui/data-display/Chart';
import { Box, Stack, Typography } from '@mui/material';

const meta: Meta<typeof Chart> = {
  title: 'Components/Data Display/Chart',
  component: Chart,
  parameters: {
    layout: 'padded',
    docs: {
      description: {
        component: 'A flexible chart component for data visualization with multiple chart types and customization options.'
      }
    }
  },
  argTypes: {
    data: {
      description: 'Array of data points to display in the chart'
    },
    type: {
      control: { type: 'select' },
      options: ['line', 'bar', 'pie', 'area'],
      description: 'The type of chart to render'
    },
    width: {
      control: { type: 'number' },
      description: 'Width of the chart in pixels'
    },
    height: {
      control: { type: 'number' },
      description: 'Height of the chart in pixels'
    },
    showLegend: {
      control: { type: 'boolean' },
      description: 'Whether to show the legend'
    },
    showGrid: {
      control: { type: 'boolean' },
      description: 'Whether to show grid lines'
    }
  },
  tags: ['autodocs'],
};

export default meta;
type Story = StoryObj<typeof meta>;

const sampleData = [
  { name: 'Jan', value: 400 },
  { name: 'Feb', value: 300 },
  { name: 'Mar', value: 200 },
  { name: 'Apr', value: 278 },
  { name: 'May', value: 189 },
  { name: 'Jun', value: 239 },
];

export const Default: Story = {
  args: {
    data: sampleData,
    type: 'line',
    width: 600,
    height: 300,
  },
};

export const ChartTypes: Story = {
  render: () => (
    <Stack spacing={4}>
      <Box>
        <Typography variant="h6" gutterBottom>Line Chart</Typography>
        <Chart data={sampleData} type="line" width={600} height={200} />
      </Box>
      <Box>
        <Typography variant="h6" gutterBottom>Bar Chart</Typography>
        <Chart data={sampleData} type="bar" width={600} height={200} />
      </Box>
      <Box>
        <Typography variant="h6" gutterBottom>Pie Chart</Typography>
        <Chart data={sampleData.slice(0, 4)} type="pie" width={400} height={200} />
      </Box>
      <Box>
        <Typography variant="h6" gutterBottom>Area Chart</Typography>
        <Chart data={sampleData} type="area" width={600} height={200} />
      </Box>
    </Stack>
  ),
};

export const WithLegend: Story = {
  render: () => (
    <Stack spacing={4}>
      <Box>
        <Typography variant="h6" gutterBottom>With Legend</Typography>
        <Chart 
          data={sampleData} 
          type="line" 
          width={600} 
          height={200} 
          showLegend={true}
        />
      </Box>
      <Box>
        <Typography variant="h6" gutterBottom>With Grid</Typography>
        <Chart 
          data={sampleData} 
          type="bar" 
          width={600} 
          height={200} 
          showGrid={true}
        />
      </Box>
    </Stack>
  ),
};

export const Responsive: Story = {
  render: () => (
    <Box sx={{ width: '100%', maxWidth: 800 }}>
      <Typography variant="h6" gutterBottom>Responsive Chart</Typography>
      <Chart 
        data={sampleData} 
        type="line" 
        width="100%" 
        height={300} 
        showLegend={true}
        showGrid={true}
      />
    </Box>
  ),
};

export const RealWorldData: Story = {
  render: () => {
    const salesData = [
      { name: 'Q1', value: 120000 },
      { name: 'Q2', value: 150000 },
      { name: 'Q3', value: 180000 },
      { name: 'Q4', value: 200000 },
    ];
    
    const userData = [
      { name: 'Mobile', value: 45 },
      { name: 'Desktop', value: 35 },
      { name: 'Tablet', value: 20 },
    ];
    
    return (
      <Stack spacing={4}>
        <Box>
          <Typography variant="h6" gutterBottom>Quarterly Sales</Typography>
          <Chart data={salesData} type="bar" width={600} height={200} />
        </Box>
        <Box>
          <Typography variant="h6" gutterBottom>User Device Distribution</Typography>
          <Chart data={userData} type="pie" width={400} height={200} />
        </Box>
      </Stack>
    );
  },
};
