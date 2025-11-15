import i18n from "i18next";
import { initReactI18next } from "react-i18next";

// Minimal resources for Storybook. Replace/extend with your real app resources.
const resources = {
  "en-US": {
    translation: {
      hello: "Hello",
      submit: "Submit",
      cancel: "Cancel",
    },
  },
  "pt-BR": {
    translation: {
      hello: "Olá",
      submit: "Enviar",
      cancel: "Cancelar",
    },
  },
};

if (!i18n.isInitialized) {
  i18n
    .use(initReactI18next)
    .init({
      resources,
      lng: "en-US",
      fallbackLng: "en-US",
      interpolation: { escapeValue: false },
    })
    .catch((e) => {
      console.warn("i18n init error (storybook):", e);
    });
}

export default i18n;
