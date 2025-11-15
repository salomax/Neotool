import type { Meta, StoryObj } from '@storybook/react';
import Tabs, { TabItem } from '@/shared/components/ui/navigation/Tabs';
import { Stack, Box, Typography } from '@mui/material';
import { useState } from 'react';

const meta: Meta<typeof Tabs> = {
  title: 'Components/Navigation/Tabs',
  component: Tabs,
  parameters: {
    layout: 'padded',
    docs: {
      description: {
        component: 'Tabs component for organizing content into separate views.'
      }
    }
  },
  argTypes: {
    tabs: {
      description: 'Array of tab items'
    },
    defaultTab: {
      control: { type: 'text' },
      description: 'Default active tab ID'
    },
    variant: {
      control: { type: 'select' },
      options: ['standard', 'fullWidth', 'scrollable'],
      description: 'Variant of the tabs'
    },
    orientation: {
      control: { type: 'select' },
      options: ['horizontal', 'vertical'],
      description: 'Orientation of the tabs'
    }
  },
  tags: ['autodocs'],
};

export default meta;
type Story = StoryObj<typeof meta>;

const basicTabs: TabItem[] = [
  { id: 'tab1', label: 'Tab 1', content: 'Content for Tab 1' },
  { id: 'tab2', label: 'Tab 2', content: 'Content for Tab 2' },
  { id: 'tab3', label: 'Tab 3', content: 'Content for Tab 3' },
];

export const Default: Story = {
  render: () => <Tabs tabs={basicTabs} />,
};

export const Variants: Story = {
  render: () => (
    <Stack spacing={4}>
      <Box>
        <Typography variant="h6" gutterBottom>Standard</Typography>
        <Tabs tabs={basicTabs} variant="standard" />
      </Box>
      <Box>
        <Typography variant="h6" gutterBottom>Full Width</Typography>
        <Tabs tabs={basicTabs} variant="fullWidth" />
      </Box>
      <Box>
        <Typography variant="h6" gutterBottom>Scrollable</Typography>
        <Tabs 
          tabs={[
            { id: 'tab1', label: 'Very Long Tab Name 1', content: 'Content 1' },
            { id: 'tab2', label: 'Very Long Tab Name 2', content: 'Content 2' },
            { id: 'tab3', label: 'Very Long Tab Name 3', content: 'Content 3' },
            { id: 'tab4', label: 'Very Long Tab Name 4', content: 'Content 4' },
            { id: 'tab5', label: 'Very Long Tab Name 5', content: 'Content 5' },
          ]} 
          variant="scrollable" 
        />
      </Box>
    </Stack>
  ),
};

export const WithIcons: Story = {
  render: () => {
    const tabsWithIcons: TabItem[] = [
      { id: 'home', label: 'Home', icon: 'home', content: 'Home content' },
      { id: 'profile', label: 'Profile', icon: 'person', content: 'Profile content' },
      { id: 'settings', label: 'Settings', icon: 'settings', content: 'Settings content' },
    ];
    
    return <Tabs tabs={tabsWithIcons} />;
  },
};

export const Vertical: Story = {
  render: () => (
    <Box sx={{ height: 300 }}>
      <Tabs 
        tabs={basicTabs} 
        orientation="vertical"
        sx={{ height: '100%' }}
      />
    </Box>
  ),
};

export const WithComplexContent: Story = {
  render: () => {
    const complexTabs: TabItem[] = [
      { 
        id: 'overview', 
        label: 'Overview', 
        content: (
          <Box sx={{ p: 2 }}>
            <Typography variant="h6" gutterBottom>Overview</Typography>
            <Typography>This is the overview content with detailed information.</Typography>
          </Box>
        )
      },
      { 
        id: 'details', 
        label: 'Details', 
        content: (
          <Box sx={{ p: 2 }}>
            <Typography variant="h6" gutterBottom>Details</Typography>
            <Typography>Detailed information about the selected item.</Typography>
          </Box>
        )
      },
      { 
        id: 'settings', 
        label: 'Settings', 
        content: (
          <Box sx={{ p: 2 }}>
            <Typography variant="h6" gutterBottom>Settings</Typography>
            <Typography>Configuration options and preferences.</Typography>
          </Box>
        )
      },
    ];
    
    return <Tabs tabs={complexTabs} />;
  },
};

export const Controlled: Story = {
  render: () => {
    const [activeTab, setActiveTab] = useState('tab1');
    
    return (
      <Box>
        <Typography gutterBottom>Active Tab: {activeTab}</Typography>
        <Tabs 
          tabs={basicTabs} 
          value={activeTab}
          onChange={setActiveTab}
        />
      </Box>
    );
  },
};
