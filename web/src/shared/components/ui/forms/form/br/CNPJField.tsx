"use client";

import * as React from "react";
import { MaskedField } from "../MaskedField";

export type CNPJFieldProps = {
  name: string;
  label?: string;
  placeholder?: string;
  disabled?: boolean;
  fullWidth?: boolean;
};

export const CNPJField: React.FC<CNPJFieldProps> = (props) => {
  return (
    <MaskedField
      {...props}
      mask="99.999.999/9999-99"
      placeholder={props.placeholder ?? "00.000.000/0000-00"}
    />
  );
};
