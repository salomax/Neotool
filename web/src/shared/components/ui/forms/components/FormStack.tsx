// web/src/shared/components/ui/forms/components/FormStack.tsx
"use client";

import * as React from "react";
import MuiStack, { StackProps as MuiStackProps } from "@mui/material/Stack";

export type FormStackProps = MuiStackProps;

const DEFAULT_SPACING = 1.5;

export const FormStack = React.forwardRef<HTMLDivElement, FormStackProps>(function FormStack(
  { spacing = DEFAULT_SPACING, direction = "column", ...props },
  ref
) {
  return <MuiStack ref={ref} spacing={spacing} direction={direction} {...props} />;
});

export default FormStack;
