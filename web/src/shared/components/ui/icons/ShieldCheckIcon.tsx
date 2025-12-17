import React from 'react';
import SvgIcon from '@mui/material/SvgIcon';
import type { SvgIconProps } from '@mui/material/SvgIcon';

/**
 * ShieldCheckIcon - A shield icon with a checkmark inside
 * Custom icon component for representing verified/secure roles
 */
export const ShieldCheckIcon: React.FC<SvgIconProps> = (props) => {
  return (
    <SvgIcon {...props} viewBox="0 0 24 24">
      {/* Shield shape */}
      <path d="M12 1L3 5v6c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V5l-9-4zm-1 14.59l-3.29-3.3 1.41-1.41L11 13.17l4.88-4.88 1.41 1.41L11 15.59z" />
    </SvgIcon>
  );
};

export default ShieldCheckIcon;
