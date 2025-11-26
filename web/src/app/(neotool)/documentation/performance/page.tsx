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

export default function PerformancePage() {
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
          Performance Optimization
        </Typography>
        <Typography variant="h6" color="text.secondary" sx={{ maxWidth: 800 }}>
          Best practices and strategies for optimizing your Next.js application&apos;s performance.
        </Typography>
      </Box>

      <Stack spacing={4}>
        {/* Overview */}
        <Paper sx={{ p: 4 }}>
          <Typography variant="h4" component="h2" gutterBottom>
            Performance Overview
          </Typography>
          <Typography variant="body1" paragraph>
            Performance is crucial for user experience. Fast-loading applications improve user satisfaction,
            reduce bounce rates, and improve SEO rankings. This guide covers key optimization strategies
            for Next.js applications.
          </Typography>
          <Alert severity="info" sx={{ mt: 2 }}>
            Next.js provides built-in optimizations like automatic code splitting, image optimization,
            and static generation. Use these features effectively.
          </Alert>
        </Paper>

        {/* Bundle Size */}
        <Paper sx={{ p: 4 }}>
          <Typography variant="h4" component="h2" gutterBottom>
            Bundle Size Optimization
          </Typography>
          <Typography variant="body1" paragraph>
            Smaller bundle sizes mean faster load times. Here are strategies to reduce your bundle size:
          </Typography>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Strategies
          </Typography>
          <List>
            <ListItem>
              <ListItemText
                primary="Tree Shaking"
                secondary="Remove unused code from your bundle. Use ES modules and avoid default exports when possible"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="Dynamic Imports"
                secondary="Load components and libraries only when needed using dynamic imports"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="Analyze Bundle"
                secondary="Use tools like @next/bundle-analyzer to identify large dependencies"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="Optimize Dependencies"
                secondary="Replace heavy libraries with lighter alternatives when possible"
              />
            </ListItem>
          </List>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Bundle Analysis
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
            <code>{`// Install bundle analyzer
npm install @next/bundle-analyzer

// Add to next.config.mjs
import bundleAnalyzer from '@next/bundle-analyzer';

const withBundleAnalyzer = bundleAnalyzer({
  enabled: process.env.ANALYZE === 'true',
});

export default withBundleAnalyzer({
  // ... your config
});

// Run analysis
ANALYZE=true npm run build`}</code>
          </Box>
        </Paper>

        {/* Code Splitting */}
        <Paper sx={{ p: 4 }}>
          <Typography variant="h4" component="h2" gutterBottom>
            Code Splitting & Lazy Loading
          </Typography>
          <Typography variant="body1" paragraph>
            Code splitting allows you to split your code into smaller chunks that can be loaded on demand.
          </Typography>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Dynamic Imports
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
            <code>{`// Dynamic import for components
import dynamic from 'next/dynamic';

const HeavyComponent = dynamic(() => import('./HeavyComponent'), {
  loading: () => <p>Loading...</p>,
  ssr: false, // Disable SSR if component doesn't need it
});

// Dynamic import for libraries
const Chart = dynamic(() => import('react-chartjs-2'), {
  ssr: false,
});

// Use in your component
function MyPage() {
  return <HeavyComponent />;
}`}</code>
          </Box>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Route-Based Code Splitting
          </Typography>
          <Typography variant="body2" paragraph>
            Next.js automatically splits code by route. Each page is loaded only when needed.
          </Typography>
        </Paper>

        {/* Image Optimization */}
        <Paper sx={{ p: 4 }}>
          <Typography variant="h4" component="h2" gutterBottom>
            Image Optimization
          </Typography>
          <Typography variant="body1" paragraph>
            Images often account for the largest portion of page weight. Next.js provides built-in
            image optimization.
          </Typography>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Using Next.js Image Component
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
            <code>{`import Image from 'next/image';

// Optimized image
<Image
  src="/hero.jpg"
  alt="Hero image"
  width={800}
  height={600}
  priority // Load immediately for above-the-fold images
  placeholder="blur" // Show blur placeholder
/>

// Responsive image
<Image
  src="/product.jpg"
  alt="Product"
  width={500}
  height={500}
  sizes="(max-width: 768px) 100vw, 50vw"
/>`}</code>
          </Box>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Best Practices
          </Typography>
          <List>
            <ListItem>
              <ListItemText
                primary="Use Next.js Image Component"
                secondary="Automatically optimizes images, provides lazy loading, and responsive images"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="Optimize Image Formats"
                secondary="Use WebP or AVIF formats when possible for better compression"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="Provide Dimensions"
                secondary="Always specify width and height to prevent layout shift"
              />
            </ListItem>
          </List>
        </Paper>

        {/* Caching Strategies */}
        <Paper sx={{ p: 4 }}>
          <Typography variant="h4" component="h2" gutterBottom>
            Caching Strategies
          </Typography>
          <Typography variant="body1" paragraph>
            Effective caching reduces server load and improves response times for returning visitors.
          </Typography>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Next.js Caching
          </Typography>
          <List>
            <ListItem>
              <ListItemText
                primary="Static Generation (SSG)"
                secondary="Pages are generated at build time and cached. Perfect for content that doesn't change often"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="Incremental Static Regeneration (ISR)"
                secondary="Regenerate static pages on-demand after a timeout period"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="Server-Side Rendering (SSR)"
                secondary="Render pages on-demand with caching headers"
              />
            </ListItem>
          </List>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Example: ISR Implementation
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
            <code>{`export async function getStaticProps() {
  const data = await fetchData();
  
  return {
    props: { data },
    revalidate: 3600, // Regenerate every hour
  };
}`}</code>
          </Box>
        </Paper>

        {/* React Optimization */}
        <Paper sx={{ p: 4 }}>
          <Typography variant="h4" component="h2" gutterBottom>
            React Optimization
          </Typography>
          <Typography variant="body1" paragraph>
            Optimize React components to reduce unnecessary re-renders and improve performance.
          </Typography>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Techniques
          </Typography>
          <List>
            <ListItem>
              <ListItemText
                primary="React.memo"
                secondary="Memoize components to prevent unnecessary re-renders"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="useMemo & useCallback"
                secondary="Memoize expensive computations and callback functions"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="Code Splitting"
                secondary="Split large components into smaller, lazy-loaded chunks"
              />
            </ListItem>
          </List>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Example: Memoization
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
            <code>{`import { memo, useMemo, useCallback } from 'react';

// Memoize component
const ExpensiveComponent = memo(({ data }) => {
  // Component logic
});

// Memoize expensive computation
function MyComponent({ items }) {
  const sortedItems = useMemo(() => {
    return items.sort((a, b) => a.name.localeCompare(b.name));
  }, [items]);
  
  const handleClick = useCallback(() => {
    // Handle click
  }, []);
  
  return <ExpensiveComponent data={sortedItems} onClick={handleClick} />;
}`}</code>
          </Box>
        </Paper>

        {/* Performance Monitoring */}
        <Paper sx={{ p: 4 }}>
          <Typography variant="h4" component="h2" gutterBottom>
            Performance Monitoring
          </Typography>
          <Typography variant="body1" paragraph>
            Monitor your application&apos;s performance to identify bottlenecks and track improvements.
          </Typography>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Key Metrics
          </Typography>
          <Stack direction="row" spacing={1} flexWrap="wrap" sx={{ mb: 2 }}>
            <Chip label="First Contentful Paint (FCP)" />
            <Chip label="Largest Contentful Paint (LCP)" />
            <Chip label="Time to Interactive (TTI)" />
            <Chip label="Cumulative Layout Shift (CLS)" />
            <Chip label="First Input Delay (FID)" />
          </Stack>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Tools
          </Typography>
          <List>
            <ListItem>
              <ListItemText
                primary="Lighthouse"
                secondary="Chrome DevTools for performance auditing"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="Web Vitals"
                secondary="Real User Monitoring (RUM) for performance metrics"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="Next.js Analytics"
                secondary="Built-in analytics for Next.js applications"
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
              href="https://nextjs.org/docs/app/building-your-application/optimizing"
              target="_blank"
              rel="noopener noreferrer"
              style={{ color: "inherit", textDecoration: "underline" }}
            >
              Next.js Optimization Guide
            </Link>
            <Link
              href="https://web.dev/performance/"
              target="_blank"
              rel="noopener noreferrer"
              style={{ color: "inherit", textDecoration: "underline" }}
            >
              Web.dev Performance Guide
            </Link>
          </Stack>
        </Paper>
      </Stack>
    </Container>
  );
}

