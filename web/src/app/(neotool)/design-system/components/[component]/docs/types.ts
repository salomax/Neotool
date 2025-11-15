export interface ComponentProp {
  name: string;
  type: string;
  required: boolean;
  description: string;
  default?: string;
}

export interface ComponentExample {
  title: string;
  description: string;
}

export interface ComponentData {
  name: string;
  description: string;
  status: 'stable' | 'beta' | 'deprecated';
  props: ComponentProp[];
  examples: ComponentExample[];
  githubUrl: string;
  type?: 'custom' | 'mui-wrapper' | 'mui-simple';
  muiDocsUrl?: string;
}

export interface ComponentRendererProps {
  example: string;
  [key: string]: any;
}

