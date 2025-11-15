
import { Slot } from "expo-router";
import { useEffect } from "react";
import * as Updates from "expo-updates";

export default function Layout() {
  useEffect(() => {
    (async () => {
      try {
        const update = await Updates.checkForUpdateAsync();
        if (update.isAvailable) await Updates.fetchUpdateAsync().then(Updates.reloadAsync);
      } catch {}
    })();
  }, []);
  return <Slot />;
}
