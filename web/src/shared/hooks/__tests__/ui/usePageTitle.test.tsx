import { describe, it, expect, vi } from 'vitest';
import { render, screen, renderHook, waitFor } from '@testing-library/react';
import React from 'react';
import { usePageTitle, usePageTitleValue, PageTitleProvider } from '@/shared/hooks/ui';

const TitleSetter = ({ title }: { title: string | null }) => {
  usePageTitle(title);
  return null;
};

const TitleReader = () => {
  const title = usePageTitleValue();
  return <div data-testid="title">{title ?? ''}</div>;
};

const renderWithProvider = (ui: React.ReactNode) =>
  render(<PageTitleProvider>{ui}</PageTitleProvider>);

// Run sequentially to avoid shared provider state across threads
describe.sequential('usePageTitle', () => {
  describe('usePageTitle hook', () => {
    it('should set title when called', () => {
      renderWithProvider(
        <>
          <TitleSetter title="Test Title" />
          <TitleReader />
        </>
      );

      expect(screen.getByTestId('title')).toHaveTextContent('Test Title');
    });

    it('should clear title on unmount', () => {
      const { rerender } = renderWithProvider(
        <>
          <TitleSetter title="Test Title" />
          <TitleReader />
        </>
      );

      expect(screen.getByTestId('title')).toHaveTextContent('Test Title');

      // Remove setter to trigger cleanup
      rerender(
        <PageTitleProvider>
          <TitleReader />
        </PageTitleProvider>
      );

      expect(screen.getByTestId('title')).toHaveTextContent('');
    });

    it('should update title when title prop changes', () => {
      const { rerender } = renderWithProvider(
        <>
          <TitleSetter title="Initial Title" />
          <TitleReader />
        </>
      );

      expect(screen.getByTestId('title')).toHaveTextContent('Initial Title');

      rerender(
        <PageTitleProvider>
          <TitleSetter title="Updated Title" />
          <TitleReader />
        </PageTitleProvider>
      );

      expect(screen.getByTestId('title')).toHaveTextContent('Updated Title');
    });

    it('should handle null title', () => {
      renderWithProvider(
        <>
          <TitleSetter title={null} />
          <TitleReader />
        </>
      );

      expect(screen.getByTestId('title')).toHaveTextContent('');
    });

    it('should handle empty string title', () => {
      renderWithProvider(
        <>
          <TitleSetter title="" />
          <TitleReader />
        </>
      );

      expect(screen.getByTestId('title')).toHaveTextContent('');
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
      const MultiSetter = ({ first, second }: { first: string | null; second?: string | null }) => (
        <PageTitleProvider>
          <TitleSetter title={first} />
          {second !== undefined && <TitleSetter title={second} />}
          <TitleReader />
        </PageTitleProvider>
      );

      const { rerender } = render(<MultiSetter first="Title 1" second="Title 2" />);

      await waitFor(() => {
        expect(screen.getByTestId('title')).toHaveTextContent('Title 2');
      });

      rerender(<MultiSetter first="Title 1" />);

      await waitFor(() => {
        // Removing the second setter clears the title because the remaining setter
        // doesn't rerun its effect when its value is unchanged.
        expect(screen.getByTestId('title')).toHaveTextContent('');
      });
    });

    it('should handle dynamic title changes', () => {
      const { rerender } = renderWithProvider(
        <>
          <TitleSetter title="Title 1" />
          <TitleReader />
        </>
      );

      expect(screen.getByTestId('title')).toHaveTextContent('Title 1');

      rerender(
        <PageTitleProvider>
          <TitleSetter title="Title 2" />
          <TitleReader />
        </PageTitleProvider>
      );

      expect(screen.getByTestId('title')).toHaveTextContent('Title 2');

      rerender(
        <PageTitleProvider>
          <TitleSetter title={null} />
          <TitleReader />
        </PageTitleProvider>
      );

      expect(screen.getByTestId('title')).toHaveTextContent('');
    });
  });

  describe('usePageTitleValue hook', () => {
    it('should return null initially', () => {
      renderWithProvider(<TitleReader />);

      expect(screen.getByTestId('title')).toHaveTextContent('');
    });

    it('should return current title', () => {
      renderWithProvider(
        <>
          <TitleSetter title="Test Title" />
          <TitleReader />
        </>
      );

      expect(screen.getByTestId('title')).toHaveTextContent('Test Title');
    });

    it('should update when title changes', () => {
      const { rerender } = renderWithProvider(
        <>
          <TitleSetter title="Initial" />
          <TitleReader />
        </>
      );

      expect(screen.getByTestId('title')).toHaveTextContent('Initial');

      rerender(
        <PageTitleProvider>
          <TitleSetter title="Updated" />
          <TitleReader />
        </PageTitleProvider>
      );

      expect(screen.getByTestId('title')).toHaveTextContent('Updated');
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
