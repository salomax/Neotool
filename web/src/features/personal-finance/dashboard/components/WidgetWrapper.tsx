import React from 'react';
import { 
  Paper, 
  Box, 
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
import { Label } from '@/shared/components/ui/primitives/Label';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '@/app/personal-finance/i18n';

export interface WidgetWrapperProps {
  title: string;
  icon?: React.ReactNode;
  headerRight?: React.ReactNode;
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
  headerRight,
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
  const { t } = useTranslation(personalFinanceTranslations);
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
        borderRadius: 1.5,
        borderBottomRightRadius: 1,
        overflow: 'hidden',
        position: 'relative',
        transition: 'border-color 0.2s',
        ...(isEditMode && {
          borderStyle: 'dashed',
          borderColor: theme.palette.primary.main,
          backgroundColor: alpha(theme.palette.primary.main, 0.02),
        }),
      }}
    >
      <Box
        sx={{
          p: 1,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          borderBottom: `1px solid ${theme.palette.divider}`,
          bgcolor: theme.palette.background.paper,
          '&:hover .widget-drag-handle': { opacity: 1, pointerEvents: 'auto' },
          '&:focus-within .widget-drag-handle': { opacity: 1, pointerEvents: 'auto' },
          '@media (hover: none)': {
            '& .widget-drag-handle': { opacity: 1, pointerEvents: 'auto' },
          },
        }}
      >
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
          <Box
            className="drag-handle widget-drag-handle"
            {...(dragHandleProps ?? {})}
            sx={{
              display: 'flex',
              alignItems: 'center',
              color: 'action.active',
              opacity: .2,
              transition: 'opacity 150ms ease',
              cursor: 'grab',
              userSelect: 'none',
              touchAction: 'none',
              pointerEvents: 'none',
              '&:active': { cursor: 'grabbing' },
            }}
          >
            <DragIndicatorIcon />
          </Box>
          <Label sx={{ fontSize: '.9rem', mb: 0, lineHeight: 1, display: 'flex', alignItems: 'center' }}>
            {title}
          </Label>
        </Box>

        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          {headerRight}
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
            <ListItemText>{t('dashboard.widgets.menu.refresh')}</ListItemText>
          </MenuItem>
        )}
        {onConfigure && (
          <MenuItem onClick={() => { onConfigure(); handleMenuClose(); }}>
            <ListItemIcon><SettingsIcon fontSize="small" /></ListItemIcon>
            <ListItemText>{t('dashboard.widgets.menu.settings')}</ListItemText>
          </MenuItem>
        )}
        {(onRemove || isEditMode) && (
          <>
            <Divider />
            <MenuItem onClick={() => { onRemove?.(); handleMenuClose(); }} sx={{ color: 'error.main' }}>
              <ListItemIcon><VisibilityOffIcon fontSize="small" color="error" /></ListItemIcon>
              <ListItemText>{t('dashboard.widgets.menu.hide')}</ListItemText>
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
