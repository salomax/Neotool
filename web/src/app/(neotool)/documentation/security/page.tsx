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
import SecurityIcon from "@mui/icons-material/Security";
import Link from "next/link";

export default function SecurityPage() {
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
          Security Best Practices
        </Typography>
        <Typography variant="h6" color="text.secondary" sx={{ maxWidth: 800 }}>
          Security considerations and best practices for building secure web applications.
        </Typography>
      </Box>

      <Stack spacing={4}>
        {/* Overview */}
        <Paper sx={{ p: 4 }}>
          <Typography variant="h4" component="h2" gutterBottom>
            Security Overview
          </Typography>
          <Typography variant="body1" paragraph>
            Security is a critical aspect of web application development. This guide covers common
            security vulnerabilities and best practices to protect your application and users.
          </Typography>
          <Alert severity="warning" sx={{ mt: 2 }}>
            Security is an ongoing process. Regularly update dependencies, review code, and stay
            informed about security best practices.
          </Alert>
        </Paper>

        {/* XSS Prevention */}
        <Paper sx={{ p: 4 }}>
          <Typography variant="h4" component="h2" gutterBottom>
            XSS (Cross-Site Scripting) Prevention
          </Typography>
          <Typography variant="body1" paragraph>
            XSS attacks occur when malicious scripts are injected into web pages viewed by other users.
            React provides built-in protection, but you should still follow best practices.
          </Typography>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            React&apos;s Built-in Protection
          </Typography>
          <Typography variant="body2" paragraph>
            React automatically escapes values in JSX, preventing most XSS attacks. However, you should
            be careful with:
          </Typography>
          <List>
            <ListItem>
              <ListItemText
                primary="dangerouslySetInnerHTML"
                secondary="Only use when absolutely necessary and sanitize all content"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="User Input"
                secondary="Always validate and sanitize user input before rendering"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="Third-party Libraries"
                secondary="Use trusted libraries like DOMPurify for HTML sanitization"
              />
            </ListItem>
          </List>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Best Practices
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
            <code>{`// ✅ Good: React automatically escapes
<div>{userInput}</div>

// ✅ Good: Sanitize if you must use dangerouslySetInnerHTML
import DOMPurify from 'dompurify';

<div
  dangerouslySetInnerHTML={{
    __html: DOMPurify.sanitize(userHtml)
  }}
/>

// ❌ Bad: Never trust user input without sanitization
<div dangerouslySetInnerHTML={{ __html: userInput }} />`}</code>
          </Box>
        </Paper>

        {/* CSRF Protection */}
        <Paper sx={{ p: 4 }}>
          <Typography variant="h4" component="h2" gutterBottom>
            CSRF (Cross-Site Request Forgery) Protection
          </Typography>
          <Typography variant="body1" paragraph>
            CSRF attacks trick users into performing actions they didn&apos;t intend. Protect your API
            endpoints with proper CSRF tokens.
          </Typography>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Strategies
          </Typography>
          <List>
            <ListItem>
              <ListItemText
                primary="CSRF Tokens"
                secondary="Include CSRF tokens in all state-changing requests"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="SameSite Cookies"
                secondary="Use SameSite cookie attribute to prevent CSRF attacks"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="Origin Validation"
                secondary="Validate the Origin and Referer headers on the server"
              />
            </ListItem>
          </List>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Example: CSRF Token in Request
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
            <code>{`// Get CSRF token from server
const csrfToken = await fetch('/api/csrf-token').then(r => r.json());

// Include in all requests
fetch('/api/data', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'X-CSRF-Token': csrfToken,
  },
  body: JSON.stringify(data),
});`}</code>
          </Box>
        </Paper>

        {/* Content Security Policy */}
        <Paper sx={{ p: 4 }}>
          <Typography variant="h4" component="h2" gutterBottom>
            Content Security Policy (CSP)
          </Typography>
          <Typography variant="body1" paragraph>
            CSP is a security standard that helps prevent XSS attacks by controlling which resources
            can be loaded and executed.
          </Typography>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Next.js CSP Configuration
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
            <code>{`// next.config.mjs
const securityHeaders = [
  {
    key: 'Content-Security-Policy',
    value: \`
      default-src 'self';
      script-src 'self' 'unsafe-eval' 'unsafe-inline';
      style-src 'self' 'unsafe-inline';
      img-src 'self' data: https:;
      font-src 'self' data:;
      connect-src 'self' https://api.example.com;
    \`.replace(/\\s{2,}/g, ' ').trim()
  },
];

export default {
  async headers() {
    return [
      {
        source: '/:path*',
        headers: securityHeaders,
      },
    ];
  },
};`}</code>
          </Box>
        </Paper>

        {/* Dependency Security */}
        <Paper sx={{ p: 4 }}>
          <Typography variant="h4" component="h2" gutterBottom>
            Dependency Security
          </Typography>
          <Typography variant="body1" paragraph>
            Keep your dependencies up to date and scan for known vulnerabilities.
          </Typography>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Best Practices
          </Typography>
          <List>
            <ListItem>
              <ListItemText
                primary="Regular Updates"
                secondary="Regularly update dependencies to patch security vulnerabilities"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="Security Audits"
                secondary="Run npm audit or yarn audit regularly to check for vulnerabilities"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="Dependency Scanning"
                secondary="Use tools like Snyk or Dependabot for automated vulnerability scanning"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="Minimize Dependencies"
                secondary="Only include necessary dependencies to reduce attack surface"
              />
            </ListItem>
          </List>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Security Audit Commands
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
            <code>{`# Check for vulnerabilities
npm audit

# Fix automatically fixable issues
npm audit fix

# Install Snyk for continuous monitoring
npm install -g snyk
snyk test
snyk monitor`}</code>
          </Box>
        </Paper>

        {/* Data Validation */}
        <Paper sx={{ p: 4 }}>
          <Typography variant="h4" component="h2" gutterBottom>
            Data Validation
          </Typography>
          <Typography variant="body1" paragraph>
            Always validate and sanitize user input on both client and server side.
          </Typography>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Validation Strategies
          </Typography>
          <List>
            <ListItem>
              <ListItemText
                primary="Client-Side Validation"
                secondary="Provide immediate feedback to users, but don't rely on it for security"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="Server-Side Validation"
                secondary="Always validate on the server - client-side validation can be bypassed"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="Schema Validation"
                secondary="Use libraries like Zod or Yup for type-safe validation"
              />
            </ListItem>
          </List>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Example: Zod Validation
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
            <code>{`import { z } from 'zod';

// Define schema
const userSchema = z.object({
  email: z.string().email('Invalid email address'),
  password: z.string().min(8, 'Password must be at least 8 characters'),
  age: z.number().min(18).max(100),
});

// Validate on server
export async function POST(request: Request) {
  const body = await request.json();
  
  try {
    const validatedData = userSchema.parse(body);
    // Process validated data
  } catch (error) {
    return Response.json({ error: 'Validation failed' }, { status: 400 });
  }
}`}</code>
          </Box>
        </Paper>

        {/* Authentication & Authorization */}
        <Paper sx={{ p: 4 }}>
          <Typography variant="h4" component="h2" gutterBottom>
            Authentication & Authorization
          </Typography>
          <Typography variant="body1" paragraph>
            Secure authentication and proper authorization are essential for protecting user data.
          </Typography>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Best Practices
          </Typography>
          <List>
            <ListItem>
              <ListItemText
                primary="Secure Password Storage"
                secondary="Never store passwords in plain text. Use bcrypt or similar hashing algorithms"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="HTTPS Only"
                secondary="Always use HTTPS in production to encrypt data in transit"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="Token Management"
                secondary="Use secure, httpOnly cookies for tokens or implement proper token storage"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="Session Management"
                secondary="Implement proper session expiration and refresh token rotation"
              />
            </ListItem>
          </List>
        </Paper>

        {/* Environment Variables */}
        <Paper sx={{ p: 4 }}>
          <Typography variant="h4" component="h2" gutterBottom>
            Environment Variables & Secrets
          </Typography>
          <Typography variant="body1" paragraph>
            Never expose sensitive information like API keys, secrets, or credentials in your code.
          </Typography>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Best Practices
          </Typography>
          <List>
            <ListItem>
              <ListItemText
                primary="Use .env Files"
                secondary="Store sensitive data in .env.local (never commit to git)"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="NEXT_PUBLIC Prefix"
                secondary="Only variables prefixed with NEXT_PUBLIC_ are exposed to the browser"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="Secret Management"
                secondary="Use secret management services in production (AWS Secrets Manager, etc.)"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="Never Commit Secrets"
                secondary="Always add .env* to .gitignore"
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
              href="https://owasp.org/www-project-top-ten/"
              target="_blank"
              rel="noopener noreferrer"
              style={{ color: "inherit", textDecoration: "underline" }}
            >
              OWASP Top 10
            </Link>
            <Link
              href="https://nextjs.org/docs/app/building-your-application/configuring/security-headers"
              target="_blank"
              rel="noopener noreferrer"
              style={{ color: "inherit", textDecoration: "underline" }}
            >
              Next.js Security Headers
            </Link>
          </Stack>
        </Paper>
      </Stack>
    </Container>
  );
}

