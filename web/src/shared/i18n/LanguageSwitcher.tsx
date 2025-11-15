"use client";
import React from "react";
import i18n from "@/shared/i18n/config";
import { Button, Stack } from "@mui/material";

export default function LanguageSwitcher() {
  const [lang, setLang] = React.useState(i18n.language || "en");

  React.useEffect(() => {
    const onChange = (lng: string) => setLang(lng);
    i18n.on("languageChanged", onChange);
    return () => {
      i18n.off("languageChanged", onChange);
    };
  }, []);

  const change = (lng: "en" | "pt") => {
    if (lng !== i18n.language) i18n.changeLanguage(lng);
  };

  return (
    <Stack
      direction="row"
      spacing={1}
      alignItems="center"
      data-testid="lang-switcher"
    >
      <span data-testid="lang-current">{lang}</span>
      <Button
        size="small"
        variant="outlined"
        onClick={() => change("en")}
        data-testid="lang-en"
      >
        EN
      </Button>
      <Button
        size="small"
        variant="outlined"
        onClick={() => change("pt")}
        data-testid="lang-pt"
      >
        PT
      </Button>
    </Stack>
  );
}
