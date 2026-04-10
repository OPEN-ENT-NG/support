import { odeServices } from '@edifice.io/client';
import { useToast } from '@edifice.io/react';
import { useState } from 'react';
import { useI18n } from '~/hooks/usei18n';
import { isFileTooLarge } from '~/utils';

export type AttachmentToCopy = {
  documentId: string;
  name: string;
  origin: 'workspace' | 'gridfs';
};

export function useCopyToWorkspace(ticketId: number) {
  const { t } = useI18n();
  const toast = useToast();
  const [isCopying, setIsCopying] = useState(false);

  async function copyToWorkspace(
    attachment: AttachmentToCopy,
    parentId?: string,
  ): Promise<boolean> {
    setIsCopying(true);
    try {
      const url =
        attachment.origin === 'gridfs'
          ? `/support/gridfs/${ticketId}/${attachment.documentId}`
          : `/workspace/document/${attachment.documentId}`;

      const response = await fetch(url);
      if (!response.ok) throw new Error('Download failed');

      const blob = await response.blob();
      const file = new File([blob], attachment.name, { type: blob.type });

      await odeServices
        .workspace()
        .saveFile(file, parentId ? { parentId } : undefined);
      toast.success(t('support.workspace.copy.success'));
      return true;
    } catch (error) {
      if (isFileTooLarge(error)) {
        toast.error(t('support.workspace.copy.error.too.large'));
      } else {
        toast.error(t('support.workspace.copy.error'));
      }
      return false;
    } finally {
      setIsCopying(false);
    }
  }

  return { isCopying, copyToWorkspace };
}
