"use client";

import React from "react";
import {
  Box,
  Typography,
  Container,
  Paper,
  Button,
  Stack,
  List,
  ListItem,
  ListItemText,
  Chip,
  Divider,
} from "@mui/material";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import Link from "next/link";

export default function ComponentsPage() {
  return (
    <Container maxWidth="lg" sx={{ py: 4 }}>
      <Box sx={{ mb: 4 }}>
        <Button
          component={Link}
          href="/documentation"
          startIcon={<ArrowBackIcon />}
          sx={{ mb: 3 }}
        >
          Back to Documentation
        </Button>
        <Typography variant="h3" component="h1" gutterBottom>
          Components
        </Typography>
        <Typography variant="h6" color="text.secondary" sx={{ maxWidth: 800 }}>
          Complete component library with examples, props, and usage guidelines.
        </Typography>
      </Box>

      <Stack spacing={4}>
        {/* Overview */}
        <Paper sx={{ p: 4 }}>
          <Typography variant="h4" component="h2" gutterBottom>
            Component Library Overview
          </Typography>
          <Typography variant="body1" paragraph>
            The shared components library provides a comprehensive set of reusable UI components
            built on top of Material-UI, organized by functional purpose. This system ensures
            consistency, maintainability, and scalability across the entire application.
          </Typography>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Key Features
          </Typography>
          <Stack direction="row" spacing={1} flexWrap="wrap" sx={{ mb: 2 }}>
            <Chip label="TypeScript First" />
            <Chip label="Material-UI Based" />
            <Chip label="Storybook Integration" />
            <Chip label="Accessibility Compliant" />
            <Chip label="Themeable" />
            <Chip label="Comprehensive Testing" />
          </Stack>
        </Paper>

        {/* Component Categories */}
        <Paper sx={{ p: 4 }}>
          <Typography variant="h4" component="h2" gutterBottom>
            Component Categories
          </Typography>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Primitives
          </Typography>
          <Typography variant="body2" color="text.secondary" paragraph>
            Basic UI building blocks that cannot be broken down further without losing their meaning.
          </Typography>
          <List>
            <ListItem>
              <ListItemText
                primary="Button"
                secondary="Interactive buttons with loading states and variants"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="TextField"
                secondary="Text input fields with validation and formatting"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="Select"
                secondary="Dropdown selection components"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="Avatar"
                secondary="User profile pictures and icons"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="Badge"
                secondary="Status indicators and labels"
              />
            </ListItem>
          </List>

          <Divider sx={{ my: 3 }} />

          <Typography variant="h6" sx={{ mb: 2 }}>
            Layout Components
          </Typography>
          <Typography variant="body2" color="text.secondary" paragraph>
            Components for structuring and organizing content.
          </Typography>
          <List>
            <ListItem>
              <ListItemText
                primary="Stack"
                secondary="Flexible layout container with consistent spacing"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="Paper"
                secondary="Elevated surfaces and containers with shadows"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="PageLayout"
                secondary="Complete page layout wrapper"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="Grid"
                secondary="CSS Grid layout component"
              />
            </ListItem>
          </List>

          <Divider sx={{ my: 3 }} />

          <Typography variant="h6" sx={{ mb: 2 }}>
            Form Components
          </Typography>
          <Typography variant="body2" color="text.secondary" paragraph>
            Comprehensive form components with validation and formatting.
          </Typography>
          <List>
            <ListItem>
              <ListItemText
                primary="TextField"
                secondary="Text input with validation"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="CurrencyField"
                secondary="Currency input with formatting"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="DatePicker"
                secondary="Date selection component"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="SelectField"
                secondary="Dropdown selection with validation"
              />
            </ListItem>
          </List>

          <Divider sx={{ my: 3 }} />

          <Typography variant="h6" sx={{ mb: 2 }}>
            Data Display
          </Typography>
          <Typography variant="body2" color="text.secondary" paragraph>
            Components for displaying data and visualizations.
          </Typography>
          <List>
            <ListItem>
              <ListItemText
                primary="DataTable"
                secondary="Advanced data tables with pagination, sorting, and filtering"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="Chart"
                secondary="Data visualization charts"
              />
            </ListItem>
          </List>

          <Divider sx={{ my: 3 }} />

          <Typography variant="h6" sx={{ mb: 2 }}>
            Feedback Components
          </Typography>
          <Typography variant="body2" color="text.secondary" paragraph>
            Components for providing user feedback and notifications.
          </Typography>
          <List>
            <ListItem>
              <ListItemText
                primary="ToastProvider"
                secondary="Toast notification system"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="ConfirmDialog"
                secondary="Confirmation dialogs"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="Tooltip"
                secondary="Contextual information tooltips"
              />
            </ListItem>
          </List>
        </Paper>

        {/* Usage Guidelines */}
        <Paper sx={{ p: 4 }}>
          <Typography variant="h4" component="h2" gutterBottom>
            Usage Guidelines
          </Typography>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Import Strategy
          </Typography>
          <Typography variant="body2" paragraph>
            Preferred: Import from the main index file
          </Typography>
          <Box
            component="pre"
            sx={{
              bgcolor: "background.default",
              p: 2,
              borderRadius: 1,
              overflow: "auto",
              mb: 2,
            }}
          >
            <code>{`import { Button, TextField, DataTable } from '@/shared/components/ui';`}</code>
          </Box>

          <Typography variant="body2" paragraph>
            Alternative: Import from specific categories
          </Typography>
          <Box
            component="pre"
            sx={{
              bgcolor: "background.default",
              p: 2,
              borderRadius: 1,
              overflow: "auto",
            }}
          >
            <code>{`import { Button } from '@/shared/components/ui/primitives';
import { ConfirmDialog } from '@/shared/components/ui/feedback';
import { DataTable } from '@/shared/components/ui/data-display';`}</code>
          </Box>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Example Usage
          </Typography>
          <Box
            component="pre"
            sx={{
              bgcolor: "background.default",
              p: 2,
              borderRadius: 1,
              overflow: "auto",
            }}
          >
            <code>{`import { Button, TextField, Stack } from '@/shared/components/ui';

function MyForm() {
  return (
    <Stack spacing={2}>
      <TextField
        name="email"
        label="Email"
        required
      />
      <Button
        variant="contained"
        loading={isLoading}
        onClick={handleSubmit}
      >
        Submit
      </Button>
    </Stack>
  );
}`}</code>
          </Box>
        </Paper>

        {/* Testing */}
        <Paper sx={{ p: 4 }}>
          <Typography variant="h4" component="h2" gutterBottom>
            Testing with data-testid
          </Typography>
          <Typography variant="body1" paragraph>
            All framework components support automatic <code>data-testid</code> generation through
            an optional <code>name</code> prop, making testing more maintainable and predictable.
          </Typography>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Usage Pattern
          </Typography>
          <Box
            component="pre"
            sx={{
              bgcolor: "background.default",
              p: 2,
              borderRadius: 1,
              overflow: "auto",
            }}
          >
            <code>{`// Without name: data-testid="button"
<Button>Click me</Button>

// With name: data-testid="button-submit"
<Button name="submit">Submit</Button>

// In tests
expect(screen.getByTestId('button-submit')).toBeInTheDocument();`}</code>
          </Box>
        </Paper>

        {/* Resources */}
        <Paper sx={{ p: 4, bgcolor: "primary.main", color: "primary.contrastText" }}>
          <Typography variant="h4" component="h2" gutterBottom>
            Explore Components
          </Typography>
          <Typography variant="body1" paragraph>
            For detailed component documentation with examples and interactive demos:
          </Typography>
          <Stack spacing={2} sx={{ mt: 2 }}>
            <Button
              component={Link}
              href="/design-system/components"
              variant="contained"
              sx={{ bgcolor: "background.paper", color: "text.primary" }}
            >
              Browse Component Library
            </Button>
            <Button
              component={Link}
              href="/design-system"
              variant="outlined"
              sx={{ borderColor: "background.paper", color: "background.paper" }}
            >
              Design System Overview
            </Button>
          </Stack>
        </Paper>
      </Stack>
    </Container>
  );
}

