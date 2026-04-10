import { AddAttachments, Flex, useToast } from '@edifice.io/react';
import { useMemo, useState } from 'react';
import { type TicketAttachment } from '~/models';
import { useI18n } from '~/hooks/usei18n';
import { uploadAttachment } from '~/services';
import { isFileTooLarge } from '~/utils';
import { Attachment } from '~/models/attachment';

interface TicketAttachmentProps {
  onChange: (updater: (prev: TicketAttachment[]) => TicketAttachment[]) => void;
  attachments: TicketAttachment[];
}

export function TicketAddAttachment({
  onChange,
  attachments,
}: TicketAttachmentProps) {
  const { t } = useI18n();
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
        toast.error(t('support.ticket.attachment.upload.error.too.large'));
      } else {
        toast.error(t('support.ticket.attachment.upload.error'));
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
      })) as Attachment[],
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
