import type { Metadata } from 'next';
import { getApolloClient } from '@/lib/graphql/client';
import { extractIdFromSemanticPath, createSemanticPath } from '@/shared/utils/url';
import { safeDecodeId } from '@/shared/utils/idObfuscation';
import {
  BacenInstitutionDocument,
  type BacenInstitutionQuery,
  type BacenInstitutionQueryVariables,
} from '@/features/financial-data/view-institution-detail/api/index.generated';
import {
  BacenInstitutionsDocument,
  type BacenInstitutionsQuery,
  type BacenInstitutionsQueryVariables,
} from '@/features/financial-data/search-institutions/api/index.generated';
import BacenInstitutionDetailClient from './client';

export const revalidate = 3600;

type Props = {
  params: Promise<{ 'bacen-cod-inst': string }>;
};

/**
 * Generates dynamic metadata for SEO based on the institution data.
 * This runs on the server and ensures search engines see proper meta tags.
 */
export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { 'bacen-cod-inst': rawParam } = await params;
  const encodedId = extractIdFromSemanticPath(rawParam);
  const institutionId = safeDecodeId(encodedId) ?? encodedId;

  try {
    const client = getApolloClient();
    const { data } = await client.query<BacenInstitutionQuery, BacenInstitutionQueryVariables>({
      query: BacenInstitutionDocument,
      variables: { id: institutionId },
    });

    const institution = data?.bacenFinanceInstitution;

    if (!institution) {
      return {
        title: 'Instituição não encontrada',
        description: 'A instituição financeira solicitada não foi encontrada.',
      };
    }

    const institutionName = institution.name;
    const institutionType = institution.institutionType || 'Instituição Financeira';
    const description = `Dados financeiros e balanços de ${institutionName}. Análise de indicadores BACEN, índice de Basileia, patrimônio líquido, lucro e informações regulatórias.`;

    return {
      title: institutionName,
      description,
      openGraph: {
        title: `${institutionName} - Dados Financeiros BACEN`,
        description,
        type: 'article',
        url: `/financial-data/bacen-institutions/${rawParam}`,
        siteName: 'Invistus',
        locale: 'pt_BR',
        images: institution.logoAsset?.publicUrl
          ? [{ url: institution.logoAsset.publicUrl, alt: institutionName }]
          : undefined,
      },
      twitter: {
        card: 'summary_large_image',
        title: `${institutionName} - Dados Financeiros`,
        description,
        images: institution.logoAsset?.publicUrl ? [institution.logoAsset.publicUrl] : undefined,
      },
      alternates: {
        canonical: `/financial-data/bacen-institutions/${rawParam}`,
      },
      keywords: [
        institutionName,
        'BACEN',
        'dados financeiros',
        'balanço',
        'instituição financeira',
        institutionType,
        'índice de Basileia',
        'patrimônio líquido',
      ],
    };
  } catch (error) {
    console.error('[SEO] Error fetching institution for metadata:', error);
    return {
      title: 'Instituição Financeira',
      description: 'Dados financeiros de instituições reguladas pelo Banco Central do Brasil.',
    };
  }
}

/**
 * Pre-generates static paths for all institutions at build time.
 * This enables ISR (Incremental Static Regeneration) for better performance.
 */
async function fetchAllInstitutions(): Promise<Array<{ id: string; name: string }>> {
  const client = getApolloClient();
  const allInstitutions: Array<{ id: string; name: string }> = [];

  // Fetch first page
  const firstPage = await client.query<BacenInstitutionsQuery, BacenInstitutionsQueryVariables>({
    query: BacenInstitutionsDocument,
    variables: { first: 100 },
  });

  let edges = firstPage.data?.bacenInstitutions?.edges || [];
  for (const edge of edges) {
    if (edge.node) {
      allInstitutions.push({ id: edge.node.id, name: edge.node.name });
    }
  }

  let hasNextPage = firstPage.data?.bacenInstitutions?.pageInfo?.hasNextPage ?? false;
  let cursor = firstPage.data?.bacenInstitutions?.pageInfo?.endCursor ?? null;

  // Fetch remaining pages
  while (hasNextPage && cursor) {
    const nextPage = await client.query<BacenInstitutionsQuery, BacenInstitutionsQueryVariables>({
      query: BacenInstitutionsDocument,
      variables: { first: 100, after: cursor },
    });

    edges = nextPage.data?.bacenInstitutions?.edges || [];
    for (const edge of edges) {
      if (edge.node) {
        allInstitutions.push({ id: edge.node.id, name: edge.node.name });
      }
    }

    hasNextPage = nextPage.data?.bacenInstitutions?.pageInfo?.hasNextPage ?? false;
    cursor = nextPage.data?.bacenInstitutions?.pageInfo?.endCursor ?? null;
  }

  return allInstitutions;
}

export async function generateStaticParams(): Promise<Array<{ 'bacen-cod-inst': string }>> {
  try {
    const allInstitutions = await fetchAllInstitutions();

    return allInstitutions.map((inst) => ({
      'bacen-cod-inst': createSemanticPath(inst.name, inst.id),
    }));
  } catch (error) {
    console.error('[SSG] Error generating static params for institutions:', error);
    // Return empty array - pages will be generated on-demand
    return [];
  }
}

/**
 * Server Component page that wraps the client component.
 * This pattern allows us to use generateMetadata for SEO while
 * keeping the interactive UI in a client component.
 */
export default function BacenInstitutionDetailPage() {
  return <BacenInstitutionDetailClient />;
}
