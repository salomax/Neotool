import type { Metadata } from 'next';
import BacenInstitutionsClient from './BacenInstitutionsClient';

const SITE_URL =
  process.env.SITE_URL ||
  (process.env.NODE_ENV === 'production' ? 'https://invistus.com.br' : 'http://localhost:3000');
const PAGE_PATH = '/financial-data/bacen-institutions';
const PAGE_URL = `${SITE_URL}${PAGE_PATH}`;

const POPULAR_INSTITUTIONS = [
  'Nubank',
  'Caixa',
  'Banco do Brasil',
  'Itaú',
  'Bradesco',
  'Santander',
  'Inter',
  'PicPay',
  'Mercado Pago',
  'PagBank',
  'BTG Pactual',
  'C6 Bank',
  'XP',
  'Safra',
  'Daycoval',
  'Banco Pan',
  'ABC Brasil',
  'BV',
  'BMG',
  'Sofisa Direto',
] as const;

const METRICS = ['Índice de Basileia', 'Índice de Mobilização', 'Lucro Líquido'] as const;

const METRIC_QUERY_TERMS = [
  'índice de basileia',
  'indice de basileia',
  'índice basiléia',
  'indice basiléia',
  'índice de mobilização',
  'indice de mobilização',
  'índice de imobilização',
  'indice de imobilização',
  'lucro líquido',
  'lucro liquido',
] as const;

const BANK_METRIC_QUERIES = POPULAR_INSTITUTIONS.flatMap((institution) =>
  METRIC_QUERY_TERMS.map((metric) => `${metric} do ${institution}`)
);

const RANKING_QUERIES = METRIC_QUERY_TERMS.map((metric) => `ranking do ${metric}`);

const KEYWORDS = Array.from(
  new Set([
    'instituições financeiras',
    'instituições financeiras bacen',
    'busca bacen',
    'dados bacen',
    'rankings financeiros',
    'instituições mais populares',
    'itau',
    ...METRICS,
    ...POPULAR_INSTITUTIONS,
    ...BANK_METRIC_QUERIES,
    ...RANKING_QUERIES,
  ])
);

export const metadata: Metadata = {
  title: 'Instituições Financeiras BACEN: Rankings, Basileia, Mobilização e Lucro Líquido | Invistus',
  description:
    'Busque instituições financeiras do BACEN e acompanhe rankings por Índice de Basileia, Índice de Mobilização e Lucro Líquido. Veja também a lista de instituições mais populares no Invistus.',
  keywords: KEYWORDS,
  alternates: {
    canonical: PAGE_PATH,
  },
  openGraph: {
    title: 'Instituições Financeiras BACEN com Rankings e Métricas | Invistus',
    description:
      'Consulte instituições como Nubank, Caixa, Banco do Brasil, Itaú, Bradesco e Santander, com rankings por Basileia, Mobilização e Lucro Líquido.',
    type: 'website',
    locale: 'pt_BR',
    url: PAGE_PATH,
  },
  twitter: {
    card: 'summary_large_image',
    title: 'Instituições Financeiras BACEN com Rankings e Métricas',
    description:
      'Compare instituições do BACEN com métricas como Basileia, Mobilização e Lucro Líquido.',
  },
};

export default function BacenInstitutionsPage() {
  const jsonLd = {
    '@context': 'https://schema.org',
    '@type': 'CollectionPage',
    name: 'Busca de Instituições Financeiras do BACEN',
    description:
      'Página de busca de instituições financeiras com métricas e rankings por Índice de Basileia, Índice de Mobilização e Lucro Líquido.',
    url: PAGE_URL,
    keywords: KEYWORDS,
    mainEntity: [
      {
        '@type': 'ItemList',
        name: 'Instituições mais populares',
        numberOfItems: POPULAR_INSTITUTIONS.length,
        itemListElement: POPULAR_INSTITUTIONS.map((institution, index) => ({
          '@type': 'ListItem',
          position: index + 1,
          name: institution,
        })),
      },
      {
        '@type': 'ItemList',
        name: 'Métricas e rankings disponíveis',
        numberOfItems: METRICS.length,
        itemListElement: METRICS.map((metric, index) => ({
          '@type': 'ListItem',
          position: index + 1,
          name: metric,
        })),
      },
    ],
  };

  return (
    <>
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }}
      />
      <BacenInstitutionsClient />
    </>
  );
}
