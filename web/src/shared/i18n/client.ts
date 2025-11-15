import i18n from "i18next";
import { initReactI18next } from "react-i18next";
import LanguageDetector from "i18next-browser-languagedetector";
import Backend from "i18next-http-backend";

if (typeof window !== "undefined" && !i18n.isInitialized) {
  i18n
    .use(Backend)
    .use(LanguageDetector)
    .use(initReactI18next)
    .init({
      fallbackLng: "en-US",
      supportedLngs: ["en-US", "pt-BR"],
      interpolation: { escapeValue: false },
      backend: { loadPath: "/locales/{{lng}}/common.json" },
    });
}

export default i18n;
