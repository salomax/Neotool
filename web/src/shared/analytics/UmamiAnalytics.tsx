"use client";

import Script from "next/script";
import { getRuntimeConfig } from "@/shared/config/runtime-config";
import { useFeatureFlagEnabled } from "@/shared/hooks/useFeatureFlag";

const DEFAULT_UMAMI_SCRIPT_URL = "https://webanalytics.invistus.com.br/script.js";

export function UmamiAnalytics() {
  const analyticsEnabledByFlag = useFeatureFlagEnabled("web-analytics-enabled");
  const config = getRuntimeConfig();
  const { umamiWebsiteId, umamiScriptUrl } = config;

  const shouldLoad = analyticsEnabledByFlag && Boolean(umamiWebsiteId);

  if (!shouldLoad) {
    return null;
  }

  const scriptSrc = umamiScriptUrl || DEFAULT_UMAMI_SCRIPT_URL;

  return (
    <Script
      defer
      src={scriptSrc}
      data-website-id={umamiWebsiteId}
      strategy="afterInteractive"
    />
  );
}
