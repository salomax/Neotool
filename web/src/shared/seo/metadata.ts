import type { Metadata } from 'next';
import { getDictionary } from '@/shared/i18n/server';

// Default to Portuguese since we don't have locale routing yet
const t = getDictionary('pt').seo.default;

const siteUrl =
  process.env.SITE_URL ||
  (process.env.NODE_ENV === 'production'
    ? 'https://neotool.com.br'
    : 'http://localhost:3000');

const baseMetadata: Metadata = {
  title: { default: t.title, template: t.titleTemplate },
  description: t.description,
  applicationName: 'neotool',
  keywords: t.keywords.split(', '),
  metadataBase: new URL(siteUrl),
  openGraph: {
    title: t.ogTitle,
    description: t.ogDescription,
    url: '/',
    siteName: 'neotool',
    locale: 'pt_BR',
    type: 'website',
    images: [
      {
        url: '/images/opengraph/opengraph-cover.png',
        width: 1200,
        height: 630,
        alt: 'neotool',
      },
    ],
  },
  twitter: { 
    card: 'summary_large_image', 
    title: t.ogTitle, 
    description: t.twitterDescription,
    images: ['/images/opengraph/opengraph-cover.png'],
  },
  icons: {
    icon: [
      { url: '/favicon.ico' },
      { url: '/favicon-16x16.png', sizes: '16x16', type: 'image/png' },
      { url: '/favicon-32x32.png', sizes: '32x32', type: 'image/png' },
    ],
    apple: [
      { url: '/apple-touch-icon.png' },
    ],
  },
};

export const metadata: Metadata = baseMetadata;
