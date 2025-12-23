import React from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { CartIcon } from '../CartIcon';
import { AppThemeProvider } from '@/styles/themes/AppThemeProvider';

// Mock the hook to control cart state
vi.mock('@/lib/hooks/cart/useCart', () => ({
  useCart: vi.fn(),
}));

import { useCart } from '@/lib/hooks/cart/useCart';

const mockUseCart = vi.mocked(useCart);

const renderCartIcon = (props?: React.ComponentProps<typeof CartIcon>) => {
  return render(
    <AppThemeProvider>
      <CartIcon {...props} />
    </AppThemeProvider>
  );
};

describe.sequential('CartIcon', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it('renders cart icon', () => {
    mockUseCart.mockReturnValue({
      itemCount: 0,
      items: [],
      totalPriceCents: 0,
      isEmpty: true,
      addItem: vi.fn(),
      removeItem: vi.fn(),
      updateQuantity: vi.fn(),
      clearCart: vi.fn(),
    });

    renderCartIcon();
    
    const button = screen.getByRole('button', { name: /shopping cart/i });
    expect(button).toBeInTheDocument();
  });

  it('displays badge when cart has items', () => {
    mockUseCart.mockReturnValue({
      itemCount: 3,
      items: [],
      totalPriceCents: 0,
      isEmpty: false,
      addItem: vi.fn(),
      removeItem: vi.fn(),
      updateQuantity: vi.fn(),
      clearCart: vi.fn(),
    });

    renderCartIcon();
    
    // MUI Badge renders the badge content
    expect(screen.getByText('3')).toBeInTheDocument();
  });

  it('hides badge when cart is empty', () => {
    mockUseCart.mockReturnValue({
      itemCount: 0,
      items: [],
      totalPriceCents: 0,
      isEmpty: true,
      addItem: vi.fn(),
      removeItem: vi.fn(),
      updateQuantity: vi.fn(),
      clearCart: vi.fn(),
    });

    renderCartIcon();
    
    const button = screen.getByRole('button', { name: /shopping cart/i });
    expect(button).toBeInTheDocument();
    // Badge should not be visible when itemCount is 0
    expect(screen.queryByText('0')).not.toBeInTheDocument();
  });

  it('calls onClick when clicked', async () => {
    mockUseCart.mockReturnValue({
      itemCount: 0,
      items: [],
      totalPriceCents: 0,
      isEmpty: true,
      addItem: vi.fn(),
      removeItem: vi.fn(),
      updateQuantity: vi.fn(),
      clearCart: vi.fn(),
    });

    const handleClick = vi.fn();
    const user = userEvent.setup();
    
    renderCartIcon({ onClick: handleClick });
    
    const button = screen.getByRole('button', { name: /shopping cart/i });
    await user.click(button);
    
    expect(handleClick).toHaveBeenCalledTimes(1);
  });

  it('uses custom aria-label when provided', () => {
    mockUseCart.mockReturnValue({
      itemCount: 0,
      items: [],
      totalPriceCents: 0,
      isEmpty: true,
      addItem: vi.fn(),
      removeItem: vi.fn(),
      updateQuantity: vi.fn(),
      clearCart: vi.fn(),
    });

    renderCartIcon({ 'aria-label': 'Custom cart label' });
    
    const button = screen.getByRole('button', { name: /custom cart label/i });
    expect(button).toBeInTheDocument();
  });

  it('uses default aria-label when not provided', () => {
    mockUseCart.mockReturnValue({
      itemCount: 0,
      items: [],
      totalPriceCents: 0,
      isEmpty: true,
      addItem: vi.fn(),
      removeItem: vi.fn(),
      updateQuantity: vi.fn(),
      clearCart: vi.fn(),
    });

    renderCartIcon();
    
    const button = screen.getByRole('button', { name: /shopping cart/i });
    expect(button).toBeInTheDocument();
  });

  it('passes through other IconButton props', () => {
    mockUseCart.mockReturnValue({
      itemCount: 0,
      items: [],
      totalPriceCents: 0,
      isEmpty: true,
      addItem: vi.fn(),
      removeItem: vi.fn(),
      updateQuantity: vi.fn(),
      clearCart: vi.fn(),
    });

    renderCartIcon({ disabled: true, 'data-testid': 'cart-icon' });
    
    const button = screen.getByTestId('cart-icon');
    expect(button).toBeDisabled();
  });
});
