import type { MetadataRoute } from 'next';
import { getApolloClient } from '@/lib/graphql/client';
import { createSemanticPath } from '@/shared/utils/url';
import { gql } from '@apollo/client';
import { unstable_cache } from 'next/cache';

const SITE_URL = process.env.SITE_URL || 'https://invistus.com.br';

/** Max page size allowed by the bacenInstitutionSlugs API (relay pagination). */
const SITEMAP_PAGE_SIZE = 100;

export const revalidate = 604800;

const SITEMAP_INSTITUTION_SLUGS_QUERY = gql`
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

const fetchAllInstitutions = unstable_cache(
  async (): Promise<SitemapInstitution[]> => {
    const client = getApolloClient();
    const allInstitutions: SitemapInstitution[] = [];

    try {
      const firstPage = await client.query<
        SitemapInstitutionSlugsQuery,
        SitemapInstitutionSlugsQueryVariables
      >({
        query: SITEMAP_INSTITUTION_SLUGS_QUERY,
        variables: { first: SITEMAP_PAGE_SIZE },
      });

      let edges = firstPage.data?.bacenInstitutionSlugs?.edges ?? [];
      for (const edge of edges) {
        if (edge.node) {
          allInstitutions.push({
            id: edge.node.id,
            name: edge.node.name,
            updatedAt: edge.node.updatedAt ?? undefined,
          });
        }
      }

      let hasNextPage = firstPage.data?.bacenInstitutionSlugs?.pageInfo?.hasNextPage ?? false;
      let cursor = firstPage.data?.bacenInstitutionSlugs?.pageInfo?.endCursor ?? null;

      while (hasNextPage && cursor) {
        const nextPage = await client.query<
          SitemapInstitutionSlugsQuery,
          SitemapInstitutionSlugsQueryVariables
        >({
          query: SITEMAP_INSTITUTION_SLUGS_QUERY,
          variables: { first: SITEMAP_PAGE_SIZE, after: cursor },
        });

        edges = nextPage.data?.bacenInstitutionSlugs?.edges ?? [];
        for (const edge of edges) {
          if (edge.node) {
            allInstitutions.push({
              id: edge.node.id,
              name: edge.node.name,
              updatedAt: edge.node.updatedAt ?? undefined,
            });
          }
        }

        hasNextPage = nextPage.data?.bacenInstitutionSlugs?.pageInfo?.hasNextPage ?? false;
        cursor = nextPage.data?.bacenInstitutionSlugs?.pageInfo?.endCursor ?? null;
      }

      return allInstitutions;
    } catch (error) {
      console.error('Sitemap: failed to fetch institutions', error);
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
  ];

  try {
    const institutions = await fetchAllInstitutions();

    const institutionPages: MetadataRoute.Sitemap = institutions.map((inst) => ({
      url: `${SITE_URL}/financial-data/bacen-institutions/${createSemanticPath(inst.name, inst.id)}`,
      lastModified: inst.updatedAt ? new Date(inst.updatedAt) : now,
      changeFrequency: 'weekly' as const,
      priority: 0.7,
    }));

    return [...staticPages, ...institutionPages];
  } catch (error) {
    console.error('Sitemap: error building sitemap', error);
    return staticPages;
  }
}
