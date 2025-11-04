import { ComponentData } from '../types';

export const emptyerrorstateData: ComponentData = {
  name: "EmptyErrorState",
  description: "Displays when no data is available or an error occurs",
  status: "stable" as const,

  githubUrl: "/web/src/shared/components/ui/data-display/EmptyErrorState.tsx",
  type: "'empty' | 'error' | 'loading'" as const,
  
  props: [
    { name: "type", type: "'empty' | 'error' | 'loading'", required: true, description: "State type" },
        { name: "title", type: "string", required: true, description: "State title" },
        { name: "message", type: "string", required: false, description: "State message" },
        { name: "actionText", type: "string", required: false, description: "Action button text" },
        { name: "onAction", type: "() => void", required: false, description: "Action handler" },
  ],
  examples: [
    { title: "Empty State", description: "When no data is available" },
        { title: "Error State", description: "When an error occurs" },
        { title: "With Action", description: "State with action button" },
  ]
};
