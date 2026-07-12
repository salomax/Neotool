import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { CATEGORY_ICON_NAMES as categoryFormIconNames } from '@/app/personal-finance/categories/constants';
import { CategoryIcon } from '../CategoryIcon';
import {
  CATEGORY_ICON_NAMES as categoryPickerIconNames,
  categoryIconMap,
} from '../categoryIconMap';

describe('CategoryIcon registry', () => {
  it('keeps category form icon names in sync with the icon map', () => {
    const missingIcons = categoryFormIconNames.filter((iconName) => !(iconName in categoryIconMap));

    expect(missingIcons).toEqual([]);
  });

  it('keeps category picker icon names in sync with the icon map', () => {
    const missingIcons = categoryPickerIconNames.filter((iconName) => !(iconName in categoryIconMap));

    expect(missingIcons).toEqual([]);
  });
});

describe('CategoryIcon', () => {
  it('renders representative new icons without falling back to HelpOutlineIcon', () => {
    render(
      <>
        <CategoryIcon iconName="content_cut" />
        <CategoryIcon iconName="face_retouching_natural" />
        <CategoryIcon iconName="diamond" />
      </>,
    );

    expect(screen.getByTestId('ContentCutIcon')).toBeInTheDocument();
    expect(screen.getByTestId('FaceRetouchingNaturalIcon')).toBeInTheDocument();
    expect(screen.getByTestId('DiamondIcon')).toBeInTheDocument();
    expect(screen.queryByTestId('HelpOutlineIcon')).not.toBeInTheDocument();
  });
});
