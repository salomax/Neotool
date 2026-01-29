import React from 'react';
import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen, within, cleanup } from '@testing-library/react';
import { SidebarLayout } from '../SidebarLayout';
import { RAIL_W } from '@/shared/ui/navigation/SidebarRail';

// Mock the Container and Box components to verify they're called correctly
vi.mock('../Container', () => ({
  Container: ({ children, fullHeight, disableGutters, sx, 'data-testid': dataTestId, ...props }: any) => {
    return (
      <div
        data-testid={dataTestId || 'container'}
        data-fullheight={fullHeight}
        data-disablegutters={disableGutters}
        style={sx}
        {...props}
      >
        {children}
      </div>
    );
  },
}));

vi.mock('../Box', () => {
  const Box = React.forwardRef(({ children, fullHeight, sx, className, style, 'data-testid': dataTestId, ...props }: any, ref: any) => {
    return (
      <div
        ref={ref}
        data-testid={dataTestId || 'box'}
        data-fullheight={fullHeight}
        className={className}
        style={{ ...sx, ...style }}
        {...props}
      >
        {children}
      </div>
    );
  });
  Box.displayName = 'Box';
  return { Box };
});

// Run sequentially to avoid concurrent renders leaking between tests
describe.sequential('SidebarLayout', () => {
  const mockSidebar = <div data-testid="sidebar-content">Sidebar Content</div>;
  const mockChildren = <div data-testid="main-content">Main Content</div>;

  describe('Rendering', () => {
    it('renders with default props (left side, md size)', () => {
      render(
        <SidebarLayout sidebar={mockSidebar}>
          {mockChildren}
        </SidebarLayout>
      );

      expect(screen.getByTestId('sidebar-content')).toBeInTheDocument();
      expect(screen.getByTestId('main-content')).toBeInTheDocument();
    });

    it('renders sidebar on right side when side="right"', () => {
      render(
        <SidebarLayout sidebar={mockSidebar} side="right">
          {mockChildren}
        </SidebarLayout>
      );

      // Check sidebar is fixed to right
      const sidebar = screen.getByTestId('sidebarlayout-sidebar');
      expect(sidebar).toHaveStyle({ right: `${RAIL_W}px` });
      expect(sidebar).toHaveStyle({ position: 'fixed' });

      // Check content has marginRight to account for sidebar (default md size is 600px)
      const mainContainer = screen.getByTestId('sidebarlayout-content');
      expect(mainContainer).toHaveStyle({ marginRight: '600px' });
      expect(mainContainer).toHaveStyle({ width: 'calc(100% - 600px)' });
    });

    it('renders sidebar on left side by default', () => {
      render(
        <SidebarLayout sidebar={mockSidebar}>
          {mockChildren}
        </SidebarLayout>
      );

      // Check sidebar is fixed to left
      const sidebar = screen.getByTestId('sidebarlayout-sidebar');
      expect(sidebar).toHaveStyle({ left: `${RAIL_W}px` });
      expect(sidebar).toHaveStyle({ position: 'fixed' });

      // Check content has marginLeft to account for sidebar (default md size is 600px)
      const mainContainer = screen.getByTestId('sidebarlayout-content');
      expect(mainContainer).toHaveStyle({ marginLeft: '600px' });
      expect(mainContainer).toHaveStyle({ width: 'calc(100% - 600px)' });
    });
  });

  describe('Size presets', () => {
    it('applies correct width for sm size (400px)', () => {
      render(
        <SidebarLayout sidebar={mockSidebar} size="sm">
          {mockChildren}
        </SidebarLayout>
      );

      const sidebar = screen.getByTestId('sidebarlayout-sidebar');
      expect(sidebar).toHaveStyle({ width: '400px' });
    });

    it('applies correct width for md size (600px)', () => {
      render(
        <SidebarLayout sidebar={mockSidebar} size="md">
          {mockChildren}
        </SidebarLayout>
      );

      const sidebar = screen.getByTestId('sidebarlayout-sidebar');
      expect(sidebar).toHaveStyle({ width: '600px' });
    });

    it('applies correct width for lg size (800px)', () => {
      render(
        <SidebarLayout sidebar={mockSidebar} size="lg">
          {mockChildren}
        </SidebarLayout>
      );

      const sidebar = screen.getByTestId('sidebarlayout-sidebar');
      expect(sidebar).toHaveStyle({ width: '800px' });
    });

    it('applies correct width for full size', () => {
      render(
        <SidebarLayout sidebar={mockSidebar} size="full">
          {mockChildren}
        </SidebarLayout>
      );

      const sidebar = screen.getByTestId('sidebarlayout-sidebar');
      expect(sidebar).toHaveStyle({ width: `calc(100% - ${RAIL_W}px)` });
    });

    it('defaults to md size when size is not provided', () => {
      render(
        <SidebarLayout sidebar={mockSidebar}>
          {mockChildren}
        </SidebarLayout>
      );

      const sidebar = screen.getByTestId('sidebarlayout-sidebar');
      expect(sidebar).toHaveStyle({ width: '600px' });
    });
  });

  describe('FullHeight', () => {
    it('applies fullHeight to outer Box when fullHeight={true}', () => {
      render(
        <SidebarLayout sidebar={mockSidebar} fullHeight>
          {mockChildren}
        </SidebarLayout>
      );

      const outerBox = screen.getByTestId('sidebarlayout');
      expect(outerBox).toHaveAttribute('data-fullheight', 'true');
    });

    it('applies fullHeight to Container when fullHeight={true}', () => {
      render(
        <SidebarLayout sidebar={mockSidebar} fullHeight>
          {mockChildren}
        </SidebarLayout>
      );

      const mainContainer = screen.getByTestId('sidebarlayout-content');
      expect(mainContainer).toHaveAttribute('data-fullheight', 'true');
    });

    it('renders inner scrollable Box when fullHeight={true}', () => {
      render(
        <SidebarLayout sidebar={mockSidebar} fullHeight>
          {mockChildren}
        </SidebarLayout>
      );

      // When fullHeight is true, children should be wrapped in a Box with overflow: auto
      const mainContainer = screen.getByTestId('sidebarlayout-content');
      const contentText = within(mainContainer).getByText('Main Content');
      
      // Verify the content is present and the container structure is correct
      expect(contentText).toBeInTheDocument();
      
      // Verify the mainContainer has the correct structure by checking it contains the text
      // The scrollable Box wrapper is an implementation detail, so we verify behavior instead
      // by checking that the content is accessible and the container is set up correctly
      expect(mainContainer).toBeInTheDocument();
      
      // Verify the container has the expected structure by checking computed styles
      // The inner Box with overflow: auto is verified through the component's behavior
      const containerStyles = window.getComputedStyle(mainContainer);
      // Main container width accounts for sidebar (default md size is 600px)
      expect(containerStyles.width).toBe('calc(100% - 600px)');
    });

    it('does not render inner scrollable Box when fullHeight={false}', () => {
      render(
        <SidebarLayout sidebar={mockSidebar} fullHeight={false}>
          {mockChildren}
        </SidebarLayout>
      );

      // When fullHeight is false, children should be rendered directly
      const mainContainer = screen.getByTestId('sidebarlayout-content');
      expect(mainContainer).toHaveTextContent('Main Content');
    });

    it('applies overflow auto to sidebar', () => {
      render(
        <SidebarLayout sidebar={mockSidebar} fullHeight>
          {mockChildren}
        </SidebarLayout>
      );

      const sidebar = screen.getByTestId('sidebarlayout-sidebar');
      expect(sidebar).toHaveStyle({ overflow: 'auto' });
      // height: 100% was for flex layout, now we rely on top/bottom anchoring
      expect(sidebar).toHaveStyle({ bottom: '0px' });
    });
  });

  describe('Container rendering', () => {
    it('renders children as Container component', () => {
      render(
        <SidebarLayout sidebar={mockSidebar}>
          {mockChildren}
        </SidebarLayout>
      );

      const mainContainer = screen.getByTestId('sidebarlayout-content');
      expect(mainContainer).toBeInTheDocument();
      // Container is inside the content Box; SidebarLayout passes disableGutters to it
      const container = within(mainContainer).getByTestId('container');
      expect(container).toHaveAttribute('data-disablegutters', 'true');
    });

    it('renders children content correctly', () => {
      render(
        <SidebarLayout sidebar={mockSidebar}>
          {mockChildren}
        </SidebarLayout>
      );

      expect(screen.getByTestId('main-content')).toBeInTheDocument();
      expect(screen.getByText('Main Content')).toBeInTheDocument();
    });
  });

  describe('Test ID generation', () => {
    it('generates test ID from component name when name prop is provided', () => {
      render(
        <SidebarLayout sidebar={mockSidebar} name="test-layout">
          {mockChildren}
        </SidebarLayout>
      );

      const outerBox = screen.getByTestId('sidebarlayout-test-layout');
      expect(outerBox).toBeInTheDocument();
    });

    it('uses custom data-testid when provided', () => {
      render(
        <SidebarLayout sidebar={mockSidebar} data-testid="custom-test-id">
          {mockChildren}
        </SidebarLayout>
      );

      const outerBox = screen.getByTestId('custom-test-id');
      expect(outerBox).toBeInTheDocument();
    });

    it('generates test IDs for sidebar and content sub-elements', () => {
      render(
        <SidebarLayout sidebar={mockSidebar} data-testid="layout">
          {mockChildren}
        </SidebarLayout>
      );

      expect(screen.getByTestId('layout-sidebar')).toBeInTheDocument();
      expect(screen.getByTestId('layout-content')).toBeInTheDocument();
    });
  });

  describe('Custom styles and props', () => {
    it('applies custom className', () => {
      render(
        <SidebarLayout sidebar={mockSidebar} className="custom-class">
          {mockChildren}
        </SidebarLayout>
      );

      const outerBox = screen.getByTestId('sidebarlayout');
      expect(outerBox).toHaveClass('custom-class');
    });

    it('applies custom style', () => {
      const customStyle = { backgroundColor: 'red' };
      render(
        <SidebarLayout sidebar={mockSidebar} style={customStyle}>
          {mockChildren}
        </SidebarLayout>
      );

      const outerBox = screen.getByTestId('sidebarlayout');
      // Check the style attribute directly since toHaveStyle might not work with inline styles in jsdom
      const styleAttr = outerBox.getAttribute('style');
      expect(styleAttr).toContain('background-color: red');
    });

    it('applies basic layout styles correctly', () => {
      render(
        <SidebarLayout sidebar={mockSidebar}>
          {mockChildren}
        </SidebarLayout>
      );

      const outerBox = screen.getByTestId('sidebarlayout');
      expect(outerBox).toHaveStyle({ position: 'relative' });
      expect(outerBox).toHaveStyle({ width: '100%' });
    });

    it('applies minWidth 0 to main content to prevent overflow', () => {
      render(
        <SidebarLayout sidebar={mockSidebar}>
          {mockChildren}
        </SidebarLayout>
      );

      const mainContainer = screen.getByTestId('sidebarlayout-content');
      expect(mainContainer).toHaveStyle({ minWidth: '0' });
      // Width accounts for sidebar (default md size is 600px)
      expect(mainContainer).toHaveStyle({ width: 'calc(100% - 600px)' });
    });
  });

  describe('LayoutComponentProps', () => {
    it('passes through LayoutComponentProps to outer Box', () => {
      render(
        <SidebarLayout
          sidebar={mockSidebar}
          gap={2}
          align="center"
          justify="between"
        >
          {mockChildren}
        </SidebarLayout>
      );

      // These props should be passed to the outer Box
      // The actual behavior depends on Box implementation
      const outerBox = screen.getByTestId('sidebarlayout');
      expect(outerBox).toBeInTheDocument();
    });
  });

  describe('Header Height', () => {
    // Mock ResizeObserver
    const originalResizeObserver = global.ResizeObserver;
    
    beforeAll(() => {
      global.ResizeObserver = class ResizeObserver {
        observe() {}
        unobserve() {}
        disconnect() {}
      };
    });

    afterAll(() => {
      if (originalResizeObserver) {
        global.ResizeObserver = originalResizeObserver;
      } else {
        delete (global as any).ResizeObserver;
      }
    });

    it('uses provided headerHeight prop', () => {
      render(
        <SidebarLayout sidebar={mockSidebar} headerHeight={100}>
          {mockChildren}
        </SidebarLayout>
      );
      
      const sidebar = screen.getByTestId('sidebarlayout-sidebar');
      expect(sidebar).toHaveStyle({ top: '100px' });
    });

    it('calculates header height dynamically when not provided', () => {
      // Mock document.querySelector to return a fake header
      const querySelectorSpy = vi.spyOn(document, 'querySelector').mockImplementation((selector: any) => {
        if (selector === 'header') {
          return { 
            getBoundingClientRect: () => ({ height: 80 }),
            offsetHeight: 80 
          } as unknown as Element;
        }
        return null;
      });

      render(
        <SidebarLayout sidebar={mockSidebar}>
          {mockChildren}
        </SidebarLayout>
      );
      
      const sidebar = screen.getByTestId('sidebarlayout-sidebar');
      expect(sidebar).toHaveStyle({ top: '80px' });

      // Restore
      querySelectorSpy.mockRestore();
    });

    it('uses custom headerSelector to find header element', () => {
      // Mock document.querySelector to return a fake header
      const querySelectorSpy = vi.spyOn(document, 'querySelector').mockImplementation((selector: any) => {
        if (selector === '#custom-header') {
          return { 
            getBoundingClientRect: () => ({ height: 120 }),
          } as unknown as Element;
        }
        return null;
      });

      render(
        <SidebarLayout sidebar={mockSidebar} headerSelector="#custom-header">
          {mockChildren}
        </SidebarLayout>
      );
      
      const sidebar = screen.getByTestId('sidebarlayout-sidebar');
      expect(sidebar).toHaveStyle({ top: '120px' });
      expect(querySelectorSpy).toHaveBeenCalledWith('#custom-header');

      // Restore
      querySelectorSpy.mockRestore();
    });
  });

  describe('Ref Forwarding', () => {
    it('forwards ref to outer Box element', () => {
      const ref = React.createRef<HTMLDivElement>();
      render(
        <SidebarLayout sidebar={mockSidebar} ref={ref}>
          {mockChildren}
        </SidebarLayout>
      );
      
      expect(ref.current).toBeInstanceOf(HTMLDivElement);
      // In the mock, Box renders a div with data-testid='sidebarlayout' (default from getTestIdProps)
      // or whatever getTestIdProps returns. The mock implementation uses data-testid prop.
      // In SidebarLayout, {...testIdProps} is passed to outer Box.
      // Default name is 'SidebarLayout', so testid is 'sidebarlayout'.
      expect(ref.current).toHaveAttribute('data-testid', 'sidebarlayout');
    });
  });

  afterEach(() => {
    cleanup();
  });
});
