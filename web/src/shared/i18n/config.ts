"use client";
import i18n from "i18next";
import { initReactI18next } from "react-i18next";
import en from "./locales/en/common.json";
import pt from "./locales/pt/common.json";
import { logger } from "@/shared/utils/logger";

// Initialize i18n with common translations only
if (!i18n.isInitialized) {
  i18n
    .use(initReactI18next)
    .init({
      resources: {
        en: { common: en },
        pt: { common: pt },
      },
      lng: "pt",
      fallbackLng: "pt", // Changed to pt to force Portuguese fallback
      ns: ["common"],
      defaultNS: "common",
      interpolation: { escapeValue: false },
      initImmediate: false,
    })
    .catch((error) => {
      logger.error("Failed to initialize i18n:", error);
    });
} else {
  // If already initialized, ensure language is correct
  if (i18n.language !== "pt") {
    i18n.changeLanguage("pt");
  }
}

export default i18n;
