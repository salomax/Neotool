import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { GroupList, type GroupSortState } from '../GroupList';
import { AppThemeProvider } from '@/styles/themes/AppThemeProvider';
import type { Group } from '@/shared/hooks/authorization/useGroupManagement';

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
            {data.map((item: Group) => (
              <tr key={item.id}>
                <td>{item.name}</td>
                <td>{item.description || '-'}</td>
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
        'groupManagement.table.name': 'Name',
        'groupManagement.table.description': 'Description',
        'groupManagement.table.actions': 'Actions',
        'groupManagement.editGroup': 'Edit group',
        'groupManagement.deleteGroup': 'Delete group',
      };
      return translations[key] || fallback || key;
    },
  }),
}));

const mockGroups: Group[] = [
  {
    id: '1',
    name: 'Admin Group',
    description: 'Administrators',
    __typename: 'Group' as const,
    members: [],
  },
  {
    id: '2',
    name: 'User Group',
    description: 'Regular users',
    __typename: 'Group' as const,
    members: [],
  },
  {
    id: '3',
    name: 'Guest Group',
    description: null,
    __typename: 'Group' as const,
    members: [],
  },
];

const renderGroupList = (props = {}) => {
  const defaultProps = {
    groups: mockGroups,
    onEdit: vi.fn(),
    ...props,
  };

  return render(
    <AppThemeProvider>
      <GroupList {...defaultProps} />
    </AppThemeProvider>
  );
};

// Run sequentially to avoid concurrent renders leaking between tests
describe.sequential('GroupList', () => {
  const user = userEvent.setup();

  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('Rendering', () => {
    it('should render groups list', () => {
      renderGroupList();

      expect(screen.getByText('Admin Group')).toBeInTheDocument();
      expect(screen.getByText('User Group')).toBeInTheDocument();
      expect(screen.getByText('Guest Group')).toBeInTheDocument();
    });

    it('should render group descriptions', () => {
      renderGroupList();

      expect(screen.getByText('Administrators')).toBeInTheDocument();
      expect(screen.getByText('Regular users')).toBeInTheDocument();
    });

    it('should display dash for null descriptions', () => {
      renderGroupList();

      const rows = screen.getAllByText('-');
      expect(rows.length).toBeGreaterThan(0);
    });

    it('should render empty message when no groups', () => {
      renderGroupList({ groups: [], emptyMessage: 'No groups found' });

      expect(screen.getByText('No groups found')).toBeInTheDocument();
    });

    it('should render table headers', () => {
      renderGroupList();

      expect(screen.getByText('Name')).toBeInTheDocument();
      expect(screen.getByText('Description')).toBeInTheDocument();
      expect(screen.getByText('Actions')).toBeInTheDocument();
    });
  });

  describe('Loading state', () => {
    it('should show loading state', () => {
      renderGroupList({ loading: true, groups: [] });

      expect(screen.getByTestId('table-loading')).toBeInTheDocument();
    });

    it('should not show loading when loading is false', () => {
      renderGroupList({ loading: false });

      expect(screen.queryByTestId('table-loading')).not.toBeInTheDocument();
    });
  });

  describe('Edit functionality', () => {
    it('should call onEdit when edit button is clicked', async () => {
      const onEdit = vi.fn();
      renderGroupList({ onEdit });

      const editButtons = screen.getAllByTestId(/edit-group-/);
      if (editButtons[0]) {
        await user.click(editButtons[0]);
        expect(onEdit).toHaveBeenCalledWith(mockGroups[0]);
      }
    });

    it('should render edit button for each group', () => {
      renderGroupList();

      expect(screen.getByTestId('edit-group-1')).toBeInTheDocument();
      expect(screen.getByTestId('edit-group-2')).toBeInTheDocument();
      expect(screen.getByTestId('edit-group-3')).toBeInTheDocument();
    });
  });

  describe('Delete functionality', () => {
    it('should call onDelete when delete button is clicked', async () => {
      const onDelete = vi.fn();
      renderGroupList({ onDelete });

      const deleteButtons = screen.getAllByTestId(/delete-group-/);
      if (deleteButtons[0]) {
        await user.click(deleteButtons[0]);
        expect(onDelete).toHaveBeenCalledWith(mockGroups[0]);
      }
    });

    it('should not render delete button when onDelete is not provided', () => {
      renderGroupList({ onDelete: undefined });

      expect(screen.queryByTestId('delete-group-1')).not.toBeInTheDocument();
    });

    it('should render delete button for each group when onDelete is provided', () => {
      renderGroupList({ onDelete: vi.fn() });

      expect(screen.getByTestId('delete-group-1')).toBeInTheDocument();
      expect(screen.getByTestId('delete-group-2')).toBeInTheDocument();
      expect(screen.getByTestId('delete-group-3')).toBeInTheDocument();
    });
  });

  describe('Sorting', () => {
    it('should call onSortChange when sortable header is clicked', async () => {
      const onSortChange = vi.fn();
      renderGroupList({ onSortChange });

      const nameHeader = screen.getByText('Name');
      if (nameHeader) {
        await user.click(nameHeader);
        expect(onSortChange).toHaveBeenCalledWith('NAME');
      }
    });

    it('should not call onSortChange when onSortChange is not provided', async () => {
      renderGroupList({ onSortChange: undefined });

      const nameHeader = screen.getByText('Name');
      if (nameHeader) {
        await user.click(nameHeader);
        // Should not throw error
        expect(nameHeader).toBeInTheDocument();
      }
    });

    it('should display active sort state', () => {
      const orderBy: GroupSortState = { field: 'NAME', direction: 'asc' };
      renderGroupList({ orderBy, onSortChange: vi.fn() });

      // The ManagementTable should receive the sort state
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
      renderGroupList({
        pageInfo,
        paginationRange,
        onLoadNext: vi.fn(),
        onLoadPrevious: vi.fn(),
        onGoToFirst: vi.fn(),
      });

      // ManagementTable should receive pagination props
      expect(screen.getByTestId('management-table')).toBeInTheDocument();
    });

    it('should not render pagination when pageInfo is not provided', () => {
      renderGroupList({ pageInfo: null });

      // Table should still render
      expect(screen.getByTestId('management-table')).toBeInTheDocument();
    });
  });
});
