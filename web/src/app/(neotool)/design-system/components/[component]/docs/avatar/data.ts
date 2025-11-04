import { ComponentData } from '../types';

export const avatarData: ComponentData = {
  name: "Avatar",
  description: "User profile images and initials with fallback options",
  status: "stable" as const,
  githubUrl: "/web/src/shared/components/ui/primitives/Avatar.tsx",
  
  props: [
    { name: "src", type: "string", required: false, description: "Image source URL" },
        { name: "alt", type: "string", required: false, description: "Alt text for image" },
        { name: "children", type: "string", required: false, description: "Text to display (usually initials)" },
        { name: "size", type: "number | 'small' | 'medium' | 'large'", required: false, description: "Avatar size", default: "'medium'" },
        { name: "variant", type: "'circular' | 'rounded' | 'square'", required: false, description: "Avatar shape", default: "'circular'" },
  ],
  examples: [
    { title: "With Image", description: "Avatar with profile image" },
        { title: "With Initials", description: "Avatar showing user initials" },
        { title: "Sizes", description: "Different avatar sizes" },
  ]
};
