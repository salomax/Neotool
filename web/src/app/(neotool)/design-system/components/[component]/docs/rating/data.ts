import { ComponentData } from '../types';

export const ratingData: ComponentData = {
  name: "Rating",
  description: "Interactive rating component with multiple variants and customization options",
  status: "stable" as const,

  githubUrl: "/web/src/shared/components/ui/primitives/Rating.tsx",

  props: [
    { name: "value", type: "number", required: false, description: "Current rating value", default: "0" },
        { name: "max", type: "number", required: false, description: "Maximum rating value", default: "5" },
        { name: "size", type: "'small' | 'medium' | 'large'", required: false, description: "Size of the rating icons", default: "'medium'" },
        { name: "variant", type: "'star' | 'thumbs' | 'heart' | 'emoji'", required: false, description: "Visual variant of the rating", default: "'star'" },
        { name: "color", type: "'primary' | 'secondary' | 'error' | 'warning' | 'info' | 'success'", required: false, description: "Color theme of the rating", default: "'primary'" },
        { name: "readOnly", type: "boolean", required: false, description: "Whether the rating is read-only", default: "false" },
        { name: "disabled", type: "boolean", required: false, description: "Whether the rating is disabled", default: "false" },
        { name: "showLabels", type: "boolean", required: false, description: "Whether to show labels on hover", default: "false" },
        { name: "showValue", type: "boolean", required: false, description: "Whether to show the current value", default: "false" },
        { name: "precision", type: "number", required: false, description: "Precision of the rating (1 for whole numbers, 0.5 for half ratings)", default: "1" },
        { name: "onChange", type: "(value: number) => void", required: false, description: "Callback fired when rating changes" },
        { name: "onHover", type: "(value: number) => void", required: false, description: "Callback fired when hovering over rating" },
        { name: "onLeave", type: "() => void", required: false, description: "Callback fired when mouse leaves rating" },
        { name: "className", type: "string", required: false, description: "Custom CSS class name" },
        { name: "data-testid", type: "string", required: false, description: "Test identifier for testing" }
  ],
  examples: [
    { title: "Star Rating", description: "Classic 5-star rating system" },
        { title: "Thumbs Rating", description: "Like/dislike thumbs up/down rating" },
        { title: "Heart Rating", description: "Heart-based rating for favorites" },
        { title: "Emoji Rating", description: "Emoji-based emotional rating" },
        { title: "Half Precision", description: "Rating with half-star precision" },
        { title: "Read Only", description: "Display-only rating without interaction" }
  ]
};
