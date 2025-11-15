"use client";

import * as React from "react";
import MuiLink, { LinkProps as MuiLinkProps } from "@mui/material/Link";
import OpenInNewIcon from "@mui/icons-material/OpenInNew";
import { getTestIdProps } from '@/shared/utils/testid';

export interface LinkProps extends MuiLinkProps {
  external?: boolean;
  showIcon?: boolean;
  name?: string;
  'data-testid'?: string;
}

export const Link: React.FC<LinkProps> = ({
  external,
  showIcon = external,
  children,
  name,
  'data-testid': dataTestId,
  ...rest
}) => {
  // Generate data-testid from component name and optional name prop
  const testIdProps = getTestIdProps('Link', name, dataTestId);
  
  const props = external
    ? { target: "_blank", rel: "noopener noreferrer" }
    : {};
  return (
    <MuiLink {...props} {...testIdProps} {...rest}>
      {children}
      {showIcon && (
        <OpenInNewIcon fontSize="inherit" style={{ marginLeft: 4 }} />
      )}
    </MuiLink>
  );
};

export default Link;
