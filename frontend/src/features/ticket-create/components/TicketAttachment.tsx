import { AddAttachments, Flex, useToast } from '@edifice.io/react';
import { useMemo, useState } from 'react';
import { type TicketAttachment } from '~/models';
import { uploadAttachment } from '~/services';

interface TicketAttachmentProps {
  onChange: (updater: (prev: TicketAttachment[]) => TicketAttachment[]) => void;
  attachments: TicketAttachment[];
}

function isFileTooLarge(e: unknown): boolean {
  return (
    typeof e === 'object' && e !== null && (e as any).error === 'file.too.large'
  );
}

export default function TicketAddAttachment({
  onChange,
  attachments,
}: TicketAttachmentProps) {
  const [isMutating, setIsMutating] = useState(false);
  const toast = useToast();

  const handleFilesSelected = async (files: File[]) => {
    setIsMutating(true);
    try {
      const uploaded = await Promise.all(files.map(uploadAttachment));
      onChange((prev) => [...prev, ...uploaded]);
    } catch (error) {
      console.error('Error uploading attachment:', error);
      if (isFileTooLarge(error)) {
        toast.error(
          'Erreur lors du téléchargement de la pièce jointe : espace disponible insuffisant.',
        );
      } else {
        toast.error('Erreur lors du téléchargement de la pièce jointe.');
      }
    } finally {
      setIsMutating(false);
    }
  };

  const handleRemoveAttachment = (attachmentId: string) => {
    onChange((prev) => prev.filter((a) => a.id !== attachmentId));
  };

  const displayedAttachments = useMemo(
    () =>
      attachments.map((a) => ({
        ...a,
        filename: a.name,
        charset: 'UTF-8',
        contentTransferEncoding: 'binary',
        contentType: 'application/octet-stream',
      })),
    [attachments],
  );

  return (
    <Flex className="ms-n16">
      <AddAttachments
        attachments={displayedAttachments}
        editMode
        isMutating={isMutating}
        onFilesSelected={handleFilesSelected}
        onRemoveAttachment={handleRemoveAttachment}
      />
    </Flex>
  );
}
