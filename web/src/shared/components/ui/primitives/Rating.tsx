import React, { useState, useCallback } from 'react';
import {
  Box,
  IconButton,
  Tooltip,
  Typography,
  Stack,
} from '@/shared/ui/mui-imports';
import {
  StarIcon,
  StarBorderIcon,
  ThumbUpIcon,
  ThumbDownIcon,
  ThumbUpOutlinedIcon,
  ThumbDownOutlinedIcon,
  FavoriteIcon,
  FavoriteBorderIcon,
  EmojiEmotionsIcon,
  EmojiEmotionsOutlinedIcon
} from '@/shared/ui/mui-imports';

export interface RatingProps {
  value?: number;
  max?: number;
  size?: 'small' | 'medium' | 'large';
  variant?: 'star' | 'thumbs' | 'heart' | 'emoji';
  color?: 'primary' | 'secondary' | 'error' | 'warning' | 'info' | 'success';
  readOnly?: boolean;
  disabled?: boolean;
  showLabels?: boolean;
  showValue?: boolean;
  precision?: number;
  onChange?: (value: number) => void;
  onHover?: (value: number) => void;
  onLeave?: () => void;
  className?: string;
  'data-testid'?: string;
}

const Rating: React.FC<RatingProps> = ({
  value,
  max = 5,
  size = 'medium',
  variant = 'star',
  color = 'primary',
  readOnly = false,
  disabled = false,
  showLabels = false,
  showValue = false,
  precision = 1,
  onChange,
  onHover,
  onLeave,
  className,
  'data-testid': testId
}) => {
  const [hoverValue, setHoverValue] = useState<number | null>(null);
  const [internalValue, setInternalValue] = useState(value ?? 0);

  const isControlled = value !== undefined;
  const currentValue = isControlled ? value : internalValue;
  
  // Display value: hover > current value
  const displayValue = hoverValue !== null ? hoverValue : currentValue;
  const isInteractive = !readOnly && !disabled;

  // Map our size to IconButton size (MUI only supports 'small' and 'medium')
  const iconButtonSize = size === 'large' ? 'medium' : size;

  const getSizeStyles = () => {
    switch (size) {
      case 'small':
        return { fontSize: '1.2rem' };
      case 'large':
        return { fontSize: '2.5rem' };
      default:
        return { fontSize: '1.8rem' };
    }
  };

  const getColorStyles = () => {
    switch (color) {
      case 'secondary':
        return { color: 'secondary.main' };
      case 'error':
        return { color: 'error.main' };
      case 'warning':
        return { color: 'warning.main' };
      case 'info':
        return { color: 'info.main' };
      case 'success':
        return { color: 'success.main' };
      default:
        return { color: 'primary.main' };
    }
  };

  const getIcon = (extraValue: number, showFilled: boolean) => {
    switch (variant) {
      case 'thumbs':
        if (extraValue === 1) {
          return showFilled ? <ThumbUpIcon /> : <ThumbUpOutlinedIcon />;
        } else {
          return showFilled ? <ThumbDownIcon /> : <ThumbDownOutlinedIcon />;
        }
      case 'heart':
        return showFilled ? <FavoriteIcon /> : <FavoriteBorderIcon />;
      case 'emoji':
        return showFilled ? <EmojiEmotionsIcon /> : <EmojiEmotionsOutlinedIcon />;
      default:
        return showFilled ? <StarIcon /> : <StarBorderIcon />;
    }
  };

  const getLabel = (index: number) => {
    if (!showLabels) return '';
    
    const labels = {
      star: ['Poor', 'Fair', 'Good', 'Very Good', 'Excellent'],
      thumbs: ['Like', 'Dislike'],
      heart: ['No Love', 'Little Love', 'Some Love', 'Much Love', 'True Love'],
      emoji: ['Very Sad', 'Sad', 'Neutral', 'Happy', 'Very Happy']
    };

    const labelArray = labels[variant] || labels.star;
    return labelArray[index] || '';
  };

  const handleClick = useCallback((clickedValue: number) => {
    if (!isInteractive) return;
    
    let newValue: number;
    
    if (variant === 'thumbs') {
      const current = isControlled ? value! : internalValue;
      if (clickedValue === 1) {
        newValue = current === 1 ? 0 : 1;
      } else {
        newValue = 0;
      }
    } else {
      newValue = clickedValue;
    }
    
    // Update internal state if uncontrolled
    if (!isControlled) {
      setInternalValue(newValue);
    }
    
    // Clear hover state
    setHoverValue(null);
    
    // Call onChange
    onChange?.(newValue);
  }, [isInteractive, isControlled, onChange, variant, value, internalValue]);

  const handleHalfClick = useCallback((index: number, isLeftHalf: boolean) => {
    if (!isInteractive) return;
    
    const newValue = isLeftHalf ? index + 0.5 : index + 1;
    
    if (!isControlled) {
      setInternalValue(newValue);
    }
    
    setHoverValue(null);
    onChange?.(newValue);
  }, [isInteractive, isControlled, onChange]);

  const handleMouseEnter = useCallback((hoveredValue: number) => {
    if (!isInteractive) return;
    
    if (variant === 'thumbs') {
      setHoverValue(hoveredValue === 1 ? 1 : 0);
    } else {
      setHoverValue(hoveredValue);
    }
    onHover?.(hoveredValue);
  }, [isInteractive, onHover, variant]);

  const handleHalfMouseEnter = useCallback((index: number, isLeftHalf: boolean) => {
    if (!isInteractive) return;
    const hoverValue = isLeftHalf ? index + 0.5 : index + 1;
    setHoverValue(hoverValue);
    onHover?.(hoverValue);
  }, [isInteractive, onHover]);

  const handleMouseLeave = useCallback(() => {
    if (!isInteractive) return;
    setHoverValue(null);
    onLeave?.();
  }, [isInteractive, onLeave]);

  const handleKeyDown = useCallback((event: React.KeyboardEvent, clickedValue: number) => {
    if (!isInteractive) return;
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      handleClick(clickedValue);
    }
  }, [isInteractive, handleClick]);

  const renderRatingItem = (index: number) => {
    const itemValue = index + 1;
    const isFilled = itemValue <= displayValue;
    
    return (
      <Tooltip 
        key={index} 
        title={showLabels ? getLabel(index) : ''} 
        placement="top"
        disableHoverListener={!showLabels}
      >
        <IconButton
          size={iconButtonSize}
          onClick={() => handleClick(itemValue)}
          onMouseEnter={() => handleMouseEnter(itemValue)}
          onMouseLeave={handleMouseLeave}
          onKeyDown={(e) => handleKeyDown(e, itemValue)}
          disabled={disabled || readOnly}
          tabIndex={isInteractive ? 0 : -1}
          data-testid={`rating-item-${index}`}
          sx={{
            ...getSizeStyles(),
            ...getColorStyles(),
            '& svg': {
              fontSize: 'inherit',
            },
            opacity: disabled ? 0.5 : 1,
            cursor: isInteractive ? 'pointer' : 'default',
            transition: 'color 0.2s ease-in-out, transform 0.1s ease-in-out',
            '&:hover': isInteractive ? {
              transform: 'scale(1.05)',
              opacity: 0.8
            } : {},
            '&:focus': isInteractive ? {
              outline: '2px solid',
              outlineColor: 'primary.main',
              outlineOffset: '2px'
            } : {}
          }}
        >
          {getIcon(itemValue, isFilled)}
        </IconButton>
      </Tooltip>
    );
  };

  const renderHalfRating = (index: number) => {
    const itemValue = index + 1;
    const isFilled = itemValue < displayValue;
    const floorValue = Math.floor(displayValue);
    const isHalfFilled = precision === 0.5 && floorValue === index && displayValue % 1 !== 0;
    
    const handleClick = (e: React.MouseEvent<HTMLDivElement>) => {
      if (!isInteractive) return;
      const rect = e.currentTarget.getBoundingClientRect();
      const clickX = e.clientX - rect.left;
      const isLeftHalf = clickX < rect.width / 2;
      handleHalfClick(index, isLeftHalf);
    };

    const handleMouseMove = (e: React.MouseEvent<HTMLDivElement>) => {
      if (!isInteractive) return;
      const rect = e.currentTarget.getBoundingClientRect();
      const mouseX = e.clientX - rect.left;
      const isLeftHalf = mouseX < rect.width / 2;
      handleHalfMouseEnter(index, isLeftHalf);
    };
    
    return (
      <Tooltip 
        key={index} 
        title={showLabels ? getLabel(index) : ''} 
        placement="top"
        disableHoverListener={!showLabels}
      >
        <Box
          onClick={handleClick}
          onMouseMove={handleMouseMove}
          onMouseLeave={handleMouseLeave}
          sx={{
            position: 'relative',
            display: 'inline-block',
            cursor: isInteractive ? 'pointer' : 'default',
            ...getSizeStyles()
          }}
        >
          {/* Background (empty) */}
          <IconButton
            size={iconButtonSize}
            disabled={true}
            tabIndex={-1}
            sx={{
              ...getSizeStyles(),
              color: 'grey.300',
              opacity: disabled ? 0.5 : 1,
              pointerEvents: 'none',
              '& svg': {
                fontSize: 'inherit',
              },
            }}
          >
            {getIcon(itemValue, false)}
          </IconButton>
          
          {/* Half filled overlay */}
          {isHalfFilled && (
            <Box
              sx={{
                position: 'absolute',
                top: 0,
                left: 0,
                width: '50%',
                height: '100%',
                overflow: 'hidden',
                pointerEvents: 'none'
              }}
            >
              <IconButton
                size={iconButtonSize}
                disabled
                tabIndex={-1}
                sx={{
                  ...getSizeStyles(),
                  ...getColorStyles(),
                  pointerEvents: 'none',
                  '& svg': {
                    fontSize: 'inherit',
                  },
                }}
              >
                {getIcon(itemValue, true)}
              </IconButton>
            </Box>
          )}
          
          {/* Fully filled overlay */}
          {isFilled && (
            <Box
              sx={{
                position: 'absolute',
                top: 0,
                left: 0,
                width: '100%',
                height: '100%',
                overflow: 'hidden',
                pointerEvents: 'none'
              }}
            >
              <IconButton
                size={iconButtonSize}
                disabled
                tabIndex={-1}
                sx={{
                  ...getSizeStyles(),
                  ...getColorStyles(),
                  pointerEvents: 'none',
                  '& svg': {
                    fontSize: 'inherit',
                  },
                }}
              >
                {getIcon(itemValue, true)}
              </IconButton>
            </Box>
          )}
        </Box>
      </Tooltip>
    );
  };

  return (
    <Box
      className={className}
      data-testid={testId}
      onMouseLeave={handleMouseLeave}
      sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 1,
        flexWrap: 'wrap',
        minHeight: '48px',
        position: 'relative'
      }}
    >
      <Stack 
        direction="row" 
        spacing={0.5} 
        alignItems="center"
      >
        {Array.from({ length: max }, (_, index) => 
          precision === 0.5 ? renderHalfRating(index) : renderRatingItem(index)
        )}
      </Stack>
      
      {showValue && (
        <Typography 
          variant="body2" 
          color="text.secondary"
          sx={{ 
            ml: 1,
            minWidth: '60px',
            textAlign: 'left'
          }}
        >
          {displayValue.toFixed(precision === 0.5 ? 1 : 0)}/{max}
        </Typography>
      )}
      
      {showLabels && (
        <Typography 
          variant="body2" 
          color={hoverValue !== null ? "primary.main" : "transparent"}
          sx={{ 
            ml: 1, 
            fontWeight: 'medium',
            minWidth: '120px',
            textAlign: 'left',
            transition: 'color 0.2s ease-in-out',
            visibility: hoverValue !== null ? 'visible' : 'hidden'
          }}
        >
          {hoverValue !== null && hoverValue > 0 ? getLabel(Math.max(0, Math.floor(hoverValue) - 1)) : ''}
        </Typography>
      )}
    </Box>
  );
};

export { Rating };
export default Rating;
