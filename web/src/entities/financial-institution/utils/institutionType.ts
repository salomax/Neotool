/**
 * Institution type configuration utilities
 */

import { Theme, alpha } from "@mui/material/styles";
import BusinessIcon from "@mui/icons-material/Business";
import LocationCityIcon from "@mui/icons-material/LocationCity";
import type { ComponentType } from "react";

export type BacenInstitutionType =
  | "INDIVIDUAL_INSTITUTION"
  | "PRUDENTIAL_GROUP"
  | "FINANCIAL_GROUP"
  | null
  | undefined;

export interface InstitutionTypeConfig {
  label: string;
  icon: ComponentType<any>;
  color: string;
  bgColor: string;
}

/**
 * Get institution type configuration based on type
 *
 * @param type - Institution type enum value
 * @param theme - MUI theme object
 * @param t - Translation function
 * @param fallbackLabel - Fallback label if type is not recognized
 * @returns Configuration object with label, icon, color, and background color
 *
 * @example
 * ```ts
 * const config = getInstitutionTypeConfig(
 *   "INDIVIDUAL_INSTITUTION",
 *   theme,
 *   (key) => translations[key]
 * );
 * // Returns: { label: "Instituição Individual", icon: BusinessIcon, color: ..., bgColor: ... }
 * ```
 */
export function getInstitutionTypeConfig(
  type: BacenInstitutionType,
  theme: Theme,
  t: (key: string) => string,
  fallbackLabel: string = ""
): InstitutionTypeConfig {
  // Default fallback configuration
  const defaultConfig: InstitutionTypeConfig = {
    label: fallbackLabel,
    icon: BusinessIcon,
    color: theme.palette.text.secondary,
    bgColor: alpha(theme.palette.action.hover, 0.5),
  };

  if (!type) {
    return defaultConfig;
  }

  switch (type) {
    case "INDIVIDUAL_INSTITUTION":
      return {
        label: t("institutionTypes.individualInstitution"),
        icon: BusinessIcon,
        color: theme.palette.primary.main,
        bgColor: alpha(theme.palette.primary.main, 0.08),
      };
    case "PRUDENTIAL_GROUP":
      return {
        label: t("institutionTypes.prudentialGroup"),
        icon: LocationCityIcon,
        color: theme.palette.secondary.main,
        bgColor: alpha(theme.palette.secondary.main, 0.08),
      };
    case "FINANCIAL_GROUP":
      return {
        label: t("institutionTypes.financialGroup"),
        icon: LocationCityIcon,
        color: theme.palette.success.main,
        bgColor: alpha(theme.palette.success.main, 0.08),
      };
    default:
      return defaultConfig;
  }
}
