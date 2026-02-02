import Script from "next/script";

const enabled = process.env.NEXT_PUBLIC_CF_WEB_ANALYTICS_ENABLED === "true";
const token = process.env.NEXT_PUBLIC_CF_WEB_ANALYTICS_TOKEN;

export function CloudflareAnalytics() {
  if (!enabled || !token) {
    return null;
  }

  return (
    <Script
      defer
      src="https://static.cloudflareinsights.com/beacon.min.js"
      data-cf-beacon={JSON.stringify({ token })}
    />
  );
}
