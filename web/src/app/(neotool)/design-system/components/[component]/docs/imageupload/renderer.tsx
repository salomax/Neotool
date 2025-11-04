"use client";

import React from "react";
import { ImageUpload } from '@/shared/components/ui/primitives/ImageUpload';
import { ComponentRendererProps } from '../types';

export const ImageuploadRenderer: React.FC<ComponentRendererProps> = ({ example }) => {
  switch (example) {
    case 'Basic ImageUpload':
      return (
        <ImageUpload
          label="Upload Images"
          helperText="Select images to upload"
          onChange={(files) => console.log('Files changed:', files)}
        />
      );
    
    case 'With Preview':
      return (
        <ImageUpload
          label="Upload with Preview"
          showPreview
          showFileList
          helperText="Images will be previewed after selection"
          onChange={(files) => console.log('Files changed:', files)}
        />
      );
    
    case 'Single File':
      return (
        <ImageUpload
          label="Single Image Upload"
          multiple={false}
          maxFiles={1}
          helperText="Upload only one image"
          onChange={(files) => console.log('Files changed:', files)}
        />
      );
    
    case 'With Constraints':
      return (
        <ImageUpload
          label="Constrained Upload"
          maxFiles={3}
          maxFileSize={2 * 1024 * 1024}
          accept="image/jpeg,image/png"
          helperText="Maximum 3 files, 2MB each, JPEG/PNG only"
          onChange={(files) => console.log('Files changed:', files)}
        />
      );
    
    case 'With Compression':
      return (
        <ImageUpload
          label="Compressed Upload"
          compressImages
          imageQuality={0.8}
          maxImageWidth={1920}
          maxImageHeight={1080}
          helperText="Images will be automatically compressed"
          onChange={(files) => console.log('Files changed:', files)}
        />
      );
    
    case 'Custom Text':
      return (
        <ImageUpload
          label="Custom Upload"
          uploadText="Choose Photos"
          dragText="Drag photos here"
          dropText="Drop photos to upload"
          helperText="Custom text for all upload actions"
          onChange={(files) => console.log('Files changed:', files)}
        />
      );
    
    default:
      return (
        <ImageUpload
          label="Default ImageUpload"
          onChange={(files) => console.log('Files changed:', files)}
        />
      );
  }
};
