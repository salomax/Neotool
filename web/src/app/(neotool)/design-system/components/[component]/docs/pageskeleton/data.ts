import { ComponentData } from '../types';

export const pageskeletonData: ComponentData = {
  name: "PageSkeleton",
  description: "Placeholder for content while loading",
  status: "stable" as const,

  githubUrl: "/web/src/shared/components/ui/primitives/PageSkeleton.tsx",
  type: "'text' | 'rectangular' | 'circular'" as const,
  
  props: [
    { name: "variant", type: "'text' | 'rectangular' | 'circular'", required: false, description: "Skeleton shape", default: "'text'" },
        { name: "width", type: "number | string", required: false, description: "Skeleton width" },
        { name: "height", type: "number | string", required: false, description: "Skeleton height" },
        { name: "animation", type: "'pulse' | 'wave' | false", required: false, description: "Animation type", default: "'pulse'" },
  ],
  examples: [
    { title: "Text Skeleton", description: "Skeleton for text content" },
        { title: "Card Skeleton", description: "Skeleton for card layouts" },
        { title: "List Skeleton", description: "Skeleton for list items" },
  ]
};
