import LandingPageClient from "./LandingPageClient";
import { Metadata } from "next";
import { getDictionary } from '@/shared/i18n/server';

// Default to PT
const t = getDictionary('pt').seo;

export const metadata: Metadata = {
  title: t.default.title,
  description: t.default.description,
  alternates: {
    canonical: "https://invistus.com.br",
  },
};

export default function LandingPage() {
  const jsonLd = {
    "@context": "https://schema.org",
    "@type": "Organization",
    "name": "Invistus",
    "url": "https://invistus.com.br",
    "logo": "https://invistus.com.br/images/logos/invistus-logo-blue.svg",
    "sameAs": [
      "https://www.linkedin.com/company/invistus"
    ],
    "description": t.landing.jsonLdDescription,
    "address": {
      "@type": "PostalAddress",
      "addressCountry": "BR"
    }
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
