import type { MetadataRoute } from 'next';
import { createSemanticPath } from '@/shared/utils/url';
import { unstable_cache } from 'next/cache';

const SITE_URL = process.env.SITE_URL || 'https://invistus.com.br';

/** Max page size allowed by the bacenInstitutionSlugs API (relay pagination). */
const SITEMAP_PAGE_SIZE = 100;

export const dynamic = 'force-dynamic';

const SITEMAP_INSTITUTION_SLUGS_QUERY = `
  query SitemapInstitutionSlugs($first: Int!, $after: String, $anoMes: String) {
    bacenInstitutionSlugs(first: $first, after: $after, anoMes: $anoMes) {
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

type SitemapInstitutionSlugsQuery = {
  bacenInstitutionSlugs: {
    edges: Array<{
      node: {
        id: string;
        name: string;
        updatedAt: string | null;
      };
    }>;
    pageInfo: {
      hasNextPage: boolean;
      endCursor: string | null;
    };
  };
};

type SitemapInstitutionSlugsQueryVariables = {
  first: number;
  after?: string;
  anoMes?: string;
};

type SitemapInstitution = {
  id: string;
  name: string;
  updatedAt?: string | null;
};

/**
 * Server-only GraphQL endpoint for sitemap. No localhost fallback in production.
 */
function getSitemapGraphQLEndpoint(): string {
  const endpoint = process.env.GRAPHQL_ENDPOINT?.trim() || '';
  if (process.env.NODE_ENV === 'production') {
    if (!endpoint) {
      throw new Error('GRAPHQL_ENDPOINT is required in production for sitemap');
    }
    return endpoint;
  }
  return endpoint || process.env.NEXT_PUBLIC_GRAPHQL_URL || 'http://localhost:4000/graphql';
}

async function sitemapGraphQLFetch<TData, TVars extends Record<string, unknown>>(
  query: string,
  variables: TVars
): Promise<TData> {
  const endpoint = getSitemapGraphQLEndpoint();
  const res = await fetch(endpoint, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ query, variables }),
  });
  if (!res.ok) {
    throw new Error(`Sitemap GraphQL request failed: ${res.status} ${res.statusText}`);
  }
  const json = await res.json();
  if (json.errors?.length) {
    throw new Error(
      `Sitemap GraphQL errors: ${json.errors.map((e: { message: string }) => e.message).join(', ')}`
    );
  }
  return json.data as TData;
}

const fetchAllInstitutions = unstable_cache(
  async (): Promise<SitemapInstitution[]> => {
    const allInstitutions: SitemapInstitution[] = [];

    try {
      const firstPage = await sitemapGraphQLFetch<
        SitemapInstitutionSlugsQuery,
        SitemapInstitutionSlugsQueryVariables
      >(SITEMAP_INSTITUTION_SLUGS_QUERY, { first: SITEMAP_PAGE_SIZE });

      let edges = firstPage.bacenInstitutionSlugs?.edges ?? [];
      for (const edge of edges) {
        if (edge.node) {
          allInstitutions.push({
            id: edge.node.id,
            name: edge.node.name,
            updatedAt: edge.node.updatedAt ?? undefined,
          });
        }
      }

      let hasNextPage = firstPage.bacenInstitutionSlugs?.pageInfo?.hasNextPage ?? false;
      let cursor = firstPage.bacenInstitutionSlugs?.pageInfo?.endCursor ?? null;

      while (hasNextPage && cursor) {
        const nextPage = await sitemapGraphQLFetch<
          SitemapInstitutionSlugsQuery,
          SitemapInstitutionSlugsQueryVariables
        >(SITEMAP_INSTITUTION_SLUGS_QUERY, {
          first: SITEMAP_PAGE_SIZE,
          after: cursor,
        });

        edges = nextPage.bacenInstitutionSlugs?.edges ?? [];
        for (const edge of edges) {
          if (edge.node) {
            allInstitutions.push({
              id: edge.node.id,
              name: edge.node.name,
              updatedAt: edge.node.updatedAt ?? undefined,
            });
          }
        }

        hasNextPage = nextPage.bacenInstitutionSlugs?.pageInfo?.hasNextPage ?? false;
        cursor = nextPage.bacenInstitutionSlugs?.pageInfo?.endCursor ?? null;
      }

      return allInstitutions;
    } catch (error) {
      if (process.env.NODE_ENV === 'production') {
        throw error;
      }
      console.error('Sitemap: failed to fetch institutions (non-production fallback)', error);
      return [];
    }
  },
  ['sitemap-institutions-list'],
  { revalidate: 60 * 60 * 24 }
);

export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  const now = new Date();

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
    {
      url: `${SITE_URL}/investments`,
      lastModified: now,
      changeFrequency: 'weekly',
      priority: 0.8,
    },
    {
      url: `${SITE_URL}/investments/fixed-income-comparison`,
      lastModified: now,
      changeFrequency: 'weekly',
      priority: 0.8,
    },
  ];

  const institutions = await fetchAllInstitutions();

  const institutionPages: MetadataRoute.Sitemap = institutions.map((inst) => ({
    url: `${SITE_URL}/financial-data/bacen-institutions/${createSemanticPath(inst.name, inst.id)}`,
    lastModified: inst.updatedAt ? new Date(inst.updatedAt) : now,
    changeFrequency: 'monthly' as const,
    priority: 0.7,
  }));

  return [...staticPages, ...institutionPages];
}
