import React from 'react';
import { Table as MuiTable, TableProps as MuiTableProps } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { getTestIdProps } from '@/shared/utils/testid';

export interface TableProps extends MuiTableProps {
  /** Custom styles to apply to the Table component. */
  sx?: MuiTableProps['sx'];
  /** 
   * Optional name used to generate data-testid. 
   * If provided, data-testid will be "table-{name}".
   * If both name and data-testid are provided, data-testid takes precedence.
   */
  name?: string;
  /** Custom data-testid attribute. Takes precedence over generated testid from name prop. */
  'data-testid'?: string;
}

/**
 * Table component wrapper that extends MUI Table with consistent styling
 * and border using theme divider color (same as input borders).
 * 
 * @example
 * ```tsx
 * <Table name="user-list">
 *   <TableHead>...</TableHead>
 *   <TableBody>...</TableBody>
 * </Table>
 * ```
 */
export function Table({ 
  sx, 
  name,
  'data-testid': dataTestId,
  ...props 
}: TableProps) {
  const theme = useTheme();
  
  // Generate data-testid from component name and optional name prop
  const testIdProps = getTestIdProps('Table', name, dataTestId);
  
  // Get border color and radius from theme tokens
  const borderColor = (theme as any).custom?.palette?.inputBorder;
  const tableRadius = (theme as any).custom?.radius?.table ?? theme.shape.borderRadius;

  return (
    <MuiTable
      sx={{
        border: '1px solid',
        borderColor: borderColor,
        borderRadius: `${tableRadius}px`,
        overflow: 'hidden',
        // Add borders between all rows (including header)
        '& .MuiTableCell-root': {
          borderBottom: `1px solid ${borderColor}`,
        },
        // Remove border from last row in table body only
        '& .MuiTableBody-root .MuiTableRow-root:last-child .MuiTableCell-root': {
          borderBottom: 'none',
        },
        // Add subtle background to header
        '& .MuiTableHead-root .MuiTableCell-root': {
          backgroundColor: theme.palette.action.hover,
        },
        ...sx,
      }}
      {...testIdProps}
      {...props}
    />
  );
}

export default Table;

