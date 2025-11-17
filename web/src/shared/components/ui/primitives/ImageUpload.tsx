import React, { useState, useCallback, useRef } from 'react';
import {
  Box,
  Typography,
  Button,
  Paper,
  Stack,
  IconButton,
  LinearProgress,
  Alert,
  Chip,
  FormControl,
  FormLabel,
  FormHelperText
} from '@/shared/ui/mui-imports';
import {
  CloudUploadIcon,
  DeleteIcon,
  ImageIcon,
  CheckCircleIcon,
  ErrorIcon
} from '@/shared/ui/mui-imports';

export interface ImageUploadProps {
  /** Current uploaded files */
  value?: File[];
  /** Default files (uncontrolled) */
  defaultValue?: File[];
  /** Whether the upload is disabled */
  disabled?: boolean;
  /** Whether the upload is required */
  required?: boolean;
  /** Label for the upload */
  label?: string;
  /** Helper text below the upload */
  helperText?: string;
  /** Error state */
  error?: boolean;
  /** Error message */
  errorMessage?: string;
  /** Maximum number of files allowed */
  maxFiles?: number;
  /** Maximum file size in bytes */
  maxFileSize?: number;
  /** Accepted file types */
  accept?: string;
  /** Whether to allow multiple files */
  multiple?: boolean;
  /** Whether to show drag and drop area */
  showDragDrop?: boolean;
  /** Whether to show file previews */
  showPreview?: boolean;
  /** Whether to show file list */
  showFileList?: boolean;
  /** Whether to show upload progress */
  showProgress?: boolean;
  /** Custom upload text */
  uploadText?: string;
  /** Custom drag text */
  dragText?: string;
  /** Custom drop text */
  dropText?: string;
  /** Preview image size */
  previewSize?: number;
  /** Whether to compress images */
  compressImages?: boolean;
  /** Image quality for compression (0-1) */
  imageQuality?: number;
  /** Maximum image width for compression */
  maxImageWidth?: number;
  /** Maximum image height for compression */
  maxImageHeight?: number;
  /** Callback fired when files change */
  onChange?: (files: File[]) => void;
  /** Callback fired when files are added */
  onAdd?: (files: File[]) => void;
  /** Callback fired when files are removed */
  onRemove?: (file: File, index: number) => void;
  /** Callback fired when upload starts */
  onUploadStart?: (files: File[]) => void;
  /** Callback fired when upload completes */
  onUploadComplete?: (files: File[]) => void;
  /** Callback fired when upload fails */
  onUploadError?: (error: Error) => void;
  /** Custom CSS class name */
  className?: string;
  /** Test identifier */
  'data-testid'?: string;
}

const ImageUpload: React.FC<ImageUploadProps> = ({
  value,
  defaultValue = [],
  disabled = false,
  required = false,
  label,
  helperText,
  error = false,
  errorMessage,
  maxFiles = 5,
  maxFileSize = 5 * 1024 * 1024, // 5MB
  accept = 'image/*',
  multiple = true,
  showDragDrop = true,
  showPreview = true,
  showFileList = true,
  showProgress = false,
  uploadText = 'Upload Images',
  dragText = 'Drag and drop images here',
  dropText = 'Drop images here',
  previewSize = 100,
  compressImages = false,
  imageQuality = 0.8,
  maxImageWidth = 1920,
  maxImageHeight = 1080,
  onChange,
  onAdd,
  onRemove,
  onUploadStart,
  onUploadComplete,
  onUploadError,
  className,
  'data-testid': dataTestId
}) => {
  const [internalFiles, setInternalFiles] = useState<File[]>(defaultValue);
  const [isDragOver, setIsDragOver] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [isUploading, setIsUploading] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);
  
  const fileInputRef = useRef<HTMLInputElement>(null);
  
  const isControlled = value !== undefined;
  const currentFiles = isControlled ? value : internalFiles;

  const compressImage = useCallback(async (file: File): Promise<File> => {
    if (!compressImages || !file.type.startsWith('image/')) {
      return file;
    }

    return new Promise((resolve) => {
      const canvas = document.createElement('canvas');
      const ctx = canvas.getContext('2d');
      const img = new Image();

      img.onload = () => {
        // Calculate new dimensions
        let { width, height } = img;
        const aspectRatio = width / height;

        if (width > maxImageWidth) {
          width = maxImageWidth;
          height = width / aspectRatio;
        }
        if (height > maxImageHeight) {
          height = maxImageHeight;
          width = height * aspectRatio;
        }

        canvas.width = width;
        canvas.height = height;

        // Draw and compress
        ctx?.drawImage(img, 0, 0, width, height);
        canvas.toBlob(
          (blob) => {
            if (blob) {
              const compressedFile = new File([blob], file.name, {
                type: file.type,
                lastModified: Date.now(),
              });
              resolve(compressedFile);
            } else {
              resolve(file);
            }
          },
          file.type,
          imageQuality
        );
      };

      img.src = URL.createObjectURL(file);
    });
  }, [compressImages, imageQuality, maxImageWidth, maxImageHeight]);

  const validateFile = useCallback((file: File): string | null => {
    // Check file size
    if (file.size > maxFileSize) {
      return `File "${file.name}" is too large. Maximum size is ${Math.round(maxFileSize / 1024 / 1024)}MB`;
    }

    // Check file type
    if (accept && !file.type.match(accept.replace('*', '.*'))) {
      return `File "${file.name}" is not a supported image format`;
    }

    return null;
  }, [maxFileSize, accept]);

  const processFiles = useCallback(async (files: FileList | File[]) => {
    const fileArray = Array.from(files);
    const newFiles: File[] = [];
    const errors: string[] = [];

    // Check if adding these files would exceed maxFiles
    if (currentFiles.length + fileArray.length > maxFiles) {
      setUploadError(`Maximum ${maxFiles} files allowed`);
      return;
    }

    for (const file of fileArray) {
      const validationError = validateFile(file);
      if (validationError) {
        errors.push(validationError);
        continue;
      }

      try {
        const processedFile = await compressImage(file);
        newFiles.push(processedFile);
      } catch (error) {
        errors.push(`Failed to process "${file.name}"`);
      }
    }

    if (errors.length > 0) {
      setUploadError(errors.join(', '));
    }

    if (newFiles.length > 0) {
      const updatedFiles = [...currentFiles, ...newFiles];
      
      if (!isControlled) {
        setInternalFiles(updatedFiles);
      }
      
      onChange?.(updatedFiles);
      onAdd?.(newFiles);
    }
  }, [currentFiles, maxFiles, validateFile, compressImage, isControlled, onChange, onAdd]);

  const handleFileSelect = useCallback((event: React.ChangeEvent<HTMLInputElement>) => {
    if (disabled) return;
    
    const files = event.target.files;
    if (files) {
      processFiles(files);
    }
    
    // Reset input
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
  }, [disabled, processFiles]);

  const handleDragOver = useCallback((event: React.DragEvent) => {
    if (disabled) return;
    
    event.preventDefault();
    setIsDragOver(true);
  }, [disabled]);

  const handleDragLeave = useCallback((event: React.DragEvent) => {
    if (disabled) return;
    
    event.preventDefault();
    setIsDragOver(false);
  }, [disabled]);

  const handleDrop = useCallback((event: React.DragEvent) => {
    if (disabled) return;
    
    event.preventDefault();
    setIsDragOver(false);
    
    const files = event.dataTransfer.files;
    if (files) {
      processFiles(files);
    }
  }, [disabled, processFiles]);

  const handleRemoveFile = useCallback((index: number) => {
    if (disabled) return;
    
    const fileToRemove = currentFiles[index];
    if (!fileToRemove) return;
    
    const updatedFiles = currentFiles.filter((_, i) => i !== index);
    
    if (!isControlled) {
      setInternalFiles(updatedFiles);
    }
    
    onChange?.(updatedFiles);
    onRemove?.(fileToRemove, index);
  }, [disabled, currentFiles, isControlled, onChange, onRemove]);

  const handleUpload = useCallback(async () => {
    if (disabled || currentFiles.length === 0) return;
    
    setIsUploading(true);
    setUploadProgress(0);
    setUploadError(null);
    
    try {
      onUploadStart?.(currentFiles);
      
      // Simulate upload progress
      for (let i = 0; i <= 100; i += 10) {
        setUploadProgress(i);
        await new Promise(resolve => setTimeout(resolve, 100));
      }
      
      onUploadComplete?.(currentFiles);
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : 'Upload failed';
      setUploadError(errorMessage);
      onUploadError?.(error instanceof Error ? error : new Error(errorMessage));
    } finally {
      setIsUploading(false);
    }
  }, [disabled, currentFiles, onUploadStart, onUploadComplete, onUploadError]);

  const formatFileSize = (bytes: number): string => {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  };

  const renderPreview = (file: File, index: number) => {
    if (!showPreview) return null;

    const url = URL.createObjectURL(file);
    
    return (
      <Box
        key={index}
        sx={{
          position: 'relative',
          width: previewSize,
          height: previewSize,
          borderRadius: 1,
          overflow: 'hidden',
          border: 1,
          borderColor: 'divider',
        }}
      >
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img
          src={url}
          alt={file.name}
          style={{
            width: '100%',
            height: '100%',
            objectFit: 'cover',
          }}
        />
        <IconButton
          size="small"
          sx={{
            position: 'absolute',
            top: 4,
            right: 4,
            bgcolor: 'error.main',
            color: 'white',
            '&:hover': {
              bgcolor: 'error.dark',
            },
          }}
          onClick={() => handleRemoveFile(index)}
          disabled={disabled}
        >
          <DeleteIcon fontSize="small" />
        </IconButton>
      </Box>
    );
  };

  const renderFileList = () => {
    if (!showFileList || currentFiles.length === 0) return null;

    return (
      <Box sx={{ mt: 2 }}>
        <Typography variant="subtitle2" gutterBottom>
          Selected Files ({currentFiles.length}/{maxFiles})
        </Typography>
        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
          {currentFiles.map((file, index) => (
            <Chip
              key={index}
              label={`${file.name} (${formatFileSize(file.size)})`}
              onDelete={disabled ? undefined : () => handleRemoveFile(index)}
              color="primary"
              variant="outlined"
            />
          ))}
        </Stack>
      </Box>
    );
  };

  return (
    <FormControl 
      fullWidth 
      disabled={disabled}
      error={error || !!errorMessage}
      {...(className && { className })}
      data-testid={dataTestId}
    >
      {label && (
        <FormLabel sx={{ mb: 1 }}>
          {label}
          {required && <span style={{ color: 'red', marginLeft: 4 }}>*</span>}
        </FormLabel>
      )}
      
      <input
        ref={fileInputRef}
        type="file"
        accept={accept}
        multiple={multiple}
        onChange={handleFileSelect}
        style={{ display: 'none' }}
        disabled={disabled}
      />
      
      {showDragDrop && (
        <Paper
          sx={{
            p: 3,
            textAlign: 'center',
            border: 2,
            borderStyle: 'dashed',
            borderColor: isDragOver ? 'primary.main' : 'divider',
            bgcolor: isDragOver ? 'action.hover' : 'background.paper',
            cursor: disabled ? 'not-allowed' : 'pointer',
            transition: 'all 0.2s ease-in-out',
            '&:hover': {
              borderColor: disabled ? 'divider' : 'primary.main',
              bgcolor: disabled ? 'background.paper' : 'action.hover',
            },
          }}
          onDragOver={handleDragOver}
          onDragLeave={handleDragLeave}
          onDrop={handleDrop}
          onClick={() => !disabled && fileInputRef.current?.click()}
        >
          <Stack spacing={2} alignItems="center">
            <CloudUploadIcon sx={{ fontSize: 48, color: 'text.secondary' }} />
            <Box>
              <Typography variant="h6" gutterBottom>
                {isDragOver ? dropText : dragText}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                or click to browse files
              </Typography>
            </Box>
            <Button
              variant="contained"
              startIcon={<ImageIcon />}
              disabled={disabled}
              onClick={(e) => {
                e.stopPropagation();
                fileInputRef.current?.click();
              }}
            >
              {uploadText}
            </Button>
          </Stack>
        </Paper>
      )}
      
      {!showDragDrop && (
        <Button
          variant="outlined"
          startIcon={<CloudUploadIcon />}
          onClick={() => fileInputRef.current?.click()}
          disabled={disabled}
          fullWidth
        >
          {uploadText}
        </Button>
      )}
      
      {showPreview && currentFiles.length > 0 && (
        <Box sx={{ mt: 2 }}>
          <Typography variant="subtitle2" gutterBottom>
            Preview
          </Typography>
          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
            {currentFiles.map((file, index) => renderPreview(file, index))}
          </Stack>
        </Box>
      )}
      
      {renderFileList()}
      
      {showProgress && isUploading && (
        <Box sx={{ mt: 2 }}>
          <LinearProgress variant="determinate" value={uploadProgress} />
          <Typography variant="caption" color="text.secondary">
            Uploading... {uploadProgress}%
          </Typography>
        </Box>
      )}
      
      {uploadError && (
        <Alert severity="error" sx={{ mt: 2 }}>
          {uploadError}
        </Alert>
      )}
      
      {(helperText || errorMessage) && (
        <FormHelperText sx={{ mt: 1 }}>
          {errorMessage || helperText}
        </FormHelperText>
      )}
    </FormControl>
  );
};

export { ImageUpload };
export default ImageUpload;
