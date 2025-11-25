import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import type { ICellRendererParams } from 'ag-grid-community';
import { actionsColumn } from '../actions';

describe('actionsColumn', () => {
  it('should create column definition with default values', () => {
    const colDef = actionsColumn({});

    expect(colDef.headerName).toBe('Actions');
    expect(colDef.field).toBe('__actions__');
    expect(colDef.sortable).toBe(false);
    expect(colDef.filter).toBe(false);
    expect(colDef.width).toBe(120);
    expect(colDef.maxWidth).toBe(140);
    expect(colDef.cellRenderer).toBeDefined();
  });

  it('should use custom headerName when provided', () => {
    const colDef = actionsColumn({ headerName: 'Custom Actions' });

    expect(colDef.headerName).toBe('Custom Actions');
  });

  it('should use custom width when provided', () => {
    const colDef = actionsColumn({ width: 200 });

    expect(colDef.width).toBe(200);
    expect(colDef.maxWidth).toBe(200);
  });

  it('should set maxWidth to at least 140 when custom width is less', () => {
    const colDef = actionsColumn({ width: 100 });

    expect(colDef.width).toBe(100);
    expect(colDef.maxWidth).toBe(140);
  });

  it('should store onEdit handler in column definition', () => {
    const onEdit = vi.fn();
    const colDef = actionsColumn({ onEdit });

    expect((colDef as any).__onEdit).toBe(onEdit);
  });

  it('should store onDelete handler in column definition', () => {
    const onDelete = vi.fn();
    const colDef = actionsColumn({ onDelete });

    expect((colDef as any).__onDelete).toBe(onDelete);
  });

  it('should store both handlers in column definition', () => {
    const onEdit = vi.fn();
    const onDelete = vi.fn();
    const colDef = actionsColumn({ onEdit, onDelete });

    expect((colDef as any).__onEdit).toBe(onEdit);
    expect((colDef as any).__onDelete).toBe(onDelete);
  });
});

describe('ActionsCell', () => {
  const createMockParams = (overrides?: Partial<ICellRendererParams>): ICellRendererParams => ({
    data: { id: 1, name: 'Test' },
    value: null,
    valueFormatted: null,
    getValue: vi.fn(),
    setValue: vi.fn(),
    formatValue: vi.fn(),
    api: {} as any,
    columnApi: {} as any,
    colDef: {} as any,
    column: {} as any,
    rowIndex: 0,
    node: {} as any,
    eGridCell: null,
    eParentOfValue: null,
    ...overrides,
  });

  it('should render both edit and delete buttons when both handlers are provided', () => {
    const onEdit = vi.fn();
    const onDelete = vi.fn();
    const colDef = actionsColumn({ onEdit, onDelete });
    const params = createMockParams({
      colDef: colDef as any,
    });

    const CellRenderer = colDef.cellRenderer as any;
    render(<CellRenderer {...params} />);

    expect(screen.getByLabelText('edit row')).toBeInTheDocument();
    expect(screen.getByLabelText('delete row')).toBeInTheDocument();
  });

  it('should render only edit button when only onEdit is provided', () => {
    const onEdit = vi.fn();
    const colDef = actionsColumn({ onEdit });
    const params = createMockParams({
      colDef: colDef as any,
    });

    const CellRenderer = colDef.cellRenderer as any;
    render(<CellRenderer {...params} />);

    expect(screen.getByLabelText('edit row')).toBeInTheDocument();
    expect(screen.queryByLabelText('delete row')).not.toBeInTheDocument();
  });

  it('should render only delete button when only onDelete is provided', () => {
    const onDelete = vi.fn();
    const colDef = actionsColumn({ onDelete });
    const params = createMockParams({
      colDef: colDef as any,
    });

    const CellRenderer = colDef.cellRenderer as any;
    render(<CellRenderer {...params} />);

    expect(screen.queryByLabelText('edit row')).not.toBeInTheDocument();
    expect(screen.getByLabelText('delete row')).toBeInTheDocument();
  });

  it('should render no buttons when neither handler is provided', () => {
    const colDef = actionsColumn({});
    const params = createMockParams({
      colDef: colDef as any,
    });

    const CellRenderer = colDef.cellRenderer as any;
    render(<CellRenderer {...params} />);

    expect(screen.queryByLabelText('edit row')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('delete row')).not.toBeInTheDocument();
  });

  it('should call onEdit when edit button is clicked', () => {
    const rowData = { id: 1, name: 'Test Row' };
    const onEdit = vi.fn();
    const colDef = actionsColumn({ onEdit });
    const params = createMockParams({
      data: rowData,
      colDef: colDef as any,
    });

    const CellRenderer = colDef.cellRenderer as any;
    render(<CellRenderer {...params} />);

    const editButton = screen.getByLabelText('edit row');
    editButton.click();

    expect(onEdit).toHaveBeenCalledTimes(1);
    expect(onEdit).toHaveBeenCalledWith(rowData);
  });

  it('should call onDelete when delete button is clicked', () => {
    const rowData = { id: 1, name: 'Test Row' };
    const onDelete = vi.fn();
    const colDef = actionsColumn({ onDelete });
    const params = createMockParams({
      data: rowData,
      colDef: colDef as any,
    });

    const CellRenderer = colDef.cellRenderer as any;
    render(<CellRenderer {...params} />);

    const deleteButton = screen.getByLabelText('delete row');
    deleteButton.click();

    expect(onDelete).toHaveBeenCalledTimes(1);
    expect(onDelete).toHaveBeenCalledWith(rowData);
  });

  it('should call both handlers when both buttons are clicked', () => {
    const rowData = { id: 1, name: 'Test Row' };
    const onEdit = vi.fn();
    const onDelete = vi.fn();
    const colDef = actionsColumn({ onEdit, onDelete });
    const params = createMockParams({
      data: rowData,
      colDef: colDef as any,
    });

    const CellRenderer = colDef.cellRenderer as any;
    render(<CellRenderer {...params} />);

    screen.getByLabelText('edit row').click();
    screen.getByLabelText('delete row').click();

    expect(onEdit).toHaveBeenCalledTimes(1);
    expect(onEdit).toHaveBeenCalledWith(rowData);
    expect(onDelete).toHaveBeenCalledTimes(1);
    expect(onDelete).toHaveBeenCalledWith(rowData);
  });
});

