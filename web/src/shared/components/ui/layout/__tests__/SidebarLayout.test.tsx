import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
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

vi.mock('../Box', () => ({
  Box: ({ children, fullHeight, sx, className, style, 'data-testid': dataTestId, ...props }: any) => {
    return (
      <div
        data-testid={dataTestId || 'box'}
        data-fullheight={fullHeight}
        className={className}
        style={{ ...sx, ...style }}
        {...props}
      >
        {children}
      </div>
    );
  },
}));

describe('SidebarLayout', () => {
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
      const { container } = render(
        <SidebarLayout sidebar={mockSidebar} side="right">
          {mockChildren}
        </SidebarLayout>
      );

      // Check flex direction is row-reverse for right side
      const outerBox = container.querySelector('[data-testid="SidebarLayout"]');
      expect(outerBox).toHaveStyle({ flexDirection: 'row-reverse' });
    });

    it('renders sidebar on left side by default', () => {
      const { container } = render(
        <SidebarLayout sidebar={mockSidebar}>
          {mockChildren}
        </SidebarLayout>
      );

      const outerBox = container.querySelector('[data-testid="SidebarLayout"]');
      expect(outerBox).toHaveStyle({ flexDirection: 'row' });
    });
  });

  describe('Size presets', () => {
    it('applies correct width for sm size (400px)', () => {
      const { container } = render(
        <SidebarLayout sidebar={mockSidebar} size="sm">
          {mockChildren}
        </SidebarLayout>
      );

      const sidebar = container.querySelector('[data-testid="SidebarLayout-sidebar"]');
      expect(sidebar).toHaveStyle({ width: '400px' });
    });

    it('applies correct width for md size (600px)', () => {
      const { container } = render(
        <SidebarLayout sidebar={mockSidebar} size="md">
          {mockChildren}
        </SidebarLayout>
      );

      const sidebar = container.querySelector('[data-testid="SidebarLayout-sidebar"]');
      expect(sidebar).toHaveStyle({ width: '600px' });
    });

    it('applies correct width for lg size (800px)', () => {
      const { container } = render(
        <SidebarLayout sidebar={mockSidebar} size="lg">
          {mockChildren}
        </SidebarLayout>
      );

      const sidebar = container.querySelector('[data-testid="SidebarLayout-sidebar"]');
      expect(sidebar).toHaveStyle({ width: '800px' });
    });

    it('applies correct width for full size', () => {
      const { container } = render(
        <SidebarLayout sidebar={mockSidebar} size="full">
          {mockChildren}
        </SidebarLayout>
      );

      const sidebar = container.querySelector('[data-testid="SidebarLayout-sidebar"]');
      expect(sidebar).toHaveStyle({ width: `calc(100% - ${RAIL_W}px)` });
    });

    it('defaults to md size when size is not provided', () => {
      const { container } = render(
        <SidebarLayout sidebar={mockSidebar}>
          {mockChildren}
        </SidebarLayout>
      );

      const sidebar = container.querySelector('[data-testid="SidebarLayout-sidebar"]');
      expect(sidebar).toHaveStyle({ width: '600px' });
    });
  });

  describe('FullHeight', () => {
    it('applies fullHeight to outer Box when fullHeight={true}', () => {
      const { container } = render(
        <SidebarLayout sidebar={mockSidebar} fullHeight>
          {mockChildren}
        </SidebarLayout>
      );

      const outerBox = container.querySelector('[data-testid="SidebarLayout"]');
      expect(outerBox).toHaveAttribute('data-fullheight', 'true');
    });

    it('applies fullHeight to Container when fullHeight={true}', () => {
      const { container } = render(
        <SidebarLayout sidebar={mockSidebar} fullHeight>
          {mockChildren}
        </SidebarLayout>
      );

      const mainContainer = container.querySelector('[data-testid="SidebarLayout-content"]');
      expect(mainContainer).toHaveAttribute('data-fullheight', 'true');
    });

    it('renders inner scrollable Box when fullHeight={true}', () => {
      const { container } = render(
        <SidebarLayout sidebar={mockSidebar} fullHeight>
          {mockChildren}
        </SidebarLayout>
      );

      // When fullHeight is true, children should be wrapped in a Box with overflow: auto
      const scrollableBox = container.querySelector('[data-testid="SidebarLayout-content"] > div');
      expect(scrollableBox).toBeInTheDocument();
      expect(scrollableBox).toHaveStyle({ overflow: 'auto' });
      expect(scrollableBox).toHaveStyle({ flex: '1' });
    });

    it('does not render inner scrollable Box when fullHeight={false}', () => {
      const { container } = render(
        <SidebarLayout sidebar={mockSidebar} fullHeight={false}>
          {mockChildren}
        </SidebarLayout>
      );

      // When fullHeight is false, children should be rendered directly
      const mainContainer = container.querySelector('[data-testid="SidebarLayout-content"]');
      expect(mainContainer).toHaveTextContent('Main Content');
    });

    it('applies overflow auto to sidebar when fullHeight={true}', () => {
      const { container } = render(
        <SidebarLayout sidebar={mockSidebar} fullHeight>
          {mockChildren}
        </SidebarLayout>
      );

      const sidebar = container.querySelector('[data-testid="SidebarLayout-sidebar"]');
      expect(sidebar).toHaveStyle({ overflow: 'auto' });
      expect(sidebar).toHaveStyle({ height: '100%' });
    });
  });

  describe('Container rendering', () => {
    it('renders children as Container component', () => {
      const { container } = render(
        <SidebarLayout sidebar={mockSidebar}>
          {mockChildren}
        </SidebarLayout>
      );

      const mainContainer = container.querySelector('[data-testid="SidebarLayout-content"]');
      expect(mainContainer).toBeInTheDocument();
      expect(mainContainer).toHaveAttribute('data-disablegutters', 'true');
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
      const { container } = render(
        <SidebarLayout sidebar={mockSidebar} name="test-layout">
          {mockChildren}
        </SidebarLayout>
      );

      const outerBox = container.querySelector('[data-testid="SidebarLayout-test-layout"]');
      expect(outerBox).toBeInTheDocument();
    });

    it('uses custom data-testid when provided', () => {
      const { container } = render(
        <SidebarLayout sidebar={mockSidebar} data-testid="custom-test-id">
          {mockChildren}
        </SidebarLayout>
      );

      const outerBox = container.querySelector('[data-testid="custom-test-id"]');
      expect(outerBox).toBeInTheDocument();
    });

    it('generates test IDs for sidebar and content sub-elements', () => {
      const { container } = render(
        <SidebarLayout sidebar={mockSidebar} data-testid="layout">
          {mockChildren}
        </SidebarLayout>
      );

      expect(container.querySelector('[data-testid="layout-sidebar"]')).toBeInTheDocument();
      expect(container.querySelector('[data-testid="layout-content"]')).toBeInTheDocument();
    });
  });

  describe('Custom styles and props', () => {
    it('applies custom className', () => {
      const { container } = render(
        <SidebarLayout sidebar={mockSidebar} className="custom-class">
          {mockChildren}
        </SidebarLayout>
      );

      const outerBox = container.querySelector('[data-testid="SidebarLayout"]');
      expect(outerBox).toHaveClass('custom-class');
    });

    it('applies custom style', () => {
      const customStyle = { backgroundColor: 'red' };
      const { container } = render(
        <SidebarLayout sidebar={mockSidebar} style={customStyle}>
          {mockChildren}
        </SidebarLayout>
      );

      const outerBox = container.querySelector('[data-testid="SidebarLayout"]');
      expect(outerBox).toHaveStyle({ backgroundColor: 'red' });
    });

    it('applies flex layout styles correctly', () => {
      const { container } = render(
        <SidebarLayout sidebar={mockSidebar}>
          {mockChildren}
        </SidebarLayout>
      );

      const outerBox = container.querySelector('[data-testid="SidebarLayout"]');
      expect(outerBox).toHaveStyle({ display: 'flex' });
      expect(outerBox).toHaveStyle({ width: '100%' });
    });

    it('applies minWidth 0 to main content to prevent overflow', () => {
      const { container } = render(
        <SidebarLayout sidebar={mockSidebar}>
          {mockChildren}
        </SidebarLayout>
      );

      const mainContainer = container.querySelector('[data-testid="SidebarLayout-content"]');
      expect(mainContainer).toHaveStyle({ minWidth: '0' });
      expect(mainContainer).toHaveStyle({ flex: '1' });
    });
  });

  describe('LayoutComponentProps', () => {
    it('passes through LayoutComponentProps to outer Box', () => {
      const { container } = render(
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
      const outerBox = container.querySelector('[data-testid="SidebarLayout"]');
      expect(outerBox).toBeInTheDocument();
    });
  });
});
