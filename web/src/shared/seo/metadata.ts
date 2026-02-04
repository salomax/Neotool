import type { Metadata } from 'next';

const baseMetadata: Metadata = {
  title: { default: 'Invistus', template: '%s • invistus' },
  description: 'Invistus Web App',
  applicationName: 'Invistus',
  openGraph: {
    title: 'Invistus',
    description: 'Invistus Web App',
    url: '/',
    siteName: 'Invistus',
    locale: 'pt_BR',
    type: 'website',
  },
  twitter: { card: 'summary_large_image', title: 'invistus', description: 'invistus Web App' },
  icons: {
    icon: [
      { url: '/favicon.ico' },
    ],
  },
};

export const metadata: Metadata = typeof window === 'undefined' 
  ? { ...baseMetadata, metadataBase: new URL(process.env.SITE_URL || 'http://localhost:3000') }
  : baseMetadata;
