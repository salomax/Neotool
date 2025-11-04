import type { Meta, StoryObj } from '@storybook/react';
import { DataTable } from '@/shared/components/ui/data-display/DataTable';
import { Box, Typography } from '@mui/material';

const meta: Meta<typeof DataTable> = {
  title: 'Components/Data Display/DataTable',
  component: DataTable,
  parameters: {
    layout: 'padded',
    docs: {
      description: {
        component: 'Data table component for displaying structured data with sorting, filtering, and pagination.'
      }
    }
  },
  argTypes: {
    rows: {
      description: 'Array of data rows'
    },
    columns: {
      description: 'Array of column definitions'
    },
    loading: {
      control: { type: 'boolean' },
      description: 'Whether the table is in loading state'
    },
    pageSize: {
      control: { type: 'number' },
      description: 'Number of rows per page'
    },
    showPagination: {
      control: { type: 'boolean' },
      description: 'Whether to show pagination'
    },
    showSorting: {
      control: { type: 'boolean' },
      description: 'Whether to enable column sorting'
    },
    showFiltering: {
      control: { type: 'boolean' },
      description: 'Whether to show filtering options'
    }
  },
  tags: ['autodocs'],
};

export default meta;
type Story = StoryObj<typeof meta>;

const sampleData = [
  { id: 1, name: 'John Doe', email: 'john@example.com', status: 'Active', role: 'Admin' },
  { id: 2, name: 'Jane Smith', email: 'jane@example.com', status: 'Inactive', role: 'User' },
  { id: 3, name: 'Bob Johnson', email: 'bob@example.com', status: 'Active', role: 'User' },
  { id: 4, name: 'Alice Brown', email: 'alice@example.com', status: 'Pending', role: 'User' },
  { id: 5, name: 'Charlie Wilson', email: 'charlie@example.com', status: 'Active', role: 'Moderator' },
];

const columns = [
  { field: 'name', headerName: 'Name', width: 200, sortable: true },
  { field: 'email', headerName: 'Email', width: 250, sortable: true },
  { field: 'status', headerName: 'Status', width: 150, sortable: true },
  { field: 'role', headerName: 'Role', width: 150, sortable: true },
];

export const Default: Story = {
  render: () => (
    <Box sx={{ height: 400 }}>
      <DataTable 
        rows={sampleData}
        columns={columns}
        height={400}
      />
    </Box>
  ),
};

export const WithPagination: Story = {
  render: () => (
    <Box sx={{ height: 400 }}>
      <DataTable 
        rows={sampleData}
        columns={columns}
        height={400}
        pagination={true}
        paginationPageSize={3}
      />
    </Box>
  ),
};

export const Loading: Story = {
  render: () => (
    <Box sx={{ height: 400 }}>
      <DataTable 
        rows={[]}
        columns={columns}
        height={400}
        loading={true}
      />
    </Box>
  ),
};

export const LargeDataset: Story = {
  render: () => {
    const largeDataset = Array.from({ length: 100 }, (_, i) => ({
      id: i + 1,
      name: `User ${i + 1}`,
      email: `user${i + 1}@example.com`,
      status: ['Active', 'Inactive', 'Pending'][i % 3],
      role: ['Admin', 'User', 'Moderator'][i % 3],
    }));
    
    return (
      <Box sx={{ height: 400 }}>
        <Typography variant="h6" gutterBottom>Large Dataset (100 rows)</Typography>
        <DataTable 
          rows={largeDataset}
          columns={columns}
          height={400}
          pagination={true}
          paginationPageSize={10}
        />
      </Box>
    );
  },
};
