import type { MetadataRoute } from 'next';
import { getApolloClient } from '@/lib/graphql/client';
import { createSemanticPath } from '@/shared/utils/url';
import {
  BacenInstitutionsDocument,
  type BacenInstitutionsQuery,
  type BacenInstitutionsQueryVariables,
} from '@/features/financial-data/search-institutions/api/index.generated';

const SITE_URL = process.env.SITE_URL || 'https://invistus.com.br';

/**
 * Revalidate sitemap every 1 week (604800 seconds).
 * A Kubernetes CronJob can request /sitemap.xml periodically to keep cache warm.
 */
export const revalidate = 604800;

/**
 * Fetches all institutions for sitemap generation.
 * Uses pagination to handle large datasets.
 */
async function fetchAllInstitutionsForSitemap(): Promise<Array<{ id: string; name: string }>> {
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

/**
 * Generates a dynamic sitemap with all static and dynamic routes.
 * This is the native Next.js approach (recommended over next-sitemap for App Router).
 *
 * @see https://nextjs.org/docs/app/api-reference/file-conventions/metadata/sitemap
 */
export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  const now = new Date();

  // Static pages
  const staticPages: MetadataRoute.Sitemap = [
    {
      url: SITE_URL,
      lastModified: now,
      changeFrequency: 'weekly',
      priority: 1.0,
    },
    {
      url: `${SITE_URL}/financial-data`,
      lastModified: now,
      changeFrequency: 'weekly',
      priority: 0.9,
    },
    {
      url: `${SITE_URL}/financial-data/bacen-institutions`,
      lastModified: now,
      changeFrequency: 'daily',
      priority: 0.9,
    },
    {
      url: `${SITE_URL}/financial-data/national-indicators`,
      lastModified: now,
      changeFrequency: 'weekly',
      priority: 0.8,
    },
  ];

  // Dynamic institution pages
  try {
    const institutions = await fetchAllInstitutionsForSitemap();

    const institutionPages: MetadataRoute.Sitemap = institutions.map((inst) => ({
      url: `${SITE_URL}/financial-data/bacen-institutions/${createSemanticPath(inst.name, inst.id)}`,
      lastModified: now,
      changeFrequency: 'weekly' as const,
      priority: 0.7,
    }));

    return [...staticPages, ...institutionPages];
  } catch (error) {
    console.error('[Sitemap] Error fetching institutions:', error);
    // Return only static pages if dynamic fetch fails
    return staticPages;
  }
}
