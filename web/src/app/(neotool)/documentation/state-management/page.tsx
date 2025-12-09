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

export default function StateManagementPage() {
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
          State Management
        </Typography>
        <Typography variant="h6" color="text.secondary" sx={{ maxWidth: 800 }}>
          Patterns and best practices for managing state in React and Next.js applications.
        </Typography>
      </Box>

      <Stack spacing={4}>
        {/* Overview */}
        <Paper sx={{ p: 4 }}>
          <Typography variant="h4" component="h2" gutterBottom>
            State Management Overview
          </Typography>
          <Typography variant="body1" paragraph>
            Effective state management is crucial for building maintainable React applications. This
            guide covers different state management patterns and when to use them.
          </Typography>
          <Alert severity="info" sx={{ mt: 2 }}>
            Choose the right state management solution based on your needs. Start simple with React
            state, then add more complex solutions as needed.
          </Alert>
        </Paper>

        {/* React State */}
        <Paper sx={{ p: 4 }}>
          <Typography variant="h4" component="h2" gutterBottom>
            React State Patterns
          </Typography>
          <Typography variant="body1" paragraph>
            React provides built-in state management with <code>useState</code> and <code>useReducer</code>.
          </Typography>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            useState Hook
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

import { useState } from 'react';

function Counter() {
  const [count, setCount] = useState(0);
  
  return (
    <div>
      <p>Count: {count}</p>
      <button onClick={() => setCount(count + 1)}>Increment</button>
    </div>
  );
}`}</code>
          </Box>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            useReducer Hook
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

import { useReducer } from 'react';

function reducer(state, action) {
  switch (action.type) {
    case 'increment':
      return { count: state.count + 1 };
    case 'decrement':
      return { count: state.count - 1 };
    default:
      return state;
  }
}

function Counter() {
  const [state, dispatch] = useReducer(reducer, { count: 0 });
  
  return (
    <div>
      <p>Count: {state.count}</p>
      <button onClick={() => dispatch({ type: 'increment' })}>Increment</button>
    </div>
  );
}`}</code>
          </Box>
        </Paper>

        {/* Custom Hooks */}
        <Paper sx={{ p: 4 }}>
          <Typography variant="h4" component="h2" gutterBottom>
            Custom Hooks
          </Typography>
          <Typography variant="body1" paragraph>
            Custom hooks allow you to extract and reuse stateful logic across components.
          </Typography>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Hook Organization
          </Typography>
          <List>
            <ListItem>
              <ListItemText
                primary="shared/hooks/"
                secondary="Reusable utility hooks (useResponsive, useAutoSave)"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="lib/hooks/[domain]/"
                secondary="Domain-specific business logic hooks (useCustomers, useProducts)"
              />
            </ListItem>
          </List>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Example: Custom Hook
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
            <code>{`// shared/hooks/useLocalStorage.ts
import { useState, useEffect } from 'react';

export function useLocalStorage<T>(key: string, initialValue: T) {
  const [storedValue, setStoredValue] = useState<T>(() => {
    if (typeof window === 'undefined') {
      return initialValue;
    }
    try {
      const item = window.localStorage.getItem(key);
      return item ? JSON.parse(item) : initialValue;
    } catch (error) {
      return initialValue;
    }
  });
  
  const setValue = (value: T | ((val: T) => T)) => {
    try {
      const valueToStore = value instanceof Function ? value(storedValue) : value;
      setStoredValue(valueToStore);
      if (typeof window !== 'undefined') {
        window.localStorage.setItem(key, JSON.stringify(valueToStore));
      }
    } catch (error) {
      // Silently handle localStorage errors (quota exceeded, etc.)
    }
  };
  
  return [storedValue, setValue] as const;
}

// Usage
function MyComponent() {
  const [name, setName] = useLocalStorage('name', '');
  
  return <input value={name} onChange={(e) => setName(e.target.value)} />;
}`}</code>
          </Box>
        </Paper>

        {/* Context API */}
        <Paper sx={{ p: 4 }}>
          <Typography variant="h4" component="h2" gutterBottom>
            Context API
          </Typography>
          <Typography variant="body1" paragraph>
            Use React Context for sharing state across multiple components without prop drilling.
          </Typography>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Creating Context
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

import { createContext, useContext, useState, ReactNode } from 'react';

interface ThemeContextType {
  theme: 'light' | 'dark';
  toggleTheme: () => void;
}

const ThemeContext = createContext<ThemeContextType | undefined>(undefined);

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setTheme] = useState<'light' | 'dark'>('light');
  
  const toggleTheme = () => {
    setTheme(prev => prev === 'light' ? 'dark' : 'light');
  };
  
  return (
    <ThemeContext.Provider value={{ theme, toggleTheme }}>
      {children}
    </ThemeContext.Provider>
  );
}

export function useTheme() {
  const context = useContext(ThemeContext);
  if (context === undefined) {
    throw new Error('useTheme must be used within ThemeProvider');
  }
  return context;
}`}</code>
          </Box>
        </Paper>

        {/* Server State */}
        <Paper sx={{ p: 4 }}>
          <Typography variant="h4" component="h2" gutterBottom>
            Server State Management
          </Typography>
          <Typography variant="body1" paragraph>
            For managing server state (data from APIs), consider using React Query or SWR.
          </Typography>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            React Query Example
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

import { useQuery } from '@tanstack/react-query';

function UserProfile({ userId }: { userId: string }) {
  const { data, isLoading, error } = useQuery({
    queryKey: ['user', userId],
    queryFn: async () => {
      const response = await fetch(\`/api/users/\${userId}\`);
      if (!response.ok) throw new Error('Failed to fetch');
      return response.json();
    },
  });
  
  if (isLoading) return <div>Loading...</div>;
  if (error) return <div>Error: {error.message}</div>;
  
  return <div>{data.name}</div>;
}`}</code>
          </Box>
        </Paper>

        {/* Form State */}
        <Paper sx={{ p: 4 }}>
          <Typography variant="h4" component="h2" gutterBottom>
            Form State Management
          </Typography>
          <Typography variant="body1" paragraph>
            For complex forms, use libraries like React Hook Form or Formik.
          </Typography>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            React Hook Form Example
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

import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';

const schema = z.object({
  email: z.string().email(),
  password: z.string().min(8),
});

type FormData = z.infer<typeof schema>;

function LoginForm() {
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormData>({
    resolver: zodResolver(schema),
  });
  
  const onSubmit = (data: FormData) => {
    console.log(data);
  };
  
  return (
    <form onSubmit={handleSubmit(onSubmit)}>
      <input {...register('email')} />
      {errors.email && <span>{errors.email.message}</span>}
      
      <input type="password" {...register('password')} />
      {errors.password && <span>{errors.password.message}</span>}
      
      <button type="submit">Submit</button>
    </form>
  );
}`}</code>
          </Box>
        </Paper>

        {/* Best Practices */}
        <Paper sx={{ p: 4 }}>
          <Typography variant="h4" component="h2" gutterBottom>
            Best Practices
          </Typography>
          <List>
            <ListItem>
              <ListItemText
                primary="Start Simple"
                secondary="Begin with React state, then add Context or state management libraries as needed"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="Lift State Up"
                secondary="Keep state as close to where it's needed as possible"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="Separate Concerns"
                secondary="Keep UI state separate from server state and business logic"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="Use Custom Hooks"
                secondary="Extract complex state logic into reusable custom hooks"
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
              href="/documentation/testing"
              style={{ color: "inherit", textDecoration: "underline" }}
            >
              Testing Documentation
            </Link>
            <Link
              href="https://react.dev/reference/react/useState"
              target="_blank"
              rel="noopener noreferrer"
              style={{ color: "inherit", textDecoration: "underline" }}
            >
              React State Documentation
            </Link>
          </Stack>
        </Paper>
      </Stack>
    </Container>
  );
}

