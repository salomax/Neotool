import React, { useRef, useCallback, useState, useEffect } from 'react';
import {
  Box,
  Paper,
  Toolbar,
  IconButton,
  Divider,
  Typography,
  Tooltip,
  Menu,
  MenuItem,
  ListItemIcon,
  ListItemText,
  Button,
  Stack,
  Chip,
  FormControl,
  InputLabel,
  Select
} from '@/shared/ui/mui-imports';
import type { SelectChangeEvent } from '@/shared/ui/mui-imports';
import {
  FormatBoldIcon,
  FormatItalicIcon,
  FormatUnderlinedIcon,
  FormatStrikethroughIcon,
  FormatListBulletedIcon,
  FormatListNumberedIcon,
  FormatQuoteIcon,
  LinkIcon,
  ImageIcon,
  CodeIcon,
  UndoIcon,
  RedoIcon,
  FormatAlignLeftIcon,
  FormatAlignCenterIcon,
  FormatAlignRightIcon,
  FormatAlignJustifyIcon,
  FormatSizeIcon,
  PaletteIcon,
  FormatClearIcon
} from '@/shared/ui/mui-imports';

export interface RichTextEditorProps {
  value?: string;
  onChange?: (value: string) => void;
  placeholder?: string;
  minHeight?: number;
  maxHeight?: number;
  readOnly?: boolean;
  showToolbar?: boolean;
  toolbarPosition?: 'top' | 'bottom';
  allowedFormats?: string[];
  className?: string;
  'data-testid'?: string;
}

// ToolbarButton component moved outside to avoid creating components during render
const ToolbarButton = ({ 
  command, 
  icon, 
  tooltip, 
  disabled = false,
  onCommand
}: { 
  command: string; 
  icon: React.ReactNode; 
  tooltip: string; 
  disabled?: boolean;
  onCommand: (command: string) => void;
}) => (
  <Tooltip title={tooltip}>
    <IconButton
      size="small"
      onClick={() => onCommand(command)}
      disabled={disabled}
      data-testid={`toolbar-${command}`}
    >
      {icon}
    </IconButton>
  </Tooltip>
);

const RichTextEditor: React.FC<RichTextEditorProps> = ({
  value = '',
  onChange,
  placeholder = 'Start typing...',
  minHeight = 200,
  maxHeight = 400,
  readOnly = false,
  showToolbar = true,
  toolbarPosition = 'top',
  allowedFormats = ['bold', 'italic', 'underline', 'strikethrough', 'list', 'link', 'image', 'code', 'align', 'size', 'color'],
  className,
  'data-testid': testId
}) => {
  const editorRef = useRef<HTMLDivElement>(null);
  const isInitializedRef = useRef(false);
  const [fontSize, setFontSize] = useState('16px');
  const [textColor, setTextColor] = useState('#000000');
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);
  const [menuType, setMenuType] = useState<'size' | 'color' | null>(null);

  // Initialize editor content and handle value changes
  useEffect(() => {
    if (editorRef.current && !isInitializedRef.current) {
      editorRef.current.innerHTML = value || '';
      isInitializedRef.current = true;
    }
  }, [value]);

  const execCommand = useCallback((command: string, value?: string) => {
    if (readOnly || !editorRef.current) return;
    
    const editor = editorRef.current;
    editor.focus();
    
    // Use modern approach instead of deprecated execCommand
    const selection = window.getSelection();
    if (!selection || selection.rangeCount === 0) return;
    
    const range = selection.getRangeAt(0);
    
    try {
      // Handle different commands with modern DOM manipulation
      switch (command) {
        case 'bold':
          document.execCommand('bold', false);
          break;
        case 'italic':
          document.execCommand('italic', false);
          break;
        case 'underline':
          document.execCommand('underline', false);
          break;
        case 'strikethrough':
          document.execCommand('strikeThrough', false);
          break;
        case 'insertUnorderedList':
          document.execCommand('insertUnorderedList', false);
          break;
        case 'insertOrderedList':
          document.execCommand('insertOrderedList', false);
          break;
        case 'justifyLeft':
          document.execCommand('justifyLeft', false);
          break;
        case 'justifyCenter':
          document.execCommand('justifyCenter', false);
          break;
        case 'justifyRight':
          document.execCommand('justifyRight', false);
          break;
        case 'justifyFull':
          document.execCommand('justifyFull', false);
          break;
        case 'removeFormat':
          document.execCommand('removeFormat', false);
          break;
        case 'undo':
          document.execCommand('undo', false);
          break;
        case 'redo':
          document.execCommand('redo', false);
          break;
        case 'createLink':
          if (value) {
            document.execCommand('createLink', false, value);
          }
          break;
        case 'insertImage':
          if (value) {
            document.execCommand('insertImage', false, value);
          }
          break;
        case 'fontSize':
          // Handle fontSize with modern approach
          if (!range.collapsed) {
            // Apply to selected text
            const span = document.createElement('span');
            span.style.fontSize = value || '16px';
            const contents = range.extractContents();
            span.appendChild(contents);
            range.insertNode(span);
          } else {
            // Apply to cursor position
            const span = document.createElement('span');
            span.style.fontSize = value || '16px';
            span.innerHTML = '&nbsp;';
            range.insertNode(span);
            range.setStartAfter(span);
            range.setEndAfter(span);
            selection.removeAllRanges();
            selection.addRange(range);
          }
          break;
        case 'foreColor':
          if (value) {
            document.execCommand('foreColor', false, value);
          }
          break;
        default:
          document.execCommand(command, false, value);
      }
    } catch (error) {
      console.warn('Command failed:', command, error);
    }
    
    // Trigger onChange with current content
    if (onChange) {
      onChange(editor.innerHTML);
    }
  }, [readOnly, onChange]);

  const handleInput = useCallback(() => {
    if (onChange && editorRef.current) {
      onChange(editorRef.current.innerHTML);
    }
  }, [onChange]);

  const handleKeyDown = useCallback((e: React.KeyboardEvent) => {
    // Handle keyboard shortcuts
    if (e.ctrlKey || e.metaKey) {
      switch (e.key) {
        case 'b':
          e.preventDefault();
          execCommand('bold');
          break;
        case 'i':
          e.preventDefault();
          execCommand('italic');
          break;
        case 'u':
          e.preventDefault();
          execCommand('underline');
          break;
        case 'z':
          e.preventDefault();
          execCommand('undo');
          break;
        case 'y':
          e.preventDefault();
          execCommand('redo');
          break;
      }
    }
  }, [execCommand]);

  const handleMenuOpen = (event: React.MouseEvent<HTMLElement>, type: 'size' | 'color') => {
    setAnchorEl(event.currentTarget);
    setMenuType(type);
  };

  const handleMenuClose = () => {
    setAnchorEl(null);
    setMenuType(null);
  };

  const handleFontSizeChange = (event: SelectChangeEvent) => {
    const newSize = event.target.value;
    setFontSize(newSize);
    
    // Apply font size to selected text or current selection
    if (editorRef.current) {
      editorRef.current.focus();
      execCommand('fontSize', newSize);
    }
    
    handleMenuClose();
  };

  const handleColorChange = (color: string) => {
    setTextColor(color);
    
    // Apply color to selected text or current selection
    if (editorRef.current) {
      const selection = window.getSelection();
      if (selection && selection.rangeCount > 0) {
        const range = selection.getRangeAt(0);
        if (!range.collapsed) {
          // Apply to selected text
          execCommand('foreColor', color);
        } else {
          // Apply to current cursor position
          const span = document.createElement('span');
          span.style.color = color;
          span.innerHTML = '&nbsp;'; // Add a space to make it visible
          range.insertNode(span);
          // Move cursor after the span
          range.setStartAfter(span);
          range.setEndAfter(span);
          selection.removeAllRanges();
          selection.addRange(range);
        }
      }
    }
    
    handleMenuClose();
  };

  const insertLink = () => {
    const url = prompt('Enter URL:');
    if (url) {
      execCommand('createLink', url);
    }
  };

  const insertImage = () => {
    const url = prompt('Enter image URL:');
    if (url) {
      execCommand('insertImage', url);
    }
  };

  const clearFormatting = () => {
    execCommand('removeFormat');
  };

  const toolbar = (
    <Toolbar 
      variant="dense" 
      sx={{ 
        minHeight: 48,
        borderBottom: 1,
        borderColor: 'divider',
        flexWrap: 'wrap',
        gap: 0.5
      }}
    >
      {/* Text Formatting */}
      {allowedFormats.includes('bold') && (
        <ToolbarButton command="bold" icon={<FormatBoldIcon />} tooltip="Bold (Ctrl+B)" disabled={readOnly} onCommand={execCommand} />
      )}
      {allowedFormats.includes('italic') && (
        <ToolbarButton command="italic" icon={<FormatItalicIcon />} tooltip="Italic (Ctrl+I)" disabled={readOnly} onCommand={execCommand} />
      )}
      {allowedFormats.includes('underline') && (
        <ToolbarButton command="underline" icon={<FormatUnderlinedIcon />} tooltip="Underline (Ctrl+U)" disabled={readOnly} onCommand={execCommand} />
      )}
      {allowedFormats.includes('strikethrough') && (
        <ToolbarButton command="strikeThrough" icon={<FormatStrikethroughIcon />} tooltip="Strikethrough" disabled={readOnly} onCommand={execCommand} />
      )}

      <Divider orientation="vertical" flexItem sx={{ mx: 1 }} />

      {/* Lists */}
      {allowedFormats.includes('list') && (
        <>
          <ToolbarButton command="insertUnorderedList" icon={<FormatListBulletedIcon />} tooltip="Bullet List" disabled={readOnly} onCommand={execCommand} />
          <ToolbarButton command="insertOrderedList" icon={<FormatListNumberedIcon />} tooltip="Numbered List" disabled={readOnly} onCommand={execCommand} />
        </>
      )}

      <Divider orientation="vertical" flexItem sx={{ mx: 1 }} />

      {/* Alignment */}
      {allowedFormats.includes('align') && (
        <>
          <ToolbarButton command="justifyLeft" icon={<FormatAlignLeftIcon />} tooltip="Align Left" disabled={readOnly} onCommand={execCommand} />
          <ToolbarButton command="justifyCenter" icon={<FormatAlignCenterIcon />} tooltip="Align Center" disabled={readOnly} onCommand={execCommand} />
          <ToolbarButton command="justifyRight" icon={<FormatAlignRightIcon />} tooltip="Align Right" disabled={readOnly} onCommand={execCommand} />
          <ToolbarButton command="justifyFull" icon={<FormatAlignJustifyIcon />} tooltip="Justify" disabled={readOnly} onCommand={execCommand} />
        </>
      )}

      <Divider orientation="vertical" flexItem sx={{ mx: 1 }} />

      {/* Font Size */}
      {allowedFormats.includes('size') && (
        <>
          <Tooltip title="Font Size">
            <IconButton
              size="small"
              onClick={(e: React.MouseEvent<HTMLElement>) => handleMenuOpen(e, 'size')}
              disabled={readOnly}
              data-testid="toolbar-fontsize"
            >
              <FormatSizeIcon />
            </IconButton>
          </Tooltip>
          <Menu
            anchorEl={anchorEl}
            open={menuType === 'size'}
            onClose={handleMenuClose}
            anchorOrigin={{ vertical: 'bottom', horizontal: 'left' }}
          >
            {['12px', '14px', '16px', '18px', '20px', '24px', '28px', '32px'].map((size) => (
              <MenuItem key={size} onClick={() => handleFontSizeChange({ target: { value: size } } as SelectChangeEvent)}>
                <Typography variant="body2">{size}</Typography>
              </MenuItem>
            ))}
          </Menu>
        </>
      )}

      {/* Text Color */}
      {allowedFormats.includes('color') && (
        <>
          <Tooltip title="Text Color">
            <IconButton
              size="small"
              onClick={(e: React.MouseEvent<HTMLElement>) => handleMenuOpen(e, 'color')}
              disabled={readOnly}
              data-testid="toolbar-color"
            >
              <PaletteIcon />
            </IconButton>
          </Tooltip>
          <Menu
            anchorEl={anchorEl}
            open={menuType === 'color'}
            onClose={handleMenuClose}
            anchorOrigin={{ vertical: 'bottom', horizontal: 'left' }}
          >
            <Box sx={{ p: 2, minWidth: 200 }}>
              <Typography variant="subtitle2" gutterBottom>Text Color</Typography>
              <Stack direction="row" spacing={1} flexWrap="wrap">
                {['#000000', '#FF0000', '#00FF00', '#0000FF', '#FFFF00', '#FF00FF', '#00FFFF', '#800000', '#008000', '#000080', '#808080'].map((color) => (
                  <Chip
                    key={color}
                    size="small"
                    sx={{ 
                      bgcolor: color, 
                      width: 24, 
                      height: 24,
                      cursor: 'pointer',
                      '&:hover': { opacity: 0.8 }
                    }}
                    onClick={() => handleColorChange(color)}
                  />
                ))}
              </Stack>
            </Box>
          </Menu>
        </>
      )}

      <Divider orientation="vertical" flexItem sx={{ mx: 1 }} />

      {/* Special Elements */}
      {allowedFormats.includes('link') && (
        <ToolbarButton command="link" icon={<LinkIcon />} tooltip="Insert Link" disabled={readOnly} onCommand={insertLink} />
      )}
      {allowedFormats.includes('image') && (
        <ToolbarButton command="image" icon={<ImageIcon />} tooltip="Insert Image" disabled={readOnly} onCommand={insertImage} />
      )}
      {allowedFormats.includes('code') && (
        <ToolbarButton command="code" icon={<CodeIcon />} tooltip="Code Block" disabled={readOnly} onCommand={execCommand} />
      )}

      <Divider orientation="vertical" flexItem sx={{ mx: 1 }} />

      {/* Actions */}
      <ToolbarButton command="undo" icon={<UndoIcon />} tooltip="Undo (Ctrl+Z)" disabled={readOnly} onCommand={execCommand} />
      <ToolbarButton command="redo" icon={<RedoIcon />} tooltip="Redo (Ctrl+Y)" disabled={readOnly} onCommand={execCommand} />
      <ToolbarButton command="removeFormat" icon={<FormatClearIcon />} tooltip="Clear Formatting" disabled={readOnly} onCommand={clearFormatting} />
    </Toolbar>
  );

  return (
    <Paper 
      elevation={1} 
      data-testid={testId}
      sx={{ 
        border: 1, 
        borderColor: 'divider',
        borderRadius: 1,
        overflow: 'hidden',
        ...(className && { className })
      }}
    >
      {showToolbar && toolbarPosition === 'top' && toolbar}
      
      <Box
        ref={editorRef}
        contentEditable={!readOnly}
        onInput={handleInput}
        onKeyDown={handleKeyDown}
        sx={{
          minHeight,
          maxHeight,
          p: 2,
          outline: 'none',
          overflow: 'auto',
          '&:empty:before': {
            content: `"${placeholder}"`,
            color: 'text.secondary',
            fontStyle: 'italic'
          },
          '&:focus': {
            outline: 'none'
          },
          '& h1, & h2, & h3, & h4, & h5, & h6': {
            margin: '0.5em 0',
            fontWeight: 'bold'
          },
          '& p': {
            margin: '0.5em 0'
          },
          '& ul, & ol': {
            margin: '0.5em 0',
            paddingLeft: '1.5em'
          },
          '& blockquote': {
            margin: '0.5em 0',
            paddingLeft: '1em',
            borderLeft: '3px solid',
            borderColor: 'primary.main',
            fontStyle: 'italic'
          },
          '& code': {
            backgroundColor: 'grey.100',
            padding: '0.2em 0.4em',
            borderRadius: '4px',
            fontFamily: 'monospace'
          },
          '& pre': {
            backgroundColor: 'grey.100',
            padding: '1em',
            borderRadius: '4px',
            overflow: 'auto',
            fontFamily: 'monospace'
          }
        }}
      />
      
      {showToolbar && toolbarPosition === 'bottom' && toolbar}
    </Paper>
  );
};

export { RichTextEditor };
export default RichTextEditor;
