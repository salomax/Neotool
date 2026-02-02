import path from 'path'

/** @type {import('next').NextConfig} */
const nextConfig = {
  // Enable standalone output for Docker optimization (production only)
  ...(process.env.NODE_ENV === 'production' && { output: 'standalone' }),
  // Ensure proper workspace root detection
  outputFileTracingRoot: process.cwd(),
  // Disable strict mode for development to avoid double rendering issues
  reactStrictMode: false,
  // Temporarily disable TypeScript checking during build
  typescript: {
    ignoreBuildErrors: true,
  },
  // Optimize bundle
  compiler: {
    removeConsole: process.env.NODE_ENV === 'production',
  },
  // Handle images
  images: {
    remotePatterns: [
      {
        protocol: 'http',
        hostname: 'localhost',
      },
    ],
  },
  // Configure source maps
  productionBrowserSourceMaps: false,
  // Source map handling is now done in middleware.ts
  // Add headers to handle source map requests gracefully
  async headers() {
    return [
      {
        source: '/(.*)',
        headers: [
          {
            key: 'X-Content-Type-Options',
            value: 'nosniff',
          },
          {
            key: 'X-Frame-Options',
            value: 'DENY',
          },
          {
            key: 'X-XSS-Protection',
            value: '1; mode=block',
          },
        ],
      },
    ]
  },
  // Turbopack configuration (Next.js 16 default)
  turbopack: {},
  // Webpack configuration (used when --webpack flag is passed)
  webpack: (config, { dev, isServer }) => {
    // Optimize for development
    if (dev) {
      config.watchOptions = {
        poll: 1000,
        aggregateTimeout: 300,
        // Ignore test files to reduce memory usage
        ignored: [
          '**/node_modules/**',
          '**/__tests__/**',
          '**/*.test.ts',
          '**/*.test.tsx',
          '**/*.spec.ts',
          '**/*.spec.tsx',
        ],
      }

      // Disable source maps in development to prevent 404 errors from browser extensions
      config.devtool = false

      // Use filesystem cache to reduce memory usage
      config.cache = {
        type: 'filesystem',
        maxMemoryGenerations: 1,
        cacheDirectory: path.resolve(process.cwd(), '.next', 'cache', 'webpack'),
      }


      // Add fallback for missing source maps
      config.resolve.fallback = {
        ...config.resolve.fallback,
        fs: false,
        path: false,
        os: false,
      }
    }
    
    // Suppress webpack cache warnings
    config.infrastructureLogging = {
      level: 'error',
    };
    
    // Let Next.js handle bundle splitting by default
    // Only customize if needed for specific performance requirements
    
    return config
  },
}

export default nextConfig
