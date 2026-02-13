import type { Metadata } from 'next';
import { getDictionary } from '@/shared/i18n/server';

// Default to Portuguese since we don't have locale routing yet
const t = getDictionary('pt').seo.default;

const baseMetadata: Metadata = {
  title: { default: t.title, template: t.titleTemplate },
  description: t.description,
  applicationName: 'Invistus',
  keywords: t.keywords.split(', '),
  openGraph: {
    title: t.ogTitle,
    description: t.ogDescription,
    url: '/',
    siteName: 'Invistus',
    locale: 'pt_BR',
    type: 'website',
    images: [
      {
        url: '/images/opengraph/opengraph-cover.png',
        width: 1200,
        height: 630,
        alt: 'Invistus',
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

export const metadata: Metadata = typeof window === 'undefined' 
  ? { ...baseMetadata, metadataBase: new URL(process.env.SITE_URL || 'http://localhost:3000') }
  : baseMetadata;
