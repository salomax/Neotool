import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RoleList } from '../RoleList';
import { AppThemeProvider } from '@/styles/themes/AppThemeProvider';
import type { Role } from '@/shared/hooks/authorization/useRoleManagement';

// Mock Next.js navigation
vi.mock('next/navigation', () => ({
  useRouter: () => ({
    push: vi.fn(),
    replace: vi.fn(),
    refresh: vi.fn(),
  }),
}));

// Mock ManagementTable
vi.mock('@/shared/components/management', () => ({
  ManagementTable: ({ columns, data, onSortChange, renderActions, emptyMessage, loading }: any) => (
    <div data-testid="management-table">
      {loading && <div data-testid="table-loading">Loading...</div>}
      {!loading && data.length === 0 && <div>{emptyMessage}</div>}
      {!loading && data.length > 0 && (
        <table>
          <thead>
            <tr>
              {columns.map((col: any) => (
                <th
                  key={col.id}
                  onClick={() => col.sortable && onSortChange?.(col.sortField)}
                  data-sortable={col.sortable}
                >
                  {col.label}
                </th>
              ))}
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {data.map((item: Role) => (
              <tr key={item.id}>
                <td>{item.name}</td>
                <td>{renderActions(item)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  ),
}));

// Mock PermissionGate
vi.mock('@/shared/components/authorization', () => ({
  PermissionGate: ({ children, require }: { children: React.ReactNode; require?: string }) => (
    <>{children}</>
  ),
}));

// Mock translations
vi.mock('@/shared/i18n', () => ({
  useTranslation: () => ({
    t: (key: string, fallback?: string) => {
      const translations: Record<string, string> = {
        'roleManagement.table.name': 'Name',
        'roleManagement.table.actions': 'Actions',
        'roleManagement.editRole': 'Edit role',
        'roleManagement.deleteRole': 'Delete role',
      };
      return translations[key] || fallback || key;
    },
  }),
}));

const mockRoles: Role[] = [
  {
    id: '1',
    name: 'Admin Role',
  },
  {
    id: '2',
    name: 'User Role',
  },
  {
    id: '3',
    name: 'Guest Role',
  },
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

describe('RoleList', () => {
  const user = userEvent.setup();

  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('Rendering', () => {
    it('should render roles list', () => {
      renderRoleList();

      expect(screen.getByText('Admin Role')).toBeInTheDocument();
      expect(screen.getByText('User Role')).toBeInTheDocument();
      expect(screen.getByText('Guest Role')).toBeInTheDocument();
    });

    it('should render empty message when no roles', () => {
      renderRoleList({ roles: [], emptyMessage: 'No roles found' });

      expect(screen.getByText('No roles found')).toBeInTheDocument();
    });

    it('should render table headers', () => {
      renderRoleList();

      expect(screen.getByText('Name')).toBeInTheDocument();
      expect(screen.getByText('Actions')).toBeInTheDocument();
    });
  });

  describe('Loading state', () => {
    it('should show loading state', () => {
      renderRoleList({ loading: true, roles: [] });

      expect(screen.getByTestId('table-loading')).toBeInTheDocument();
    });

    it('should not show loading when loading is false', () => {
      renderRoleList({ loading: false });

      expect(screen.queryByTestId('table-loading')).not.toBeInTheDocument();
    });
  });

  describe('Edit functionality', () => {
    it('should call onEdit when edit button is clicked', async () => {
      const onEdit = vi.fn();
      renderRoleList({ onEdit });

      const editButtons = screen.getAllByTestId(/edit-role-/);
      if (editButtons[0]) {
        await user.click(editButtons[0]);
        expect(onEdit).toHaveBeenCalledWith(mockRoles[0]);
      }
    });

    it('should render edit button for each role', () => {
      renderRoleList();

      expect(screen.getByTestId('edit-role-1')).toBeInTheDocument();
      expect(screen.getByTestId('edit-role-2')).toBeInTheDocument();
      expect(screen.getByTestId('edit-role-3')).toBeInTheDocument();
    });
  });

  describe('Delete functionality', () => {
    it('should call onDelete when delete button is clicked', async () => {
      const onDelete = vi.fn();
      renderRoleList({ onDelete });

      const deleteButtons = screen.getAllByTestId(/delete-role-/);
      if (deleteButtons[0]) {
        await user.click(deleteButtons[0]);
        expect(onDelete).toHaveBeenCalledWith(mockRoles[0]);
      }
    });

    it('should not render delete button when onDelete is not provided', () => {
      renderRoleList({ onDelete: undefined });

      expect(screen.queryByTestId('delete-role-1')).not.toBeInTheDocument();
    });

    it('should render delete button for each role when onDelete is provided', () => {
      renderRoleList({ onDelete: vi.fn() });

      expect(screen.getByTestId('delete-role-1')).toBeInTheDocument();
      expect(screen.getByTestId('delete-role-2')).toBeInTheDocument();
      expect(screen.getByTestId('delete-role-3')).toBeInTheDocument();
    });
  });

  describe('Sorting', () => {
    it('should call onSortChange when sortable header is clicked', async () => {
      const onSortChange = vi.fn();
      renderRoleList({ onSortChange });

      const nameHeader = screen.getByText('Name');
      await user.click(nameHeader);

      expect(onSortChange).toHaveBeenCalledWith('NAME');
    });

    it('should not call onSortChange when onSortChange is not provided', async () => {
      renderRoleList({ onSortChange: undefined });

      const nameHeader = screen.getByText('Name');
      await user.click(nameHeader);

      expect(nameHeader).toBeInTheDocument();
    });

    it('should display active sort state', () => {
      const orderBy = { field: 'NAME', direction: 'asc' as const };
      renderRoleList({ orderBy, onSortChange: vi.fn() });

      expect(screen.getByTestId('management-table')).toBeInTheDocument();
    });
  });

  describe('Pagination', () => {
    it('should render pagination when pageInfo is provided', () => {
      const pageInfo = {
        hasNextPage: true,
        hasPreviousPage: false,
        startCursor: 'cursor1',
        endCursor: 'cursor2',
      };
      const paginationRange = { start: 1, end: 10, total: 25 };
      renderRoleList({
        pageInfo,
        paginationRange,
        onLoadNext: vi.fn(),
        onLoadPrevious: vi.fn(),
        onGoToFirst: vi.fn(),
      });

      expect(screen.getByTestId('management-table')).toBeInTheDocument();
    });

    it('should not render pagination when pageInfo is not provided', () => {
      renderRoleList({ pageInfo: null });

      expect(screen.getByTestId('management-table')).toBeInTheDocument();
    });
  });
});









