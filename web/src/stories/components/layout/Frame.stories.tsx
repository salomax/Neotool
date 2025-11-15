import type { Meta, StoryObj } from '@storybook/react';
import { Frame } from '@/shared/components/ui/layout/Frame';
import { Stack, Box, Typography } from '@mui/material';

const meta: Meta<typeof Frame> = {
  title: 'Components/Layout/Frame',
  component: Frame,
  parameters: {
    layout: 'centered',
    docs: {
      description: {
        component: 'Frame component for creating aspect-ratio containers for media and charts with optional cropping. The Frame maintains a consistent aspect ratio regardless of content size.'
      }
    }
  },
  argTypes: {
    ratio: {
      control: { type: 'text' },
      description: 'Aspect ratio (e.g., "16/9", "4/3", "1/1", "21/9")',
    },
    crop: {
      control: { type: 'boolean' },
      description: 'Whether to crop content that overflows the frame',
    },
    background: {
      control: { type: 'color' },
      description: 'Background color for the frame',
    },
    gap: {
      control: { type: 'text' },
      description: 'Spacing inside the frame (note: works with relative positioning)',
    },
    as: {
      control: { type: 'text' },
      description: 'HTML element to render as (default: "div")',
    },
    className: {
      control: { type: 'text' },
      description: 'Additional CSS class name',
    }
  },
  tags: ['autodocs'],
};

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
  args: {
    ratio: '16/9',
    crop: true,
  },
  render: (args) => (
    <Frame {...args} style={{ width: '400px', border: '2px dashed #ccc' }}>
      <Box
        sx={{
          width: '100%',
          height: '100%',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          bgcolor: 'primary.light',
          color: 'primary.contrastText',
        }}
      >
        <Typography variant="h6">16:9 Aspect Ratio</Typography>
      </Box>
    </Frame>
  ),
};

export const AspectRatios: Story = {
  render: () => (
    <Stack spacing={4} sx={{ alignItems: 'center' }}>
      <Box>
        <Typography variant="h6" gutterBottom>16:9 (Widescreen)</Typography>
        <Frame ratio="16/9" style={{ width: '400px', border: '2px dashed #ccc' }}>
          <Box
            sx={{
              width: '100%',
              height: '100%',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              bgcolor: 'primary.light',
              color: 'primary.contrastText',
            }}
          >
            <Typography>16:9</Typography>
          </Box>
        </Frame>
      </Box>
      <Box>
        <Typography variant="h6" gutterBottom>4:3 (Standard)</Typography>
        <Frame ratio="4/3" style={{ width: '400px', border: '2px dashed #ccc' }}>
          <Box
            sx={{
              width: '100%',
              height: '100%',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              bgcolor: 'secondary.light',
              color: 'secondary.contrastText',
            }}
          >
            <Typography>4:3</Typography>
          </Box>
        </Frame>
      </Box>
      <Box>
        <Typography variant="h6" gutterBottom>1:1 (Square)</Typography>
        <Frame ratio="1/1" style={{ width: '300px', border: '2px dashed #ccc' }}>
          <Box
            sx={{
              width: '100%',
              height: '100%',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              bgcolor: 'success.light',
              color: 'success.contrastText',
            }}
          >
            <Typography>1:1</Typography>
          </Box>
        </Frame>
      </Box>
      <Box>
        <Typography variant="h6" gutterBottom>21:9 (Ultrawide)</Typography>
        <Frame ratio="21/9" style={{ width: '400px', border: '2px dashed #ccc' }}>
          <Box
            sx={{
              width: '100%',
              height: '100%',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              bgcolor: 'warning.light',
              color: 'warning.contrastText',
            }}
          >
            <Typography>21:9</Typography>
          </Box>
        </Frame>
      </Box>
    </Stack>
  ),
};

export const CropBehavior: Story = {
  render: () => (
    <Stack spacing={4} sx={{ alignItems: 'center' }}>
      <Box>
        <Typography variant="h6" gutterBottom>With Crop (overflow: hidden)</Typography>
        <Typography variant="body2" color="text.secondary" gutterBottom>
          Content that overflows is cropped
        </Typography>
        <Frame ratio="16/9" crop={true} style={{ width: '400px', border: '2px dashed #ccc' }}>
          <Box
            sx={{
              width: '150%',
              height: '150%',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              bgcolor: 'error.light',
              color: 'error.contrastText',
            }}
          >
            <Typography>150% Size Content (Cropped)</Typography>
          </Box>
        </Frame>
      </Box>
      <Box>
        <Typography variant="h6" gutterBottom>Without Crop (overflow: visible)</Typography>
        <Typography variant="body2" color="text.secondary" gutterBottom>
          Content can overflow the frame boundaries
        </Typography>
        <Frame ratio="16/9" crop={false} style={{ width: '400px', border: '2px dashed #ccc' }}>
          <Box
            sx={{
              width: '150%',
              height: '150%',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              bgcolor: 'info.light',
              color: 'info.contrastText',
            }}
          >
            <Typography>150% Size Content (Visible)</Typography>
          </Box>
        </Frame>
      </Box>
    </Stack>
  ),
};

export const WithBackground: Story = {
  render: () => (
    <Stack spacing={4} sx={{ alignItems: 'center' }}>
      <Box>
        <Typography variant="h6" gutterBottom>With Background Color</Typography>
        <Frame 
          ratio="16/9" 
          background="#f0f0f0"
          style={{ width: '400px', border: '2px solid #333' }}
        >
          <Box
            sx={{
              width: '100%',
              height: '100%',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <Typography variant="h6">Content on Gray Background</Typography>
          </Box>
        </Frame>
      </Box>
      <Box>
        <Typography variant="h6" gutterBottom>With Primary Color Background</Typography>
        <Frame 
          ratio="4/3" 
          background="#1976d2"
          style={{ width: '400px', border: '2px solid #333' }}
        >
          <Box
            sx={{
              width: '100%',
              height: '100%',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: 'white',
            }}
          >
            <Typography variant="h6" sx={{ color: 'white' }}>
              Content on Primary Background
            </Typography>
          </Box>
        </Frame>
      </Box>
    </Stack>
  ),
};

export const ImageExample: Story = {
  render: () => (
    <Stack spacing={4} sx={{ alignItems: 'center' }}>
      <Box>
        <Typography variant="h6" gutterBottom>Image in 16:9 Frame</Typography>
        <Frame 
          ratio="16/9" 
          crop={true}
          style={{ width: '600px', border: '2px solid #333' }}
        >
          <Box
            component="img"
            src="https://via.placeholder.com/1920x1080/4a90e2/ffffff?text=16:9+Image"
            alt="Placeholder"
            sx={{
              width: '100%',
              height: '100%',
              objectFit: 'cover',
            }}
          />
        </Frame>
      </Box>
      <Box>
        <Typography variant="h6" gutterBottom>Image in 1:1 Frame</Typography>
        <Frame 
          ratio="1/1" 
          crop={true}
          style={{ width: '400px', border: '2px solid #333' }}
        >
          <Box
            component="img"
            src="https://via.placeholder.com/800x800/e74c3c/ffffff?text=1:1+Square"
            alt="Placeholder"
            sx={{
              width: '100%',
              height: '100%',
              objectFit: 'cover',
            }}
          />
        </Frame>
      </Box>
    </Stack>
  ),
};

export const ChartExample: Story = {
  render: () => (
    <Stack spacing={4} sx={{ alignItems: 'center' }}>
      <Box>
        <Typography variant="h6" gutterBottom>Chart Container (16:9)</Typography>
        <Frame 
          ratio="16/9" 
          background="#ffffff"
          style={{ width: '600px', border: '2px solid #e0e0e0', boxShadow: '0 2px 8px rgba(0,0,0,0.1)' }}
        >
          <Box
            sx={{
              width: '100%',
              height: '100%',
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              justifyContent: 'center',
              p: 2,
            }}
          >
            <Typography variant="h6" gutterBottom>Sample Chart</Typography>
            <Box
              sx={{
                width: '80%',
                height: '60%',
                display: 'flex',
                alignItems: 'flex-end',
                justifyContent: 'space-around',
                gap: 1,
              }}
            >
              {[60, 80, 45, 90, 70].map((height, index) => (
                <Box
                  key={index}
                  sx={{
                    width: '18%',
                    height: `${height}%`,
                    bgcolor: 'primary.main',
                    borderRadius: '4px 4px 0 0',
                  }}
                />
              ))}
            </Box>
            <Typography variant="caption" color="text.secondary" sx={{ mt: 1 }}>
              Bar Chart Visualization
            </Typography>
          </Box>
        </Frame>
      </Box>
    </Stack>
  ),
};

export const ResponsiveExample: Story = {
  render: () => (
    <Box sx={{ width: '100%', maxWidth: '800px', mx: 'auto' }}>
      <Typography variant="h6" gutterBottom>Responsive Frame</Typography>
      <Typography variant="body2" color="text.secondary" paragraph>
        Frame maintains aspect ratio while container width changes
      </Typography>
      <Frame 
        ratio="16/9" 
        background="#f5f5f5"
        style={{ width: '100%', border: '2px solid #ccc' }}
      >
        <Box
          sx={{
            width: '100%',
            height: '100%',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          <Typography variant="h6">Responsive 16:9 Frame</Typography>
        </Box>
      </Frame>
    </Box>
  ),
  parameters: {
    layout: 'padded',
  },
};

export const ContentAlignment: Story = {
  render: () => (
    <Stack spacing={4} sx={{ alignItems: 'center' }}>
      <Box>
        <Typography variant="h6" gutterBottom>Centered Content (Default)</Typography>
        <Frame 
          ratio="4/3" 
          style={{ width: '400px', border: '2px dashed #ccc' }}
        >
          <Box
            sx={{
              width: '60%',
              height: '60%',
              bgcolor: 'primary.main',
              color: 'white',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              borderRadius: 1,
            }}
          >
            <Typography>Centered Box</Typography>
          </Box>
        </Frame>
      </Box>
      <Box>
        <Typography variant="h6" gutterBottom>Full-Width Content</Typography>
        <Frame 
          ratio="4/3" 
          style={{ width: '400px', border: '2px dashed #ccc' }}
        >
          <Box
            sx={{
              width: '100%',
              height: '100%',
              bgcolor: 'secondary.main',
              color: 'white',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <Typography>Full Frame</Typography>
          </Box>
        </Frame>
      </Box>
    </Stack>
  ),
};
