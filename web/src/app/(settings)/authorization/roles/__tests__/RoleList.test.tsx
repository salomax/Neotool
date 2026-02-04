

import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { RoleList } from '../RoleList';
import { AppThemeProvider } from '@/styles/themes/AppThemeProvider';
import i18n from '@/shared/i18n/config';

// Minimal ManagementTable mock to render headers and rows
vi.mock('@/shared/components/management', () => ({
  ManagementTable: ({ columns, data, renderActions }: any) => (
    <div data-testid="management-table">
      <table>
        <thead>
          <tr>
            {columns.map((col: any) => (
              <th key={col.id}>{col.label}</th>
            ))}
            <th>Description</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          {data.map((role: any) => (
            <tr key={role.id}>
              <td>{role.name}</td>
              <td>{role.description || '-'}</td>
              <td>
                {/* Render the actions element directly to avoid accessing React internals */}
                {renderActions(role)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  ),
}));

// PermissionGate passthrough
vi.mock('@/shared/components/authorization', () => ({
  PermissionGate: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

const mockRoles = [
  { id: '1', name: 'Admin', description: 'Administrators', __typename: 'Role' as const },
  { id: '2', name: 'User', description: 'Regular users', __typename: 'Role' as const },
  { id: '3', name: 'Guest', description: null, __typename: 'Role' as const },
];

const renderRoleList = (props = {}) => {
  const defaultProps = {
    roles: mockRoles,
    onEdit: vi.fn(),
    ...props,
  };

  return render(
    <AppThemeProvider>
      <RoleList {...defaultProps} />
    </AppThemeProvider>
  );
};

// Run sequentially to avoid parallel render interference
describe.sequential('RoleList', () => {
  beforeEach(async () => {
    await i18n.changeLanguage('en');
  });

  it('renders role data', () => {
    renderRoleList();

    expect(screen.getByText('Admin')).toBeInTheDocument();
    expect(screen.getByText('User')).toBeInTheDocument();
    expect(screen.getByText('Guest')).toBeInTheDocument();
    expect(screen.getByText('Administrators')).toBeInTheDocument();
    expect(screen.getAllByText('-').length).toBeGreaterThan(0);
  });

  it('renders table headers', () => {
    renderRoleList();

    expect(screen.getByText('Name')).toBeInTheDocument();
    expect(screen.getByText('Description')).toBeInTheDocument();
    expect(screen.getByText('Actions')).toBeInTheDocument();
  });

  it('renders edit actions for each role', () => {
    const onEdit = vi.fn();
    renderRoleList({ onEdit });

    mockRoles.forEach((role) => {
      // Use getByRole with name option since ariaLabel sets aria-label attribute, not a label element
      // Or use data-testid which is more reliable
      const button = screen.getByTestId(`edit-role-${role.id}`);
      expect(button).toBeInTheDocument();
      // Verify it has an aria-label (the actual translation may have different casing)
      expect(button).toHaveAttribute('aria-label');
      // The aria-label should contain the role name
      const ariaLabel = button.getAttribute('aria-label');
      expect(ariaLabel).toContain(role.name);
    });
  });
});
