import { odeServices } from '@edifice.io/client';
import { useToast } from '@edifice.io/react';
import { useState } from 'react';

export type AttachmentToCopy = {
  documentId: string;
  name: string;
  origin: 'workspace' | 'gridfs';
};

function isFileTooLarge(e: unknown): boolean {
  return (
    typeof e === 'object' && e !== null && (e as any).error === 'file.too.large'
  );
}

export function useCopyToWorkspace(ticketId: number) {
  const toast = useToast();
  const [isCopying, setIsCopying] = useState(false);

  async function copyToWorkspace(attachment: AttachmentToCopy) {
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

      await odeServices.workspace().saveFile(file);
      toast.success('Pièce jointe copiée dans votre espace documentaire.');
    } catch (error) {
      if (isFileTooLarge(error)) {
        toast.error(
          "Erreur lors de la copie dans l'espace documentaire : espace disponible insuffisant.",
        );
      } else {
        toast.error("Erreur lors de la copie dans l'espace documentaire.");
      }
    } finally {
      setIsCopying(false);
    }
  }

  return { isCopying, copyToWorkspace };
}
