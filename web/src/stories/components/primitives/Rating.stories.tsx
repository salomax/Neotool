import type { Meta, StoryObj } from '@storybook/react';
import { Rating } from '@/shared/components/ui/primitives/Rating';
import { Stack, Box, Typography } from '@mui/material';
import { useState } from 'react';

const meta: Meta<typeof Rating> = {
  title: 'Components/Primitives/Rating',
  component: Rating,
  parameters: {
    layout: 'centered',
    docs: {
      description: {
        component: 'Rating component for displaying and selecting star ratings.'
      }
    }
  },
  argTypes: {
    value: {
      control: { type: 'number', min: 0, max: 5, step: 0.5 },
      description: 'Current rating value'
    },
    max: {
      control: { type: 'number' },
      description: 'Maximum rating value'
    },
    precision: {
      control: { type: 'select' },
      options: [0.5, 1],
      description: 'Precision of the rating'
    },
    readOnly: {
      control: { type: 'boolean' },
      description: 'Whether the rating is read-only'
    },
    disabled: {
      control: { type: 'boolean' },
      description: 'Whether the rating is disabled'
    },
    size: {
      control: { type: 'select' },
      options: ['small', 'medium', 'large'],
      description: 'Size of the rating'
    }
  },
  tags: ['autodocs'],
};

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
  render: () => {
    const [value, setValue] = useState(3);
    
    return (
      <Box>
        <Typography gutterBottom>Rating: {value}</Typography>
        <Rating
          value={value}
          onChange={(newValue) => setValue(newValue)}
        />
      </Box>
    );
  },
};

export const Interactive: Story = {
  render: () => {
    const [value, setValue] = useState(3);
    
    return (
      <Box>
        <Typography gutterBottom>Rating: {value}</Typography>
        <Rating
          value={value}
          onChange={(newValue) => setValue(newValue || 0)}
        />
      </Box>
    );
  },
  parameters: {
    docs: {
      source: {
        code: `
<Box>
  <Typography gutterBottom>Rating: 3</Typography>
  <Rating
    value={value}
    onChange={(_, newValue) => setValue(newValue || 0)}
  />
</Box>`,
      },
    },
  },
};

export const HalfRating: Story = {
  render: () => {
    const [value, setValue] = useState(3.5);
    
    return (
      <Box>
        <Typography gutterBottom>Half Rating: {value}</Typography>
        <Rating
          value={value}
          precision={0.5}
          onChange={(newValue) => setValue(newValue || 0)}
        />
      </Box>
    );
  },
  parameters: {
    docs: {
      source: {
        code: `
<Box>
  <Typography gutterBottom>Half Rating: 3.5</Typography>
  <Rating
    value={value}
    precision={0.5}
    onChange={(_, newValue) => setValue(newValue || 0)}
  />
</Box>`,
      },
    },
  },
};

export const Sizes: Story = {
  render: () => (
    <Stack spacing={2}>
      <Box>
        <Typography gutterBottom>Small</Typography>
        <Rating size="small" value={3} />
      </Box>
      <Box>
        <Typography gutterBottom>Medium</Typography>
        <Rating size="medium" value={3} />
      </Box>
      <Box>
        <Typography gutterBottom>Large</Typography>
        <Rating size="large" value={3} />
      </Box>
    </Stack>
  ),
  parameters: {
    docs: {
      source: {
        code: `
<Stack spacing={2}>
  <Box>
    <Typography gutterBottom>Small</Typography>
    <Rating size="small" value={3} />
  </Box>
  <Box>
    <Typography gutterBottom>Medium</Typography>
    <Rating size="medium" value={3} />
  </Box>
  <Box>
    <Typography gutterBottom>Large</Typography>
    <Rating size="large" value={3} />
  </Box>
</Stack>`,
      },
    },
  },
};

export const ReadOnly: Story = {
  render: () => (
    <Stack spacing={2}>
      <Box>
        <Typography gutterBottom>Read Only</Typography>
        <Rating value={3} readOnly />
      </Box>
      <Box>
        <Typography gutterBottom>Disabled</Typography>
        <Rating value={3} disabled />
      </Box>
    </Stack>
  ),
  parameters: {
    docs: {
      source: {
        code: `
<Stack spacing={2}>
  <Box>
    <Typography gutterBottom>Read Only</Typography>
    <Rating value={3} readOnly />
  </Box>
  <Box>
    <Typography gutterBottom>Disabled</Typography>
    <Rating value={3} disabled />
  </Box>
</Stack>`,
      },
    },
  },
};

export const WithLabels: Story = {
  render: () => {
    const [value, setValue] = useState(3);
    const labels = ['Terrible', 'Poor', 'Average', 'Good', 'Excellent'];
    
    return (
      <Box>
        <Typography gutterBottom>Rating: {labels[Math.round(value) - 1]}</Typography>
        <Rating
          value={value}
          onChange={(newValue) => setValue(newValue || 0)}
        />
        <Box sx={{ ml: 2 }}>{labels[Math.round(value) - 1]}</Box>
      </Box>
    );
  },
  parameters: {
    docs: {
      source: {
        code: `
<Box>
  <Typography gutterBottom>Rating: Good</Typography>
  <Rating
    value={3}
    onChange={(_, newValue) => setValue(newValue || 0)}
  />
  <Box sx={{ ml: 2 }}>Good</Box>
</Box>`,
      },
    },
  },
};
