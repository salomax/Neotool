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

## Android Setup (Android Studio)

If `expo start` shows `No Android connected device found`, complete this setup:

### 1. Install SDK components

In Android Studio:
- Open `Settings` (or `Preferences` on macOS) -> `Android SDK`
- Install `Android Emulator`
- Install one Android SDK platform (for example API 34 or newer)
- Install at least one system image (`Google APIs`, `arm64-v8a` on Apple Silicon)

### 2. Create and start an emulator (AVD)

- Open `Tools` -> `Device Manager`
- Click `Create device`
- Pick a device and a downloaded system image
- Start the emulator once using the play button

### 3. Configure environment variables (macOS, zsh)

Add this to `~/.zshrc`:

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$PATH:$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin"
```

Reload your shell:

```bash
source ~/.zshrc
```

### 4. Verify setup

```bash
emulator -list-avds
adb devices
```

`emulator -list-avds` should show your AVD and `adb devices` should show a running emulator like `emulator-5554`.

### 5. Run the app on Android

```bash
pnpm start
```

Then press `a` in the Expo terminal.

## Testing on Android Devices

For comprehensive mobile testing, use multiple emulator configurations to cover common device variations.

### Current Setup

**Pixel_3a_API_34_extension_level_7_arm64-v8a**:
- Screen: 5.6" (2220x1080, 441 ppi)
- Android 14 (API 34)
- Good for: Mid-size screen, latest Android features

This is a solid starting point but insufficient for comprehensive validation.

### Recommended Emulator Matrix

Create these emulators to cover the most common scenarios:

#### Minimum Coverage (3 emulators)

1. **Pixel 3a API 34** (5.6", 1080x2220)
   - Current setup - keep it
   - Mid-size screen, latest Android

2. **Pixel 6 or 7 API 34** (6.4", 1080x2400)
   - Large modern screen (most common size range)
   - Latest Android, pure Google experience

3. **Medium Phone API 29** (5.5-6.0", 1080x2340)
   - Android 10 (still widely used ~30% market share)
   - Tests backward compatibility

#### Full Coverage (4+ emulators)

Add these for comprehensive testing:

4. **Small Phone API 27-28** (4.7-5.0", 720x1280)
   - Small/budget devices
   - Older Android (7-8) for edge case testing

5. **Nexus 10 or Pixel Tablet API 34** (10", 2560x1600)
   - Tablet layout testing
   - Different UI patterns

### What to Test

- **Screen Sizes**: Layouts break differently on 4.5" vs 6.7" displays
- **API Levels**: Permission models, dark mode, behaviors change across versions
- **Densities**: Image assets, touch targets scale differently (mdpi, hdpi, xxhdpi)
- **Orientations**: Portrait and landscape modes

### Industry Standards

Popular devices in production (2024-2026):
- Samsung Galaxy S21/S22/S23 series (6.1-6.8", high-end)
- Samsung Galaxy A series (mid-range, large market share)
- Google Pixel 6/7/8 (developer reference devices)
- Xiaomi/Redmi (popular in Asia and emerging markets)

### Creating Additional Emulators

In Android Studio:
1. Open `Tools` -> `Device Manager`
2. Click `Create device`
3. Choose device definition (e.g., "Pixel 6")
4. Select system image (API level)
5. Configure AVD settings
6. Click `Finish`

### Cloud Testing (Optional)

For testing on real devices without physical hardware:
- **Firebase Test Lab** - Google's device farm
- **BrowserStack** - Real device cloud testing
- **AWS Device Farm** - Amazon's testing infrastructure

### Best Practices

- Run tests on at least 2-3 emulators regularly
- Test on both latest API and API 29-30 (Android 10-11)
- Cover small (5"), medium (6"), and large (6.5"+) screens
- Consider testing on 1-2 physical devices if available
- Use cloud testing for release validation

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
