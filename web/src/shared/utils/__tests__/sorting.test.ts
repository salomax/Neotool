import { describe, it, expect } from 'vitest';
import {
  toGraphQLOrderBy,
  fromGraphQLOrderBy,
  getNextSortState,
  type UserSortState,
  type UserOrderByInput,
} from '../sorting';

describe('sorting utilities', () => {
  describe('toGraphQLOrderBy', () => {
    it('should return null for null input (use backend default)', () => {
      expect(toGraphQLOrderBy(null)).toBeNull();
    });

    it('should convert DISPLAY_NAME asc to GraphQL format', () => {
      const result = toGraphQLOrderBy({ field: 'DISPLAY_NAME', direction: 'asc' });
      expect(result).toEqual([
        { field: 'DISPLAY_NAME', direction: 'ASC' },
      ]);
    });

    it('should convert DISPLAY_NAME desc to GraphQL format', () => {
      const result = toGraphQLOrderBy({ field: 'DISPLAY_NAME', direction: 'desc' });
      expect(result).toEqual([
        { field: 'DISPLAY_NAME', direction: 'DESC' },
      ]);
    });

    it('should convert EMAIL asc to GraphQL format', () => {
      const result = toGraphQLOrderBy({ field: 'EMAIL', direction: 'asc' });
      expect(result).toEqual([
        { field: 'EMAIL', direction: 'ASC' },
      ]);
    });

    it('should convert EMAIL desc to GraphQL format', () => {
      const result = toGraphQLOrderBy({ field: 'EMAIL', direction: 'desc' });
      expect(result).toEqual([
        { field: 'EMAIL', direction: 'DESC' },
      ]);
    });

    it('should convert ENABLED asc to GraphQL format', () => {
      const result = toGraphQLOrderBy({ field: 'ENABLED', direction: 'asc' });
      expect(result).toEqual([
        { field: 'ENABLED', direction: 'ASC' },
      ]);
    });

    it('should convert ENABLED desc to GraphQL format', () => {
      const result = toGraphQLOrderBy({ field: 'ENABLED', direction: 'desc' });
      expect(result).toEqual([
        { field: 'ENABLED', direction: 'DESC' },
      ]);
    });
  });

  describe('fromGraphQLOrderBy', () => {
    it('should return null for null input', () => {
      expect(fromGraphQLOrderBy(null)).toBeNull();
    });

    it('should return null for undefined input', () => {
      expect(fromGraphQLOrderBy(undefined)).toBeNull();
    });

    it('should return null for empty array', () => {
      expect(fromGraphQLOrderBy([])).toBeNull();
    });

    it('should convert DISPLAY_NAME ASC to frontend format', () => {
      const input: UserOrderByInput[] = [
        { field: 'DISPLAY_NAME', direction: 'ASC' },
      ];
      const result = fromGraphQLOrderBy(input);
      expect(result).toEqual({ field: 'DISPLAY_NAME', direction: 'asc' });
    });

    it('should convert DISPLAY_NAME DESC to frontend format', () => {
      const input: UserOrderByInput[] = [
        { field: 'DISPLAY_NAME', direction: 'DESC' },
      ];
      const result = fromGraphQLOrderBy(input);
      expect(result).toEqual({ field: 'DISPLAY_NAME', direction: 'desc' });
    });

    it('should convert EMAIL ASC to frontend format', () => {
      const input: UserOrderByInput[] = [
        { field: 'EMAIL', direction: 'ASC' },
      ];
      const result = fromGraphQLOrderBy(input);
      expect(result).toEqual({ field: 'EMAIL', direction: 'asc' });
    });

    it('should convert EMAIL DESC to frontend format', () => {
      const input: UserOrderByInput[] = [
        { field: 'EMAIL', direction: 'DESC' },
      ];
      const result = fromGraphQLOrderBy(input);
      expect(result).toEqual({ field: 'EMAIL', direction: 'desc' });
    });

    it('should convert ENABLED ASC to frontend format', () => {
      const input: UserOrderByInput[] = [
        { field: 'ENABLED', direction: 'ASC' },
      ];
      const result = fromGraphQLOrderBy(input);
      expect(result).toEqual({ field: 'ENABLED', direction: 'asc' });
    });

    it('should convert ENABLED DESC to frontend format', () => {
      const input: UserOrderByInput[] = [
        { field: 'ENABLED', direction: 'DESC' },
      ];
      const result = fromGraphQLOrderBy(input);
      expect(result).toEqual({ field: 'ENABLED', direction: 'desc' });
    });

    it('should take first orderBy entry and ignore subsequent entries', () => {
      const input: UserOrderByInput[] = [
        { field: 'DISPLAY_NAME', direction: 'ASC' },
        { field: 'EMAIL', direction: 'DESC' },
      ];
      const result = fromGraphQLOrderBy(input);
      expect(result).toEqual({ field: 'DISPLAY_NAME', direction: 'asc' });
    });

    it('should return null for invalid field', () => {
      const input: any[] = [
        { field: 'INVALID_FIELD', direction: 'ASC' },
      ];
      const result = fromGraphQLOrderBy(input);
      expect(result).toBeNull();
    });
  });

  describe('getNextSortState', () => {
    it('should return asc when current sort is null', () => {
      const result = getNextSortState(null, 'DISPLAY_NAME');
      expect(result).toEqual({ field: 'DISPLAY_NAME', direction: 'asc' });
    });

    it('should return asc when current sort is for different field', () => {
      const currentSort: UserSortState = { field: 'EMAIL', direction: 'asc' };
      const result = getNextSortState(currentSort, 'DISPLAY_NAME');
      expect(result).toEqual({ field: 'DISPLAY_NAME', direction: 'asc' });
    });

    it('should return desc when current sort is asc for same field', () => {
      const currentSort: UserSortState = { field: 'DISPLAY_NAME', direction: 'asc' };
      const result = getNextSortState(currentSort, 'DISPLAY_NAME');
      expect(result).toEqual({ field: 'DISPLAY_NAME', direction: 'desc' });
    });

    it('should return null when current sort is desc for same field', () => {
      const currentSort: UserSortState = { field: 'DISPLAY_NAME', direction: 'desc' };
      const result = getNextSortState(currentSort, 'DISPLAY_NAME');
      expect(result).toBeNull();
    });

    it('should handle EMAIL field transitions', () => {
      // null -> asc
      expect(getNextSortState(null, 'EMAIL')).toEqual({ field: 'EMAIL', direction: 'asc' });
      
      // asc -> desc
      const asc: UserSortState = { field: 'EMAIL', direction: 'asc' };
      expect(getNextSortState(asc, 'EMAIL')).toEqual({ field: 'EMAIL', direction: 'desc' });
      
      // desc -> null
      const desc: UserSortState = { field: 'EMAIL', direction: 'desc' };
      expect(getNextSortState(desc, 'EMAIL')).toBeNull();
    });

    it('should handle ENABLED field transitions', () => {
      // null -> asc
      expect(getNextSortState(null, 'ENABLED')).toEqual({ field: 'ENABLED', direction: 'asc' });
      
      // asc -> desc
      const asc: UserSortState = { field: 'ENABLED', direction: 'asc' };
      expect(getNextSortState(asc, 'ENABLED')).toEqual({ field: 'ENABLED', direction: 'desc' });
      
      // desc -> null
      const desc: UserSortState = { field: 'ENABLED', direction: 'desc' };
      expect(getNextSortState(desc, 'ENABLED')).toBeNull();
    });

    it('should switch to new field when clicking different column', () => {
      const currentSort: UserSortState = { field: 'DISPLAY_NAME', direction: 'desc' };
      const result = getNextSortState(currentSort, 'EMAIL');
      expect(result).toEqual({ field: 'EMAIL', direction: 'asc' });
    });
  });
});

