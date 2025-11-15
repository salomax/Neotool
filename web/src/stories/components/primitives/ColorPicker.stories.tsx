import type { Meta, StoryObj } from '@storybook/react';
import { ColorPicker } from '@/shared/components/ui/primitives/ColorPicker';
import { Stack, Box, Typography } from '@mui/material';
import { useState } from 'react';

const meta: Meta<typeof ColorPicker> = {
  title: 'Components/Primitives/ColorPicker',
  component: ColorPicker,
  parameters: {
    layout: 'centered',
    docs: {
      description: {
        component: 'Color picker component for selecting colors with presets and custom input.'
      }
    }
  },
  argTypes: {
    variant: {
      control: { type: 'select' },
      options: ['standard', 'outlined', 'filled'],
      description: 'Variant of the input'
    },
    size: {
      control: { type: 'select' },
      options: ['small', 'medium', 'large'],
      description: 'Size of the color picker'
    },
    disabled: {
      control: { type: 'boolean' },
      description: 'Whether the color picker is disabled'
    }
  },
  tags: ['autodocs'],
};

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
  render: () => {
    const [color, setColor] = useState('#3f51b5');
    
    return (
      <Box sx={{ width: 300 }}>
        <Typography gutterBottom>Selected: {color}</Typography>
        <Box sx={{ width: 50, height: 50, backgroundColor: color, borderRadius: 1, mb: 2, border: '1px solid #ccc' }} />
        <ColorPicker
          value={color}
          onChange={(newColor) => setColor(newColor)}
          label="Choose a color"
        />
      </Box>
    );
  },
};

export const WithCustomPresets: Story = {
  render: () => {
    const [color, setColor] = useState('#ff5722');
    const customPresets = [
      '#FF0000', '#00FF00', '#0000FF',
      '#FFFF00', '#FF00FF', '#00FFFF',
      '#FFA500', '#800080', '#FFC0CB',
    ];
    
    return (
      <Box sx={{ width: 300 }}>
        <ColorPicker
          value={color}
          onChange={(newColor) => setColor(newColor)}
          label="Choose a color"
          presets={customPresets}
        />
      </Box>
    );
  },
  parameters: {
    docs: {
      source: {
        code: `
<ColorPicker
  value={color}
  onChange={(newColor) => setColor(newColor)}
  label="Choose a color"
  presets={customPresets}
/>`,
      },
    },
  },
};

export const Variants: Story = {
  render: () => {
    const [color1, setColor1] = useState('#3f51b5');
    const [color2, setColor2] = useState('#f44336');
    const [color3, setColor3] = useState('#4caf50');
    
    return (
      <Stack spacing={3} sx={{ width: 300 }}>
        <ColorPicker
          variant="standard"
          value={color1}
          onChange={(newColor) => setColor1(newColor)}
          label="Standard"
        />
        <ColorPicker
          variant="outlined"
          value={color2}
          onChange={(newColor) => setColor2(newColor)}
          label="Outlined"
        />
        <ColorPicker
          variant="filled"
          value={color3}
          onChange={(newColor) => setColor3(newColor)}
          label="Filled"
        />
      </Stack>
    );
  },
  parameters: {
    docs: {
      source: {
        code: `
<ColorPicker variant="standard" value={color} onChange={setColor} label="Standard" />
<ColorPicker variant="outlined" value={color} onChange={setColor} label="Outlined" />
<ColorPicker variant="filled" value={color} onChange={setColor} label="Filled" />`,
      },
    },
  },
};

export const Sizes: Story = {
  render: () => {
    const [color1, setColor1] = useState('#3f51b5');
    const [color2, setColor2] = useState('#f44336');
    const [color3, setColor3] = useState('#4caf50');
    
    return (
      <Stack spacing={3} sx={{ width: 300 }}>
        <ColorPicker
          size="small"
          value={color1}
          onChange={(newColor) => setColor1(newColor)}
          label="Small"
        />
        <ColorPicker
          size="medium"
          value={color2}
          onChange={(newColor) => setColor2(newColor)}
          label="Medium"
        />
        <ColorPicker
          size="large"
          value={color3}
          onChange={(newColor) => setColor3(newColor)}
          label="Large"
        />
      </Stack>
    );
  },
  parameters: {
    docs: {
      source: {
        code: `
<ColorPicker size="small" value={color} onChange={setColor} label="Small" />
<ColorPicker size="medium" value={color} onChange={setColor} label="Medium" />
<ColorPicker size="large" value={color} onChange={setColor} label="Large" />`,
      },
    },
  },
};

export const Disabled: Story = {
  render: () => (
    <Box sx={{ width: 300 }}>
      <ColorPicker
        value="#3f51b5"
        label="Disabled"
        disabled
      />
    </Box>
  ),
  parameters: {
    docs: {
      source: {
        code: `
<ColorPicker
  value="#3f51b5"
  label="Disabled"
  disabled
/>`,
      },
    },
  },
};

export const WithoutPresets: Story = {
  render: () => {
    const [color, setColor] = useState('#9c27b0');
    
    return (
      <Box sx={{ width: 300 }}>
        <ColorPicker
          value={color}
          onChange={(newColor) => setColor(newColor)}
          label="Custom color only"
          showPresets={false}
        />
      </Box>
    );
  },
  parameters: {
    docs: {
      source: {
        code: `
<ColorPicker
  value={color}
  onChange={setColor}
  label="Custom color only"
  showPresets={false}
/>`,
      },
    },
  },
};

