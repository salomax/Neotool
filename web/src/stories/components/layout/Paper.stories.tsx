import type { Meta, StoryObj } from '@storybook/react';
import { Paper } from '@/shared/components/ui/layout/Paper';
import { Stack, Box, Typography, Button } from '@mui/material';

const meta: Meta<typeof Paper> = {
  title: 'Components/Layout/Paper',
  component: Paper,
  parameters: {
    layout: 'centered',
    docs: {
      description: {
        component: 'Paper component for creating elevated surfaces and containers with consistent styling. Automatically applies padding from theme and supports test ID generation via name or data-testid props.'
      }
    }
  },
  argTypes: {
    elevation: {
      control: { type: 'range', min: 0, max: 24, step: 1 },
      description: 'Shadow depth (0-24)'
    },
    variant: {
      control: { type: 'select' },
      options: ['elevation', 'outlined'],
      description: 'Variant of the paper'
    },
    square: {
      control: { type: 'boolean' },
      description: 'Whether to use square corners'
    },
    name: {
      control: { type: 'text' },
      description: 'Optional name for generating data-testid'
    },
    'data-testid': {
      control: { type: 'text' },
      description: 'Custom data-testid attribute (takes precedence over name)'
    }
  },
  tags: ['autodocs'],
};

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
  args: {
    elevation: 1,
  },
  render: (args) => (
    <Paper {...args} sx={{ minWidth: 200 }}>
      <Typography variant="h6" gutterBottom>Paper Content</Typography>
      <Typography variant="body2" color="text.secondary">
        This is content inside a Paper component with default theme padding.
      </Typography>
    </Paper>
  ),
};

export const Elevations: Story = {
  render: () => (
    <Stack direction="row" spacing={3} flexWrap="wrap">
      {[0, 1, 3, 6, 12, 24].map((elevation) => (
        <Paper key={elevation} elevation={elevation} sx={{ p: 3, minWidth: 150, textAlign: 'center' }}>
          <Typography variant="h6">Elevation {elevation}</Typography>
        </Paper>
      ))}
    </Stack>
  ),
};

export const Variants: Story = {
  render: () => (
    <Stack direction="row" spacing={3}>
      <Paper variant="elevation" elevation={2} sx={{ p: 3, minWidth: 150 }}>
        <Typography variant="h6">Elevation</Typography>
        <Typography variant="body2" color="text.secondary">
          With shadow
        </Typography>
      </Paper>
      <Paper variant="outlined" sx={{ p: 3, minWidth: 150 }}>
        <Typography variant="h6">Outlined</Typography>
        <Typography variant="body2" color="text.secondary">
          With border
        </Typography>
      </Paper>
    </Stack>
  ),
};

export const SquareCorners: Story = {
  render: () => (
    <Stack direction="row" spacing={3}>
      <Paper elevation={2} sx={{ p: 3, minWidth: 150 }}>
        <Typography variant="h6">Rounded</Typography>
        <Typography variant="body2" color="text.secondary">
          Default rounded corners
        </Typography>
      </Paper>
      <Paper elevation={2} square sx={{ p: 3, minWidth: 150 }}>
        <Typography variant="h6">Square</Typography>
        <Typography variant="body2" color="text.secondary">
          Square corners
        </Typography>
      </Paper>
    </Stack>
  ),
};

export const CardExample: Story = {
  render: () => (
    <Paper elevation={3} sx={{ p: 3, maxWidth: 300 }}>
      <Typography variant="h5" gutterBottom>Product Card</Typography>
      <Typography variant="body2" color="text.secondary" paragraph>
        This is an example of using Paper as a card container with content and actions.
      </Typography>
      <Stack direction="row" spacing={2} sx={{ mt: 2 }}>
        <Button variant="outlined" size="small">Learn More</Button>
        <Button variant="contained" size="small">Buy Now</Button>
      </Stack>
    </Paper>
  ),
};

export const LayoutExample: Story = {
  render: () => (
    <Stack spacing={2}>
      <Paper elevation={1} sx={{ p: 2 }}>
        <Typography variant="h6">Header</Typography>
      </Paper>
      <Stack direction="row" spacing={2}>
        <Paper elevation={1} sx={{ p: 2, flex: 1 }}>
          <Typography variant="h6">Sidebar</Typography>
          <Typography variant="body2" color="text.secondary">
            Navigation content
          </Typography>
        </Paper>
        <Paper elevation={1} sx={{ p: 2, flex: 2 }}>
          <Typography variant="h6">Main Content</Typography>
          <Typography variant="body2" color="text.secondary">
            Main content area with more space
          </Typography>
        </Paper>
      </Stack>
    </Stack>
  ),
};

export const WithNameProp: Story = {
  render: () => (
    <Paper name="search-filters" elevation={2} sx={{ minWidth: 200 }}>
      <Typography variant="h6" gutterBottom>Search Filters</Typography>
      <Typography variant="body2" color="text.secondary">
        This Paper has name=&quot;search-filters&quot;, which generates data-testid=&quot;paper-search-filters&quot;
      </Typography>
      <Typography variant="caption" color="text.secondary" sx={{ mt: 1, display: 'block' }}>
        Check the DOM inspector to see the data-testid attribute
      </Typography>
    </Paper>
  ),
  parameters: {
    docs: {
      description: {
        story: 'Demonstrates the name prop which automatically generates a data-testid attribute for testing.'
      }
    }
  }
};

export const WithCustomTestId: Story = {
  render: () => (
    <Paper 
      name="should-be-ignored" 
      data-testid="custom-test-id" 
      elevation={2} 
      sx={{ minWidth: 200 }}
    >
      <Typography variant="h6" gutterBottom>Custom Test ID</Typography>
      <Typography variant="body2" color="text.secondary">
        When both name and data-testid are provided, data-testid takes precedence.
      </Typography>
      <Typography variant="caption" color="text.secondary" sx={{ mt: 1, display: 'block' }}>
        This should have data-testid=&quot;custom-test-id&quot; (not &quot;paper-should-be-ignored&quot;)
      </Typography>
    </Paper>
  ),
  parameters: {
    docs: {
      description: {
        story: 'Shows that data-testid prop takes precedence over the name prop when both are provided.'
      }
    }
  }
};

export const NoPadding: Story = {
  render: () => (
    <Paper elevation={2} sx={{ p: 0, minWidth: 200 }}>
      <Typography variant="h6" gutterBottom sx={{ p: 2, pb: 1 }}>
        Custom Padding Override
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ px: 2, pb: 2 }}>
        You can override the default padding by passing p: 0 in sx, then apply padding to specific children.
      </Typography>
    </Paper>
  ),
  parameters: {
    docs: {
      description: {
        story: 'Shows how to override default padding by using sx prop with p: 0.'
      }
    }
  }
};
