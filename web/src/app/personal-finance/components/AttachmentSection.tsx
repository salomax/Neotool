'use client';

import React, { useRef, useState } from 'react';
import {
  Box,
  Chip,
  CircularProgress,
  IconButton,
  Stack,
  Tooltip,
  Typography,
  alpha,
  useTheme,
} from '@mui/material';
import AttachFileIcon from '@mui/icons-material/AttachFile';
import CloseIcon from '@mui/icons-material/Close';
import DownloadIcon from '@mui/icons-material/Download';
import ImageIcon from '@mui/icons-material/Image';
import PictureAsPdfIcon from '@mui/icons-material/PictureAsPdf';
import InsertDriveFileIcon from '@mui/icons-material/InsertDriveFile';
import { useMutation } from '@apollo/client/react';
import {
  CREATE_ASSET_UPLOAD,
  CONFIRM_ASSET_UPLOAD,
  ATTACH_FILE_TO_TRANSACTION,
  REMOVE_TRANSACTION_ATTACHMENT,
  ATTACH_FILE_TO_HOLDING,
  REMOVE_HOLDING_ATTACHMENT,
} from '@/lib/graphql/operations/banking/mutations';
import type {
  CreateAssetUploadMutation,
  CreateAssetUploadMutationVariables,
} from '@/lib/graphql/operations/banking/mutations.generated';
import { FilePreviewDialog, type PreviewAttachment } from './FilePreviewDialog';

// ── Constants ──────────────────────────────────────────────────────────────────

const MAX_ATTACHMENTS = 3;
const MAX_FILE_BYTES = 3 * 1024 * 1024; // 3 MB
const ACCEPTED_TYPES =
  'image/*,text/plain,text/csv,.csv,application/vnd.ms-excel,.xls,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,.xlsx,application/pdf,.pdf';

const BANKING_ATTACHMENTS_NAMESPACE = 'banking-attachments';

// ── Types ──────────────────────────────────────────────────────────────────────

export interface Attachment {
  id: string;
  assetId: string;
  filename?: string | null;
  mimeType?: string | null;
  sizeBytes?: number | null;
  downloadUrl?: string | null;
  createdAt?: unknown;
}

interface AttachmentSectionProps {
  /** Pass either transactionId or holdingId (not both) */
  transactionId?: string;
  holdingId?: string;
  attachments: Attachment[];
  /** Called after any successful upload or remove so parent can refetch */
  onChanged: () => void;
  /** Compact mode — hides labels, only shows chips */
  compact?: boolean;
}

// ── Helpers ────────────────────────────────────────────────────────────────────

function humanSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  const kb = bytes / 1024;
  if (kb < 1024) return `${kb.toFixed(1)} KB`;
  return `${(kb / 1024).toFixed(1)} MB`;
}

function FileTypeIcon({ mimeType }: { mimeType?: string | null }) {
  if (mimeType?.startsWith('image/')) return <ImageIcon fontSize="small" />;
  if (mimeType === 'application/pdf') return <PictureAsPdfIcon fontSize="small" />;
  return <InsertDriveFileIcon fontSize="small" />;
}

// ── Component ──────────────────────────────────────────────────────────────────

export function AttachmentSection({
  transactionId,
  holdingId,
  attachments,
  onChanged,
  compact = false,
}: AttachmentSectionProps) {
  const theme = useTheme();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [removingId, setRemovingId] = useState<string | null>(null);
  const [preview, setPreview] = useState<PreviewAttachment | null>(null);

  const [createAssetUpload] = useMutation<CreateAssetUploadMutation, CreateAssetUploadMutationVariables>(CREATE_ASSET_UPLOAD);
  const [confirmAssetUpload] = useMutation(CONFIRM_ASSET_UPLOAD);
  const [attachToTransaction] = useMutation(ATTACH_FILE_TO_TRANSACTION);
  const [removeFromTransaction] = useMutation(REMOVE_TRANSACTION_ATTACHMENT);
  const [attachToHolding] = useMutation(ATTACH_FILE_TO_HOLDING);
  const [removeFromHolding] = useMutation(REMOVE_HOLDING_ATTACHMENT);

  const canUpload = attachments.length < MAX_ATTACHMENTS;

  // ── Upload flow ──────────────────────────────────────────────────────────────

  const handleFileSelected = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    // Reset input so the same file can be re-selected after an error
    if (fileInputRef.current) fileInputRef.current.value = '';
    if (!file) return;

    if (file.size > MAX_FILE_BYTES) {
      setUploadError('Arquivo muito grande. Máximo 3 MB.');
      return;
    }

    setUploadError(null);
    setUploading(true);

    try {
      // Step 1 — create upload slot
      const { data: uploadData } = await createAssetUpload({
        variables: {
          input: {
            namespace: BANKING_ATTACHMENTS_NAMESPACE,
            filename: file.name,
            mimeType: file.type || 'application/octet-stream',
            sizeBytes: file.size,
          },
        },
      });

      const assetId = uploadData?.createAssetUpload.id;
      const uploadUrl = uploadData?.createAssetUpload.uploadUrl;
      if (!assetId || !uploadUrl) throw new Error('Não foi possível iniciar o upload.');

      // Step 2 — PUT file directly to presigned URL
      const putResponse = await fetch(uploadUrl, {
        method: 'PUT',
        body: file,
        headers: { 'Content-Type': file.type || 'application/octet-stream' },
      });

      if (!putResponse.ok) {
        throw new Error(`Upload falhou: ${putResponse.status}`);
      }

      // Step 3 — confirm upload
      await confirmAssetUpload({ variables: { input: { assetId } } });

      // Step 4 — attach to record
      if (transactionId) {
        await attachToTransaction({ variables: { input: { transactionId, assetId } } });
      } else if (holdingId) {
        await attachToHolding({ variables: { input: { holdingId, assetId } } });
      }

      onChanged();
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Erro ao enviar arquivo.';
      setUploadError(message);
    } finally {
      setUploading(false);
    }
  };

  // ── Remove ───────────────────────────────────────────────────────────────────

  const handleRemove = async (attachment: Attachment) => {
    setRemovingId(attachment.id);
    try {
      if (transactionId) {
        await removeFromTransaction({ variables: { attachmentId: attachment.id } });
      } else if (holdingId) {
        await removeFromHolding({ variables: { attachmentId: attachment.id } });
      }
      onChanged();
    } catch {
      // silent — parent will see no change
    } finally {
      setRemovingId(null);
    }
  };

  // ── Render ───────────────────────────────────────────────────────────────────

  if (compact) {
    return (
      <>
        <Stack direction="row" spacing={0.5} flexWrap="wrap" alignItems="center">
          {attachments.map((att) => (
            <Chip
              key={att.id}
              icon={<FileTypeIcon mimeType={att.mimeType} />}
              label={att.filename ?? 'Anexo'}
              size="small"
              onClick={() => setPreview(att)}
              onDelete={removingId === att.id ? undefined : () => handleRemove(att)}
              deleteIcon={
                removingId === att.id
                  ? <CircularProgress size={12} />
                  : <CloseIcon fontSize="small" />
              }
              sx={{ maxWidth: 160 }}
            />
          ))}

          {canUpload && (
            <Tooltip title={uploading ? 'Enviando…' : 'Anexar arquivo'}>
              <span>
                <IconButton
                  size="small"
                  onClick={() => fileInputRef.current?.click()}
                  disabled={uploading}
                  sx={{ color: 'text.secondary' }}
                >
                  {uploading ? <CircularProgress size={16} /> : <AttachFileIcon fontSize="small" />}
                </IconButton>
              </span>
            </Tooltip>
          )}
        </Stack>

        {uploadError && (
          <Typography variant="caption" color="error" display="block">
            {uploadError}
          </Typography>
        )}

        <input
          ref={fileInputRef}
          type="file"
          accept={ACCEPTED_TYPES}
          hidden
          onChange={handleFileSelected}
        />

        <FilePreviewDialog attachment={preview} onClose={() => setPreview(null)} />
      </>
    );
  }

  // Full mode
  return (
    <Box>
      {!compact && (
        <Stack direction="row" spacing={1} alignItems="center" mb={attachments.length ? 1 : 0}>
          <Typography variant="caption" color="text.secondary" sx={{ textTransform: 'uppercase', letterSpacing: '0.05em' }}>
            Anexos
          </Typography>
          {canUpload && (
            <Tooltip title={uploading ? 'Enviando…' : `Adicionar arquivo (máx. ${MAX_ATTACHMENTS})`}>
              <span>
                <IconButton
                  size="small"
                  onClick={() => fileInputRef.current?.click()}
                  disabled={uploading}
                  sx={{ color: 'text.secondary', p: 0.25 }}
                >
                  {uploading ? <CircularProgress size={14} /> : <AttachFileIcon sx={{ fontSize: 16 }} />}
                </IconButton>
              </span>
            </Tooltip>
          )}
        </Stack>
      )}

      {attachments.length > 0 && (
        <Stack spacing={0.5}>
          {attachments.map((att) => {
            const isRemoving = removingId === att.id;
            return (
              <Box
                key={att.id}
                sx={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 1,
                  px: 1,
                  py: 0.5,
                  borderRadius: 1,
                  border: 1,
                  borderColor: 'divider',
                  bgcolor: 'background.paper',
                  '&:hover .att-actions': { opacity: 1 },
                }}
              >
                <Box sx={{ color: 'text.secondary', display: 'flex', flexShrink: 0 }}>
                  <FileTypeIcon mimeType={att.mimeType} />
                </Box>

                <Box sx={{ flex: 1, minWidth: 0 }}>
                  <Typography
                    variant="caption"
                    noWrap
                    sx={{ display: 'block', cursor: att.downloadUrl ? 'pointer' : 'default', '&:hover': { textDecoration: att.downloadUrl ? 'underline' : 'none' } }}
                    onClick={() => att.downloadUrl && setPreview(att)}
                  >
                    {att.filename ?? 'Anexo'}
                  </Typography>
                  {att.sizeBytes && (
                    <Typography variant="caption" color="text.disabled" sx={{ display: 'block', lineHeight: 1 }}>
                      {humanSize(att.sizeBytes)}
                    </Typography>
                  )}
                </Box>

                <Stack
                  direction="row"
                  className="att-actions"
                  sx={{ opacity: 0, transition: 'opacity 0.15s', flexShrink: 0 }}
                >
                  {att.downloadUrl && (
                    <Tooltip title="Download">
                      <IconButton
                        size="small"
                        href={att.downloadUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                        sx={{ p: 0.25 }}
                        component="a"
                      >
                        <DownloadIcon sx={{ fontSize: 16 }} />
                      </IconButton>
                    </Tooltip>
                  )}
                  <Tooltip title="Remover">
                    <IconButton
                      size="small"
                      onClick={() => handleRemove(att)}
                      disabled={isRemoving}
                      sx={{ p: 0.25, color: alpha(theme.palette.error.main, 0.7), '&:hover': { color: 'error.main' } }}
                    >
                      {isRemoving ? <CircularProgress size={12} /> : <CloseIcon sx={{ fontSize: 16 }} />}
                    </IconButton>
                  </Tooltip>
                </Stack>
              </Box>
            );
          })}
        </Stack>
      )}

      {uploadError && (
        <Typography variant="caption" color="error" display="block" mt={0.5}>
          {uploadError}
        </Typography>
      )}

      <input
        ref={fileInputRef}
        type="file"
        accept={ACCEPTED_TYPES}
        hidden
        onChange={handleFileSelected}
      />

      <FilePreviewDialog attachment={preview} onClose={() => setPreview(null)} />
    </Box>
  );
}
