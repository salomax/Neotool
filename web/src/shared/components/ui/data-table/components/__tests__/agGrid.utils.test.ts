import { describe, it, expect, vi } from 'vitest';
import { withResetFilter, percentColDef, ColDefAny } from '../agGrid.utils';

describe('withResetFilter', () => {
  it('should add reset filter menu item to column definition', () => {
    const col: ColDefAny = {};
    const result = withResetFilter(col);

    expect(result.getMainMenuItems).toBeDefined();
    expect(typeof result.getMainMenuItems).toBe('function');
  });

  it('should preserve existing getMainMenuItems', () => {
    const existingItems = [{ name: 'Existing Item' }];
    const existingGetMainMenuItems = vi.fn(() => existingItems);
    const col: ColDefAny = { getMainMenuItems: existingGetMainMenuItems };
    
    const params = {
      defaultItems: [{ name: 'Default Item' }],
      column: {
        getColId: vi.fn(() => 'testCol'),
      },
      api: {
        getFilterModel: vi.fn(() => ({ testCol: 'value' })),
        setFilterModel: vi.fn(),
      },
    };

    const result = withResetFilter(col);
    const items = result.getMainMenuItems(params);

    expect(existingGetMainMenuItems).toHaveBeenCalledWith(params);
    expect(items).toHaveLength(2);
    expect(items[0]).toEqual({ name: 'Existing Item' });
    expect(items[1].name).toBe('Reset filter');
  });

  it('should use defaultItems when no existing getMainMenuItems', () => {
    const col: ColDefAny = {};
    const defaultItems = [{ name: 'Default Item' }];
    
    const params = {
      defaultItems,
      column: {
        getColId: vi.fn(() => 'testCol'),
      },
      api: {
        getFilterModel: vi.fn(() => ({ testCol: 'value' })),
        setFilterModel: vi.fn(),
      },
    };

    const result = withResetFilter(col);
    const items = result.getMainMenuItems(params);

    expect(items).toHaveLength(2);
    expect(items[0]).toEqual({ name: 'Default Item' });
    expect(items[1].name).toBe('Reset filter');
  });

  it('should handle empty defaultItems', () => {
    const col: ColDefAny = {};
    
    const params = {
      defaultItems: [],
      column: {
        getColId: vi.fn(() => 'testCol'),
      },
      api: {
        getFilterModel: vi.fn(() => ({ testCol: 'value' })),
        setFilterModel: vi.fn(),
      },
    };

    const result = withResetFilter(col);
    const items = result.getMainMenuItems(params);

    expect(items).toHaveLength(1);
    expect(items[0].name).toBe('Reset filter');
  });

  it('should reset filter when action is called', () => {
    const col: ColDefAny = {};
    const setFilterModel = vi.fn();
    const getFilterModel = vi.fn(() => ({ testCol: 'value' }));
    
    const params = {
      defaultItems: [],
      column: {
        getColId: vi.fn(() => 'testCol'),
      },
      api: {
        getFilterModel,
        setFilterModel,
      },
    };

    const result = withResetFilter(col);
    const items = result.getMainMenuItems(params);
    
    items[0].action();

    expect(getFilterModel).toHaveBeenCalled();
    expect(setFilterModel).toHaveBeenCalledWith({});
  });

  it('should not reset filter if column not in filter model', () => {
    const col: ColDefAny = {};
    const setFilterModel = vi.fn();
    const getFilterModel = vi.fn(() => ({ otherCol: 'value' }));
    
    const params = {
      defaultItems: [],
      column: {
        getColId: vi.fn(() => 'testCol'),
      },
      api: {
        getFilterModel,
        setFilterModel,
      },
    };

    const result = withResetFilter(col);
    const items = result.getMainMenuItems(params);
    
    items[0].action();

    expect(getFilterModel).toHaveBeenCalled();
    expect(setFilterModel).toHaveBeenCalledWith({ otherCol: 'value' });
  });

  it('should handle missing api methods gracefully', () => {
    const col: ColDefAny = {};
    
    const params = {
      defaultItems: [],
      column: {
        getColId: vi.fn(() => 'testCol'),
      },
      api: {},
    };

    const result = withResetFilter(col);
    const items = result.getMainMenuItems(params);
    
    // Should not throw
    expect(() => items[0].action()).not.toThrow();
  });
});

describe('percentColDef', () => {
  it('should create column definition with default headerName', () => {
    const result = percentColDef('percentage');
    
    expect(result.field).toBe('percentage');
    expect(result.headerName).toBe('Percent');
    expect(result.filter).toBe('agNumberColumnFilter');
  });

  it('should use custom headerName when provided', () => {
    const result = percentColDef('percentage', { headerName: 'Custom Percent' });
    
    expect(result.headerName).toBe('Custom Percent');
  });

  it('should format value as percentage', () => {
    const result = percentColDef('percentage');
    const formatter = result.valueFormatter;
    
    expect(formatter({ value: 0.2 })).toBe('20%');
    expect(formatter({ value: 0.5 })).toBe('50%');
    expect(formatter({ value: 1 })).toBe('100%');
    expect(formatter({ value: 0.123 })).toBe('12%');
  });

  it('should handle null and empty values', () => {
    const result = percentColDef('percentage');
    const formatter = result.valueFormatter;
    
    expect(formatter({ value: null })).toBe('');
    expect(formatter({ value: '' })).toBe('');
    expect(formatter({ value: undefined })).toBe('');
  });

  it('should use custom decimals when provided', () => {
    const result = percentColDef('percentage', { decimals: 2 });
    const formatter = result.valueFormatter;
    
    expect(formatter({ value: 0.123 })).toBe('12.30%');
    expect(formatter({ value: 0.1234 })).toBe('12.34%');
  });

  it('should parse percentage string to decimal', () => {
    const result = percentColDef('percentage');
    const parser = result.valueParser;
    
    expect(parser({ newValue: '20' })).toBe(0.2);
    expect(parser({ newValue: '20%' })).toBe(0.2);
    expect(parser({ newValue: '50' })).toBe(0.5);
    expect(parser({ newValue: '100' })).toBe(1);
  });

  it('should handle values greater than 1 as percentage', () => {
    const result = percentColDef('percentage');
    const parser = result.valueParser;
    
    expect(parser({ newValue: '200' })).toBe(2);
    expect(parser({ newValue: '150' })).toBe(1.5);
  });

  it('should handle decimal input with comma', () => {
    const result = percentColDef('percentage');
    const parser = result.valueParser;
    
    expect(parser({ newValue: '20,5' })).toBe(0.205);
    expect(parser({ newValue: '50,25' })).toBe(0.5025);
  });

  it('should return null for invalid input', () => {
    const result = percentColDef('percentage');
    const parser = result.valueParser;
    
    expect(parser({ newValue: null })).toBe(null);
    expect(parser({ newValue: '' })).toBe(null);
    expect(parser({ newValue: 'abc' })).toBe(null);
    expect(parser({ newValue: 'invalid' })).toBe(null);
  });

  it('should handle null and empty newValue', () => {
    const result = percentColDef('percentage');
    const parser = result.valueParser;
    
    expect(parser({ newValue: null })).toBe(null);
    expect(parser({ newValue: '' })).toBe(null);
  });

  it('should merge with additional options', () => {
    const result = percentColDef('percentage', { 
      sortable: false,
      resizable: true,
    });
    
    expect(result.sortable).toBe(false);
    expect(result.resizable).toBe(true);
  });

  it('should include reset filter functionality', () => {
    const result = percentColDef('percentage');
    
    expect(result.getMainMenuItems).toBeDefined();
    expect(typeof result.getMainMenuItems).toBe('function');
  });
});

