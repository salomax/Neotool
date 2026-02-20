import LandingPageClient from "./LandingPageClient";
import { Metadata } from "next";
import { getDictionary } from "@/shared/i18n/server";

// Default to PT
const t = getDictionary("pt").seo;

export const metadata: Metadata = {
  title: t.default.title,
  description: t.default.description,
  alternates: {
    canonical: "https://neotool.com.br",
  },
};

export default function LandingPage() {
  const jsonLd = {
    "@context": "https://schema.org",
    "@type": "Organization",
    name: "neotool",
    url: "https://neotool.com.br",
    logo: "https://neotool.com.br/images/logos/neotool-logo-blue.svg",
    sameAs: ["https://www.linkedin.com/company/neotool"],
    description: t.landing.jsonLdDescription,
    address: {
      "@type": "PostalAddress",
      addressCountry: "BR",
    },
  };

  return (
    <>
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }}
      />
      <LandingPageClient />
    </>
  );
}
