"use client";

import React from "react";
import { Rating } from '@/shared/components/ui/primitives/Rating';
import { ComponentRendererProps } from '../types';

// Controlled Rating component for interactive examples
const ControlledRating: React.FC<{
  value: number;
  max: number;
  variant?: 'star' | 'thumbs' | 'heart' | 'emoji';
  showValue?: boolean;
  showLabels?: boolean;
  precision?: number;
}> = ({ value: initialValue, max, variant = 'star', showValue = false, showLabels = false, precision = 1 }) => {
  const [ratingValue, setRatingValue] = React.useState(initialValue);
  
  return (
    <Rating
      value={ratingValue}
      max={max}
      variant={variant}
      showValue={showValue}
      showLabels={showLabels}
      precision={precision}
      onChange={(value: number) => {
        console.log('Rating changed:', value);
        setRatingValue(value);
      }}
    />
  );
};

export const RatingRenderer: React.FC<ComponentRendererProps> = ({ example }) => {
  switch (example) {
    case 'Star Rating':
      return <ControlledRating value={4} max={5} variant="star" showValue showLabels />;
    
    case 'Thumbs Rating':
      return <ControlledRating value={1} max={2} variant="thumbs" showValue showLabels />;
    
    case 'Heart Rating':
      return <ControlledRating value={3} max={5} variant="heart" showValue showLabels />;
    
    case 'Emoji Rating':
      return <ControlledRating value={4} max={5} variant="emoji" showValue showLabels />;
    
    case 'Half Precision':
      return <ControlledRating value={3.5} max={5} variant="star" precision={0.5} showValue />;
    
    case 'Read Only':
      return (
        <Rating
          value={4}
          max={5}
          readOnly
          showValue
        />
      );
    
    default:
      return <ControlledRating value={3} max={5} variant="star" showValue />;
  }
};
