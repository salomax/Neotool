import { ExpoConfig } from "expo/config";

export default ({ config }: { config: ExpoConfig }) => ({
  name: "Invistus",
  slug: "neotool",
  version: "1.0.0",
  updates: { url: "https://u.expo.dev/00000000-0000-0000-0000-000000000000" },
  ios: { supportsTablet: true },
  android: {
    adaptiveIcon: {
      foregroundImage: "./assets/icon.png",
      backgroundColor: "#ffffff",
    },
  },
  plugins: ["expo-router"],
});
