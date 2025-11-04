import type { Meta, StoryObj } from '@storybook/react';
import { Slider } from '@/shared/components/ui/primitives/Slider';
import { Stack, Box, Typography } from '@mui/material';
import { useState } from 'react';

const meta: Meta<typeof Slider> = {
  title: 'Components/Primitives/Slider',
  component: Slider,
  parameters: {
    layout: 'centered',
    docs: {
      description: {
        component: 'Slider component for selecting a value from a range.'
      }
    }
  },
  argTypes: {
    value: {
      control: { type: 'range', min: 0, max: 100, step: 1 },
      description: 'Current value of the slider'
    },
    min: {
      control: { type: 'number' },
      description: 'Minimum value'
    },
    max: {
      control: { type: 'number' },
      description: 'Maximum value'
    },
    step: {
      control: { type: 'number' },
      description: 'Step size'
    },
    disabled: {
      control: { type: 'boolean' },
      description: 'Whether the slider is disabled'
    },
    marks: {
      control: { type: 'boolean' },
      description: 'Whether to show marks'
    },
    size: {
      control: { type: 'select' },
      options: ['small', 'medium', 'large'],
      description: 'Size of the slider'
    }
  },
  tags: ['autodocs'],
};

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
  render: () => {
    const [value, setValue] = useState(30);
    
    return (
      <Box sx={{ width: 300 }}>
        <Typography gutterBottom>Value: {value}</Typography>
        <Slider
          value={value}
          onChange={(newValue) => setValue(newValue as number)}
          min={0}
          max={100}
        />
      </Box>
    );
  },
};

export const WithMarks: Story = {
  render: () => {
    const [value, setValue] = useState(30);
    
    return (
      <Box sx={{ width: 300 }}>
        <Typography gutterBottom>Value: {value}</Typography>
        <Slider
          value={value}
          onChange={(newValue) => setValue(newValue as number)}
          marks={[
            { value: 0, label: '0°C' },
            { value: 20, label: '20°C' },
            { value: 37, label: '37°C' },
            { value: 100, label: '100°C' },
          ]}
        />
      </Box>
    );
  },
  parameters: {
    docs: {
      source: {
        code: `
<Box sx={{ width: 300 }}>
  <Typography gutterBottom>Value: 30</Typography>
  <Slider
    value={30}
    onChange={(_, newValue) => setValue(newValue as number)}
    marks={[
      { value: 0, label: '0°C' },
      { value: 20, label: '20°C' },
      { value: 37, label: '37°C' },
      { value: 100, label: '100°C' },
    ]}
  />
</Box>`,
      },
    },
  },
};

export const Range: Story = {
  render: () => {
    const [value, setValue] = useState([20, 37]);
    
    return (
      <Box sx={{ width: 300 }}>
        <Typography gutterBottom>
          Range: {value[0]} - {value[1]}
        </Typography>
        <Slider
          range={true}
          value={value}
          onChange={(newValue) => setValue(newValue as number[])}
          min={0}
          max={100}
        />
      </Box>
    );
  },
  parameters: {
    docs: {
      source: {
        code: `
<Box sx={{ width: 300 }}>
  <Typography gutterBottom>
    Range: 20 - 37
  </Typography>
  <Slider
    value={[20, 37]}
    onChange={(_, newValue) => setValue(newValue as number[])}
    valueLabelDisplay="on"
  />
</Box>`,
      },
    },
  },
};

export const Colors: Story = {
  render: () => (
    <Stack spacing={4} sx={{ width: 300 }}>
      <Box>
        <Typography gutterBottom>Primary</Typography>
        <Slider color="primary" value={30} readOnly />
      </Box>
      <Box>
        <Typography gutterBottom>Secondary</Typography>
        <Slider color="secondary" value={30} readOnly />
      </Box>
    </Stack>
  ),
  parameters: {
    docs: {
      source: {
        code: `
<Stack spacing={4} sx={{ width: 300 }}>
  <Box>
    <Typography gutterBottom>Primary</Typography>
    <Slider color="primary" defaultValue={30} />
  </Box>
  <Box>
    <Typography gutterBottom>Secondary</Typography>
    <Slider color="secondary" defaultValue={30} />
  </Box>
</Stack>`,
      },
    },
  },
};

export const Sizes: Story = {
  render: () => (
    <Stack spacing={4} sx={{ width: 300 }}>
      <Box>
        <Typography gutterBottom>Small</Typography>
        <Slider size="small" value={30} readOnly />
      </Box>
      <Box>
        <Typography gutterBottom>Medium</Typography>
        <Slider size="medium" value={30} readOnly />
      </Box>
      <Box>
        <Typography gutterBottom>Large</Typography>
        <Slider size="large" value={30} readOnly />
      </Box>
    </Stack>
  ),
  parameters: {
    docs: {
      source: {
        code: `
<Stack spacing={4} sx={{ width: 300 }}>
  <Box>
    <Typography gutterBottom>Small</Typography>
    <Slider size="small" defaultValue={30} />
  </Box>
  <Box>
    <Typography gutterBottom>Medium</Typography>
    <Slider size="medium" defaultValue={30} />
  </Box>
  <Box>
    <Typography gutterBottom>Large</Typography>
    <Slider size="large" defaultValue={30} />
  </Box>
</Stack>`,
      },
    },
  },
};

export const Disabled: Story = {
  render: () => (
    <Box sx={{ width: 300 }}>
      <Slider disabled value={30} />
    </Box>
  ),
  parameters: {
    docs: {
      source: {
        code: `
<Box sx={{ width: 300 }}>
  <Slider disabled defaultValue={30} />
</Box>`,
      },
    },
  },
};
