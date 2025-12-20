import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act, render, screen, waitFor } from '@testing-library/react';
import React from 'react';
import { usePageTitle, usePageTitleValue, PageTitleProvider } from '@/shared/hooks/ui';

// Helper to create a test wrapper with PageTitleProvider
const createWrapper = () => {
  const Wrapper = ({ children }: { children: React.ReactNode }) => {
    return React.createElement(PageTitleProvider, null, children);
  };
  
  Wrapper.displayName = 'TestWrapper';
  
  return Wrapper;
};

describe('usePageTitle', () => {
  describe('usePageTitle hook', () => {
    it('should set title when called', async () => {
      const wrapper = createWrapper();
      const { result: titleResult } = renderHook(() => usePageTitleValue(), { wrapper });
      
      // Initially null
      expect(titleResult.current).toBeNull();
      
      // Set title
      renderHook(() => usePageTitle('Test Title'), { wrapper });
      
      // Wait for effect to run
      await waitFor(() => {
        expect(titleResult.current).toBe('Test Title');
      });
    });

    it('should clear title on unmount', async () => {
      const wrapper = createWrapper();
      const { result: titleResult } = renderHook(() => usePageTitleValue(), { wrapper });
      
      // Set title
      const { unmount } = renderHook(() => usePageTitle('Test Title'), { wrapper });
      
      await waitFor(() => {
        expect(titleResult.current).toBe('Test Title');
      });
      
      // Unmount should clear title
      unmount();
      
      await waitFor(() => {
        expect(titleResult.current).toBeNull();
      });
    });

    it('should update title when title prop changes', async () => {
      const wrapper = createWrapper();
      const { result: titleResult } = renderHook(() => usePageTitleValue(), { wrapper });
      
      const { rerender } = renderHook(
        ({ title }: { title: string | null }) => usePageTitle(title),
        {
          wrapper,
          initialProps: { title: 'Initial Title' },
        }
      );
      
      await waitFor(() => {
        expect(titleResult.current).toBe('Initial Title');
      });
      
      // Change title
      rerender({ title: 'Updated Title' });
      
      await waitFor(() => {
        expect(titleResult.current).toBe('Updated Title');
      });
    });

    it('should handle null title', async () => {
      const wrapper = createWrapper();
      const { result: titleResult } = renderHook(() => usePageTitleValue(), { wrapper });
      
      renderHook(() => usePageTitle(null), { wrapper });
      
      await waitFor(() => {
        expect(titleResult.current).toBeNull();
      });
    });

    it('should handle empty string title', async () => {
      const wrapper = createWrapper();
      const { result: titleResult } = renderHook(() => usePageTitleValue(), { wrapper });
      
      renderHook(() => usePageTitle(''), { wrapper });
      
      await waitFor(() => {
        expect(titleResult.current).toBe('');
      });
    });

    it('should throw error when used outside provider', () => {
      // Suppress console.error for this test
      const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
      
      expect(() => {
        renderHook(() => usePageTitle('Test Title'));
      }).toThrow('usePageTitle must be used within PageTitleProvider');
      
      consoleSpy.mockRestore();
    });

    it('should handle multiple components setting title (last one wins)', async () => {
      const wrapper = createWrapper();
      const { result: titleResult } = renderHook(() => usePageTitleValue(), { wrapper });
      
      // First component sets title
      const { unmount: unmount1 } = renderHook(() => usePageTitle('Title 1'), { wrapper });
      await waitFor(() => {
        expect(titleResult.current).toBe('Title 1');
      });
      
      // Second component sets different title
      const { unmount: unmount2 } = renderHook(() => usePageTitle('Title 2'), { wrapper });
      await waitFor(() => {
        expect(titleResult.current).toBe('Title 2');
      });
      
      // Unmount second component - should clear to null (cleanup)
      unmount2();
      await waitFor(() => {
        expect(titleResult.current).toBeNull();
      });
      
      // Unmount first component - should still be null
      unmount1();
      await waitFor(() => {
        expect(titleResult.current).toBeNull();
      });
    });

    it('should handle dynamic title changes', async () => {
      const wrapper = createWrapper();
      const { result: titleResult } = renderHook(() => usePageTitleValue(), { wrapper });
      
      const { rerender } = renderHook(
        ({ title }: { title: string | null }) => usePageTitle(title),
        {
          wrapper,
          initialProps: { title: 'Title 1' as string | null },
        }
      );
      
      await waitFor(() => {
        expect(titleResult.current).toBe('Title 1');
      });
      
      rerender({ title: 'Title 2' as string | null });
      
      await waitFor(() => {
        expect(titleResult.current).toBe('Title 2');
      });
      
      rerender({ title: null as string | null });
      
      await waitFor(() => {
        expect(titleResult.current).toBeNull();
      });
    });
  });

  describe('usePageTitleValue hook', () => {
    it('should return null initially', () => {
      const wrapper = createWrapper();
      const { result } = renderHook(() => usePageTitleValue(), { wrapper });
      
      expect(result.current).toBeNull();
    });

    it('should return current title', async () => {
      const wrapper = createWrapper();
      const { result } = renderHook(() => usePageTitleValue(), { wrapper });
      
      renderHook(() => usePageTitle('Test Title'), { wrapper });
      
      await waitFor(() => {
        expect(result.current).toBe('Test Title');
      });
    });

    it('should update when title changes', async () => {
      const wrapper = createWrapper();
      const { result } = renderHook(() => usePageTitleValue(), { wrapper });
      const { rerender } = renderHook(
        ({ title }: { title: string | null }) => usePageTitle(title),
        {
          wrapper,
          initialProps: { title: 'Initial' },
        }
      );
      
      await waitFor(() => {
        expect(result.current).toBe('Initial');
      });
      
      rerender({ title: 'Updated' });
      
      await waitFor(() => {
        expect(result.current).toBe('Updated');
      });
    });

    it('should throw error when used outside provider', () => {
      // Suppress console.error for this test
      const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
      
      expect(() => {
        renderHook(() => usePageTitleValue());
      }).toThrow('usePageTitleValue must be used within PageTitleProvider');
      
      consoleSpy.mockRestore();
    });
  });

  describe('PageTitleProvider', () => {
    it('should provide context to children', () => {
      const TestComponent = () => {
        usePageTitle('Provider Test');
        const title = usePageTitleValue();
        return <div data-testid="title">{title}</div>;
      };

      render(
        <PageTitleProvider>
          <TestComponent />
        </PageTitleProvider>
      );

      expect(screen.getByTestId('title')).toHaveTextContent('Provider Test');
    });

    it('should allow multiple consumers', () => {
      const Consumer1 = () => {
        usePageTitle('Title 1');
        return null;
      };
      
      const Consumer2 = () => {
        const title = usePageTitleValue();
        return <div data-testid="title">{title}</div>;
      };

      render(
        <PageTitleProvider>
          <Consumer1 />
          <Consumer2 />
        </PageTitleProvider>
      );

      expect(screen.getByTestId('title')).toHaveTextContent('Title 1');
    });

    it('should handle nested providers (inner provider takes precedence)', () => {
      const InnerConsumer = () => {
        usePageTitle('Inner Title');
        const title = usePageTitleValue();
        return <div data-testid="inner">{title}</div>;
      };
      
      const OuterConsumer = () => {
        usePageTitle('Outer Title');
        const title = usePageTitleValue();
        return <div data-testid="outer">{title}</div>;
      };

      render(
        <PageTitleProvider>
          <OuterConsumer />
          <PageTitleProvider>
            <InnerConsumer />
          </PageTitleProvider>
        </PageTitleProvider>
      );

      // Each consumer sees its own provider's context
      expect(screen.getByTestId('outer')).toHaveTextContent('Outer Title');
      expect(screen.getByTestId('inner')).toHaveTextContent('Inner Title');
    });
  });
});
