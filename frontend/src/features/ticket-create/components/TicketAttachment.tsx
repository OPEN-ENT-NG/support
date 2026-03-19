import { AddAttachments, Flex } from '@edifice.io/react';
import { useState } from 'react';
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

  return (
    <Flex style={{ marginLeft: -16 }}>
      <AddAttachments
        attachments={attachments as any}
        editMode
        isMutating={isMutating}
        onFilesSelected={handleFilesSelected}
        onRemoveAttachment={handleRemoveAttachment}
      />
    </Flex>
  );
}
