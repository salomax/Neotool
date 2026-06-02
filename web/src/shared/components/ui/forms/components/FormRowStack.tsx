// web/src/shared/components/ui/forms/components/FormRowStack.tsx
"use client";

import * as React from "react";
import MuiStack, { StackProps as MuiStackProps } from "@mui/material/Stack";

export type FormRowStackProps = MuiStackProps;

const DEFAULT_SPACING = 1.5;
const DEFAULT_DIRECTION = { xs: "column", sm: "row" } as const;

export const FormRowStack = React.forwardRef<HTMLDivElement, FormRowStackProps>(function FormRowStack(
  { spacing = DEFAULT_SPACING, direction = DEFAULT_DIRECTION, ...props },
  ref
) {
  return <MuiStack ref={ref} spacing={spacing} direction={direction as any} {...props} />;
});

export default FormRowStack;
