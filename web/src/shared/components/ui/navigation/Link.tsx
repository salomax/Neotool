"use client";

import * as React from "react";
import MuiLink, { LinkProps as MuiLinkProps } from "@mui/material/Link";
import OpenInNewIcon from "@mui/icons-material/OpenInNew";
import NextLink from "next/link";
import { getTestIdProps } from '@/shared/utils/testid';

export interface LinkProps extends MuiLinkProps {
  external?: boolean;
  showIcon?: boolean;
  name?: string;
  'data-testid'?: string;
}

// Create a NextLinkBehavior component that properly forwards refs for MUI
// This is required when using Next.js Link with MUI's Link component prop
const NextLinkBehavior = React.forwardRef<HTMLAnchorElement, React.ComponentProps<typeof NextLink>>(
  (props, ref) => {
    return <NextLink ref={ref} {...props} />;
  }
);
NextLinkBehavior.displayName = 'NextLinkBehavior';

export const Link = React.forwardRef<HTMLAnchorElement, LinkProps>(({
  external,
  showIcon = external,
  children,
  name,
  'data-testid': dataTestId,
  component,
  ...rest
}, ref) => {
  // Generate data-testid from component name and optional name prop
  const testIdProps = getTestIdProps('Link', name, dataTestId);
  
  const props = external
    ? { target: "_blank", rel: "noopener noreferrer" }
    : {};
  
  // If component is NextLink, use our NextLinkBehavior wrapper instead
  // Check by reference, name, or displayName to handle different import scenarios
  const isNextLink = component === NextLink || 
    (typeof component === 'function' && 
     (component.name === 'Link' || component.displayName === 'Link'));
  const linkComponent = isNextLink ? NextLinkBehavior : component;
  
  // Only pass component prop if it's defined
  const muiLinkProps = linkComponent ? { component: linkComponent } : {};
  
  return (
    <MuiLink ref={ref} {...props} {...testIdProps} {...muiLinkProps} {...rest}>
      {children}
      {showIcon && (
        <OpenInNewIcon fontSize="inherit" style={{ marginLeft: 4 }} />
      )}
    </MuiLink>
  );
});

Link.displayName = 'Link';

export default Link;
