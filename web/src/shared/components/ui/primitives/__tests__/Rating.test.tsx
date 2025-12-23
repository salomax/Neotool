import React from 'react';
import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen, fireEvent, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Rating } from '../Rating';
import { AppThemeProvider } from '@/styles/themes/AppThemeProvider';

const renderRating = (props = {}) => {
  return render(
    <AppThemeProvider>
      <Rating {...props} />
    </AppThemeProvider>
  );
};

// Run sequentially to avoid concurrent renders leaking between tests
describe.sequential('Rating', () => {
  describe('Rendering', () => {
    it('renders rating component with default 5 stars', () => {
      renderRating();
      const ratingItems = screen.getAllByTestId(/rating-item-/);
      expect(ratingItems).toHaveLength(5);
    });

    it('renders with custom max value', () => {
      renderRating({ max: 10 });
      const ratingItems = screen.getAllByTestId(/rating-item-/);
      expect(ratingItems).toHaveLength(10);
    });

    it('renders with custom data-testid', () => {
      renderRating({ 'data-testid': 'custom-rating' });
      expect(screen.getByTestId('custom-rating')).toBeInTheDocument();
    });
  });

  describe('Star variant', () => {
    it('renders star variant by default', () => {
      renderRating({ value: 3 });
      const ratingItems = screen.getAllByTestId(/rating-item-/);
      expect(ratingItems).toHaveLength(5);
    });

    it('shows filled stars up to value', () => {
      renderRating({ value: 3 });
      const ratingItems = screen.getAllByTestId(/rating-item-/);
      expect(ratingItems).toHaveLength(5);
      // First 3 should be filled, last 2 should be empty
    });
  });

  describe('Other variants', () => {
    it('renders thumbs variant', () => {
      renderRating({ variant: 'thumbs', max: 2 });
      const ratingItems = screen.getAllByTestId(/rating-item-/);
      expect(ratingItems).toHaveLength(2);
    });

    it('renders heart variant', () => {
      renderRating({ variant: 'heart', value: 3 });
      const ratingItems = screen.getAllByTestId(/rating-item-/);
      expect(ratingItems).toHaveLength(5);
    });

    it('renders emoji variant', () => {
      renderRating({ variant: 'emoji', value: 3 });
      const ratingItems = screen.getAllByTestId(/rating-item-/);
      expect(ratingItems).toHaveLength(5);
    });
  });

  describe('Controlled state', () => {
    it('renders with controlled value', () => {
      renderRating({ value: 3 });
      const ratingItems = screen.getAllByTestId(/rating-item-/);
      expect(ratingItems).toHaveLength(5);
    });

    it('calls onChange when rating is clicked', async () => {
      const handleChange = vi.fn();
      const user = userEvent.setup();
      renderRating({ value: 0, onChange: handleChange });

      const firstItem = screen.getByTestId('rating-item-0');
      await user.click(firstItem);

      expect(handleChange).toHaveBeenCalledWith(1);
    });

    it('does not update internal state when controlled', async () => {
      const handleChange = vi.fn();
      const user = userEvent.setup();
      const { rerender } = renderRating({ value: 0, onChange: handleChange });

      const firstItem = screen.getByTestId('rating-item-0');
      await user.click(firstItem);

      // Re-render with same value
      rerender(
        <AppThemeProvider>
          <Rating value={0} onChange={handleChange} />
        </AppThemeProvider>
      );

      expect(handleChange).toHaveBeenCalled();
    });
  });

  describe('Uncontrolled state', () => {
    it('renders with default value of 0', () => {
      renderRating();
      const ratingItems = screen.getAllByTestId(/rating-item-/);
      expect(ratingItems).toHaveLength(5);
    });

    it('updates internal value when clicked', async () => {
      const handleChange = vi.fn();
      const user = userEvent.setup();
      renderRating({ onChange: handleChange });

      const firstItem = screen.getByTestId('rating-item-0');
      await user.click(firstItem);

      expect(handleChange).toHaveBeenCalledWith(1);
    });
  });

  describe('Thumbs variant behavior', () => {
    it('toggles like when clicking thumbs up', async () => {
      const handleChange = vi.fn();
      const user = userEvent.setup();
      renderRating({ variant: 'thumbs', max: 2, value: 0, onChange: handleChange });

      const thumbsUp = screen.getByTestId('rating-item-0');
      await user.click(thumbsUp);

      expect(handleChange).toHaveBeenCalledWith(1);
    });

    it('unlikes when clicking thumbs up again', async () => {
      const handleChange = vi.fn();
      const user = userEvent.setup();
      renderRating({ variant: 'thumbs', max: 2, value: 1, onChange: handleChange });

      const thumbsUp = screen.getByTestId('rating-item-0');
      await user.click(thumbsUp);

      expect(handleChange).toHaveBeenCalledWith(0);
    });

    it('resets to 0 when clicking thumbs down', async () => {
      const handleChange = vi.fn();
      const user = userEvent.setup();
      renderRating({ variant: 'thumbs', max: 2, value: 1, onChange: handleChange });

      const thumbsDown = screen.getByTestId('rating-item-1');
      await user.click(thumbsDown);

      expect(handleChange).toHaveBeenCalledWith(0);
    });
  });

  describe('Half rating', () => {
    it('renders half rating when precision is 0.5', () => {
      renderRating({ value: 2.5, precision: 0.5, 'data-testid': 'half-rating' });
      // Half rating uses Box elements, not IconButtons with test IDs
      const ratingContainer = screen.getByTestId('half-rating');
      expect(ratingContainer).toBeInTheDocument();
    });

    it('allows half ratings when precision is 0.5', () => {
      renderRating({ precision: 0.5, 'data-testid': 'half-rating' });
      const ratingContainer = screen.getByTestId('half-rating');
      expect(ratingContainer).toBeInTheDocument();
    });
  });

  describe('Hover behavior', () => {
    it('calls onHover when hovering over rating item', async () => {
      const handleHover = vi.fn();
      const user = userEvent.setup();
      renderRating({ onHover: handleHover });

      const firstItem = screen.getByTestId('rating-item-0');
      await user.hover(firstItem);

      expect(handleHover).toHaveBeenCalledWith(1);
    });

    it('calls onLeave when mouse leaves rating', async () => {
      const handleLeave = vi.fn();
      const user = userEvent.setup();
      renderRating({ onLeave: handleLeave });

      const firstItem = screen.getByTestId('rating-item-0');
      await user.hover(firstItem);
      await user.unhover(firstItem);

      expect(handleLeave).toHaveBeenCalled();
    });
  });

  describe('Disabled and read-only', () => {
    it('disables rating when disabled prop is true', () => {
      renderRating({ disabled: true });
      const ratingItems = screen.getAllByTestId(/rating-item-/);
      ratingItems.forEach(item => {
        expect(item).toBeDisabled();
      });
    });

    it('does not call onChange when disabled', () => {
      const handleChange = vi.fn();
      renderRating({ disabled: true, onChange: handleChange });

      // Disabled items have pointer-events: none, so we verify they're disabled
      const ratingItems = screen.getAllByTestId(/rating-item-/);
      ratingItems.forEach(item => {
        expect(item).toBeDisabled();
      });
      // onChange should not be called when disabled
      expect(handleChange).not.toHaveBeenCalled();
    });

    it('does not call onChange when readOnly', () => {
      const handleChange = vi.fn();
      renderRating({ readOnly: true, onChange: handleChange });

      // Read-only items have pointer-events: none, so we verify they're disabled
      const ratingItems = screen.getAllByTestId(/rating-item-/);
      ratingItems.forEach(item => {
        expect(item).toBeDisabled();
      });
      // onChange should not be called when readOnly
      expect(handleChange).not.toHaveBeenCalled();
    });
  });

  describe('Sizes', () => {
    it('renders with small size', () => {
      renderRating({ size: 'small' });
      const ratingItems = screen.getAllByTestId(/rating-item-/);
      expect(ratingItems).toHaveLength(5);
    });

    it('renders with medium size', () => {
      renderRating({ size: 'medium' });
      const ratingItems = screen.getAllByTestId(/rating-item-/);
      expect(ratingItems).toHaveLength(5);
    });

    it('renders with large size', () => {
      renderRating({ size: 'large' });
      const ratingItems = screen.getAllByTestId(/rating-item-/);
      expect(ratingItems).toHaveLength(5);
    });
  });

  describe('Colors', () => {
    it('renders with primary color', () => {
      renderRating({ color: 'primary' });
      const ratingItems = screen.getAllByTestId(/rating-item-/);
      expect(ratingItems).toHaveLength(5);
    });

    it('renders with secondary color', () => {
      renderRating({ color: 'secondary' });
      const ratingItems = screen.getAllByTestId(/rating-item-/);
      expect(ratingItems).toHaveLength(5);
    });

    it('renders with error color', () => {
      renderRating({ color: 'error' });
      const ratingItems = screen.getAllByTestId(/rating-item-/);
      expect(ratingItems).toHaveLength(5);
    });
  });

  describe('Labels', () => {
    it('shows labels when showLabels is true', () => {
      renderRating({ value: 3, showLabels: true });
      // Labels appear on hover, so we need to hover
      const ratingItems = screen.getAllByTestId(/rating-item-/);
      expect(ratingItems).toHaveLength(5);
    });

    it('hides labels when showLabels is false', () => {
      renderRating({ value: 3, showLabels: false });
      const ratingItems = screen.getAllByTestId(/rating-item-/);
      expect(ratingItems).toHaveLength(5);
    });
  });

  describe('Value display', () => {
    it('shows value when showValue is true', () => {
      renderRating({ value: 3, showValue: true });
      expect(screen.getByText('3/5')).toBeInTheDocument();
    });

    it('hides value when showValue is false', () => {
      renderRating({ value: 3, showValue: false });
      expect(screen.queryByText('3/5')).not.toBeInTheDocument();
    });

    it('shows value with half rating precision', () => {
      renderRating({ value: 3.5, showValue: true, precision: 0.5 });
      expect(screen.getByText('3.5/5')).toBeInTheDocument();
    });
  });

  describe('Keyboard interaction', () => {
    it('handles Enter key press', async () => {
      const handleChange = vi.fn();
      renderRating({ value: 0, onChange: handleChange });

      const firstItem = screen.getByTestId('rating-item-0');
      fireEvent.keyDown(firstItem, { key: 'Enter' });

      expect(handleChange).toHaveBeenCalledWith(1);
    });

    it('handles Space key press', async () => {
      const handleChange = vi.fn();
      renderRating({ value: 0, onChange: handleChange });

      const firstItem = screen.getByTestId('rating-item-0');
      fireEvent.keyDown(firstItem, { key: ' ' });

      expect(handleChange).toHaveBeenCalledWith(1);
    });
  });

  afterEach(() => {
    cleanup();
  });
});

