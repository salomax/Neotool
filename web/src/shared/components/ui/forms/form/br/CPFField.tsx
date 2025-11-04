"use client";

import * as React from "react";
import { MaskedField } from "../MaskedField";

export type CPFFieldProps = {
  name: string;
  label?: string;
  placeholder?: string;
  disabled?: boolean;
  fullWidth?: boolean;
};

export const CPFField: React.FC<CPFFieldProps> = (props) => {
  return (
    <MaskedField
      {...props}
      mask="999.999.999-99"
      placeholder={props.placeholder ?? "000.000.000-00"}
    />
  );
};
