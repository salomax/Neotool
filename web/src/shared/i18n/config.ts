"use client";
import i18n from "i18next";
import { initReactI18next } from "react-i18next";
import en from "./locales/en/common.json";
import pt from "./locales/pt/common.json";

// Initialize i18n with common translations only
if (!i18n.isInitialized) {
  i18n
    .use(initReactI18next)
    .init({
      resources: {
        en: { common: en },
        pt: { common: pt },
      },
      lng: "en",
      fallbackLng: "en",
      ns: ["common"],
      defaultNS: "common",
      interpolation: { escapeValue: false },
      initImmediate: false,
    })
    .catch((error) => {
      console.error("Failed to initialize i18n:", error);
    });
}

export default i18n;
