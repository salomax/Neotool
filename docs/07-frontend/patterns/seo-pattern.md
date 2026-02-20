---

title: SEO Pattern
type: pattern
category: frontend
status: current
version: 1.0.0
tags: [seo, metadata, json-ld, server-components, nextjs, core-web-vitals, performance]
ai_optimized: true
search_keywords: [seo, metadata, json-ld, structured data, open graph, server components, nextjs seo, lcp, cls, core web vitals]
related:

- 07-frontend/patterns/styling-pattern.md
- ## 07-frontend/patterns/shared-components-pattern.md

# SEO Pattern

> **Purpose**: Standard pattern for maximizing Search Engine Optimization (SEO), social sharing visibility, and Core Web Vitals performance in the Next.js App Router architecture.

## Overview

The SEO Pattern leverages Next.js App Router features to deliver static, indexable metadata and structured data while maintaining a dynamic user experience. It enforces a strict separation between Server Components (for SEO) and Client Components (for interactivity) to ensure search engines can parse critical page information without executing JavaScript.

## Metadata Strategy

All public pages must export a static or dynamic `metadata` object from a Server Component (`page.tsx`).

### Rules

1.  **Server-Side Only**: Metadata must be defined in `page.tsx` (Server Component).
2.  **No Client Side Metadata**: Do not use `document.title` or `Head` components in Client Components.
3.  **Required Fields**:
    - `title`: Page title (suffixed with "| neotool").
    - `description`: Concise summary (< 160 chars).
    - `keywords`: Array of relevant search terms.
    - `openGraph`: Title, description, type, locale.

### Example

```typescript
// page.tsx (Server Component)
import { Metadata } from "next";

export const metadata: Metadata = {
  title: "Indicadores Econômicos Nacionais | neotool",
  description:
    "Acompanhe os principais indicadores econômicos do Brasil em tempo real: IPCA, Selic, PIB.",
  keywords: ["indicadores", "economia", "brasil", "IPCA", "Selic"],
  openGraph: {
    title: "Indicadores Econômicos Nacionais | neotool",
    description:
      "Monitore a saúde da economia brasileira com dados atualizados.",
    type: "website",
    locale: "pt_BR",
  },
};
```

## Client/Server Split Pattern

To balance SEO with interactive features (like search, tabs, or client-side fetching), use the **Page/Client Split** pattern.

### Structure

- **`page.tsx` (Server Component)**:
  - Exports `metadata`.
  - Generates JSON-LD Structured Data.
  - Renders the Client Component.
- **`*Client.tsx` (Client Component)**:
  - Contains `use client`.
  - Handles hooks (`useState`, `useEffect`, `useQuery`).
  - Renders the UI.

### Implementation

```typescript
// page.tsx
import { Metadata } from 'next';
import NationalIndicatorsClient from './NationalIndicatorsClient';

export const metadata: Metadata = { ... };

export default function NationalIndicatorsPage() {
  // Static JSON-LD generation
  const jsonLd = { ... };

  return (
    <>
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }}
      />
      <NationalIndicatorsClient />
    </>
  );
}
```

```typescript
// NationalIndicatorsClient.tsx
"use client";

import { useState } from "react";
// ... imports

export default function NationalIndicatorsClient() {
  // Client-side logic here
  return <Page>...</Page>;
}
```

## Structured Data (JSON-LD)

Use JSON-LD to provide search engines with detailed context about the page content (e.g., specific economic indicators, FAQs, Articles).

### Rules

1.  **Format**: Use `application/ld+json`.
2.  **Placement**: Inject via `dangerouslySetInnerHTML` in the Server Component.
3.  **Schema**: Use standard [Schema.org](https://schema.org) types (e.g., `DefinedTermSet`, `Article`, `Organization`).

### Example

```typescript
const jsonLd = {
  "@context": "https://schema.org",
  "@type": "DefinedTermSet",
  name: "Indicadores Econômicos Nacionais",
  description: "Lista dos principais indicadores...",
  definedTerm: [
    {
      "@type": "DefinedTerm",
      name: "IPCA",
      description: "Índice Nacional de Preços ao Consumidor Amplo...",
    },
  ],
};
```

## Core Web Vitals (Performance)

SEO is not just about keywords; it's about performance. We follow specific patterns to optimize Core Web Vitals (LCP, CLS, FID).

### 1. LCP (Largest Contentful Paint)

- **Priority Images**: The largest visible image above the fold (e.g., Hero image) **MUST** use the `priority` prop.
- **Sizing**: Use proper `sizes` prop to serve the correct image resolution.

```typescript
// ✅ Correct
<Image
  src="/hero.png"
  alt="Hero"
  fill
  priority
  sizes="(max-width: 768px) 100vw, 50vw"
/>
```

### 2. CLS (Cumulative Layout Shift)

- **Responsive Sizing**: Use CSS/MUI `sx` breakpoints for responsive dimensions.
- **Avoid `useMediaQuery` for Layout**: Do NOT conditionally render layout elements based on `useMediaQuery` or `window.width` if it changes the DOM structure or dimensions during hydration. This causes layout shifts.
- **Image Dimensions**: Always provide `width` and `height` (or `fill`) to reserve space.

```typescript
// ✅ Correct (CSS-based, no CLS)
<Box sx={{ width: { xs: '100%', md: '50%' } }} />

// ❌ Incorrect (JS-based, causes CLS/hydration mismatch)
const isMobile = useMediaQuery(...);
<Box sx={{ width: isMobile ? '100%' : '50%' }} />
```

## Competitor SEO Strategy

It is a common question whether to include competitor names in metadata keywords to capture their traffic.

### Guidelines

1.  **Do NOT use competitor names in `keywords` or `meta` tags**: This is considered "keyword stuffing" and can be penalized by search engines. It provides no value to the user and degrades trust.
2.  **Use Comparison Pages**: The industry standard for ranking for competitor keywords is to create dedicated **Comparison Pages** (e.g., "neotool vs CompetitorX").
    - These pages must provide _genuine, helpful comparison content_.
    - They should be linked from the footer or a "Comparisons" section.
    - The URL structure should be `/compare/neotool-vs-competitor`.
    - This approach targets the "alternative to [Competitor]" intent legitimately.

## Checklist

- [ ] **Metadata**: `title`, `description`, `openGraph` defined in `page.tsx`.
- [ ] **Structured Data**: JSON-LD present for key entities.
- [ ] **Split**: Logic moved to `*Client.tsx`, `page.tsx` remains Server Component.
- [ ] **LCP**: Hero images have `priority`.
- [ ] **CLS**: Responsive layout uses CSS (`sx` breakpoints), not JS hooks.
