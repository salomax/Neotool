import type { Metadata } from "next";
import FixedIncomeComparisonPageClient from "./FixedIncomeComparisonPageClient";

const PAGE_PATH = "/investments/fixed-income-comparison";
const PAGE_TITLE = "Comparador de taxas de Renda Fixa";
const PAGE_DESCRIPTION =
  "Compare taxas líquidas de renda fixa entre CDB, LCI, LCA, CRI, CRA e debêntures. Simule cenários com CDI, IPCA e pré-fixado para encontrar a melhor equivalência líquida.";

export const metadata: Metadata = {
  title: PAGE_TITLE,
  description: PAGE_DESCRIPTION,
  keywords: [
    "comparador renda fixa",
    "simulador renda fixa",
    "CDB vs LCI",
    "LCA",
    "CRI",
    "CRA",
    "debênture",
    "debênture incentivada",
    "equivalência líquida",
    "taxa líquida renda fixa",
    "IR renda fixa",
    "CDI",
    "IPCA",
    "pré-fixado",
  ],
  alternates: {
    canonical: PAGE_PATH,
  },
  openGraph: {
    title: `${PAGE_TITLE} | Invistus`,
    description: PAGE_DESCRIPTION,
    url: PAGE_PATH,
    type: "website",
    locale: "pt_BR",
    images: [
      {
        url: "/images/opengraph/opengraph-cover.png",
        width: 1200,
        height: 630,
        alt: "Invistus - Comparador de taxas de Renda Fixa",
      },
    ],
  },
  twitter: {
    card: "summary_large_image",
    title: `${PAGE_TITLE} | Invistus`,
    description: PAGE_DESCRIPTION,
    images: ["/images/opengraph/opengraph-cover.png"],
  },
  robots: {
    index: true,
    follow: true,
  },
};

export default function FixedIncomeComparisonPage() {
  const jsonLd = {
    "@context": "https://schema.org",
    "@type": "WebApplication",
    name: "Comparador de taxas de Renda Fixa",
    applicationCategory: "FinanceApplication",
    operatingSystem: "Web",
    inLanguage: "pt-BR",
    url: "https://invistus.com.br/investments/fixed-income-comparison",
    description: PAGE_DESCRIPTION,
    offers: {
      "@type": "Offer",
      price: "0",
      priceCurrency: "BRL",
    },
    publisher: {
      "@type": "Organization",
      name: "Invistus",
      url: "https://invistus.com.br",
    },
  };

  return (
    <>
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }}
      />
      <FixedIncomeComparisonPageClient />
    </>
  );
}
