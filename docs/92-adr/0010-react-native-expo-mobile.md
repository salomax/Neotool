---
title: ADR-0010 React Native Expo Mobile
type: adr
category: mobile
status: accepted
version: 1.0.0
tags: [react-native, expo, mobile, typescript, graphql, ios, android]
related:
  - docs/02-architecture/mobile-architecture.md
  - docs/92-adr/0004-typescript-nextjs-frontend.md
  - docs/02-architecture/frontend-architecture.md
  - docs/02-architecture/api-architecture.md
---

# ADR-0010: React Native + Expo Mobile Technology Stack

## Status

Accepted

## Context

NeoTool needs to choose a mobile technology stack that supports:
- Cross-platform development for iOS and Android from a single codebase
- Type safety and modern development experience consistent with the web application
- Integration with the existing GraphQL backend
- Shared code and patterns with the web application (TypeScript + React)
- Fast development iteration with hot reload
- Native performance and access to device features
- Internationalization (i18n) support
- Easy deployment and updates
- Scalable architecture that aligns with the monorepo structure

The mobile application will provide the same core functionality as the web application while leveraging native mobile capabilities.

## Decision

We will use **React Native + Expo** as the primary mobile technology stack.

### Technology Components

- **Language**: TypeScript
- **Framework**: React Native
- **Development Platform**: Expo (managed workflow)
- **UI Components**: React Native Paper + custom design system (aligned with web MUI)
- **Navigation**: React Navigation
- **State Management**: Zustand + React Context
- **API Integration**: Apollo Client (GraphQL)
- **Internationalization**: i18next + react-i18next (same as web)
- **Forms**: React Hook Form + Zod (same as web)
- **Testing**: Jest + React Native Testing Library
- **Build & Deploy**: EAS (Expo Application Services)

## Consequences

### Positive

- **Code sharing with web**: TypeScript, React, GraphQL queries, business logic, and utilities can be shared between web and mobile
- **Single codebase for iOS & Android**: Write once, deploy to both platforms, reducing development and maintenance costs
- **Expo ecosystem**: Out-of-the-box support for OTA updates, push notifications, camera, location, and other native features
- **Type safety**: TypeScript + GraphQL codegen ensures end-to-end type safety from API to UI
- **Fast development**: Hot reload, Expo Go for instant preview, and excellent developer experience
- **Consistent patterns**: Same state management (Zustand), forms (React Hook Form), and validation (Zod) as web
- **i18n ready**: Same i18next configuration and translation files as web application
- **Monorepo integration**: Fits naturally into existing NX/pnpm monorepo structure
- **EAS Build & Submit**: Simplified CI/CD for app store deployments
- **Community & ecosystem**: Large React Native community, extensive libraries, and active development

### Negative

- **Bundle size**: React Native apps can have larger bundle sizes compared to native apps
- **Performance overhead**: JavaScript bridge introduces some overhead compared to fully native apps
- **Expo limitations**: Some advanced native features may require custom native modules (ejecting or using Expo modules)
- **Learning curve**: Developers need to understand React Native-specific patterns and mobile development concepts
- **Platform differences**: Despite "write once", some platform-specific code and testing is still required
- **Debugging complexity**: Mobile debugging can be more complex than web development

### Risks

- **Expo evolution**: Dependency on Expo's continued development and managed workflow
- **Native module compatibility**: Some third-party libraries may not work with Expo managed workflow
- **Performance bottlenecks**: Complex UIs or heavy computations may hit performance limits
- **App store policies**: Changes in iOS/Android policies may affect deployment

### Mitigation Strategies

- **Expo modules**: Use Expo modules (config plugins) for native features before considering ejection
- **Performance profiling**: Regular performance testing and optimization using React Native Performance Monitor
- **Gradual native adoption**: Start with Expo managed workflow, migrate to bare workflow only if absolutely necessary
- **Platform testing**: Test on both iOS and Android throughout development, not just at the end
- **Code sharing strategy**: Use shared packages in monorepo for business logic, keep platform-specific code isolated

## Problems This Choice Solves

- **Cross-platform development**: Single codebase for iOS and Android reduces development time and maintenance
- **Consistency with web**: Same language, framework, and patterns as web application ensures consistency
- **Developer velocity**: Reuse React knowledge, share code with web, and leverage Expo's tools for fast iteration
- **Type safety**: End-to-end type safety from GraphQL schema to mobile UI
- **Deployment complexity**: EAS simplifies build and submission process compared to native toolchains
- **Feature parity**: Easier to maintain feature parity between web and mobile applications

## Alternatives Considered

### Flutter

- **Pros**: Excellent performance, beautiful UI, strong typing (Dart), single codebase
- **Cons**: Different language (Dart), different patterns, cannot share code with TypeScript web app, smaller ecosystem for business apps, steeper learning curve for React developers

### Native (Swift/Kotlin)

- **Pros**: Best performance, full platform control, access to latest platform features
- **Cons**: Two separate codebases (iOS + Android), no code sharing with web, 2x development and maintenance effort, different languages and patterns

### Ionic + Capacitor

- **Pros**: Web technologies (HTML/CSS/JS), can reuse Next.js components
- **Cons**: WebView performance limitations, less native feel, not true "native" experience, different patterns from React Native

### React Native (without Expo)

- **Pros**: More flexibility, full control over native modules
- **Cons**: More complex setup, no OTA updates out of the box, manual native configuration, slower development iteration

### Progressive Web App (PWA)

- **Pros**: Same codebase as web, no app store required
- **Cons**: Limited offline capabilities, no native features (push notifications, background tasks), worse performance, not installed on home screen by default

## Decision Drivers

1. **Code sharing**: Maximize code reuse with existing TypeScript + React web application
2. **Developer experience**: Leverage existing React knowledge and maintain consistency across platforms
3. **Time to market**: Fast development with hot reload, Expo Go, and simplified deployment
4. **Type safety**: Maintain end-to-end type safety from GraphQL API to mobile UI
5. **Ecosystem maturity**: Large React Native community with extensive libraries and support
6. **Monorepo alignment**: Natural fit for existing NX/pnpm monorepo structure
7. **Deployment simplicity**: EAS provides streamlined build and submission process
8. **Future-proof**: React Native is actively developed by Meta and widely adopted

## Implementation Notes

### Monorepo Structure

```
mobile/
├── app/                          # Expo Router (file-based routing)
│   ├── (auth)/                   # Auth screens (login, signup)
│   ├── (tabs)/                   # Tab navigation (dashboard, settings)
│   ├── _layout.tsx               # Root layout (providers)
│   └── index.tsx                 # Entry screen
├── src/
│   ├── components/               # React Native components
│   ├── navigation/               # Navigation configuration
│   ├── hooks/                    # Custom hooks
│   ├── providers/                # Context providers (shared with web)
│   ├── utils/                    # Utility functions (shared with web)
│   └── generated/                # GraphQL types (codegen)
├── assets/                       # Images, fonts, etc.
├── app.json                      # Expo configuration
├── eas.json                      # EAS Build configuration
└── package.json
```

### Shared Packages (in monorepo)

Create shared packages that both web and mobile can use:

```
packages/
├── shared-ui/                    # Shared components (headless)
├── shared-graphql/               # GraphQL queries & types
├── shared-utils/                 # Business logic, validators, formatters
├── shared-i18n/                  # Translation files & i18n config
└── shared-types/                 # TypeScript types
```

### Technology Alignment with Web

| Feature | Web | Mobile |
|---------|-----|--------|
| Language | TypeScript | TypeScript ✅ |
| Framework | React + Next.js | React Native + Expo |
| UI Library | Material-UI (MUI) | React Native Paper |
| Navigation | Next.js App Router | React Navigation |
| State Management | Zustand + Context | Zustand + Context ✅ |
| Forms | React Hook Form | React Hook Form ✅ |
| Validation | Zod | Zod ✅ |
| GraphQL Client | Apollo Client | Apollo Client ✅ |
| i18n | i18next | i18next ✅ |
| Testing | Jest + RTL | Jest + RNTL |

### Key Features to Implement

1. **i18n Support**
   - Same i18next configuration as web
   - Shared translation files
   - Locale detection and switching

2. **GraphQL Integration**
   - Apollo Client with same configuration
   - Shared GraphQL queries and mutations
   - Type generation with GraphQL Code Generator

3. **Authentication**
   - Shared auth logic with web
   - Secure token storage (Expo SecureStore)
   - Biometric authentication support

4. **Offline Support**
   - Apollo Client cache persistence
   - Offline mutation queue
   - Network status detection

5. **Push Notifications**
   - Expo Notifications
   - Deep linking support
   - Notification preferences

6. **Theme & Design System**
   - Design tokens shared with web (colors, spacing, typography)
   - React Native Paper for Material Design
   - Dark mode support

7. **Performance Optimization**
   - Code splitting with dynamic imports
   - Image optimization
   - List virtualization (FlatList)
   - Memo and callback optimization

### Expo Configuration

```json
{
  "expo": {
    "name": "NeoTool",
    "slug": "neotool",
    "version": "1.0.0",
    "orientation": "portrait",
    "icon": "./assets/icon.png",
    "userInterfaceStyle": "automatic",
    "splash": {
      "image": "./assets/splash.png",
      "resizeMode": "contain",
      "backgroundColor": "#ffffff"
    },
    "updates": {
      "fallbackToCacheTimeout": 0,
      "url": "https://u.expo.dev/[project-id]"
    },
    "assetBundlePatterns": ["**/*"],
    "ios": {
      "supportsTablet": true,
      "bundleIdentifier": "com.neotool.app"
    },
    "android": {
      "adaptiveIcon": {
        "foregroundImage": "./assets/adaptive-icon.png",
        "backgroundColor": "#ffffff"
      },
      "package": "com.neotool.app"
    },
    "plugins": [
      "expo-router",
      "expo-secure-store",
      "expo-localization"
    ]
  }
}
```

### EAS Build Configuration

```json
{
  "build": {
    "development": {
      "developmentClient": true,
      "distribution": "internal"
    },
    "preview": {
      "distribution": "internal",
      "ios": {
        "simulator": true
      }
    },
    "production": {
      "autoIncrement": true
    }
  },
  "submit": {
    "production": {}
  }
}
```

### Migration Path

1. **Phase 1: Foundation**
   - Set up Expo project in monorepo
   - Configure TypeScript, ESLint, Prettier (same as web)
   - Set up Apollo Client and GraphQL codegen
   - Implement authentication flow

2. **Phase 2: Core Features**
   - Build navigation structure
   - Implement i18n
   - Create shared components
   - Set up state management

3. **Phase 3: Feature Parity**
   - Implement main features from web app
   - Add offline support
   - Implement push notifications
   - Performance optimization

4. **Phase 4: Platform Polish**
   - iOS-specific refinements
   - Android-specific refinements
   - Accessibility improvements
   - App store submission

## Related Documentation

- [Mobile Architecture](../02-architecture/mobile-architecture.md)
- [Frontend Architecture (Web)](../02-architecture/frontend-architecture.md)
- [ADR-0004: TypeScript/Next.js Frontend](./0004-typescript-nextjs-frontend.md)
- [API Architecture](../02-architecture/api-architecture.md)

## References

- [React Native Documentation](https://reactnative.dev/)
- [Expo Documentation](https://docs.expo.dev/)
- [React Navigation](https://reactnavigation.org/)
- [Apollo Client](https://www.apollographql.com/docs/react/)
- [EAS Build](https://docs.expo.dev/build/introduction/)
- [React Native Performance](https://reactnative.dev/docs/performance)
