'use client';

import React from 'react';
import {
  Box,
  Typography,
} from '@mui/material';
import { gql } from '@apollo/client';
import { useApolloClient } from '@apollo/client/react';
import InsertDriveFileIcon from '@mui/icons-material/InsertDriveFile';
import { Button } from '@/shared/components/ui/primitives/Button';
import { Drawer } from '@/shared/components/ui/layout/Drawer';
import { ConfirmationDialog } from '@/shared/components/ui/feedback';

export interface PreviewAttachment {
  id: string;
  assetId?: string | null;
  filename?: string | null;
  mimeType?: string | null;
  downloadUrl?: string | null;
}

interface FilePreviewDialogProps {
  attachments: PreviewAttachment[] | null;
  initialIndex?: number;
  onClose: () => void;
  onRemove?: (attachment: PreviewAttachment) => void | Promise<void>;
  removingAttachmentId?: string | null;
}

function isImage(mimeType?: string | null): boolean {
  return !!mimeType && mimeType.startsWith('image/');
}

function isPdf(mimeType?: string | null): boolean {
  return mimeType === 'application/pdf';
}

const ASSET_PREVIEW = gql`
  query AssetPreview($id: ID!, $ttlSeconds: Int) {
    asset(id: $id) {
      id
      status
      originalFilename
      mimeType
      sizeBytes
      downloadUrl(ttlSeconds: $ttlSeconds)
    }
  }
`;

type AssetPreviewQuery = {
  asset: {
    id: string;
    status: string;
    originalFilename: string | null;
    mimeType: string | null;
    sizeBytes: number | null;
    downloadUrl: string | null;
  } | null;
};

export function FilePreviewDialog({
  attachments,
  initialIndex = 0,
  onClose,
  onRemove,
  removingAttachmentId,
}: FilePreviewDialogProps) {
  const apolloClient = useApolloClient();
  const [textContent, setTextContent] = React.useState<string | null>(null);
  const [textError, setTextError] = React.useState<string | null>(null);
  const [index, setIndex] = React.useState<number>(initialIndex);
  const [confirmRemoveOpen, setConfirmRemoveOpen] = React.useState(false);
  const [resolvedByAssetId, setResolvedByAssetId] = React.useState<
    Record<string, { filename: string | null; mimeType: string | null; downloadUrl: string | null }>
  >({});
  const [resolvingAssetId, setResolvingAssetId] = React.useState<string | null>(null);
  const open = Boolean(attachments?.length);
  const current = open && attachments ? attachments[Math.min(Math.max(index, 0), attachments.length - 1)] : null;
  const assetId = current?.assetId ?? null;
  const resolved = assetId ? resolvedByAssetId[assetId] : undefined;
  const effective = current && resolved ? { ...current, ...resolved } : current;

  const url = effective?.downloadUrl ?? undefined;
  const name = effective?.filename ?? 'Arquivo';
  const mime = effective?.mimeType;
  const removeLoading = Boolean(removingAttachmentId && removingAttachmentId === effective?.id);

  const isText = !!mime && mime.startsWith('text/');
  const canPreview = isImage(mime) || isPdf(mime) || isText;

  React.useEffect(() => {
    if (!open) return;
    setIndex(initialIndex);
  }, [open, initialIndex]);

  React.useEffect(() => {
    if (open) return;
    setConfirmRemoveOpen(false);
  }, [open]);

  React.useEffect(() => {
    if (!open || !attachments?.length) return;
    setIndex((prev) => Math.min(Math.max(prev, 0), attachments.length - 1));
  }, [open, attachments?.length]);

  React.useEffect(() => {
    if (!open || !assetId) return;
    if (url) return;
    if (resolvedByAssetId[assetId]) return;

    let cancelled = false;
    setResolvingAssetId(assetId);
    apolloClient
      .query<AssetPreviewQuery>({
        query: ASSET_PREVIEW,
        variables: { id: assetId, ttlSeconds: 3600 },
        fetchPolicy: 'network-only',
      })
      .then((result) => {
        if (cancelled) return;
        const asset = result.data?.asset;
        if (!asset) return;
        setResolvedByAssetId((prev) => ({
          ...prev,
          [assetId]: {
            filename: asset.originalFilename,
            mimeType: asset.mimeType,
            downloadUrl: asset.downloadUrl,
          },
        }));
      })
      .finally(() => {
        if (!cancelled) setResolvingAssetId(null);
      });

    return () => {
      cancelled = true;
    };
  }, [apolloClient, assetId, open, resolvedByAssetId, url]);

  React.useEffect(() => {
    setTextContent(null);
    setTextError(null);
    if (!open || !url || !isText) return;
    const fetchText = async () => {
      try {
        const res = await fetch(url);
        const txt = await res.text();
        setTextContent(txt.length > 200000 ? txt.slice(0, 200000) : txt);
      } catch {
        setTextError('Não foi possível carregar o conteúdo de texto.');
      }
    };
    fetchText();
  }, [open, url, isText]);

  return (
    <>
      <Drawer open={open} onClose={onClose} anchor="right" size="lg">
        <Drawer.Header title={name} showCloseButton />

        <Drawer.Body sx={{ p: 0, overflow: 'hidden' }}>
          <Box sx={{ height: '100%', minHeight: 0, display: 'flex', flexDirection: 'column' }}>
            <Box sx={{ flex: 1, minHeight: 0, width: '100%', display: 'flex' }}>
              {!effective ? null : isImage(mime) && url ? (
                <Box
                  sx={{
                    flex: 1,
                    minHeight: 0,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                  }}
                >
                  <Box component="img" src={url} alt={name} sx={{ width: '100%', height: '100%', objectFit: 'contain', display: 'block' }} />
                </Box>
              ) : isPdf(mime) && url ? (
                <Box
                  component="object"
                  data={url}
                  title={name}
                  sx={{ width: '100%', height: '100%', border: 'none' }}
                >
                  <Box sx={{ textAlign: 'center', p: 4 }}>
                    <Typography variant="body2" color="text.secondary">
                      Pré-visualização não disponível. Abra o arquivo para visualizar.
                    </Typography>
                  </Box>
                </Box>
              ) : isText && url ? (
                <Box sx={{ width: '100%', height: '100%', overflow: 'auto', p: 2 }}>
                  {textError ? (
                    <Typography variant="body2" color="text.secondary">
                      {textError}
                    </Typography>
                  ) : textContent !== null ? (
                    <Box
                      component="pre"
                      sx={{
                        m: 0,
                        fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace',
                        whiteSpace: 'pre-wrap',
                        wordWrap: 'break-word',
                      }}
                    >
                      {textContent}
                    </Box>
                  ) : (
                    <Typography variant="body2" color="text.secondary">
                      Carregando conteúdo…
                    </Typography>
                  )}
                </Box>
              ) : resolvingAssetId && assetId === resolvingAssetId ? (
                <Box sx={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', p: 4 }}>
                  <Typography variant="body2" color="text.secondary">
                    Carregando arquivo…
                  </Typography>
                </Box>
              ) : (
                <Box sx={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', p: canPreview ? 0 : 4 }}>
                  <Box sx={{ textAlign: 'center' }}>
                    <InsertDriveFileIcon sx={{ fontSize: 64, color: 'text.disabled', mb: 2 }} />
                    <Typography variant="body2" color="text.secondary">
                      Pré-visualização não disponível para este tipo de arquivo.
                    </Typography>
                    <Typography variant="caption" color="text.disabled">
                      {mime ?? 'Tipo desconhecido'}
                    </Typography>
                  </Box>
                </Box>
              )}
            </Box>
          </Box>
        </Drawer.Body>

        <Drawer.Footer>
          <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 2 }}>
            <Box sx={{ display: 'flex', gap: 1 }}>
              <Button
                variant="text"
                onClick={() => setIndex((i) => Math.max(i - 1, 0))}
                disabled={!attachments || attachments.length <= 1 || index === 0}
              >
                Anterior
              </Button>
              <Button
                variant="text"
                onClick={() => setIndex((i) => Math.min(i + 1, (attachments?.length ?? 1) - 1))}
                disabled={!attachments || attachments.length <= 1 || index >= (attachments?.length ?? 1) - 1}
              >
                Próximo
              </Button>
            </Box>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              {onRemove ? (
                <Button
                  variant="text"
                  color="error"
                  onClick={() => {
                    if (!effective || removeLoading) return;
                    setConfirmRemoveOpen(true);
                  }}
                  disabled={!effective || removeLoading}
                >
                  Remover
                </Button>
              ) : null}
              <Drawer.ActionButtons
                cancelText="Fechar"
                submitText="Download"
                onCancel={onClose}
                onSubmit={() => {
                  if (url) {
                    window.open(url, '_blank', 'noopener,noreferrer');
                  }
                }}
                submitDisabled={!url}
              />
            </Box>
          </Box>
        </Drawer.Footer>
      </Drawer>

      <ConfirmationDialog
        open={confirmRemoveOpen}
        onClose={() => {
          if (removeLoading) return;
          setConfirmRemoveOpen(false);
        }}
        onConfirm={() => {
          if (!onRemove || !effective || removeLoading) return;
          void (async () => {
            await onRemove(effective);
            setConfirmRemoveOpen(false);
          })();
        }}
        title="Remover arquivo?"
        message={`Tem certeza que deseja remover "${name}"?`}
        confirmText="Remover"
        cancelText="Cancelar"
        confirmColor="error"
        loading={removeLoading}
        disabled={!effective}
      />
    </>
  );
}
