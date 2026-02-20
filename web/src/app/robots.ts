import type { MetadataRoute } from 'next';

const SITE_URL = process.env.SITE_URL || 'https://neotool.com.br';

/**
 * Generates robots.txt configuration for search engine crawlers.
 *
 * @see https://nextjs.org/docs/app/api-reference/file-conventions/metadata/robots
 */
export default function robots(): MetadataRoute.Robots {
  return {
    rules: [
      {
        userAgent: '*',
        allow: '/',
        disallow: [
          '/api/',
          '/settings/',
          '/og',
          '/signin',
          '/signup',
          '/reset-password',
          '/forgot-password',
          '/verify-email',
          '/verify-email-link',
        ],
      },
    ],
    sitemap: `${SITE_URL}/sitemap.xml`,
  };
}
