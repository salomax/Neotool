import type { Metadata } from 'next';

const baseMetadata: Metadata = {
  title: { default: 'neotool', template: '%s • neotool' },
  description: 'neotool Web App',
  applicationName: 'neotool',
  openGraph: {
    title: 'neotool',
    description: 'neotool Web App',
    url: '/',
    siteName: 'neotool',
    locale: 'pt_BR',
    type: 'website',
  },
  twitter: { card: 'summary_large_image', title: 'neotool', description: 'neotool Web App' },
  icons: {
    icon: [
      { url: '/favicon.ico' },
    ],
  },
};

export const metadata: Metadata = typeof window === 'undefined' 
  ? { ...baseMetadata, metadataBase: new URL(process.env.SITE_URL || 'http://localhost:3000') }
  : baseMetadata;
