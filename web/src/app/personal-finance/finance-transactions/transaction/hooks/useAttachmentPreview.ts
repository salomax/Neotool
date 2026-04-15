import { useCallback, useState } from 'react';
import type { PreviewAttachment } from '../../../components/FilePreviewDialog';
import { useMutation } from '@apollo/client/react';
import { REMOVE_TRANSACTION_ATTACHMENT } from '@/lib/graphql/operations/banking/mutations';
import { useToast } from '@/shared/providers';
import { extractErrorMessage } from '@/shared/utils/error';

interface UseAttachmentPreviewOptions {
  onSuccess?: () => void;
  beginBusy: () => void;
  endBusy: () => void;
}

export function useAttachmentPreview({ onSuccess, beginBusy, endBusy }: UseAttachmentPreviewOptions) {
  const toast = useToast();
  const [previewAttachments, setPreviewAttachments] = useState<PreviewAttachment[] | null>(null);
  const [previewIndex, setPreviewIndex] = useState<number>(0);
  const [previewTransactionId, setPreviewTransactionId] = useState<string | null>(null);
  const [removingAttachmentId, setRemovingAttachmentId] = useState<string | null>(null);

  const [removeTransactionAttachment] = useMutation(REMOVE_TRANSACTION_ATTACHMENT);

  const openPreview = useCallback((transactionId: string, attachments: PreviewAttachment[]) => {
    const firstWithUrlIndex = attachments.findIndex((a) => !!a.downloadUrl);
    setRemovingAttachmentId(null);
    setPreviewTransactionId(transactionId);
    setPreviewAttachments(attachments);
    setPreviewIndex(firstWithUrlIndex >= 0 ? firstWithUrlIndex : 0);
  }, []);

  const closePreview = useCallback(() => {
    setPreviewAttachments(null);
    setPreviewTransactionId(null);
    setRemovingAttachmentId(null);
  }, []);

  const handleRemoveAttachment = useCallback(
    async (att: PreviewAttachment) => {
      if (!previewTransactionId) return;
      setRemovingAttachmentId(att.id);
      try {
        beginBusy();
        await removeTransactionAttachment({ variables: { attachmentId: att.id } });
        setPreviewAttachments((prev) => {
          if (!prev) return prev;
          const next = prev.filter((a) => a.id !== att.id);
          return next.length > 0 ? next : null;
        });
        onSuccess?.();
      } catch (err) {
        toast.error(extractErrorMessage(err, 'Não foi possível remover o anexo'));
      } finally {
        setRemovingAttachmentId(null);
        endBusy();
      }
    },
    [previewTransactionId, removeTransactionAttachment, beginBusy, endBusy, onSuccess, toast],
  );

  return {
    previewAttachments,
    previewIndex,
    previewTransactionId,
    removingAttachmentId,
    openPreview,
    closePreview,
    handleRemoveAttachment,
  };
}
