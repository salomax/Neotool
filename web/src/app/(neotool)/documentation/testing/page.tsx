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
  Alert,
  Chip,
} from "@mui/material";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import Link from "next/link";

export default function TestingPage() {
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
          Testing
        </Typography>
        <Typography variant="h6" color="text.secondary" sx={{ maxWidth: 800 }}>
          Testing strategies and best practices for ensuring quality and reliability.
        </Typography>
      </Box>

      <Stack spacing={4}>
        {/* Overview */}
        <Paper sx={{ p: 4 }}>
          <Typography variant="h4" component="h2" gutterBottom>
            Testing Overview
          </Typography>
          <Typography variant="body1" paragraph>
            Comprehensive testing ensures your application works correctly, handles edge cases, and
            maintains quality as it evolves. This guide covers different testing strategies and tools.
          </Typography>
          <Alert severity="info" sx={{ mt: 2 }}>
            The project uses Vitest for unit testing, React Testing Library for component testing,
            and Playwright for end-to-end testing.
          </Alert>
        </Paper>

        {/* Testing Setup */}
        <Paper sx={{ p: 4 }}>
          <Typography variant="h4" component="h2" gutterBottom>
            Testing Setup
          </Typography>
          <Typography variant="body1" paragraph>
            The project is configured with the following testing tools:
          </Typography>

          <List>
            <ListItem>
              <ListItemText
                primary="Vitest"
                secondary="Fast unit test framework with Vite integration"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="React Testing Library"
                secondary="Simple and complete testing utilities for React components"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="Playwright"
                secondary="End-to-end testing framework for modern web apps"
              />
            </ListItem>
          </List>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Running Tests
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
            <code>{`# Run all tests
npm test

# Run tests in watch mode
npm test -- --watch

# Run tests with coverage
npm test -- --coverage

# Run specific test file
npm test -- Button.test.tsx

# Run E2E tests
npm run test:e2e`}</code>
          </Box>
        </Paper>

        {/* Unit Testing */}
        <Paper sx={{ p: 4 }}>
          <Typography variant="h4" component="h2" gutterBottom>
            Unit Testing
          </Typography>
          <Typography variant="body1" paragraph>
            Unit tests verify that individual functions and components work correctly in isolation.
          </Typography>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Example: Testing a Utility Function
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
            <code>{`// utils/formatCurrency.ts
export function formatCurrency(amount: number, currency = 'USD'): string {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency,
  }).format(amount);
}

// utils/formatCurrency.test.ts
import { describe, it, expect } from 'vitest';
import { formatCurrency } from './formatCurrency';

describe('formatCurrency', () => {
  it('formats USD currency correctly', () => {
    expect(formatCurrency(1000)).toBe('$1,000.00');
  });
  
  it('formats EUR currency correctly', () => {
    expect(formatCurrency(1000, 'EUR')).toBe('€1,000.00');
  });
  
  it('handles decimal amounts', () => {
    expect(formatCurrency(1234.56)).toBe('$1,234.56');
  });
});`}</code>
          </Box>
        </Paper>

        {/* Component Testing */}
        <Paper sx={{ p: 4 }}>
          <Typography variant="h4" component="h2" gutterBottom>
            Component Testing
          </Typography>
          <Typography variant="body1" paragraph>
            Test React components to ensure they render correctly and handle user interactions.
          </Typography>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Example: Testing a Component
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
            <code>{`import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { Button } from './Button';

describe('Button', () => {
  it('renders button with text', () => {
    render(<Button>Click me</Button>);
    expect(screen.getByText('Click me')).toBeInTheDocument();
  });
  
  it('calls onClick when clicked', () => {
    const handleClick = vi.fn();
    render(<Button onClick={handleClick}>Click me</Button>);
    
    fireEvent.click(screen.getByText('Click me'));
    expect(handleClick).toHaveBeenCalledTimes(1);
  });
  
  it('shows loading state', () => {
    render(<Button loading>Submit</Button>);
    expect(screen.getByRole('button')).toBeDisabled();
  });
});`}</code>
          </Box>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Testing with data-testid
          </Typography>
          <Typography variant="body2" paragraph>
            All components support automatic <code>data-testid</code> generation via the <code>name</code> prop:
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
            <code>{`<Button name="submit-button">Submit</Button>
// Generates: data-testid="button-submit-button"

// In tests
expect(screen.getByTestId('button-submit-button')).toBeInTheDocument();`}</code>
          </Box>
        </Paper>

        {/* Integration Testing */}
        <Paper sx={{ p: 4 }}>
          <Typography variant="h4" component="h2" gutterBottom>
            Integration Testing
          </Typography>
          <Typography variant="body1" paragraph>
            Integration tests verify that multiple components work together correctly.
          </Typography>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Example: Testing a Form Flow
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
            <code>{`import { describe, it, expect } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { LoginForm } from './LoginForm';

describe('LoginForm Integration', () => {
  it('submits form with valid data', async () => {
    const handleSubmit = vi.fn();
    render(<LoginForm onSubmit={handleSubmit} />);
    
    fireEvent.change(screen.getByLabelText('Email'), {
      target: { value: 'user@example.com' },
    });
    fireEvent.change(screen.getByLabelText('Password'), {
      target: { value: 'password123' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Submit' }));
    
    await waitFor(() => {
      expect(handleSubmit).toHaveBeenCalledWith({
        email: 'user@example.com',
        password: 'password123',
      });
    });
  });
});`}</code>
          </Box>
        </Paper>

        {/* E2E Testing */}
        <Paper sx={{ p: 4 }}>
          <Typography variant="h4" component="h2" gutterBottom>
            End-to-End Testing
          </Typography>
          <Typography variant="body1" paragraph>
            E2E tests verify that the entire application works correctly from the user's perspective.
          </Typography>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Playwright Example
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
            <code>{`import { test, expect } from '@playwright/test';

test('user can complete login flow', async ({ page }) => {
  await page.goto('/login');
  
  await page.fill('[data-testid="textfield-email"]', 'user@example.com');
  await page.fill('[data-testid="textfield-password"]', 'password123');
  await page.click('[data-testid="button-submit"]');
  
  await expect(page).toHaveURL('/dashboard');
  await expect(page.locator('h1')).toContainText('Dashboard');
});`}</code>
          </Box>
        </Paper>

        {/* Best Practices */}
        <Paper sx={{ p: 4 }}>
          <Typography variant="h4" component="h2" gutterBottom>
            Testing Best Practices
          </Typography>
          <List>
            <ListItem>
              <ListItemText
                primary="Test User Behavior"
                secondary="Test what users see and do, not implementation details"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="Write Descriptive Tests"
                secondary="Test names should clearly describe what is being tested"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="Keep Tests Independent"
                secondary="Each test should be able to run independently"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="Test Edge Cases"
                secondary="Test error states, empty states, and boundary conditions"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="Maintain Test Coverage"
                secondary="Aim for meaningful coverage, not just high percentages"
              />
            </ListItem>
          </List>
        </Paper>

        {/* Resources */}
        <Paper sx={{ p: 4, bgcolor: "primary.main", color: "primary.contrastText" }}>
          <Typography variant="h4" component="h2" gutterBottom>
            Additional Resources
          </Typography>
          <Stack spacing={2} sx={{ mt: 2 }}>
            <Link
              href="https://testing-library.com/docs/react-testing-library/intro/"
              target="_blank"
              rel="noopener noreferrer"
              style={{ color: "inherit", textDecoration: "underline" }}
            >
              React Testing Library Documentation
            </Link>
            <Link
              href="https://vitest.dev/"
              target="_blank"
              rel="noopener noreferrer"
              style={{ color: "inherit", textDecoration: "underline" }}
            >
              Vitest Documentation
            </Link>
            <Link
              href="https://playwright.dev/"
              target="_blank"
              rel="noopener noreferrer"
              style={{ color: "inherit", textDecoration: "underline" }}
            >
              Playwright Documentation
            </Link>
          </Stack>
        </Paper>
      </Stack>
    </Container>
  );
}

