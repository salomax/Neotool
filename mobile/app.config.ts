import { ExpoConfig, ConfigContext } from 'expo/config';

type AppEnv = 'development' | 'staging' | 'production';

const LOCAL_GRAPHQL_ENDPOINT = 'http://localhost:4000/graphql';

function normalizeAppEnv(raw: string | undefined): AppEnv {
  const normalized = raw?.trim().toLowerCase();

  if (normalized === 'production' || normalized === 'prod') {
    return 'production';
  }

  if (normalized === 'staging' || normalized === 'stage') {
    return 'staging';
  }

  return 'development';
}

function readEnvVariable(value: string | undefined): string | undefined {
  const trimmed = value?.trim();
  return trimmed ? trimmed : undefined;
}

function resolveGraphqlEndpoint(appEnv: AppEnv): string {
  const explicitEndpoint = readEnvVariable(process.env.EXPO_PUBLIC_GRAPHQL_ENDPOINT);
  if (explicitEndpoint) {
    return explicitEndpoint;
  }

  const envSpecificEndpoint =
    appEnv === 'production'
      ? readEnvVariable(process.env.EXPO_PUBLIC_GRAPHQL_ENDPOINT_PRODUCTION)
      : appEnv === 'staging'
        ? readEnvVariable(process.env.EXPO_PUBLIC_GRAPHQL_ENDPOINT_STAGING)
        : readEnvVariable(process.env.EXPO_PUBLIC_GRAPHQL_ENDPOINT_DEVELOPMENT);

  if (envSpecificEndpoint) {
    return envSpecificEndpoint;
  }

  if (appEnv === 'development') {
    return LOCAL_GRAPHQL_ENDPOINT;
  }

  throw new Error(
    `Missing GraphQL endpoint for ${appEnv}. Configure EXPO_PUBLIC_GRAPHQL_ENDPOINT or EXPO_PUBLIC_GRAPHQL_ENDPOINT_${appEnv.toUpperCase()}.`
  );
}

export default ({ config }: ConfigContext): ExpoConfig => ({
  ...config,
  name: 'NeoTool',
  slug: 'neotool',
  version: '1.0.0',
  orientation: 'portrait',
  icon: './assets/icon.png',
  userInterfaceStyle: 'automatic',
  scheme: 'neotool',
  splash: {
    image: './assets/splash.png',
    resizeMode: 'contain',
    backgroundColor: '#ffffff',
  },
  assetBundlePatterns: ['**/*'],
  ios: {
    supportsTablet: true,
    bundleIdentifier: 'com.neotool.app',
    infoPlist: {
      NSCameraUsageDescription: 'Allow NeoTool to access your camera to upload photos',
      NSPhotoLibraryUsageDescription: 'Allow NeoTool to access your photo library to upload images',
      NSFaceIDUsageDescription: 'Allow NeoTool to use Face ID for secure authentication',
    },
  },
  android: {
    adaptiveIcon: {
      foregroundImage: './assets/adaptive-icon.png',
      backgroundColor: '#ffffff',
    },
    package: 'com.neotool.app',
    permissions: [
      'android.permission.CAMERA',
      'android.permission.READ_EXTERNAL_STORAGE',
      'android.permission.WRITE_EXTERNAL_STORAGE',
      'android.permission.USE_BIOMETRIC',
      'android.permission.USE_FINGERPRINT',
    ],
  },
  web: {
    favicon: './assets/favicon.png',
    bundler: 'metro',
  },
  plugins: [
    'expo-router',
    'expo-secure-store',
    'expo-localization',
    [
      'expo-notifications',
      {
        icon: './assets/notification-icon.png',
        color: '#ffffff',
      },
    ],
    [
      'expo-local-authentication',
      {
        faceIDPermission: 'Allow NeoTool to use Face ID for secure authentication',
      },
    ],
  ],
  experiments: {
    typedRoutes: true,
  },
  extra: (() => {
    const appEnv = normalizeAppEnv(process.env.EXPO_PUBLIC_APP_ENV);

    return {
      router: {
        origin: false,
      },
      appEnv,
      graphqlEndpoint: resolveGraphqlEndpoint(appEnv),
      eas: {
        projectId: process.env.EXPO_PUBLIC_PROJECT_ID || '',
      },
    };
  })(),
});
