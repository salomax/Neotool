import { describe, it, expect, vi } from "vitest";
import { createTheme } from "@mui/material/styles";
import { getInstitutionTypeConfig } from "../institutionType";
import type { BacenInstitutionType } from "../institutionType";

describe("institutionType", () => {
  const theme = createTheme();
  const t = vi.fn((key: string) => {
    const map: Record<string, string> = {
      "institutionTypes.individualInstitution": "Instituição Individual",
      "institutionTypes.prudentialGroup": "Grupo Prudencial",
      "institutionTypes.financialGroup": "Grupo Financeiro",
    };
    return map[key] ?? key;
  });

  describe("getInstitutionTypeConfig", () => {
    it("should return default config for null", () => {
      const config = getInstitutionTypeConfig(null, theme, t);
      expect(config.label).toBe("");
      expect(config.icon).toBeDefined();
      expect(config.color).toBe(theme.palette.text.secondary);
    });

    it("should return default config for undefined", () => {
      const config = getInstitutionTypeConfig(undefined, theme, t);
      expect(config.label).toBe("");
    });

    it("should return default config with fallbackLabel when type is null", () => {
      const config = getInstitutionTypeConfig(
        null,
        theme,
        t,
        "Unknown Type"
      );
      expect(config.label).toBe("Unknown Type");
    });

    it("should return INDIVIDUAL_INSTITUTION config", () => {
      const config = getInstitutionTypeConfig(
        "INDIVIDUAL_INSTITUTION",
        theme,
        t
      );
      expect(config.label).toBe("Instituição Individual");
      expect(config.color).toBe(theme.palette.primary.main);
      expect(t).toHaveBeenCalledWith("institutionTypes.individualInstitution");
    });

    it("should return PRUDENTIAL_GROUP config", () => {
      const config = getInstitutionTypeConfig(
        "PRUDENTIAL_GROUP",
        theme,
        t
      );
      expect(config.label).toBe("Grupo Prudencial");
      expect(config.color).toBe(theme.palette.secondary.main);
      expect(t).toHaveBeenCalledWith("institutionTypes.prudentialGroup");
    });

    it("should return FINANCIAL_GROUP config", () => {
      const config = getInstitutionTypeConfig(
        "FINANCIAL_GROUP",
        theme,
        t
      );
      expect(config.label).toBe("Grupo Financeiro");
      expect(config.color).toBe(theme.palette.success.main);
      expect(t).toHaveBeenCalledWith("institutionTypes.financialGroup");
    });

    it("should return default config for unknown type", () => {
      const config = getInstitutionTypeConfig(
        "UNKNOWN" as BacenInstitutionType,
        theme,
        t,
        "Fallback"
      );
      expect(config.label).toBe("Fallback");
    });
  });
});
