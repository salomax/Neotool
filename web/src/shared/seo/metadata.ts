import type { Metadata } from 'next';

const baseMetadata: Metadata = {
  title: { default: 'Neotool', template: '%s • Neotool' },
  description: 'Neotool Web App',
  applicationName: 'Neotool',
  openGraph: {
    title: 'Neotool',
    description: 'Neotool Web App',
    url: '/',
    siteName: 'Neotool',
    locale: 'pt_BR',
    type: 'website',
  },
  twitter: { card: 'summary_large_image', title: 'Neotool', description: 'Neotool Web App' },
  icons: {
    icon: [
      { url: '/favicon.ico' },
    ],
  },
};

export const metadata: Metadata = typeof window === 'undefined' 
  ? { ...baseMetadata, metadataBase: new URL(process.env.SITE_URL || 'http://localhost:3000') }
  : baseMetadata;
