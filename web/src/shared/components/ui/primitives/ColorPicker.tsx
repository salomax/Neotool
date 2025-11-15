import React, { useState, useCallback, useRef, useEffect } from 'react';
import {
  Box,
  Button,
  Popover,
  Typography,
  Stack,
  Paper,
  TextField,
  InputAdornment,
  IconButton,
  Tooltip,
  Divider,
  Chip,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Grid
} from '@/shared/ui/mui-imports';
import {
  PaletteIcon,
  CheckIcon,
  ContentCopyIcon,
  RefreshIcon
} from '@/shared/ui/mui-imports';

export interface ColorPickerProps {
  value?: string;
  onChange?: (color: string) => void;
  variant?: 'standard' | 'outlined' | 'filled';
  size?: 'small' | 'medium' | 'large';
  showPresets?: boolean;
  showCustomInput?: boolean;
  showHexInput?: boolean;
  showRgbInput?: boolean;
  showHslInput?: boolean;
  presets?: string[];
  disabled?: boolean;
  readOnly?: boolean;
  placeholder?: string;
  label?: string;
  helperText?: string;
  error?: boolean;
  className?: string;
  'data-testid'?: string;
}

const ColorPicker: React.FC<ColorPickerProps> = ({
  value = '#000000',
  onChange,
  variant = 'standard',
  size = 'medium',
  showPresets = true,
  showCustomInput = true,
  showHexInput = true,
  showRgbInput = false,
  showHslInput = false,
  presets = [
    '#000000', '#FFFFFF', '#FF0000', '#00FF00', '#0000FF', '#FFFF00', '#FF00FF', '#00FFFF',
    '#800000', '#008000', '#000080', '#808080', '#FFA500', '#FFC0CB', '#A52A2A', '#800080',
    '#FFD700', '#C0C0C0', '#808000', '#00CED1', '#FF6347', '#40E0D0', '#EE82EE', '#F0E68C'
  ],
  disabled = false,
  readOnly = false,
  placeholder = 'Select a color',
  label = 'Color',
  helperText,
  error = false,
  className,
  'data-testid': testId
}) => {
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);
  const [tempColor, setTempColor] = useState(value);
  const [inputValue, setInputValue] = useState(value);
  const [inputFormat, setInputFormat] = useState<'hex' | 'rgb' | 'hsl'>('hex');
  const [isValidColor, setIsValidColor] = useState(true);

  // Sync internal state with external value prop
  useEffect(() => {
    setTempColor(value);
    setInputValue(value);
  }, [value]);
  const inputRef = useRef<HTMLInputElement>(null);

  const isOpen = Boolean(anchorEl);

  // Convert color formats
  const hexToRgb = (hex: string) => {
    const result = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex);
    return result ? {
      r: parseInt(result[1]!, 16),
      g: parseInt(result[2]!, 16),
      b: parseInt(result[3]!, 16)
    } : null;
  };

  const rgbToHex = (r: number, g: number, b: number) => {
    return "#" + ((1 << 24) + (r << 16) + (g << 8) + b).toString(16).slice(1);
  };

  const hexToHsl = (hex: string) => {
    const rgb = hexToRgb(hex);
    if (!rgb) return null;
    
    const { r, g, b } = rgb;
    const rNorm = r / 255;
    const gNorm = g / 255;
    const bNorm = b / 255;
    
    const max = Math.max(rNorm, gNorm, bNorm);
    const min = Math.min(rNorm, gNorm, bNorm);
    let h = 0, s = 0, l = (max + min) / 2;
    
    if (max !== min) {
      const d = max - min;
      s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
      switch (max) {
        case rNorm: h = (gNorm - bNorm) / d + (gNorm < bNorm ? 6 : 0); break;
        case gNorm: h = (bNorm - rNorm) / d + 2; break;
        case bNorm: h = (rNorm - gNorm) / d + 4; break;
      }
      h /= 6;
    }
    
    return {
      h: Math.round(h * 360),
      s: Math.round(s * 100),
      l: Math.round(l * 100)
    };
  };

  const hslToHex = (h: number, s: number, l: number) => {
    const hNorm = h / 360;
    const sNorm = s / 100;
    const lNorm = l / 100;
    
    const hue2rgb = (p: number, q: number, t: number) => {
      if (t < 0) t += 1;
      if (t > 1) t -= 1;
      if (t < 1/6) return p + (q - p) * 6 * t;
      if (t < 1/2) return q;
      if (t < 2/3) return p + (q - p) * (2/3 - t) * 6;
      return p;
    };
    
    let r, g, b;
    if (sNorm === 0) {
      r = g = b = lNorm;
    } else {
      const q = lNorm < 0.5 ? lNorm * (1 + sNorm) : lNorm + sNorm - lNorm * sNorm;
      const p = 2 * lNorm - q;
      r = hue2rgb(p, q, hNorm + 1/3);
      g = hue2rgb(p, q, hNorm);
      b = hue2rgb(p, q, hNorm - 1/3);
    }
    
    return rgbToHex(Math.round(r * 255), Math.round(g * 255), Math.round(b * 255));
  };

  const validateColor = (color: string) => {
    const hexPattern = /^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$/;
    const rgbPattern = /^rgb\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})\s*\)$/;
    const hslPattern = /^hsl\(\s*(\d{1,3})\s*,\s*(\d{1,3})%\s*,\s*(\d{1,3})%\s*\)$/;
    
    return hexPattern.test(color) || rgbPattern.test(color) || hslPattern.test(color);
  };

  const parseColorInput = (input: string, format: 'hex' | 'rgb' | 'hsl') => {
    let hex = '';
    
    switch (format) {
      case 'hex':
        hex = input.startsWith('#') ? input : `#${input}`;
        break;
      case 'rgb':
        const rgbMatch = input.match(/^rgb\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})\s*\)$/);
        if (rgbMatch) {
          const [, r, g, b] = rgbMatch.map(Number);
          hex = rgbToHex(r!, g!, b!);
        }
        break;
      case 'hsl':
        const hslMatch = input.match(/^hsl\(\s*(\d{1,3})\s*,\s*(\d{1,3})%\s*,\s*(\d{1,3})%\s*\)$/);
        if (hslMatch) {
          const [, h, s, l] = hslMatch.map(Number);
          hex = hslToHex(h!, s!, l!);
        }
        break;
    }
    
    return hex;
  };

  const handleOpen = (event: React.MouseEvent<HTMLElement>) => {
    if (disabled || readOnly) return;
    setAnchorEl(event.currentTarget);
    setTempColor(value);
    setInputValue(value);
  };

  const handleClose = () => {
    setAnchorEl(null);
    setTempColor(value);
    setInputValue(value);
  };

  const handleColorSelect = useCallback((color: string) => {
    setTempColor(color);
    setInputValue(color);
    onChange?.(color);
    handleClose();
  }, [onChange]);

  const handleInputChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const newValue = event.target.value;
    setInputValue(newValue);
    
    const isValid = validateColor(newValue);
    setIsValidColor(isValid);
    
    if (isValid) {
      const hex = parseColorInput(newValue, inputFormat);
      if (hex) {
        setTempColor(hex);
      }
    }
  };

  const handleInputSubmit = () => {
    if (isValidColor && inputValue) {
      const hex = parseColorInput(inputValue, inputFormat);
      if (hex) {
        handleColorSelect(hex);
      }
    }
  };

  const handleCopyColor = () => {
    navigator.clipboard.writeText(value);
  };

  const handleRandomColor = () => {
    const randomHex = '#' + Math.floor(Math.random() * 16777215).toString(16).padStart(6, '0');
    handleColorSelect(randomHex);
  };

  const getCurrentColorDisplay = () => {
    const rgb = hexToRgb(value);
    const hsl = hexToHsl(value);
    
    return {
      hex: value,
      rgb: rgb ? `rgb(${rgb.r}, ${rgb.g}, ${rgb.b})` : '',
      hsl: hsl ? `hsl(${hsl.h}, ${hsl.s}%, ${hsl.l}%)` : ''
    };
  };

  const currentColor = getCurrentColorDisplay();

  const renderColorPresets = () => (
    <Box sx={{ mb: 2 }}>
      <Typography variant="subtitle2" gutterBottom>
        Preset Colors
      </Typography>
      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
        {presets.map((color) => (
          <Box key={color}>
            <Tooltip title={color}>
              <Box
                sx={{
                  width: 32,
                  height: 32,
                  backgroundColor: color,
                  border: '2px solid',
                  borderColor: tempColor === color ? 'primary.main' : 'divider',
                  borderRadius: 1,
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  transition: 'all 0.2s ease-in-out',
                  '&:hover': {
                    transform: 'scale(1.1)',
                    borderColor: 'primary.main'
                  }
                }}
                onClick={() => handleColorSelect(color)}
                data-testid={`preset-color-${color}`}
              >
                {tempColor === color && (
                  <CheckIcon sx={{ color: 'white', fontSize: 16 }} />
                )}
              </Box>
            </Tooltip>
          </Box>
        ))}
      </Box>
    </Box>
  );

  const renderCustomInput = () => (
    <Box sx={{ mb: 2 }}>
      <Typography variant="subtitle2" gutterBottom>
        Custom Color
      </Typography>
      <Stack spacing={2}>
        <Box>
          <TextField
            ref={inputRef}
            fullWidth
            size="small"
            value={inputValue}
            onChange={handleInputChange}
            onKeyPress={(e: React.KeyboardEvent) => e.key === 'Enter' && handleInputSubmit()}
            error={!isValidColor}
            helperText={!isValidColor ? 'Invalid color format' : ''}
            placeholder={inputFormat === 'hex' ? '#000000' : inputFormat === 'rgb' ? 'rgb(0, 0, 0)' : 'hsl(0, 0%, 0%)'}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <Box
                    sx={{
                      width: 20,
                      height: 20,
                      backgroundColor: isValidColor ? tempColor : 'grey.300',
                      border: '1px solid',
                      borderColor: 'divider',
                      borderRadius: 0.5
                    }}
                  />
                </InputAdornment>
              ),
              endAdornment: (
                <InputAdornment position="end">
                  <IconButton size="small" onClick={handleInputSubmit} disabled={!isValidColor}>
                    <CheckIcon />
                  </IconButton>
                </InputAdornment>
              )
            }}
          />
        </Box>
        
        {(showRgbInput || showHslInput) && (
          <FormControl size="small" fullWidth>
            <InputLabel>Format</InputLabel>
            <Select
              value={inputFormat}
              onChange={(e) => setInputFormat(e.target.value as 'hex' | 'rgb' | 'hsl')}
              label="Format"
            >
              {showHexInput && <MenuItem value="hex">Hex</MenuItem>}
              {showRgbInput && <MenuItem value="rgb">RGB</MenuItem>}
              {showHslInput && <MenuItem value="hsl">HSL</MenuItem>}
            </Select>
          </FormControl>
        )}
      </Stack>
    </Box>
  );

  const renderCurrentColor = () => (
    <Box sx={{ mb: 2 }}>
      <Typography variant="subtitle2" gutterBottom>
        Current Color
      </Typography>
      <Stack direction="row" spacing={1} alignItems="center">
        <Box
          sx={{
            width: 40,
            height: 40,
            backgroundColor: value,
            border: '2px solid',
            borderColor: 'divider',
            borderRadius: 1
          }}
        />
        <Stack spacing={0.5}>
          <Chip label={currentColor.hex} size="small" />
          {showRgbInput && <Chip label={currentColor.rgb} size="small" variant="outlined" />}
          {showHslInput && <Chip label={currentColor.hsl} size="small" variant="outlined" />}
        </Stack>
        <IconButton size="small" onClick={handleCopyColor}>
          <ContentCopyIcon />
        </IconButton>
      </Stack>
    </Box>
  );

  const getButtonSize = () => {
    switch (size) {
      case 'small': return 'small';
      case 'large': return 'large';
      default: return 'medium';
    }
  };

  const getButtonVariant = () => {
    switch (variant) {
      case 'outlined': return 'outlined';
      case 'filled': return 'contained';
      default: return 'text';
    }
  };

  return (
    <Box className={className} data-testid={testId}>
      <Button
        variant={getButtonVariant()}
        size={getButtonSize()}
        onClick={handleOpen}
        disabled={disabled}
        startIcon={<PaletteIcon />}
        sx={{
          justifyContent: 'flex-start',
          textTransform: 'none',
          minWidth: 120,
          ...(variant === 'filled' && {
            backgroundColor: value,
            color: 'white',
            '&:hover': {
              backgroundColor: value,
              opacity: 0.9
            }
          })
        }}
      >
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <Box
            sx={{
              width: 16,
              height: 16,
              backgroundColor: value,
              border: '1px solid',
              borderColor: 'divider',
              borderRadius: 0.5
            }}
          />
          {value}
        </Box>
      </Button>

      <Popover
        open={isOpen}
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
          sx: { p: 2, minWidth: 300 }
        }}
      >
        <Stack spacing={2}>
          {label && (
            <Typography variant="h6" gutterBottom>
              {label}
            </Typography>
          )}

          {renderCurrentColor()}

          {showPresets && renderColorPresets()}

          {showCustomInput && renderCustomInput()}

          <Divider />

          <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: 1 }}>
            <Button
              size="small"
              startIcon={<RefreshIcon />}
              onClick={handleRandomColor}
            >
              Random
            </Button>
            <Button
              size="small"
              variant="outlined"
              onClick={handleClose}
            >
              Cancel
            </Button>
            <Button
              size="small"
              variant="contained"
              onClick={() => handleColorSelect(tempColor)}
            >
              Apply
            </Button>
          </Box>
        </Stack>
      </Popover>
    </Box>
  );
};

export { ColorPicker };
export default ColorPicker;
