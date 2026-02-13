import type { MetadataRoute } from 'next';
import { getApolloClient } from '@/lib/graphql/client';
import { createSemanticPath } from '@/shared/utils/url';
import { gql } from '@apollo/client';
import { unstable_cache } from 'next/cache';

const SITE_URL = process.env.SITE_URL || 'https://invistus.com.br';
const SITEMAP_CHUNK_SIZE = 10000; // Split sitemap every 10k URLs (Google limit is 50k)

/**
 * Revalidate sitemap every 1 week (604800 seconds).
 * A Kubernetes CronJob can request /sitemap.xml periodically to keep cache warm.
 */
export const revalidate = 604800;

// Lightweight query for sitemap generation
const SITEMAP_INSTITUTIONS_QUERY = gql`
  query SitemapInstitutions($first: Int!, $after: String) {
    bacenInstitutions(first: $first, after: $after) {
      totalCount
      edges {
        node {
          id
          name
          updatedAt
        }
      }
      pageInfo {
        hasNextPage
        endCursor
      }
    }
  }
`;

type SitemapInstitution = {
  id: string;
  name: string;
  updatedAt?: string;
};

/**
 * Fetches all institutions for sitemap generation.
 * Uses pagination to handle large datasets.
 * Cached via unstable_cache to avoid redundant DB hits during splitting.
 */
const fetchAllInstitutions = unstable_cache(
  async (): Promise<SitemapInstitution[]> => {
    console.log('[Sitemap] Fetching all institutions...');
    const client = getApolloClient();
    const allInstitutions: SitemapInstitution[] = [];
    
    try {
      // Fetch first page
      const firstPage = await client.query({
        query: SITEMAP_INSTITUTIONS_QUERY,
        variables: { first: 1000 }, // Increased page size for performance
      });

      let edges = firstPage.data?.bacenInstitutions?.edges || [];
      for (const edge of edges) {
        if (edge.node) {
          allInstitutions.push({ 
            id: edge.node.id, 
            name: edge.node.name,
            updatedAt: edge.node.updatedAt 
          });
        }
      }

      let hasNextPage = firstPage.data?.bacenInstitutions?.pageInfo?.hasNextPage ?? false;
      let cursor = firstPage.data?.bacenInstitutions?.pageInfo?.endCursor ?? null;

      // Fetch remaining pages
      while (hasNextPage && cursor) {
        const nextPage = await client.query({
          query: SITEMAP_INSTITUTIONS_QUERY,
          variables: { first: 1000, after: cursor },
        });

        edges = nextPage.data?.bacenInstitutions?.edges || [];
        for (const edge of edges) {
          if (edge.node) {
            allInstitutions.push({ 
              id: edge.node.id, 
              name: edge.node.name,
              updatedAt: edge.node.updatedAt 
            });
          }
        }

        hasNextPage = nextPage.data?.bacenInstitutions?.pageInfo?.hasNextPage ?? false;
        cursor = nextPage.data?.bacenInstitutions?.pageInfo?.endCursor ?? null;
      }

      console.log(`[Sitemap] Fetched ${allInstitutions.length} institutions.`);
      return allInstitutions;
    } catch (error) {
      console.error('[Sitemap] Error fetching institutions:', error);
      return [];
    }
  },
  ['sitemap-institutions-list'],
  { revalidate: 60 * 60 * 24 } // Cache for 24h
);

export async function generateSitemaps() {
  const institutions = await fetchAllInstitutions();
  const totalCount = institutions.length;
  const numberOfSitemaps = Math.ceil(totalCount / SITEMAP_CHUNK_SIZE) || 1;

  // Generate IDs for each sitemap chunk (0, 1, 2...)
  return Array.from({ length: numberOfSitemaps }, (_, i) => ({ id: i }));
}

/**
 * Generates a dynamic sitemap with all static and dynamic routes.
 * This is the native Next.js approach (recommended over next-sitemap for App Router).
 *
 * @see https://nextjs.org/docs/app/api-reference/file-conventions/metadata/sitemap
 */
export default async function sitemap({ id }: { id: number }): Promise<MetadataRoute.Sitemap> {
  const now = new Date();
  
  // Only include static pages in the first sitemap chunk
  const staticPages: MetadataRoute.Sitemap = id === 0 ? [
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
  ] : [];

  // Dynamic institution pages
  try {
    const institutions = await fetchAllInstitutions();
    
    // Slice the institutions array based on the current sitemap ID
    const start = id * SITEMAP_CHUNK_SIZE;
    const end = start + SITEMAP_CHUNK_SIZE;
    const chunk = institutions.slice(start, end);

    const institutionPages: MetadataRoute.Sitemap = chunk.map((inst) => ({
      url: `${SITE_URL}/financial-data/bacen-institutions/${createSemanticPath(inst.name, inst.id)}`,
      lastModified: inst.updatedAt ? new Date(inst.updatedAt) : now,
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

