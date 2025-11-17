import type { Metadata } from 'next';

const baseMetadata: Metadata = {
  title: { default: 'ProcureFlow', template: '%s • ProcureFlow' },
  description: 'ProcureFlow Web App',
  applicationName: 'ProcureFlow',
  openGraph: {
    title: 'ProcureFlow',
    description: 'ProcureFlow Web App',
    url: '/',
    siteName: 'ProcureFlow',
    locale: 'pt_BR',
    type: 'website',
  },
  twitter: { card: 'summary_large_image', title: 'ProcureFlow', description: 'ProcureFlow Web App' },
  icons: {
    icon: [
      { url: '/favicon.ico' },
    ],
  },
};

export const metadata: Metadata = typeof window === 'undefined' 
  ? { ...baseMetadata, metadataBase: new URL(process.env.SITE_URL || 'http://localhost:3000') }
  : baseMetadata;
