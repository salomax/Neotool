"use client";

import React from "react";
import { RichTextEditor } from '@/shared/components/ui/forms/RichTextEditor';
import { ComponentRendererProps } from '../types';

export const RichtexteditorRenderer: React.FC<ComponentRendererProps> = ({ example }) => {
  switch (example) {
    case 'Basic Editor':
      return (
        <RichTextEditor
          placeholder="Start typing your content here..."
          minHeight={200}
        />
      );
    
    case 'Read Only':
      return (
        <RichTextEditor
          value="<h2>Read Only Content</h2><p>This content cannot be edited. The toolbar is disabled and the editor is not interactive.</p><blockquote>This is a quote that cannot be modified.</blockquote>"
          readOnly
          minHeight={200}
        />
      );
    
    case 'Custom Toolbar':
      return (
        <RichTextEditor
          value="<p>This editor only allows <strong>bold</strong> and <em>italic</em> formatting.</p>"
          allowedFormats={['bold', 'italic']}
          minHeight={200}
        />
      );
    
    case 'Bottom Toolbar':
      return (
        <RichTextEditor
          value="<p>This editor has the toolbar at the bottom instead of the top.</p>"
          toolbarPosition="bottom"
          minHeight={200}
        />
      );
    
    default:
      return (
        <RichTextEditor
          placeholder="Start typing your content here..."
          minHeight={200}
        />
      );
  }
};
