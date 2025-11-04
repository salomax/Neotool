"use client";

import * as React from "react";
import { MaskedField } from "../MaskedField";

export type CEPFieldProps = {
  name: string;
  label?: string;
  placeholder?: string;
  disabled?: boolean;
  fullWidth?: boolean;
};

export const CEPField: React.FC<CEPFieldProps> = (props) => {
  return (
    <MaskedField
      {...props}
      mask="99999-999"
      placeholder={props.placeholder ?? "00000-000"}
    />
  );
};
