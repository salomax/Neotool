import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act, render, screen } from '@testing-library/react';
import React from 'react';
import { usePageTitle, usePageTitleValue, PageTitleProvider } from '@/shared/hooks/ui';

// Helper to create a test wrapper with PageTitleProvider
const createWrapper = () => {
  const Wrapper = ({ children }: { children: React.ReactNode }) => {
    return <PageTitleProvider>{children}</PageTitleProvider>;
  };
  
  Wrapper.displayName = 'TestWrapper';
  
  return Wrapper;
};

describe('usePageTitle', () => {
  describe('usePageTitle hook', () => {
    it('should set title when called', () => {
      const wrapper = createWrapper();
      const { result: titleResult } = renderHook(() => usePageTitleValue(), { wrapper });
      
      // Initially null
      expect(titleResult.current).toBeNull();
      
      // Set title
      renderHook(() => usePageTitle('Test Title'), { wrapper });
      
      // Title should be set
      expect(titleResult.current).toBe('Test Title');
    });

    it('should clear title on unmount', () => {
      const wrapper = createWrapper();
      const { result: titleResult } = renderHook(() => usePageTitleValue(), { wrapper });
      
      // Set title
      const { unmount } = renderHook(() => usePageTitle('Test Title'), { wrapper });
      
      expect(titleResult.current).toBe('Test Title');
      
      // Unmount should clear title
      unmount();
      
      expect(titleResult.current).toBeNull();
    });

    it('should update title when title prop changes', () => {
      const wrapper = createWrapper();
      const { result: titleResult } = renderHook(() => usePageTitleValue(), { wrapper });
      
      const { rerender } = renderHook(
        ({ title }: { title: string | null }) => usePageTitle(title),
        {
          wrapper,
          initialProps: { title: 'Initial Title' },
        }
      );
      
      expect(titleResult.current).toBe('Initial Title');
      
      // Change title
      rerender({ title: 'Updated Title' });
      
      expect(titleResult.current).toBe('Updated Title');
    });

    it('should handle null title', () => {
      const wrapper = createWrapper();
      const { result: titleResult } = renderHook(() => usePageTitleValue(), { wrapper });
      
      renderHook(() => usePageTitle(null), { wrapper });
      
      expect(titleResult.current).toBeNull();
    });

    it('should handle empty string title', () => {
      const wrapper = createWrapper();
      const { result: titleResult } = renderHook(() => usePageTitleValue(), { wrapper });
      
      renderHook(() => usePageTitle(''), { wrapper });
      
      expect(titleResult.current).toBe('');
    });

    it('should throw error when used outside provider', () => {
      // Suppress console.error for this test
      const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
      
      expect(() => {
        renderHook(() => usePageTitle('Test Title'));
      }).toThrow('usePageTitle must be used within PageTitleProvider');
      
      consoleSpy.mockRestore();
    });

    it('should handle multiple components setting title (last one wins)', () => {
      const wrapper = createWrapper();
      const { result: titleResult } = renderHook(() => usePageTitleValue(), { wrapper });
      
      // First component sets title
      const { unmount: unmount1 } = renderHook(() => usePageTitle('Title 1'), { wrapper });
      expect(titleResult.current).toBe('Title 1');
      
      // Second component sets different title
      const { unmount: unmount2 } = renderHook(() => usePageTitle('Title 2'), { wrapper });
      expect(titleResult.current).toBe('Title 2');
      
      // Unmount second component - should clear to null (cleanup)
      unmount2();
      expect(titleResult.current).toBeNull();
      
      // Unmount first component - should still be null
      unmount1();
      expect(titleResult.current).toBeNull();
    });

    it('should handle dynamic title changes', () => {
      const wrapper = createWrapper();
      const { result: titleResult } = renderHook(() => usePageTitleValue(), { wrapper });
      
      const { rerender } = renderHook(
        ({ title }: { title: string | null }) => usePageTitle(title),
        {
          wrapper,
          initialProps: { title: 'Title 1' },
        }
      );
      
      expect(titleResult.current).toBe('Title 1');
      
      act(() => {
        rerender({ title: 'Title 2' });
      });
      
      expect(titleResult.current).toBe('Title 2');
      
      act(() => {
        rerender({ title: null });
      });
      
      expect(titleResult.current).toBeNull();
    });
  });

  describe('usePageTitleValue hook', () => {
    it('should return null initially', () => {
      const wrapper = createWrapper();
      const { result } = renderHook(() => usePageTitleValue(), { wrapper });
      
      expect(result.current).toBeNull();
    });

    it('should return current title', () => {
      const wrapper = createWrapper();
      const { result } = renderHook(() => usePageTitleValue(), { wrapper });
      
      renderHook(() => usePageTitle('Test Title'), { wrapper });
      
      expect(result.current).toBe('Test Title');
    });

    it('should update when title changes', () => {
      const wrapper = createWrapper();
      const { result } = renderHook(() => usePageTitleValue(), { wrapper });
      const { rerender } = renderHook(
        ({ title }: { title: string | null }) => usePageTitle(title),
        {
          wrapper,
          initialProps: { title: 'Initial' },
        }
      );
      
      expect(result.current).toBe('Initial');
      
      act(() => {
        rerender({ title: 'Updated' });
      });
      
      expect(result.current).toBe('Updated');
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
