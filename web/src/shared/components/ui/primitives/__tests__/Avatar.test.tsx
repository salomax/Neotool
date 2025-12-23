import React from 'react';
import { describe, it, expect, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import { Avatar } from '../Avatar';
import { AppThemeProvider } from '@/styles/themes/AppThemeProvider';

const renderAvatar = (props = {}) => {
  return render(
    <AppThemeProvider>
      <Avatar {...props} />
    </AppThemeProvider>
  );
};

describe.sequential('Avatar', () => {
  afterEach(() => {
    cleanup();
  });

  describe('Initials generation', () => {
    it('renders initials from full name', () => {
      renderAvatar({ name: 'John Doe' });
      expect(screen.getByText('JD')).toBeInTheDocument();
    });

    it('renders single initial for single name', () => {
      renderAvatar({ name: 'John' });
      expect(screen.getByText('J')).toBeInTheDocument();
    });

    it('renders question mark when name is not provided', () => {
      renderAvatar();
      expect(screen.getByText('?')).toBeInTheDocument();
    });

    it('renders question mark when name is empty string', () => {
      renderAvatar({ name: '' });
      expect(screen.getByText('?')).toBeInTheDocument();
    });

    it('handles multiple words correctly', () => {
      renderAvatar({ name: 'John Michael Doe' });
      expect(screen.getByText('JD')).toBeInTheDocument();
    });

    it('handles name with extra spaces', () => {
      renderAvatar({ name: '  John   Doe  ' });
      expect(screen.getByText('JD')).toBeInTheDocument();
    });

    it('handles single character name', () => {
      renderAvatar({ name: 'J' });
      expect(screen.getByText('J')).toBeInTheDocument();
    });

    it('uppercases initials', () => {
      renderAvatar({ name: 'john doe' });
      expect(screen.getByText('JD')).toBeInTheDocument();
    });
  });

  describe('Image source', () => {
    it('renders image when src is provided', () => {
      renderAvatar({ src: '/test-image.jpg', name: 'John Doe' });
      const avatar = screen.getByRole('img', { hidden: true });
      expect(avatar).toHaveAttribute('src', '/test-image.jpg');
    });

    it('renders initials when src is not provided', () => {
      renderAvatar({ name: 'John Doe' });
      expect(screen.getByText('JD')).toBeInTheDocument();
    });

    it('renders children when provided instead of initials', () => {
      renderAvatar({ name: 'John Doe', children: <span>Custom</span> });
      expect(screen.getByText('Custom')).toBeInTheDocument();
      expect(screen.queryByText('JD')).not.toBeInTheDocument();
    });
  });

  describe('Sizes', () => {
    it('renders with default medium size', () => {
      renderAvatar({ name: 'John Doe' });
      const avatar = screen.getByTestId('avatar-john-doe');
      expect(avatar).toBeInTheDocument();
      expect(screen.getByText('JD')).toBeInTheDocument();
    });

    it('renders with small size', () => {
      renderAvatar({ name: 'John Doe', size: 'small' });
      const avatar = screen.getByTestId('avatar-john-doe');
      expect(avatar).toBeInTheDocument();
      expect(screen.getByText('JD')).toBeInTheDocument();
    });

    it('renders with large size', () => {
      renderAvatar({ name: 'John Doe', size: 'large' });
      const avatar = screen.getByTestId('avatar-john-doe');
      expect(avatar).toBeInTheDocument();
      expect(screen.getByText('JD')).toBeInTheDocument();
    });
  });

  describe('Test IDs', () => {
    it('renders with custom data-testid', () => {
      renderAvatar({ 'data-testid': 'custom-avatar' });
      expect(screen.getByTestId('custom-avatar')).toBeInTheDocument();
    });

    it('generates data-testid from name prop', () => {
      renderAvatar({ name: 'john-doe' });
      expect(screen.getByTestId('avatar-john-doe')).toBeInTheDocument();
    });

    it('prioritizes custom data-testid over generated one', () => {
      renderAvatar({ name: 'john-doe', 'data-testid': 'custom-avatar' });
      expect(screen.getByTestId('custom-avatar')).toBeInTheDocument();
      expect(screen.queryByTestId('avatar-john-doe')).not.toBeInTheDocument();
    });
  });
});
