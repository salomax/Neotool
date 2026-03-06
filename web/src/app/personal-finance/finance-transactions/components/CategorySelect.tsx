import React, { useState, useMemo } from 'react';
import { 
  Box, 
  Chip, 
  Popover, 
  TextField, 
  List, 
  ListItem, 
  ListItemButton, 
  ListItemIcon, 
  ListItemText, 
  Typography, 
  InputAdornment,
  Stack,
  alpha,
  useTheme
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import { TransactionCategory } from '@/lib/graphql/types/__generated__/graphql';

interface CategorySelectProps {
  value: string;
  onChange: (categoryId: string) => void;
  categories: TransactionCategory[];
  placeholder?: string;
}

export function CategorySelect({ 
  value, 
  onChange, 
  categories = [],
  placeholder = "Categoria..."
}: CategorySelectProps) {
  const theme = useTheme();
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);
  const [searchQuery, setSearchQuery] = useState('');

  const selectedCategory = useMemo(() => 
    categories.find(c => c.id === value), 
    [categories, value]
  );

  const filteredCategories = useMemo(() => {
    if (!searchQuery) return categories;
    const query = searchQuery.toLowerCase();
    return categories.filter(c => 
      c.name.toLowerCase().includes(query)
    );
  }, [categories, searchQuery]);

  const handleClick = (event: React.MouseEvent<HTMLElement>) => {
    setAnchorEl(event.currentTarget);
  };

  const handleClose = () => {
    setAnchorEl(null);
    setSearchQuery('');
  };

  const handleSelect = (categoryId: string) => {
    onChange(categoryId);
    handleClose();
  };

  const open = Boolean(anchorEl);

  return (
    <>
      <Box onClick={handleClick} sx={{ cursor: 'pointer', display: 'inline-block' }}>
        {selectedCategory ? (
          <Chip
            label={
              <Stack direction="row" spacing={1} alignItems="center">
                <Typography sx={{ fontSize: '1.1em' }}>{selectedCategory.icon}</Typography>
                <Typography variant="body2">{selectedCategory.name}</Typography>
              </Stack>
            }
            sx={{
              bgcolor: selectedCategory.color ? alpha(selectedCategory.color, 0.1) : 'action.selected',
              color: selectedCategory.color || 'text.primary',
              borderColor: selectedCategory.color ? alpha(selectedCategory.color, 0.3) : 'divider',
              borderWidth: 1,
              borderStyle: 'solid',
              '&:hover': {
                bgcolor: selectedCategory.color ? alpha(selectedCategory.color, 0.2) : 'action.hover',
              }
            }}
            onDelete={handleClick}
            deleteIcon={<ExpandMoreIcon sx={{ color: 'inherit !important' }} />}
          />
        ) : (
          <Chip
            label={placeholder}
            variant="outlined"
            sx={{
              borderStyle: 'dashed',
              color: 'text.secondary',
              '&:hover': {
                borderColor: 'primary.main',
                color: 'primary.main',
                bgcolor: alpha(theme.palette.primary.main, 0.05)
              }
            }}
            onDelete={handleClick}
            deleteIcon={<ExpandMoreIcon />}
          />
        )}
      </Box>

      <Popover
        open={open}
        anchorEl={anchorEl}
        onClose={handleClose}
        anchorOrigin={{
          vertical: 'bottom',
          horizontal: 'left',
        }}
        transformOrigin={{
          vertical: 'top',
          horizontal: 'left',
        }}
        PaperProps={{
          sx: { width: 300, maxHeight: 400, display: 'flex', flexDirection: 'column' }
        }}
      >
        <Box sx={{ p: 1, borderBottom: 1, borderColor: 'divider' }}>
          <TextField
            fullWidth
            size="small"
            placeholder="Buscar categoria..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <SearchIcon fontSize="small" color="action" />
                </InputAdornment>
              ),
              disableUnderline: true,
              sx: {
                '& fieldset': { border: 'none' },
              }
            }}
            variant="standard"
            autoFocus
          />
        </Box>
        <List sx={{ flex: 1, overflow: 'auto', p: 0 }}>
          {filteredCategories.map((category) => (
            <ListItemButton 
              key={category.id} 
              onClick={() => handleSelect(category.id)}
              selected={value === category.id}
            >
              <ListItemIcon sx={{ minWidth: 36, fontSize: '1.2rem' }}>
                {category.icon}
              </ListItemIcon>
              <ListItemText 
                primary={category.name} 
                primaryTypographyProps={{ variant: 'body2' }}
              />
            </ListItemButton>
          ))}
          {filteredCategories.length === 0 && (
            <ListItem>
              <ListItemText 
                primary="Nenhuma categoria encontrada" 
                primaryTypographyProps={{ variant: 'body2', color: 'text.secondary', align: 'center' }}
              />
            </ListItem>
          )}
        </List>
      </Popover>
    </>
  );
}
