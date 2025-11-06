import { Config } from "@docusaurus/types";

const config: Config = {
  title: "Invistus Docs",
  url: "https://example.com",
  baseUrl: "/",
  organizationName: "neotool",
  projectName: "docs",
  onBrokenLinks: "throw",
  onBrokenMarkdownLinks: "warn",
  i18n: { defaultLocale: "en", locales: ["en", "pt-BR"] },
  presets: [
    ["classic", { docs: { sidebarPath: require.resolve("./sidebars.ts") } }],
  ],
};
export default config;
