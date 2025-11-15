import type { Meta, StoryObj } from '@storybook/react';
import { ProgressBar } from '@/shared/components/ui/primitives/ProgressBar';
import { Stack, Box, Typography } from '@mui/material';
import { useState } from 'react';

const meta: Meta<typeof ProgressBar> = {
  title: 'Components/Primitives/ProgressBar',
  component: ProgressBar,
  parameters: {
    layout: 'centered',
    docs: {
      description: {
        component: 'Progress bar component for showing completion status and loading states.'
      }
    }
  },
  argTypes: {
    value: {
      control: { type: 'range', min: 0, max: 100, step: 1 },
      description: 'Current progress value (0-100)'
    },
    variant: {
      control: { type: 'select' },
      options: ['linear', 'circular', 'step'],
      description: 'Type of progress indicator'
    },
    color: {
      control: { type: 'select' },
      options: ['primary', 'secondary', 'success', 'error', 'warning', 'info'],
      description: 'Color of the progress bar'
    },
    size: {
      control: { type: 'select' },
      options: ['small', 'medium', 'large'],
      description: 'Size of the progress bar'
    },
    showLabel: {
      control: { type: 'boolean' },
      description: 'Whether to show the percentage label'
    },
    indeterminate: {
      control: { type: 'boolean' },
      description: 'Whether to show indeterminate progress'
    }
  },
  tags: ['autodocs'],
};

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
  args: {
    value: 50,
    variant: 'linear',
  },
};

export const Variants: Story = {
  render: () => (
    <Stack spacing={4} alignItems="center">
      <Box sx={{ width: '100%' }}>
        <Typography gutterBottom>Linear Progress</Typography>
        <ProgressBar variant="linear" value={60} />
      </Box>
      <Box>
        <Typography gutterBottom>Circular Progress</Typography>
        <ProgressBar variant="circular" value={60} />
      </Box>
      <Box sx={{ width: '100%' }}>
        <Typography gutterBottom>Step Progress</Typography>
        <ProgressBar 
          variant="step" 
          value={60} 
          steps={['Start', 'Process', 'Review', 'Complete']}
        />
      </Box>
    </Stack>
  ),
  parameters: {
    docs: {
      source: {
        code: `
<Stack spacing={4} alignItems="center">
  <Box sx={{ width: '100%' }}>
    <Typography gutterBottom>Linear Progress</Typography>
    <ProgressBar variant="linear" value={60} />
  </Box>
  <Box>
    <Typography gutterBottom>Circular Progress</Typography>
    <ProgressBar variant="circular" value={60} />
  </Box>
  <Box sx={{ width: '100%' }}>
    <Typography gutterBottom>Step Progress</Typography>
    <ProgressBar 
      variant="step" 
      value={60} 
      steps={['Start', 'Process', 'Review', 'Complete']}
    />
  </Box>
</Stack>`,
      },
    },
  },
};

export const Colors: Story = {
  render: () => (
    <Stack spacing={3} sx={{ width: 300 }}>
      <Box>
        <Typography gutterBottom>Primary</Typography>
        <ProgressBar color="primary" value={60} />
      </Box>
      <Box>
        <Typography gutterBottom>Success</Typography>
        <ProgressBar color="success" value={60} />
      </Box>
      <Box>
        <Typography gutterBottom>Warning</Typography>
        <ProgressBar color="warning" value={60} />
      </Box>
      <Box>
        <Typography gutterBottom>Error</Typography>
        <ProgressBar color="error" value={60} />
      </Box>
    </Stack>
  ),
};

export const Sizes: Story = {
  render: () => (
    <Stack spacing={3} sx={{ width: 300 }}>
      <Box>
        <Typography gutterBottom>Small</Typography>
        <ProgressBar size="small" value={60} />
      </Box>
      <Box>
        <Typography gutterBottom>Medium</Typography>
        <ProgressBar size="medium" value={60} />
      </Box>
      <Box>
        <Typography gutterBottom>Large</Typography>
        <ProgressBar size="large" value={60} />
      </Box>
    </Stack>
  ),
};

export const WithLabels: Story = {
  render: () => (
    <Stack spacing={3} sx={{ width: 300 }}>
      <Box>
        <Typography gutterBottom>With Percentage</Typography>
        <ProgressBar value={60} showLabel />
      </Box>
      <Box>
        <Typography gutterBottom>Custom Label</Typography>
        <ProgressBar value={60} label="3 of 5 tasks completed" />
      </Box>
    </Stack>
  ),
};

export const Indeterminate: Story = {
  render: () => (
    <Stack spacing={3} sx={{ width: 300 }}>
      <Box>
        <Typography gutterBottom>Linear Indeterminate</Typography>
        <ProgressBar variant="linear" indeterminate />
      </Box>
      <Box>
        <Typography gutterBottom>Circular Indeterminate</Typography>
        <ProgressBar variant="circular" indeterminate />
      </Box>
    </Stack>
  ),
};

export const Interactive: Story = {
  render: () => {
    const [value, setValue] = useState(30);
    
    return (
      <Box sx={{ width: 300 }}>
        <Typography gutterBottom>Progress: {value}%</Typography>
        <ProgressBar value={value} showLabel />
        <Box sx={{ mt: 2 }}>
          <button onClick={() => setValue(Math.max(0, value - 10))}>-10%</button>
          <button onClick={() => setValue(Math.min(100, value + 10))} style={{ marginLeft: 8 }}>+10%</button>
        </Box>
      </Box>
    );
  },
};

export const ThicknessAndHeight: Story = {
  render: () => (
    <Stack spacing={4} alignItems="center">
      <Box>
        <Typography gutterBottom>Circular thickness=2</Typography>
        <ProgressBar variant="circular" value={60} thickness={2} />
      </Box>
      <Box>
        <Typography gutterBottom>Circular thickness=8</Typography>
        <ProgressBar variant="circular" value={60} thickness={8} />
      </Box>
      <Box sx={{ width: '100%' }}>
        <Typography gutterBottom>Linear height=6</Typography>
        <ProgressBar variant="linear" value={60} height={6} />
      </Box>
      <Box sx={{ width: '100%' }}>
        <Typography gutterBottom>Linear height=14</Typography>
        <ProgressBar variant="linear" value={60} height={14} />
      </Box>
    </Stack>
  ),
  parameters: {
    docs: {
      description: { story: 'Use thickness for circular variant, height for linear variant.' }
    }
  }
};
