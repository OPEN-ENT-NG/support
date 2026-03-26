import { AddAttachments, Flex } from '@edifice.io/react';
import { useMemo, useState } from 'react';
import { type TicketAttachment } from '~/models';
import { uploadAttachment } from '~/services';

interface TicketAttachmentProps {
  onChange: (attachments: TicketAttachment[]) => void;
}

export default function TicketAddAttachment({
  onChange,
}: TicketAttachmentProps) {
  const [attachments, setAttachments] = useState<TicketAttachment[]>([]);
  const [isMutating, setIsMutating] = useState(false);

  const updateAttachments = (updated: TicketAttachment[]) => {
    setAttachments(updated);
    onChange(updated);
  };

  const handleFilesSelected = async (files: File[]) => {
    setIsMutating(true);
    try {
      const uploaded = await Promise.all(files.map(uploadAttachment));
      updateAttachments([...attachments, ...uploaded]);
    } finally {
      setIsMutating(false);
    }
  };

  const handleRemoveAttachment = (attachmentId: string) => {
    updateAttachments(attachments.filter((a) => a.id !== attachmentId));
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
