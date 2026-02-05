# NeoTool Mobile

React Native + Expo mobile application for iOS and Android.

## Tech Stack

- **React Native** + **Expo** - Cross-platform mobile development
- **TypeScript** - Type-safe development
- **Expo Router** - File-based routing
- **Apollo Client** - GraphQL client with offline support
- **React Native Paper** - Material Design UI components
- **i18next** - Internationalization
- **Zustand** - State management
- **React Hook Form** + **Zod** - Form handling and validation

## Prerequisites

- Node.js >= 20
- pnpm >= 10
- Expo CLI (`npm install -g expo-cli`)
- iOS Simulator (macOS only) or Android Emulator
- Expo Go app on your physical device (optional)

## Getting Started

### 1. Install Dependencies

```bash
pnpm install
```

### 2. Environment Setup

Copy the environment example file:

```bash
cp .env.example .env
```

Edit `.env` and configure your GraphQL endpoint:

```env
EXPO_PUBLIC_GRAPHQL_ENDPOINT=http://localhost:4000/graphql
```

### 3. Start Development Server

```bash
pnpm start
```

This will start the Expo development server. You can then:
- Press `i` to open iOS Simulator
- Press `a` to open Android Emulator
- Scan QR code with Expo Go app on your device

### 4. Run on Specific Platform

```bash
# iOS
pnpm ios

# Android
pnpm android

# Web (for testing)
pnpm web
```

## Project Structure

```
mobile/
├── app/                    # Expo Router screens
│   ├── (auth)/            # Auth screens (login, signup)
│   ├── (tabs)/            # Tab navigation (dashboard, profile, settings)
│   ├── _layout.tsx        # Root layout with providers
│   └── index.tsx          # Entry screen
├── src/
│   ├── components/        # React components
│   ├── hooks/            # Custom hooks
│   ├── providers/        # Context providers
│   ├── lib/              # Apollo Client setup
│   ├── config/           # Configuration (i18n)
│   ├── theme/            # Theme and colors
│   ├── locales/          # Translation files
│   └── generated/        # GraphQL generated types
├── assets/               # Images, fonts, etc.
└── app.json             # Expo configuration
```

## Available Scripts

### Development

- `pnpm start` - Start development server
- `pnpm ios` - Run on iOS simulator
- `pnpm android` - Run on Android emulator
- `pnpm web` - Run in web browser

### Code Quality

- `pnpm lint` - Run ESLint
- `pnpm lint:fix` - Fix ESLint errors
- `pnpm typecheck` - Run TypeScript type checking
- `pnpm format` - Format code with Prettier

### Testing

- `pnpm test` - Run tests
- `pnpm test:watch` - Run tests in watch mode
- `pnpm test:coverage` - Run tests with coverage

### GraphQL

- `pnpm codegen` - Generate TypeScript types from GraphQL schema
- `pnpm codegen:watch` - Generate types in watch mode

## Features

### Implemented

- ✅ Expo Router file-based navigation
- ✅ Authentication flow (login, signup, logout)
- ✅ Apollo Client with cache persistence
- ✅ Offline support with mutation queue
- ✅ i18n (English and Portuguese)
- ✅ Dark mode support
- ✅ Type-safe forms with React Hook Form + Zod
- ✅ Material Design UI with React Native Paper

### TODO

- [ ] GraphQL mutations for auth
- [ ] User profile management
- [ ] Push notifications
- [ ] Biometric authentication
- [ ] Deep linking
- [ ] E2E tests

## Code Sharing with Web

The mobile app shares code with the web application through:

- **GraphQL Queries**: Shared GraphQL queries and types
- **Business Logic**: Utility functions and validators
- **i18n**: Translation files
- **Types**: TypeScript type definitions

See [Mobile Architecture](../docs/02-architecture/mobile-architecture.md) for details.

## Building for Production

### iOS

```bash
# Build for App Store
eas build --platform ios --profile production

# Submit to App Store
eas submit --platform ios
```

### Android

```bash
# Build for Play Store
eas build --platform android --profile production

# Submit to Play Store
eas submit --platform android
```

## Documentation

- [Mobile Architecture](../docs/02-architecture/mobile-architecture.md)
- [ADR-0010: React Native + Expo](../docs/92-adr/0010-react-native-expo-mobile.md)
- [Expo Documentation](https://docs.expo.dev/)
- [React Native Paper](https://callstack.github.io/react-native-paper/)

## Troubleshooting

### Metro bundler issues

```bash
# Clear Metro cache
npx expo start --clear
```

### iOS build issues

```bash
# Clean iOS build
cd ios && pod deintegrate && pod install && cd ..
```

### Android build issues

```bash
# Clean Android build
cd android && ./gradlew clean && cd ..
```

## License

MIT
