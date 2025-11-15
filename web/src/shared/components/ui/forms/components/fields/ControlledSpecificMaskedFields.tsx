// web/src/components/form/fields/ControlledSpecificMaskedFields.tsx
"use client";

import * as React from "react";
import { Control } from "react-hook-form";
import ControlledMaskedTextField from "./ControlledMaskedTextField";
import {
  maskCPF,
  maskCNPJ,
  maskCEP,
  maskPhoneBR,
  onlyDigits,
} from "../masks/br";

type CommonProps = {
  control: Control<any>;
  name: string;
  label?: string;
  required?: boolean;
};

export function ControlledCPFField(props: CommonProps) {
  return (
    <ControlledMaskedTextField
      {...props}
      numeric
      mask={maskCPF}
      transformOut={(masked) => masked.replace(/\D+/g, "")}
      transformIn={(val) =>
        typeof val === "string" ? onlyDigits(val) : String(val ?? "")
      }
    />
  );
}

export function ControlledCNPJField(props: CommonProps) {
  return (
    <ControlledMaskedTextField
      {...props}
      numeric
      mask={maskCNPJ}
      transformOut={(masked) => masked.replace(/\D+/g, "")}
      transformIn={(val) =>
        typeof val === "string" ? onlyDigits(val) : String(val ?? "")
      }
    />
  );
}

export function ControlledCEPField(props: CommonProps) {
  return (
    <ControlledMaskedTextField
      {...props}
      numeric
      mask={maskCEP}
      transformOut={(masked) => masked.replace(/\D+/g, "")}
      transformIn={(val) =>
        typeof val === "string" ? onlyDigits(val) : String(val ?? "")
      }
    />
  );
}

export function ControlledPhoneBRField(props: CommonProps) {
  return (
    <ControlledMaskedTextField
      {...props}
      numeric
      mask={maskPhoneBR}
      transformOut={(masked) => masked.replace(/\D+/g, "")}
      transformIn={(val) =>
        typeof val === "string" ? onlyDigits(val) : String(val ?? "")
      }
    />
  );
}

// Aliases com o prefixo "Controller*" se preferir essa convenção
export const ControllerCPFField = ControlledCPFField;
export const ControllerCNPJField = ControlledCNPJField;
export const ControllerCEPField = ControlledCEPField;
export const ControllerPhoneBRField = ControlledPhoneBRField;
