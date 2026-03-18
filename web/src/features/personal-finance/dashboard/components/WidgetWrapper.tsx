import React from 'react';
import { 
  Paper, 
  Box, 
  Typography, 
  IconButton, 
  Menu, 
  MenuItem, 
  ListItemIcon, 
  ListItemText,
  Divider,
  useTheme,
  alpha
} from '@mui/material';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import RefreshIcon from '@mui/icons-material/Refresh';
import VisibilityOffIcon from '@mui/icons-material/VisibilityOff';
import SettingsIcon from '@mui/icons-material/Settings';
import DragIndicatorIcon from '@mui/icons-material/DragIndicator';

export interface WidgetWrapperProps {
  title: string;
  icon?: React.ReactNode;
  children: React.ReactNode;
  onRemove?: () => void;
  onRefresh?: () => void;
  onConfigure?: () => void;
  isEditMode?: boolean;
  className?: string;
  height?: number | string;
  dragHandleProps?: any; // props from dnd-kit
}

export const WidgetWrapper: React.FC<WidgetWrapperProps> = ({
  title,
  icon,
  children,
  onRemove,
  onRefresh,
  onConfigure,
  isEditMode = false,
  className,
  height = '100%',
  dragHandleProps,
}) => {
  const theme = useTheme();
  const [anchorEl, setAnchorEl] = React.useState<null | HTMLElement>(null);
  const open = Boolean(anchorEl);

  const handleMenuClick = (event: React.MouseEvent<HTMLElement>) => {
    setAnchorEl(event.currentTarget);
  };

  const handleMenuClose = () => {
    setAnchorEl(null);
  };

  return (
    <Paper
      elevation={0}
      className={className}
      sx={{
        height,
        display: 'flex',
        flexDirection: 'column',
        border: `1px solid ${theme.palette.divider}`,
        borderRadius: 2,
        overflow: 'hidden',
        position: 'relative',
        transition: 'box-shadow 0.2s, border-color 0.2s',
        '&:hover': {
          borderColor: theme.palette.action.hover,
          boxShadow: theme.shadows[1],
        },
        ...(isEditMode && {
          borderStyle: 'dashed',
          borderColor: theme.palette.primary.main,
          backgroundColor: alpha(theme.palette.primary.main, 0.02),
        }),
      }}
    >
      <Box
        className={isEditMode ? 'drag-handle' : undefined}
        {...(isEditMode ? dragHandleProps : {})}
        sx={{
          p: 2,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          borderBottom: `1px solid ${theme.palette.divider}`,
          bgcolor: theme.palette.background.paper,
          ...(isEditMode && {
            cursor: 'grab',
            touchAction: 'none',
            '&:active': { cursor: 'grabbing' },
            '&:hover': { bgcolor: 'action.hover' },
          }),
        }}
      >
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
          {isEditMode && (
            <Box
              sx={{ 
                display: 'flex', 
                color: 'action.active',
              }}
            >
              <DragIndicatorIcon />
            </Box>
          )}
          {icon && (
            <Box sx={{ color: theme.palette.text.secondary, display: 'flex' }}>
              {icon}
            </Box>
          )}
          <Typography variant="h6" component="div" sx={{ fontSize: '1rem', fontWeight: 600 }}>
            {title}
          </Typography>
        </Box>

        <Box>
          {onRefresh && !isEditMode && (
            <IconButton size="small" onClick={onRefresh}>
              <RefreshIcon fontSize="small" />
            </IconButton>
          )}
          <IconButton size="small" onClick={handleMenuClick}>
            <MoreVertIcon fontSize="small" />
          </IconButton>
        </Box>
      </Box>

      <Menu
        anchorEl={anchorEl}
        open={open}
        onClose={handleMenuClose}
        transformOrigin={{ horizontal: 'right', vertical: 'top' }}
        anchorOrigin={{ horizontal: 'right', vertical: 'bottom' }}
      >
        {onRefresh && (
          <MenuItem onClick={() => { onRefresh(); handleMenuClose(); }}>
            <ListItemIcon><RefreshIcon fontSize="small" /></ListItemIcon>
            <ListItemText>Refresh</ListItemText>
          </MenuItem>
        )}
        {onConfigure && (
          <MenuItem onClick={() => { onConfigure(); handleMenuClose(); }}>
            <ListItemIcon><SettingsIcon fontSize="small" /></ListItemIcon>
            <ListItemText>Settings</ListItemText>
          </MenuItem>
        )}
        {(onRemove || isEditMode) && (
          <>
            <Divider />
            <MenuItem onClick={() => { onRemove?.(); handleMenuClose(); }} sx={{ color: 'error.main' }}>
              <ListItemIcon><VisibilityOffIcon fontSize="small" color="error" /></ListItemIcon>
              <ListItemText>Hide Widget</ListItemText>
            </MenuItem>
          </>
        )}
      </Menu>

      <Box sx={{ flexGrow: 1, overflow: 'auto', position: 'relative' }}>
        {children}
      </Box>
    </Paper>
  );
};
