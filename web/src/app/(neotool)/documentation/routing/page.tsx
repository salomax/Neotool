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
} from "@mui/material";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import Link from "next/link";

export default function RoutingPage() {
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
          Routing & Navigation
        </Typography>
        <Typography variant="h6" color="text.secondary" sx={{ maxWidth: 800 }}>
          Guide to Next.js App Router patterns, navigation, and routing best practices.
        </Typography>
      </Box>

      <Stack spacing={4}>
        {/* Overview */}
        <Paper sx={{ p: 4 }}>
          <Typography variant="h4" component="h2" gutterBottom>
            Next.js App Router
          </Typography>
          <Typography variant="body1" paragraph>
            Next.js uses a file-system based router built on React Server Components. The App Router
            provides a more intuitive way to organize routes and enables powerful features like layouts,
            loading states, and error handling.
          </Typography>
          <Alert severity="info" sx={{ mt: 2 }}>
            All routes are defined in the <code>app/</code> directory using the file-system convention.
          </Alert>
        </Paper>

        {/* Basic Routing */}
        <Paper sx={{ p: 4 }}>
          <Typography variant="h4" component="h2" gutterBottom>
            Basic Routing
          </Typography>
          <Typography variant="body1" paragraph>
            Create routes by adding files to the <code>app</code> directory:
          </Typography>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            File Structure
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
            <code>{`app/
├── page.tsx              → / (home page)
├── about/
│   └── page.tsx          → /about
├── products/
│   ├── page.tsx          → /products
│   └── [id]/
│       └── page.tsx      → /products/:id (dynamic route)
└── layout.tsx            → Root layout`}</code>
          </Box>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Example: Basic Page
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
            <code>{`// app/about/page.tsx
export default function AboutPage() {
  return (
    <div>
      <h1>About Us</h1>
      <p>Welcome to our about page</p>
    </div>
  );
}`}</code>
          </Box>
        </Paper>

        {/* Dynamic Routes */}
        <Paper sx={{ p: 4 }}>
          <Typography variant="h4" component="h2" gutterBottom>
            Dynamic Routes
          </Typography>
          <Typography variant="body1" paragraph>
            Create dynamic routes using brackets in the folder name:
          </Typography>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Dynamic Route Example
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
            <code>{`// app/products/[id]/page.tsx
interface PageProps {
  params: {
    id: string;
  };
}

export default function ProductPage({ params }: PageProps) {
  return (
    <div>
      <h1>Product {params.id}</h1>
    </div>
  );
}

// Catch-all routes
// app/shop/[...slug]/page.tsx
// Matches: /shop, /shop/a, /shop/a/b, etc.

// Optional catch-all routes
// app/docs/[[...slug]]/page.tsx
// Matches: /docs and /docs/a, /docs/a/b, etc.`}</code>
          </Box>
        </Paper>

        {/* Layouts */}
        <Paper sx={{ p: 4 }}>
          <Typography variant="h4" component="h2" gutterBottom>
            Layouts & Templates
          </Typography>
          <Typography variant="body1" paragraph>
            Layouts allow you to create shared UI that persists across multiple pages. They&apos;re perfect
            for navigation, headers, footers, and other common elements.
          </Typography>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Layout Example
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
            <code>{`// app/layout.tsx (root layout)
export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en">
      <body>
        <header>Navigation</header>
        {children}
        <footer>Footer</footer>
      </body>
    </html>
  );
}

// app/dashboard/layout.tsx (nested layout)
export default function DashboardLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <div>
      <aside>Dashboard Sidebar</aside>
      <main>{children}</main>
    </div>
  );
}`}</code>
          </Box>
        </Paper>

        {/* Navigation */}
        <Paper sx={{ p: 4 }}>
          <Typography variant="h4" component="h2" gutterBottom>
            Navigation
          </Typography>
          <Typography variant="body1" paragraph>
            Next.js provides the <code>Link</code> component and <code>useRouter</code> hook for
            navigation:
          </Typography>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Using Link Component
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
            <code>{`import Link from 'next/link';

function Navigation() {
  return (
    <nav>
      <Link href="/">Home</Link>
      <Link href="/about">About</Link>
      <Link href="/products/123">Product 123</Link>
    </nav>
  );
}`}</code>
          </Box>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Programmatic Navigation
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
            <code>{`'use client';

import { useRouter } from 'next/navigation';

function MyComponent() {
  const router = useRouter();
  
  const handleClick = () => {
    router.push('/dashboard');
    // or
    router.replace('/dashboard'); // Replace current history entry
    // or
    router.back(); // Go back
    // or
    router.forward(); // Go forward
  };
  
  return <button onClick={handleClick}>Navigate</button>;
}`}</code>
          </Box>
        </Paper>

        {/* Data Fetching */}
        <Paper sx={{ p: 4 }}>
          <Typography variant="h4" component="h2" gutterBottom>
            Data Fetching
          </Typography>
          <Typography variant="body1" paragraph>
            Next.js App Router supports multiple data fetching strategies:
          </Typography>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Server Components (Default)
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
            <code>{`// Server Component (default)
export default async function Page() {
  const data = await fetch('https://api.example.com/data');
  const json = await data.json();
  
  return <div>{json.title}</div>;
}`}</code>
          </Box>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Client Components
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
            <code>{`'use client';

import { useEffect, useState } from 'react';

export default function ClientPage() {
  const [data, setData] = useState(null);
  
  useEffect(() => {
    fetch('/api/data')
      .then(res => res.json())
      .then(setData);
  }, []);
  
  return <div>{data?.title}</div>;
}`}</code>
          </Box>
        </Paper>

        {/* Route Groups */}
        <Paper sx={{ p: 4 }}>
          <Typography variant="h4" component="h2" gutterBottom>
            Route Groups
          </Typography>
          <Typography variant="body1" paragraph>
            Route groups allow you to organize routes without affecting the URL structure. Use
            parentheses in folder names:
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
            <code>{`app/
├── (marketing)/
│   ├── about/
│   │   └── page.tsx      → /about
│   └── contact/
│       └── page.tsx      → /contact
└── (shop)/
    ├── products/
    │   └── page.tsx      → /products
    └── cart/
        └── page.tsx      → /cart`}</code>
          </Box>
        </Paper>

        {/* Loading & Error States */}
        <Paper sx={{ p: 4 }}>
          <Typography variant="h4" component="h2" gutterBottom>
            Loading & Error States
          </Typography>
          <Typography variant="body1" paragraph>
            Next.js provides special files for loading and error states:
          </Typography>

          <List>
            <ListItem>
              <ListItemText
                primary="loading.tsx"
                secondary="Shows a loading UI while the page is loading"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="error.tsx"
                secondary="Shows an error UI if something goes wrong"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="not-found.tsx"
                secondary="Shows a 404 page when a route is not found"
              />
            </ListItem>
          </List>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Example: Loading State
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
            <code>{`// app/dashboard/loading.tsx
export default function Loading() {
  return <div>Loading dashboard...</div>;
}`}</code>
          </Box>
        </Paper>

        {/* Resources */}
        <Paper sx={{ p: 4, bgcolor: "primary.main", color: "primary.contrastText" }}>
          <Typography variant="h4" component="h2" gutterBottom>
            Additional Resources
          </Typography>
          <Stack spacing={2} sx={{ mt: 2 }}>
            <Link
              href="https://nextjs.org/docs/app/building-your-application/routing"
              target="_blank"
              rel="noopener noreferrer"
              style={{ color: "inherit", textDecoration: "underline" }}
            >
              Next.js App Router Documentation
            </Link>
          </Stack>
        </Paper>
      </Stack>
    </Container>
  );
}

