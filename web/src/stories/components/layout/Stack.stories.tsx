import type { Meta, StoryObj } from '@storybook/react';
import { Stack } from '@/shared/components/ui/layout/Stack';
import { Box, Typography, Button } from '@mui/material';

const meta: Meta<typeof Stack> = {
  title: 'Components/Layout/Stack',
  component: Stack,
  parameters: {
    layout: 'padded',
    docs: {
      description: {
        component: 'Stack component for arranging elements in a vertical layout with consistent spacing using flexbox. The Stack component always arranges items in a column (vertical direction).'
      }
    }
  },
  argTypes: {
    gap: {
      control: { type: 'text' },
      description: 'Spacing between items (number in 8px units or CSS string like "1rem")'
    },
    align: {
      control: { type: 'select' },
      options: ['start', 'center', 'end', 'stretch'],
      description: 'Cross-axis alignment (horizontal alignment for vertical stack)'
    },
    justify: {
      control: { type: 'select' },
      options: ['start', 'center', 'end', 'between', 'around', 'evenly', 'stretch'],
      description: 'Main-axis justification (vertical alignment for vertical stack)'
    },
    as: {
      control: { type: 'text' },
      description: 'HTML element to render as (default: "div")'
    },
    className: {
      control: { type: 'text' },
      description: 'Additional CSS class name'
    }
  },
  tags: ['autodocs'],
};

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
  args: {
    gap: 2,
    children: (
      <>
        <Box sx={{ p: 2, bgcolor: 'primary.main', color: 'white' }}>Item 1</Box>
        <Box sx={{ p: 2, bgcolor: 'secondary.main', color: 'white' }}>Item 2</Box>
        <Box sx={{ p: 2, bgcolor: 'success.main', color: 'white' }}>Item 3</Box>
      </>
    ),
  },
};

const WithGapExample = () => (
  <Stack gap={4}>
    <Box>
      <Typography variant="h6" gutterBottom>Gap 1 (8px)</Typography>
      <Stack gap={1}>
        <Box sx={{ p: 2, bgcolor: 'primary.main', color: 'white' }}>Item 1</Box>
        <Box sx={{ p: 2, bgcolor: 'secondary.main', color: 'white' }}>Item 2</Box>
        <Box sx={{ p: 2, bgcolor: 'success.main', color: 'white' }}>Item 3</Box>
      </Stack>
    </Box>
    <Box>
      <Typography variant="h6" gutterBottom>Gap 4 (32px)</Typography>
      <Stack gap={4}>
        <Box sx={{ p: 2, bgcolor: 'primary.main', color: 'white' }}>Item 1</Box>
        <Box sx={{ p: 2, bgcolor: 'secondary.main', color: 'white' }}>Item 2</Box>
        <Box sx={{ p: 2, bgcolor: 'success.main', color: 'white' }}>Item 3</Box>
      </Stack>
    </Box>
    <Box>
      <Typography variant="h6" gutterBottom>Gap with CSS string (1rem)</Typography>
      <Stack gap="1rem">
        <Box sx={{ p: 2, bgcolor: 'primary.main', color: 'white' }}>Item 1</Box>
        <Box sx={{ p: 2, bgcolor: 'secondary.main', color: 'white' }}>Item 2</Box>
        <Box sx={{ p: 2, bgcolor: 'success.main', color: 'white' }}>Item 3</Box>
      </Stack>
    </Box>
  </Stack>
);

export const WithGap: Story = {
  render: WithGapExample,
  parameters: {
    docs: {
      source: {
        code: `<Stack gap={4}>
  <Box>
    <Typography variant="h6" gutterBottom>Gap 1 (8px)</Typography>
    <Stack gap={1}>
      <Box sx={{ p: 2, bgcolor: 'primary.main', color: 'white' }}>Item 1</Box>
      <Box sx={{ p: 2, bgcolor: 'secondary.main', color: 'white' }}>Item 2</Box>
      <Box sx={{ p: 2, bgcolor: 'success.main', color: 'white' }}>Item 3</Box>
    </Stack>
  </Box>
  <Box>
    <Typography variant="h6" gutterBottom>Gap 4 (32px)</Typography>
    <Stack gap={4}>
      <Box sx={{ p: 2, bgcolor: 'primary.main', color: 'white' }}>Item 1</Box>
      <Box sx={{ p: 2, bgcolor: 'secondary.main', color: 'white' }}>Item 2</Box>
      <Box sx={{ p: 2, bgcolor: 'success.main', color: 'white' }}>Item 3</Box>
    </Stack>
  </Box>
  <Box>
    <Typography variant="h6" gutterBottom>Gap with CSS string (1rem)</Typography>
    <Stack gap="1rem">
      <Box sx={{ p: 2, bgcolor: 'primary.main', color: 'white' }}>Item 1</Box>
      <Box sx={{ p: 2, bgcolor: 'secondary.main', color: 'white' }}>Item 2</Box>
      <Box sx={{ p: 2, bgcolor: 'success.main', color: 'white' }}>Item 3</Box>
    </Stack>
  </Box>
</Stack>`,
      },
    },
  },
};

const AlignmentExample = () => (
  <Stack gap={4}>
    <Box>
      <Typography variant="h6" gutterBottom>Align: Start (default)</Typography>
      <Box sx={{ height: 150, bgcolor: 'grey.100', p: 2 }}>
        <Stack gap={2} align="start" style={{ height: '100%' }}>
          <Box sx={{ p: 2, bgcolor: 'primary.main', color: 'white', width: '60%' }}>Item 1</Box>
          <Box sx={{ p: 2, bgcolor: 'secondary.main', color: 'white', width: '40%' }}>Item 2</Box>
        </Stack>
      </Box>
    </Box>
    <Box>
      <Typography variant="h6" gutterBottom>Align: Center</Typography>
      <Box sx={{ height: 150, bgcolor: 'grey.100', p: 2 }}>
        <Stack gap={2} align="center" style={{ height: '100%' }}>
          <Box sx={{ p: 2, bgcolor: 'primary.main', color: 'white', width: '60%' }}>Item 1</Box>
          <Box sx={{ p: 2, bgcolor: 'secondary.main', color: 'white', width: '40%' }}>Item 2</Box>
        </Stack>
      </Box>
    </Box>
    <Box>
      <Typography variant="h6" gutterBottom>Align: End</Typography>
      <Box sx={{ height: 150, bgcolor: 'grey.100', p: 2 }}>
        <Stack gap={2} align="end" style={{ height: '100%' }}>
          <Box sx={{ p: 2, bgcolor: 'primary.main', color: 'white', width: '60%' }}>Item 1</Box>
          <Box sx={{ p: 2, bgcolor: 'secondary.main', color: 'white', width: '40%' }}>Item 2</Box>
        </Stack>
      </Box>
    </Box>
    <Box>
      <Typography variant="h6" gutterBottom>Align: Stretch</Typography>
      <Box sx={{ height: 150, bgcolor: 'grey.100', p: 2 }}>
        <Stack gap={2} align="stretch" style={{ height: '100%' }}>
          <Box sx={{ p: 2, bgcolor: 'primary.main', color: 'white' }}>Item 1</Box>
          <Box sx={{ p: 2, bgcolor: 'secondary.main', color: 'white' }}>Item 2</Box>
        </Stack>
      </Box>
    </Box>
  </Stack>
);

export const Alignment: Story = {
  render: AlignmentExample,
  parameters: {
    docs: {
      source: {
        code: `<Stack gap={4}>
  <Box>
    <Typography variant="h6" gutterBottom>Align: Start (default)</Typography>
    <Box sx={{ height: 150, bgcolor: 'grey.100', p: 2 }}>
      <Stack gap={2} align="start" style={{ height: '100%' }}>
        <Box sx={{ p: 2, bgcolor: 'primary.main', color: 'white', width: '60%' }}>Item 1</Box>
        <Box sx={{ p: 2, bgcolor: 'secondary.main', color: 'white', width: '40%' }}>Item 2</Box>
      </Stack>
    </Box>
  </Box>
  <Box>
    <Typography variant="h6" gutterBottom>Align: Center</Typography>
    <Box sx={{ height: 150, bgcolor: 'grey.100', p: 2 }}>
      <Stack gap={2} align="center" style={{ height: '100%' }}>
        <Box sx={{ p: 2, bgcolor: 'primary.main', color: 'white', width: '60%' }}>Item 1</Box>
        <Box sx={{ p: 2, bgcolor: 'secondary.main', color: 'white', width: '40%' }}>Item 2</Box>
      </Stack>
    </Box>
  </Box>
  <Box>
    <Typography variant="h6" gutterBottom>Align: End</Typography>
    <Box sx={{ height: 150, bgcolor: 'grey.100', p: 2 }}>
      <Stack gap={2} align="end" style={{ height: '100%' }}>
        <Box sx={{ p: 2, bgcolor: 'primary.main', color: 'white', width: '60%' }}>Item 1</Box>
        <Box sx={{ p: 2, bgcolor: 'secondary.main', color: 'white', width: '40%' }}>Item 2</Box>
      </Stack>
    </Box>
  </Box>
  <Box>
    <Typography variant="h6" gutterBottom>Align: Stretch</Typography>
    <Box sx={{ height: 150, bgcolor: 'grey.100', p: 2 }}>
      <Stack gap={2} align="stretch" style={{ height: '100%' }}>
        <Box sx={{ p: 2, bgcolor: 'primary.main', color: 'white' }}>Item 1</Box>
        <Box sx={{ p: 2, bgcolor: 'secondary.main', color: 'white' }}>Item 2</Box>
      </Stack>
    </Box>
  </Box>
</Stack>`,
      },
    },
  },
};

const JustificationExample = () => (
  <Stack gap={4}>
    <Box>
      <Typography variant="h6" gutterBottom>Justify: Start (default)</Typography>
      <Box sx={{ height: 200, bgcolor: 'grey.100', p: 2 }}>
        <Stack justify="start" style={{ height: '100%' }}>
          <Box sx={{ p: 2, bgcolor: 'primary.main', color: 'white', height: 40 }}>Item 1</Box>
          <Box sx={{ p: 2, bgcolor: 'secondary.main', color: 'white', height: 40 }}>Item 2</Box>
        </Stack>
      </Box>
    </Box>
    <Box>
      <Typography variant="h6" gutterBottom>Justify: Center</Typography>
      <Box sx={{ height: 200, bgcolor: 'grey.100', p: 2 }}>
        <Stack justify="center" style={{ height: '100%' }}>
          <Box sx={{ p: 2, bgcolor: 'primary.main', color: 'white', height: 40 }}>Item 1</Box>
          <Box sx={{ p: 2, bgcolor: 'secondary.main', color: 'white', height: 40 }}>Item 2</Box>
        </Stack>
      </Box>
    </Box>
    <Box>
      <Typography variant="h6" gutterBottom>Justify: End</Typography>
      <Box sx={{ height: 200, bgcolor: 'grey.100', p: 2 }}>
        <Stack justify="end" style={{ height: '100%' }}>
          <Box sx={{ p: 2, bgcolor: 'primary.main', color: 'white', height: 40 }}>Item 1</Box>
          <Box sx={{ p: 2, bgcolor: 'secondary.main', color: 'white', height: 40 }}>Item 2</Box>
        </Stack>
      </Box>
    </Box>
    <Box>
      <Typography variant="h6" gutterBottom>Justify: Between</Typography>
      <Box sx={{ height: 200, bgcolor: 'grey.100', p: 2 }}>
        <Stack justify="between" style={{ height: '100%' }}>
          <Box sx={{ p: 2, bgcolor: 'primary.main', color: 'white', height: 40 }}>Item 1</Box>
          <Box sx={{ p: 2, bgcolor: 'secondary.main', color: 'white', height: 40 }}>Item 2</Box>
        </Stack>
      </Box>
    </Box>
    <Box>
      <Typography variant="h6" gutterBottom>Justify: Around</Typography>
      <Box sx={{ height: 200, bgcolor: 'grey.100', p: 2 }}>
        <Stack justify="around" style={{ height: '100%' }}>
          <Box sx={{ p: 2, bgcolor: 'primary.main', color: 'white', height: 40 }}>Item 1</Box>
          <Box sx={{ p: 2, bgcolor: 'secondary.main', color: 'white', height: 40 }}>Item 2</Box>
        </Stack>
      </Box>
    </Box>
    <Box>
      <Typography variant="h6" gutterBottom>Justify: Evenly</Typography>
      <Box sx={{ height: 200, bgcolor: 'grey.100', p: 2 }}>
        <Stack justify="evenly" style={{ height: '100%' }}>
          <Box sx={{ p: 2, bgcolor: 'primary.main', color: 'white', height: 40 }}>Item 1</Box>
          <Box sx={{ p: 2, bgcolor: 'secondary.main', color: 'white', height: 40 }}>Item 2</Box>
        </Stack>
      </Box>
    </Box>
  </Stack>
);

export const Justification: Story = {
  render: JustificationExample,
  parameters: {
    docs: {
      source: {
        code: `<Stack gap={4}>
  <Box>
    <Typography variant="h6" gutterBottom>Justify: Start (default)</Typography>
    <Box sx={{ height: 200, bgcolor: 'grey.100', p: 2 }}>
      <Stack justify="start" style={{ height: '100%' }}>
        <Box sx={{ p: 2, bgcolor: 'primary.main', color: 'white', height: 40 }}>Item 1</Box>
        <Box sx={{ p: 2, bgcolor: 'secondary.main', color: 'white', height: 40 }}>Item 2</Box>
      </Stack>
    </Box>
  </Box>
  <Box>
    <Typography variant="h6" gutterBottom>Justify: Center</Typography>
    <Box sx={{ height: 200, bgcolor: 'grey.100', p: 2 }}>
      <Stack justify="center" style={{ height: '100%' }}>
        <Box sx={{ p: 2, bgcolor: 'primary.main', color: 'white', height: 40 }}>Item 1</Box>
        <Box sx={{ p: 2, bgcolor: 'secondary.main', color: 'white', height: 40 }}>Item 2</Box>
      </Stack>
    </Box>
  </Box>
  <Box>
    <Typography variant="h6" gutterBottom>Justify: End</Typography>
    <Box sx={{ height: 200, bgcolor: 'grey.100', p: 2 }}>
      <Stack justify="end" style={{ height: '100%' }}>
        <Box sx={{ p: 2, bgcolor: 'primary.main', color: 'white', height: 40 }}>Item 1</Box>
        <Box sx={{ p: 2, bgcolor: 'secondary.main', color: 'white', height: 40 }}>Item 2</Box>
      </Stack>
    </Box>
  </Box>
  <Box>
    <Typography variant="h6" gutterBottom>Justify: Between</Typography>
    <Box sx={{ height: 200, bgcolor: 'grey.100', p: 2 }}>
      <Stack justify="between" style={{ height: '100%' }}>
        <Box sx={{ p: 2, bgcolor: 'primary.main', color: 'white', height: 40 }}>Item 1</Box>
        <Box sx={{ p: 2, bgcolor: 'secondary.main', color: 'white', height: 40 }}>Item 2</Box>
      </Stack>
    </Box>
  </Box>
  <Box>
    <Typography variant="h6" gutterBottom>Justify: Around</Typography>
    <Box sx={{ height: 200, bgcolor: 'grey.100', p: 2 }}>
      <Stack justify="around" style={{ height: '100%' }}>
        <Box sx={{ p: 2, bgcolor: 'primary.main', color: 'white', height: 40 }}>Item 1</Box>
        <Box sx={{ p: 2, bgcolor: 'secondary.main', color: 'white', height: 40 }}>Item 2</Box>
      </Stack>
    </Box>
  </Box>
  <Box>
    <Typography variant="h6" gutterBottom>Justify: Evenly</Typography>
    <Box sx={{ height: 200, bgcolor: 'grey.100', p: 2 }}>
      <Stack justify="evenly" style={{ height: '100%' }}>
        <Box sx={{ p: 2, bgcolor: 'primary.main', color: 'white', height: 40 }}>Item 1</Box>
        <Box sx={{ p: 2, bgcolor: 'secondary.main', color: 'white', height: 40 }}>Item 2</Box>
      </Stack>
    </Box>
  </Box>
</Stack>`,
      },
    },
  },
};

const CombinedExample = () => (
  <Stack gap={4}>
    <Box>
      <Typography variant="h6" gutterBottom>Centered (align + justify)</Typography>
      <Box sx={{ height: 200, bgcolor: 'grey.100', p: 2 }}>
        <Stack gap={2} align="center" justify="center" style={{ height: '100%' }}>
          <Box sx={{ p: 2, bgcolor: 'primary.main', color: 'white', width: '60%' }}>Item 1</Box>
          <Box sx={{ p: 2, bgcolor: 'secondary.main', color: 'white', width: '40%' }}>Item 2</Box>
        </Stack>
      </Box>
    </Box>
    <Box>
      <Typography variant="h6" gutterBottom>Space Between with End Alignment</Typography>
      <Box sx={{ height: 200, bgcolor: 'grey.100', p: 2 }}>
        <Stack gap={2} align="end" justify="between" style={{ height: '100%' }}>
          <Box sx={{ p: 2, bgcolor: 'primary.main', color: 'white', width: '60%' }}>Item 1</Box>
          <Box sx={{ p: 2, bgcolor: 'secondary.main', color: 'white', width: '40%' }}>Item 2</Box>
        </Stack>
      </Box>
    </Box>
  </Stack>
);

export const Combined: Story = {
  render: CombinedExample,
  parameters: {
    docs: {
      source: {
        code: `<Stack gap={4}>
  <Box>
    <Typography variant="h6" gutterBottom>Centered (align + justify)</Typography>
    <Box sx={{ height: 200, bgcolor: 'grey.100', p: 2 }}>
      <Stack gap={2} align="center" justify="center" style={{ height: '100%' }}>
        <Box sx={{ p: 2, bgcolor: 'primary.main', color: 'white', width: '60%' }}>Item 1</Box>
        <Box sx={{ p: 2, bgcolor: 'secondary.main', color: 'white', width: '40%' }}>Item 2</Box>
      </Stack>
    </Box>
  </Box>
  <Box>
    <Typography variant="h6" gutterBottom>Space Between with End Alignment</Typography>
    <Box sx={{ height: 200, bgcolor: 'grey.100', p: 2 }}>
      <Stack gap={2} align="end" justify="between" style={{ height: '100%' }}>
        <Box sx={{ p: 2, bgcolor: 'primary.main', color: 'white', width: '60%' }}>Item 1</Box>
        <Box sx={{ p: 2, bgcolor: 'secondary.main', color: 'white', width: '40%' }}>Item 2</Box>
      </Stack>
    </Box>
  </Box>
</Stack>`,
      },
    },
  },
};

const RealWorldExampleComponent = () => (
  <Stack gap={3}>
    <Typography variant="h6">Card Layout</Typography>
    <Stack gap={2}>
      <Box sx={{ p: 3, bgcolor: 'white', borderRadius: 2, boxShadow: 1 }}>
        <Typography variant="h6" gutterBottom>Card 1</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          This is a card with some content that demonstrates the Stack component.
        </Typography>
        <Stack gap={1} align="start">
          <Button size="small" variant="outlined">Action 1</Button>
          <Button size="small" variant="contained">Action 2</Button>
        </Stack>
      </Box>
      <Box sx={{ p: 3, bgcolor: 'white', borderRadius: 2, boxShadow: 1 }}>
        <Typography variant="h6" gutterBottom>Card 2</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          Another card showing how Stack can be used for responsive layouts.
        </Typography>
        <Stack gap={1} align="start">
          <Button size="small" variant="outlined">Action 1</Button>
          <Button size="small" variant="contained">Action 2</Button>
        </Stack>
      </Box>
    </Stack>
  </Stack>
);

export const RealWorldExample: Story = {
  render: RealWorldExampleComponent,
  parameters: {
    docs: {
      source: {
        code: `<Stack gap={3}>
  <Typography variant="h6">Card Layout</Typography>
  <Stack gap={2}>
    <Box sx={{ p: 3, bgcolor: 'white', borderRadius: 2, boxShadow: 1 }}>
      <Typography variant="h6" gutterBottom>Card 1</Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        This is a card with some content that demonstrates the Stack component.
      </Typography>
      <Stack gap={1} align="start">
        <Button size="small" variant="outlined">Action 1</Button>
        <Button size="small" variant="contained">Action 2</Button>
      </Stack>
    </Box>
    <Box sx={{ p: 3, bgcolor: 'white', borderRadius: 2, boxShadow: 1 }}>
      <Typography variant="h6" gutterBottom>Card 2</Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        Another card showing how Stack can be used for responsive layouts.
      </Typography>
      <Stack gap={1} align="start">
        <Button size="small" variant="outlined">Action 1</Button>
        <Button size="small" variant="contained">Action 2</Button>
      </Stack>
    </Box>
  </Stack>
</Stack>`,
      },
    },
  },
};

const AsDifferentElementExample = () => (
  <Stack gap={2}>
    <Typography variant="h6">Stack as section element</Typography>
    <Box sx={{ border: '1px solid', borderColor: 'divider', p: 2, borderRadius: 1 }}>
      <Stack as="section" gap={2}>
        <Typography variant="body1">This Stack is rendered as a &lt;section&gt; element</Typography>
        <Typography variant="body2" color="text.secondary">
          Use the `as` prop to change the HTML element type for semantic HTML.
        </Typography>
      </Stack>
    </Box>
  </Stack>
);

export const AsDifferentElement: Story = {
  render: AsDifferentElementExample,
  parameters: {
    docs: {
      source: {
        code: `<Stack gap={2}>
  <Typography variant="h6">Stack as section element</Typography>
  <Box sx={{ border: '1px solid', borderColor: 'divider', p: 2, borderRadius: 1 }}>
    <Stack as="section" gap={2}>
      <Typography variant="body1">This Stack is rendered as a &lt;section&gt; element</Typography>
      <Typography variant="body2" color="text.secondary">
        Use the \`as\` prop to change the HTML element type for semantic HTML.
      </Typography>
    </Stack>
  </Box>
</Stack>`,
      },
    },
  },
};
