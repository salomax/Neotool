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
  Divider,
} from "@mui/material";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import CheckCircleIcon from "@mui/icons-material/CheckCircle";
import WarningIcon from "@mui/icons-material/Warning";
import Link from "next/link";

export default function AccessibilityPage() {
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
          Accessibility
        </Typography>
        <Typography variant="h6" color="text.secondary" sx={{ maxWidth: 800 }}>
          Guidelines and best practices for creating accessible web applications that work for everyone.
        </Typography>
      </Box>

      <Stack spacing={4}>
        {/* Overview */}
        <Paper sx={{ p: 4 }}>
          <Typography variant="h4" component="h2" gutterBottom>
            Accessibility Overview
          </Typography>
          <Typography variant="body1" paragraph>
            Accessibility (a11y) is about making your application usable by as many people as possible.
            This includes people with disabilities, people using assistive technologies, and people in
            different contexts or environments.
          </Typography>
          <Alert severity="info" sx={{ mt: 2 }}>
            All components in our design system are built with accessibility in mind, following WCAG 2.1
            Level AA guidelines.
          </Alert>
        </Paper>

        {/* WCAG Guidelines */}
        <Paper sx={{ p: 4 }}>
          <Typography variant="h4" component="h2" gutterBottom>
            WCAG Guidelines
          </Typography>
          <Typography variant="body1" paragraph>
            The Web Content Accessibility Guidelines (WCAG) provide a framework for making web content
            more accessible. We follow WCAG 2.1 Level AA standards.
          </Typography>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Four Principles of Accessibility (POUR)
          </Typography>
          <List>
            <ListItem>
              <ListItemText
                primary="Perceivable"
                secondary="Information and UI components must be presentable to users in ways they can perceive"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="Operable"
                secondary="UI components and navigation must be operable by all users"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="Understandable"
                secondary="Information and operation of UI must be understandable"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="Robust"
                secondary="Content must be robust enough to be interpreted by assistive technologies"
              />
            </ListItem>
          </List>
        </Paper>

        {/* Keyboard Navigation */}
        <Paper sx={{ p: 4 }}>
          <Typography variant="h4" component="h2" gutterBottom>
            Keyboard Navigation
          </Typography>
          <Typography variant="body1" paragraph>
            All interactive elements should be accessible via keyboard. This is essential for users who
            cannot use a mouse or pointing device.
          </Typography>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Best Practices
          </Typography>
          <Stack spacing={2}>
            <Box sx={{ display: "flex", gap: 2 }}>
              <CheckCircleIcon color="success" />
              <Box>
                <Typography variant="body1" fontWeight="bold">
                  Tab Order
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Ensure logical tab order through all interactive elements
                </Typography>
              </Box>
            </Box>
            <Box sx={{ display: "flex", gap: 2 }}>
              <CheckCircleIcon color="success" />
              <Box>
                <Typography variant="body1" fontWeight="bold">
                  Focus Indicators
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Always provide visible focus indicators for keyboard navigation
                </Typography>
              </Box>
            </Box>
            <Box sx={{ display: "flex", gap: 2 }}>
              <CheckCircleIcon color="success" />
              <Box>
                <Typography variant="body1" fontWeight="bold">
                  Keyboard Shortcuts
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Implement standard keyboard shortcuts (Enter, Space, Escape, Arrow keys)
                </Typography>
              </Box>
            </Box>
            <Box sx={{ display: "flex", gap: 2 }}>
              <CheckCircleIcon color="success" />
              <Box>
                <Typography variant="body1" fontWeight="bold">
                  Skip Links
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Provide skip links to bypass repetitive navigation
                </Typography>
              </Box>
            </Box>
          </Stack>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Example: Accessible Button
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
            <code>{`<Button
  onClick={handleClick}
  onKeyDown={(e) => {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      handleClick();
    }
  }}
  aria-label="Submit form"
>
  Submit
</Button>`}</code>
          </Box>
        </Paper>

        {/* Screen Reader Support */}
        <Paper sx={{ p: 4 }}>
          <Typography variant="h4" component="h2" gutterBottom>
            Screen Reader Support
          </Typography>
          <Typography variant="body1" paragraph>
            Screen readers convert text and UI elements into speech or braille. Proper ARIA attributes
            and semantic HTML are essential for screen reader users.
          </Typography>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            ARIA Attributes
          </Typography>
          <List>
            <ListItem>
              <ListItemText
                primary="aria-label"
                secondary="Provides an accessible name for an element"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="aria-describedby"
                secondary="References additional descriptive text"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="aria-labelledby"
                secondary="References element(s) that label the current element"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="aria-hidden"
                secondary="Hides decorative elements from screen readers"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="role"
                secondary="Defines the element's role in the accessibility tree"
              />
            </ListItem>
          </List>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Semantic HTML
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
            <code>{`// ✅ Good: Semantic HTML
<nav>
  <ul>
    <li><a href="/">Home</a></li>
    <li><a href="/about">About</a></li>
  </ul>
</nav>

// ❌ Bad: Non-semantic HTML
<div>
  <div onClick={handleClick}>Home</div>
  <div onClick={handleClick}>About</div>
</div>`}</code>
          </Box>
        </Paper>

        {/* Color Contrast */}
        <Paper sx={{ p: 4 }}>
          <Typography variant="h4" component="h2" gutterBottom>
            Color Contrast
          </Typography>
          <Typography variant="body1" paragraph>
            Ensure sufficient color contrast between text and backgrounds. WCAG requires a contrast
            ratio of at least 4.5:1 for normal text and 3:1 for large text.
          </Typography>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Contrast Requirements
          </Typography>
          <List>
            <ListItem>
              <ListItemText
                primary="Normal Text (≤18pt or ≤14pt bold)"
                secondary="Minimum contrast ratio: 4.5:1"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="Large Text (>18pt or >14pt bold)"
                secondary="Minimum contrast ratio: 3:1"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="Non-text Content (icons, UI components)"
                secondary="Minimum contrast ratio: 3:1"
              />
            </ListItem>
          </List>

          <Alert severity="warning" sx={{ mt: 2 }}>
            Never rely on color alone to convey information. Always provide text labels or icons
            alongside color indicators.
          </Alert>
        </Paper>

        {/* Focus Management */}
        <Paper sx={{ p: 4 }}>
          <Typography variant="h4" component="h2" gutterBottom>
            Focus Management
          </Typography>
          <Typography variant="body1" paragraph>
            Proper focus management ensures users can navigate your application efficiently and
            understand where they are at all times.
          </Typography>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Best Practices
          </Typography>
          <Stack spacing={2}>
            <Box>
              <Typography variant="body1" fontWeight="bold" gutterBottom>
                Focus Trapping
              </Typography>
              <Typography variant="body2" color="text.secondary">
                In modals and dialogs, trap focus within the component until it&apos;s closed
              </Typography>
            </Box>
            <Box>
              <Typography variant="body1" fontWeight="bold" gutterBottom>
                Focus Restoration
              </Typography>
              <Typography variant="body2" color="text.secondary">
                When closing modals, restore focus to the element that opened it
              </Typography>
            </Box>
            <Box>
              <Typography variant="body1" fontWeight="bold" gutterBottom>
                Focus Visibility
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Never remove focus indicators. Make them visible and clear
              </Typography>
            </Box>
          </Stack>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Example: Focus Management in Modal
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
            <code>{`function Modal({ open, onClose, triggerRef }) {
  const modalRef = useRef(null);
  
  useEffect(() => {
    if (open) {
      // Trap focus in modal
      // Restore focus to trigger when closed
      return () => {
        triggerRef.current?.focus();
      };
    }
  }, [open]);
  
  return (
    <Dialog
      open={open}
      onClose={onClose}
      aria-labelledby="modal-title"
      aria-describedby="modal-description"
    >
      {/* Modal content */}
    </Dialog>
  );
}`}</code>
          </Box>
        </Paper>

        {/* Testing */}
        <Paper sx={{ p: 4 }}>
          <Typography variant="h4" component="h2" gutterBottom>
            Testing Accessibility
          </Typography>
          <Typography variant="body1" paragraph>
            Regularly test your application for accessibility issues using automated tools and manual
            testing with assistive technologies.
          </Typography>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Tools and Resources
          </Typography>
          <List>
            <ListItem>
              <ListItemText
                primary="axe DevTools"
                secondary="Browser extension for accessibility testing"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="WAVE"
                secondary="Web accessibility evaluation tool"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="Lighthouse"
                secondary="Automated accessibility audits"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="Screen Readers"
                secondary="Test with NVDA (Windows), JAWS, VoiceOver (Mac), or TalkBack (Android)"
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
              href="https://www.w3.org/WAI/WCAG21/quickref/"
              target="_blank"
              rel="noopener noreferrer"
              style={{ color: "inherit", textDecoration: "underline" }}
            >
              WCAG 2.1 Quick Reference
            </Link>
            <Link
              href="https://www.w3.org/WAI/ARIA/apg/"
              target="_blank"
              rel="noopener noreferrer"
              style={{ color: "inherit", textDecoration: "underline" }}
            >
              ARIA Authoring Practices Guide
            </Link>
            <Link
              href="https://webaim.org/"
              target="_blank"
              rel="noopener noreferrer"
              style={{ color: "inherit", textDecoration: "underline" }}
            >
              WebAIM Resources
            </Link>
          </Stack>
        </Paper>
      </Stack>
    </Container>
  );
}

