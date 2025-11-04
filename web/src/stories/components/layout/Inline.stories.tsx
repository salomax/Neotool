import type { Meta, StoryObj } from '@storybook/react';
import { Inline } from '@/shared/components/ui/layout/Inline';
import { Box, Typography, Button } from '@mui/material';

const meta: Meta<typeof Inline> = {
  title: 'Components/Layout/Inline',
  component: Inline,
  parameters: {
    layout: 'padded',
    docs: {
      description: {
        component: 'Inline component for arranging elements in a horizontal layout with consistent spacing using flexbox. The Inline component arranges items in a row (horizontal direction) with optional wrapping.'
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
      description: 'Cross-axis alignment (vertical alignment for horizontal inline)'
    },
    justify: {
      control: { type: 'select' },
      options: ['start', 'center', 'end', 'between', 'around', 'evenly'],
      description: 'Main-axis justification (horizontal alignment for horizontal inline)'
    },
    wrap: {
      control: { type: 'boolean' },
      description: 'Whether to wrap items to new lines when they don\'t fit'
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
  <Box>
    <Typography variant="h6" gutterBottom>Gap 1 (8px)</Typography>
    <Box sx={{ mb: 4 }}>
      <Inline gap={1}>
        <Box sx={{ p: 2, bgcolor: 'primary.main', color: 'white' }}>Item 1</Box>
        <Box sx={{ p: 2, bgcolor: 'secondary.main', color: 'white' }}>Item 2</Box>
        <Box sx={{ p: 2, bgcolor: 'success.main', color: 'white' }}>Item 3</Box>
      </Inline>
    </Box>
    <Typography variant="h6" gutterBottom>Gap 4 (32px)</Typography>
    <Box sx={{ mb: 4 }}>
      <Inline gap={4}>
        <Box sx={{ p: 2, bgcolor: 'primary.main', color: 'white' }}>Item 1</Box>
        <Box sx={{ p: 2, bgcolor: 'secondary.main', color: 'white' }}>Item 2</Box>
        <Box sx={{ p: 2, bgcolor: 'success.main', color: 'white' }}>Item 3</Box>
      </Inline>
    </Box>
    <Typography variant="h6" gutterBottom>Gap with CSS string (1rem)</Typography>
    <Inline gap="1rem">
      <Box sx={{ p: 2, bgcolor: 'primary.main', color: 'white' }}>Item 1</Box>
      <Box sx={{ p: 2, bgcolor: 'secondary.main', color: 'white' }}>Item 2</Box>
      <Box sx={{ p: 2, bgcolor: 'success.main', color: 'white' }}>Item 3</Box>
    </Inline>
  </Box>
);

export const WithGap: Story = {
  render: WithGapExample,
  parameters: {
    docs: {
      source: {
        code: `<Inline gap={1}>
  <Box sx={{ p: 2, bgcolor: 'primary.main', color: 'white' }}>Item 1</Box>
  <Box sx={{ p: 2, bgcolor: 'secondary.main', color: 'white' }}>Item 2</Box>
  <Box sx={{ p: 2, bgcolor: 'success.main', color: 'white' }}>Item 3</Box>
</Inline>

<Inline gap={4}>
  <Box sx={{ p: 2, bgcolor: 'primary.main', color: 'white' }}>Item 1</Box>
  <Box sx={{ p: 2, bgcolor: 'secondary.main', color: 'white' }}>Item 2</Box>
  <Box sx={{ p: 2, bgcolor: 'success.main', color: 'white' }}>Item 3</Box>
</Inline>

<Inline gap="1rem">
  <Box sx={{ p: 2, bgcolor: 'primary.main', color: 'white' }}>Item 1</Box>
  <Box sx={{ p: 2, bgcolor: 'secondary.main', color: 'white' }}>Item 2</Box>
  <Box sx={{ p: 2, bgcolor: 'success.main', color: 'white' }}>Item 3</Box>
</Inline>`,
      },
    },
  },
};

const AlignmentExample = () => (
  <Box>
    <Typography variant="h6" gutterBottom>Align: Start (default)</Typography>
    <Box sx={{ width: '100%', bgcolor: 'grey.100', p: 2, mb: 4, minHeight: 150 }}>
      <Inline gap={2} align="start" style={{ height: '100%' }}>
        <Box sx={{ p: 2, bgcolor: 'primary.main', color: 'white', height: 60 }}>Item 1</Box>
        <Box sx={{ p: 2, bgcolor: 'secondary.main', color: 'white', height: 100 }}>Item 2</Box>
      </Inline>
    </Box>
    <Typography variant="h6" gutterBottom>Align: Center</Typography>
    <Box sx={{ width: '100%', bgcolor: 'grey.100', p: 2, mb: 4, minHeight: 150 }}>
      <Inline gap={2} align="center" style={{ height: '100%' }}>
        <Box sx={{ p: 2, bgcolor: 'primary.main', color: 'white', height: 60 }}>Item 1</Box>
        <Box sx={{ p: 2, bgcolor: 'secondary.main', color: 'white', height: 100 }}>Item 2</Box>
      </Inline>
    </Box>
    <Typography variant="h6" gutterBottom>Align: End</Typography>
    <Box sx={{ width: '100%', bgcolor: 'grey.100', p: 2, mb: 4, minHeight: 150 }}>
      <Inline gap={2} align="end" style={{ height: '100%' }}>
        <Box sx={{ p: 2, bgcolor: 'primary.main', color: 'white', height: 60 }}>Item 1</Box>
        <Box sx={{ p: 2, bgcolor: 'secondary.main', color: 'white', height: 100 }}>Item 2</Box>
      </Inline>
    </Box>
    <Typography variant="h6" gutterBottom>Align: Stretch</Typography>
    <Box sx={{ width: '100%', bgcolor: 'grey.100', p: 2, minHeight: 150 }}>
      <Inline gap={2} align="stretch" style={{ height: '100%' }}>
        <Box sx={{ p: 2, bgcolor: 'primary.main', color: 'white' }}>Item 1</Box>
        <Box sx={{ p: 2, bgcolor: 'secondary.main', color: 'white' }}>Item 2</Box>
      </Inline>
    </Box>
  </Box>
);

export const Alignment: Story = {
  render: AlignmentExample,
  parameters: {
    docs: {
      source: {
        code: `<Inline gap={2} align="start">
  <Box sx={{ p: 2, bgcolor: 'primary.main', color: 'white', height: 60 }}>Item 1</Box>
  <Box sx={{ p: 2, bgcolor: 'secondary.main', color: 'white', height: 100 }}>Item 2</Box>
</Inline>

<Inline gap={2} align="center">
  <Box sx={{ p: 2, bgcolor: 'primary.main', color: 'white', height: 60 }}>Item 1</Box>
  <Box sx={{ p: 2, bgcolor: 'secondary.main', color: 'white', height: 100 }}>Item 2</Box>
</Inline>

<Inline gap={2} align="end">
  <Box sx={{ p: 2, bgcolor: 'primary.main', color: 'white', height: 60 }}>Item 1</Box>
  <Box sx={{ p: 2, bgcolor: 'secondary.main', color: 'white', height: 100 }}>Item 2</Box>
</Inline>

<Inline gap={2} align="stretch">
  <Box sx={{ p: 2, bgcolor: 'primary.main', color: 'white' }}>Item 1</Box>
  <Box sx={{ p: 2, bgcolor: 'secondary.main', color: 'white' }}>Item 2</Box>
</Inline>`,
      },
    },
  },
};

const JustificationExample = () => (
  <Box>
    <Typography variant="h6" gutterBottom>Justify: Start (default)</Typography>
    <Box sx={{ width: '100%', bgcolor: 'grey.100', p: 2, mb: 4 }}>
      <Inline justify="start">
        <Box sx={{ p: 2, bgcolor: 'primary.main', color: 'white', width: 100 }}>Item 1</Box>
        <Box sx={{ p: 2, bgcolor: 'secondary.main', color: 'white', width: 100 }}>Item 2</Box>
        <Box sx={{ p: 2, bgcolor: 'success.main', color: 'white', width: 100 }}>Item 3</Box>
      </Inline>
    </Box>
    <Typography variant="h6" gutterBottom>Justify: Center</Typography>
    <Box sx={{ width: '100%', bgcolor: 'grey.100', p: 2, mb: 4 }}>
      <Inline justify="center">
        <Box sx={{ p: 2, bgcolor: 'primary.main', color: 'white', width: 100 }}>Item 1</Box>
        <Box sx={{ p: 2, bgcolor: 'secondary.main', color: 'white', width: 100 }}>Item 2</Box>
        <Box sx={{ p: 2, bgcolor: 'success.main', color: 'white', width: 100 }}>Item 3</Box>
      </Inline>
    </Box>
    <Typography variant="h6" gutterBottom>Justify: End</Typography>
    <Box sx={{ width: '100%', bgcolor: 'grey.100', p: 2, mb: 4 }}>
      <Inline justify="end">
        <Box sx={{ p: 2, bgcolor: 'primary.main', color: 'white', width: 100 }}>Item 1</Box>
        <Box sx={{ p: 2, bgcolor: 'secondary.main', color: 'white', width: 100 }}>Item 2</Box>
        <Box sx={{ p: 2, bgcolor: 'success.main', color: 'white', width: 100 }}>Item 3</Box>
      </Inline>
    </Box>
    <Typography variant="h6" gutterBottom>Justify: Between</Typography>
    <Box sx={{ width: '100%', bgcolor: 'grey.100', p: 2, mb: 4 }}>
      <Inline justify="between">
        <Box sx={{ p: 2, bgcolor: 'primary.main', color: 'white', width: 100 }}>Item 1</Box>
        <Box sx={{ p: 2, bgcolor: 'secondary.main', color: 'white', width: 100 }}>Item 2</Box>
        <Box sx={{ p: 2, bgcolor: 'success.main', color: 'white', width: 100 }}>Item 3</Box>
      </Inline>
    </Box>
    <Typography variant="h6" gutterBottom>Justify: Around</Typography>
    <Box sx={{ width: '100%', bgcolor: 'grey.100', p: 2, mb: 4 }}>
      <Inline justify="around">
        <Box sx={{ p: 2, bgcolor: 'primary.main', color: 'white', width: 100 }}>Item 1</Box>
        <Box sx={{ p: 2, bgcolor: 'secondary.main', color: 'white', width: 100 }}>Item 2</Box>
        <Box sx={{ p: 2, bgcolor: 'success.main', color: 'white', width: 100 }}>Item 3</Box>
      </Inline>
    </Box>
    <Typography variant="h6" gutterBottom>Justify: Evenly</Typography>
    <Box sx={{ width: '100%', bgcolor: 'grey.100', p: 2 }}>
      <Inline justify="evenly">
        <Box sx={{ p: 2, bgcolor: 'primary.main', color: 'white', width: 100 }}>Item 1</Box>
        <Box sx={{ p: 2, bgcolor: 'secondary.main', color: 'white', width: 100 }}>Item 2</Box>
        <Box sx={{ p: 2, bgcolor: 'success.main', color: 'white', width: 100 }}>Item 3</Box>
      </Inline>
    </Box>
  </Box>
);

export const Justification: Story = {
  render: JustificationExample,
  parameters: {
    docs: {
      source: {
        code: `<Inline justify="start">
  <Box sx={{ p: 2, bgcolor: 'primary.main', color: 'white', width: 100 }}>Item 1</Box>
  <Box sx={{ p: 2, bgcolor: 'secondary.main', color: 'white', width: 100 }}>Item 2</Box>
  <Box sx={{ p: 2, bgcolor: 'success.main', color: 'white', width: 100 }}>Item 3</Box>
</Inline>

<Inline justify="center">
  <Box sx={{ p: 2, bgcolor: 'primary.main', color: 'white', width: 100 }}>Item 1</Box>
  <Box sx={{ p: 2, bgcolor: 'secondary.main', color: 'white', width: 100 }}>Item 2</Box>
  <Box sx={{ p: 2, bgcolor: 'success.main', color: 'white', width: 100 }}>Item 3</Box>
</Inline>

<Inline justify="end">
  <Box sx={{ p: 2, bgcolor: 'primary.main', color: 'white', width: 100 }}>Item 1</Box>
  <Box sx={{ p: 2, bgcolor: 'secondary.main', color: 'white', width: 100 }}>Item 2</Box>
  <Box sx={{ p: 2, bgcolor: 'success.main', color: 'white', width: 100 }}>Item 3</Box>
</Inline>

<Inline justify="between">
  <Box sx={{ p: 2, bgcolor: 'primary.main', color: 'white', width: 100 }}>Item 1</Box>
  <Box sx={{ p: 2, bgcolor: 'secondary.main', color: 'white', width: 100 }}>Item 2</Box>
  <Box sx={{ p: 2, bgcolor: 'success.main', color: 'white', width: 100 }}>Item 3</Box>
</Inline>

<Inline justify="around">
  <Box sx={{ p: 2, bgcolor: 'primary.main', color: 'white', width: 100 }}>Item 1</Box>
  <Box sx={{ p: 2, bgcolor: 'secondary.main', color: 'white', width: 100 }}>Item 2</Box>
  <Box sx={{ p: 2, bgcolor: 'success.main', color: 'white', width: 100 }}>Item 3</Box>
</Inline>

<Inline justify="evenly">
  <Box sx={{ p: 2, bgcolor: 'primary.main', color: 'white', width: 100 }}>Item 1</Box>
  <Box sx={{ p: 2, bgcolor: 'secondary.main', color: 'white', width: 100 }}>Item 2</Box>
  <Box sx={{ p: 2, bgcolor: 'success.main', color: 'white', width: 100 }}>Item 3</Box>
</Inline>`,
      },
    },
  },
};

const WrapExample = () => (
  <Box>
    <Typography variant="h6" gutterBottom>Wrap: true (default) - Items wrap to new lines</Typography>
    <Box sx={{ width: '100%', maxWidth: 600, bgcolor: 'grey.100', p: 2, mb: 4 }}>
      <Inline gap={2} wrap={true}>
        <Box sx={{ p: 2, bgcolor: 'primary.main', color: 'white', minWidth: 200 }}>Item 1</Box>
        <Box sx={{ p: 2, bgcolor: 'secondary.main', color: 'white', minWidth: 200 }}>Item 2</Box>
        <Box sx={{ p: 2, bgcolor: 'success.main', color: 'white', minWidth: 200 }}>Item 3</Box>
        <Box sx={{ p: 2, bgcolor: 'warning.main', color: 'white', minWidth: 200 }}>Item 4</Box>
        <Box sx={{ p: 2, bgcolor: 'error.main', color: 'white', minWidth: 200 }}>Item 5</Box>
      </Inline>
    </Box>
    <Typography variant="h6" gutterBottom>Wrap: false - Items stay on one line (may overflow)</Typography>
    <Box sx={{ width: '100%', maxWidth: 600, bgcolor: 'grey.100', p: 2, overflow: 'auto' }}>
      <Inline gap={2} wrap={false}>
        <Box sx={{ p: 2, bgcolor: 'primary.main', color: 'white', minWidth: 200 }}>Item 1</Box>
        <Box sx={{ p: 2, bgcolor: 'secondary.main', color: 'white', minWidth: 200 }}>Item 2</Box>
        <Box sx={{ p: 2, bgcolor: 'success.main', color: 'white', minWidth: 200 }}>Item 3</Box>
        <Box sx={{ p: 2, bgcolor: 'warning.main', color: 'white', minWidth: 200 }}>Item 4</Box>
        <Box sx={{ p: 2, bgcolor: 'error.main', color: 'white', minWidth: 200 }}>Item 5</Box>
      </Inline>
    </Box>
  </Box>
);

export const Wrap: Story = {
  render: WrapExample,
  parameters: {
    docs: {
      source: {
        code: `<Inline gap={2} wrap={true}>
  <Box sx={{ p: 2, bgcolor: 'primary.main', color: 'white', minWidth: 200 }}>Item 1</Box>
  <Box sx={{ p: 2, bgcolor: 'secondary.main', color: 'white', minWidth: 200 }}>Item 2</Box>
  <Box sx={{ p: 2, bgcolor: 'success.main', color: 'white', minWidth: 200 }}>Item 3</Box>
  <Box sx={{ p: 2, bgcolor: 'warning.main', color: 'white', minWidth: 200 }}>Item 4</Box>
  <Box sx={{ p: 2, bgcolor: 'error.main', color: 'white', minWidth: 200 }}>Item 5</Box>
</Inline>

<Inline gap={2} wrap={false}>
  <Box sx={{ p: 2, bgcolor: 'primary.main', color: 'white', minWidth: 200 }}>Item 1</Box>
  <Box sx={{ p: 2, bgcolor: 'secondary.main', color: 'white', minWidth: 200 }}>Item 2</Box>
  <Box sx={{ p: 2, bgcolor: 'success.main', color: 'white', minWidth: 200 }}>Item 3</Box>
  <Box sx={{ p: 2, bgcolor: 'warning.main', color: 'white', minWidth: 200 }}>Item 4</Box>
  <Box sx={{ p: 2, bgcolor: 'error.main', color: 'white', minWidth: 200 }}>Item 5</Box>
</Inline>

Note: The container has maxWidth: 600px to demonstrate wrapping behavior.`,
      },
    },
  },
};

const CombinedExample = () => (
  <Box>
    <Typography variant="h6" gutterBottom>Centered (align + justify)</Typography>
    <Box sx={{ width: '100%', bgcolor: 'grey.100', p: 2, mb: 4, minHeight: 150 }}>
      <Inline gap={2} align="center" justify="center" style={{ height: '100%' }}>
        <Box sx={{ p: 2, bgcolor: 'primary.main', color: 'white', width: 100, height: 60 }}>Item 1</Box>
        <Box sx={{ p: 2, bgcolor: 'secondary.main', color: 'white', width: 100, height: 60 }}>Item 2</Box>
      </Inline>
    </Box>
    <Typography variant="h6" gutterBottom>Space Between with Center Alignment</Typography>
    <Box sx={{ width: '100%', bgcolor: 'grey.100', p: 2, minHeight: 150 }}>
      <Inline gap={2} align="center" justify="between" style={{ height: '100%' }}>
        <Box sx={{ p: 2, bgcolor: 'primary.main', color: 'white', width: 100, height: 60 }}>Item 1</Box>
        <Box sx={{ p: 2, bgcolor: 'secondary.main', color: 'white', width: 100, height: 60 }}>Item 2</Box>
      </Inline>
    </Box>
  </Box>
);

export const Combined: Story = {
  render: CombinedExample,
  parameters: {
    docs: {
      source: {
        code: `<Inline gap={2} align="center" justify="center">
  <Box sx={{ p: 2, bgcolor: 'primary.main', color: 'white', width: 100 }}>Item 1</Box>
  <Box sx={{ p: 2, bgcolor: 'secondary.main', color: 'white', width: 100 }}>Item 2</Box>
</Inline>

<Inline gap={2} align="center" justify="between">
  <Box sx={{ p: 2, bgcolor: 'primary.main', color: 'white', width: 100 }}>Item 1</Box>
  <Box sx={{ p: 2, bgcolor: 'secondary.main', color: 'white', width: 100 }}>Item 2</Box>
</Inline>`,
      },
    },
  },
};

const RealWorldExampleComponent = () => (
  <Box>
    <Typography variant="h6" gutterBottom sx={{ mb: 2 }}>Button Group</Typography>
    <Box sx={{ mb: 4 }}>
      <Inline gap={2}>
        <Button variant="outlined">Cancel</Button>
        <Button variant="contained">Save</Button>
        <Button variant="text">More Options</Button>
      </Inline>
    </Box>

    <Typography variant="h6" gutterBottom sx={{ mb: 2 }}>Card Actions</Typography>
    <Box sx={{ p: 3, bgcolor: 'white', borderRadius: 2, boxShadow: 1, mb: 4 }}>
      <Typography variant="h6" gutterBottom>Card Title</Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        This card demonstrates how Inline can be used for action buttons and other horizontal layouts.
      </Typography>
      <Inline gap={1} justify="end">
        <Button size="small" variant="outlined">Cancel</Button>
        <Button size="small" variant="contained">Save</Button>
      </Inline>
    </Box>

    <Typography variant="h6" gutterBottom sx={{ mb: 2 }}>Tag List</Typography>
    <Inline gap={1} wrap>
      <Box sx={{ px: 2, py: 0.5, bgcolor: 'primary.main', color: 'white', borderRadius: 1, fontSize: '0.875rem' }}>
        React
      </Box>
      <Box sx={{ px: 2, py: 0.5, bgcolor: 'secondary.main', color: 'white', borderRadius: 1, fontSize: '0.875rem' }}>
        TypeScript
      </Box>
      <Box sx={{ px: 2, py: 0.5, bgcolor: 'success.main', color: 'white', borderRadius: 1, fontSize: '0.875rem' }}>
        Storybook
      </Box>
      <Box sx={{ px: 2, py: 0.5, bgcolor: 'warning.main', color: 'white', borderRadius: 1, fontSize: '0.875rem' }}>
        UI Components
      </Box>
    </Inline>
  </Box>
);

export const RealWorldExample: Story = {
  render: RealWorldExampleComponent,
  parameters: {
    docs: {
      source: {
        code: `<Inline gap={2}>
  <Button variant="outlined">Cancel</Button>
  <Button variant="contained">Save</Button>
  <Button variant="text">More Options</Button>
</Inline>

<Inline gap={1} justify="end">
  <Button size="small" variant="outlined">Cancel</Button>
  <Button size="small" variant="contained">Save</Button>
</Inline>

<Inline gap={1} wrap>
  <Box sx={{ px: 2, py: 0.5, bgcolor: 'primary.main', color: 'white', borderRadius: 1 }}>
    React
  </Box>
  <Box sx={{ px: 2, py: 0.5, bgcolor: 'secondary.main', color: 'white', borderRadius: 1 }}>
    TypeScript
  </Box>
</Inline>`,
      },
    },
  },
};

const AsDifferentElementExample = () => (
  <Box>
    <Typography variant="h6" gutterBottom sx={{ mb: 2 }}>Inline as nav element</Typography>
    <Box sx={{ border: '1px solid', borderColor: 'divider', p: 2, borderRadius: 1 }}>
      <Inline as="nav" gap={2}>
        <Typography variant="body1" component="a" href="#" sx={{ textDecoration: 'none', color: 'primary.main' }}>
          Home
        </Typography>
        <Typography variant="body1" component="a" href="#" sx={{ textDecoration: 'none', color: 'primary.main' }}>
          About
        </Typography>
        <Typography variant="body1" component="a" href="#" sx={{ textDecoration: 'none', color: 'primary.main' }}>
          Contact
        </Typography>
      </Inline>
    </Box>
    <Typography variant="body2" color="text.secondary" sx={{ mt: 2 }}>
      Use the `as` prop to change the HTML element type for semantic HTML.
    </Typography>
  </Box>
);

export const AsDifferentElement: Story = {
  render: AsDifferentElementExample,
  parameters: {
    docs: {
      source: {
        code: `<Inline as="nav" gap={2}>
  <Typography variant="body1" component="a" href="#">Home</Typography>
  <Typography variant="body1" component="a" href="#">About</Typography>
  <Typography variant="body1" component="a" href="#">Contact</Typography>
</Inline>`,
      },
    },
  },
};

