'use client';

import React from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Box,
  Typography,
  IconButton,
  Button,
} from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import DownloadIcon from '@mui/icons-material/Download';
import InsertDriveFileIcon from '@mui/icons-material/InsertDriveFile';

export interface PreviewAttachment {
  id: string;
  filename?: string | null;
  mimeType?: string | null;
  downloadUrl?: string | null;
}

interface FilePreviewDialogProps {
  attachment: PreviewAttachment | null;
  onClose: () => void;
}

function isImage(mimeType?: string | null): boolean {
  return !!mimeType && mimeType.startsWith('image/');
}

function isPdf(mimeType?: string | null): boolean {
  return mimeType === 'application/pdf';
}

export function FilePreviewDialog({ attachment, onClose }: FilePreviewDialogProps) {
  const open = Boolean(attachment);
  const url = attachment?.downloadUrl ?? undefined;
  const name = attachment?.filename ?? 'Arquivo';
  const mime = attachment?.mimeType;

  const canPreview = isImage(mime) || isPdf(mime);

  return (
    <Dialog
      open={open}
      onClose={onClose}
      maxWidth="md"
      fullWidth
      PaperProps={{ sx: { height: isPdf(mime) ? '90vh' : 'auto', display: 'flex', flexDirection: 'column' } }}
    >
      <DialogTitle sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', pr: 1 }}>
        <Typography variant="subtitle1" noWrap sx={{ flex: 1, mr: 1 }}>
          {name}
        </Typography>
        <IconButton onClick={onClose} size="small">
          <CloseIcon fontSize="small" />
        </IconButton>
      </DialogTitle>

      <DialogContent
        sx={{
          flex: 1,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          p: canPreview ? 0 : 4,
          overflow: 'hidden',
        }}
      >
        {!attachment ? null : isImage(mime) && url ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img
            src={url}
            alt={name}
            style={{ maxWidth: '100%', maxHeight: '70vh', objectFit: 'contain', display: 'block' }}
          />
        ) : isPdf(mime) && url ? (
          <Box component="iframe"
            src={url}
            title={name}
            sx={{ width: '100%', height: '100%', border: 'none' }}
          />
        ) : (
          <Box sx={{ textAlign: 'center' }}>
            <InsertDriveFileIcon sx={{ fontSize: 64, color: 'text.disabled', mb: 2 }} />
            <Typography variant="body2" color="text.secondary">
              Pré-visualização não disponível para este tipo de arquivo.
            </Typography>
            <Typography variant="caption" color="text.disabled">
              {mime ?? 'Tipo desconhecido'}
            </Typography>
          </Box>
        )}
      </DialogContent>

      <DialogActions>
        <Button onClick={onClose} color="inherit" size="small">
          Fechar
        </Button>
        {url && (
          <Button
            href={url}
            target="_blank"
            rel="noopener noreferrer"
            startIcon={<DownloadIcon />}
            size="small"
            variant="outlined"
          >
            Download
          </Button>
        )}
      </DialogActions>
    </Dialog>
  );
}
