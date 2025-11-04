// web/src/components/form/FormRow.tsx
"use client";

import * as React from "react";
import Grid from '@mui/material/Grid';
import type { GridProps } from '@mui/material/Grid';

type Breakpoint = "xs" | "sm" | "md" | "lg" | "xl";

export type FormRowProps = {
  /** Quantidade de colunas por breakpoint (ex.: { xs: 1, sm: 2, md: 3 }) */
  cols?: Partial<Record<Breakpoint, number>>;
  /** Espaçamento entre campos (MUI spacing units) */
  spacing?: GridProps["spacing"];
  /** Filhos: campos/inputs */
  children: React.ReactNode;
  /** Alinhamento vertical dos itens */
  alignItems?: GridProps["alignItems"];
};

/** Distribui os filhos em colunas iguais usando MUI Grid. */
export function FormRow({
  cols = { xs: 1, sm: 2 },
  spacing = 2,
  alignItems = "flex-start",
  children,
}: FormRowProps) {
  const items = React.Children.toArray(children).filter(Boolean);

  const span = (bp: Breakpoint) => {
    const count = cols[bp] ?? cols.xs ?? 1;
    const raw = Math.floor(12 / Math.max(1, count));
    // garante valor válido (1..12)
    return Math.min(12, Math.max(1, raw));
  };

  return (
    <Grid container spacing={spacing} alignItems={alignItems}>
      {items.map((child, idx) => (
        <Grid
          key={idx}
          item
          xs={12}
          sm={span("sm")}
          md={span("md")}
          lg={span("lg")}
          xl={span("xl")}
        >
          {child}
        </Grid>
      ))}
    </Grid>
  );
}

export default FormRow;
