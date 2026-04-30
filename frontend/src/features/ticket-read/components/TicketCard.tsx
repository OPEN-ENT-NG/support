import {
  Attachment as AttachmentComponent,
  Flex,
  IconButton,
} from '@edifice.io/react';
import { useMemo, useState } from 'react';
import { Editor } from '@edifice.io/react/editor';
import { IconDownload, IconFolderAdd } from '@edifice.io/react/icons';
import {
  ApiAttachment,
  BugTrackerIssue,
  Ticket,
  TicketCardAttachment,
} from '~/models/ticket';
import { useI18n } from '~/hooks/usei18n';
import { TicketDetailsHeader } from './TicketDetailsHeader';
import { useCopyToWorkspace } from '../hooks/useCopyToWorkspace';
import { AddAttachmentToWorkspaceModal } from './AddAttachmentToWorkspaceModal';

export interface TicketCardProps {
  ticket: Ticket;
  attachments: ApiAttachment[];
  bugTrackerIssue?: BugTrackerIssue;
}

export function TicketCard({
  ticket,
  attachments,
  bugTrackerIssue,
}: TicketCardProps) {
  const { t } = useI18n();
  const { copyToWorkspace } = useCopyToWorkspace(ticket.id);
  const [attachmentToSave, setAttachmentToSave] =
    useState<TicketCardAttachment | null>(null);

  const handleDownload = (
    attachmentId: string,
    origin: 'workspace' | 'gridfs' = 'workspace',
  ) => {
    if (origin === 'gridfs') {
      window.open(`/support/gridfs/${ticket.id}/${attachmentId}`, '_blank');
    } else {
      window.open(`/workspace/document/${attachmentId}`, '_blank');
    }
  };

  const handleSaveToWorkspace = (attachment: TicketCardAttachment) => {
    setAttachmentToSave(attachment);
  };

  const handleCopyToWorkspace = async (folderId: string): Promise<boolean> => {
    if (!attachmentToSave) return false;
    return copyToWorkspace(
      {
        documentId: attachmentToSave.document_id,
        name: attachmentToSave.name,
        origin: attachmentToSave.origin,
      },
      folderId,
    );
  };

  const allAttachments = useMemo<TicketCardAttachment[]>(
    () => [
      ...attachments.map((attachment) => ({
        document_id: attachment.document_id,
        name: attachment.name,
        size: attachment.size,
        created: attachment.created,
        origin: 'workspace' as const,
      })),
      ...(bugTrackerIssue?.attachments
        ?.filter((attachment) => attachment.gridfs_id !== null) // Filter out attachments without gridfs_id to keep only attachments coming from Zendesk
        .map((attachment) => ({
          document_id: attachment.gridfs_id,
          name: attachment.filename,
          size: attachment.size,
          created: attachment.created_on,
          origin: 'gridfs' as const,
        })) ?? []),
    ],
    [attachments, bugTrackerIssue?.attachments],
  );

  return (
    <>
      {attachmentToSave && (
        <AddAttachmentToWorkspaceModal
          isOpen={true}
          onModalClose={() => setAttachmentToSave(null)}
          onCopyToWorkspace={handleCopyToWorkspace}
        />
      )}
      <Flex
        direction="column"
        className="pt-24 pb-24 ps-24 pe-16 border-bottom-light"
        gap="16"
      >
        <TicketDetailsHeader ticket={ticket} />
        <Flex direction="column" className="ps-48" gap="8">
          <Editor mode="read" variant="ghost" content={ticket.description} />

          {allAttachments.length > 0 && (
            <Flex
              direction="column"
              className="pt-8 pb-8 ps-12 pe-12 rounded-4 align-self-start ticket-attachment-box"
              gap="8"
            >
              <p className="caption mt-8 pb-8 border-bottom-light">
                <strong>{t('support.ticket.read.attachments.title')}</strong>
              </p>
              {allAttachments.map((attachment) => (
                <AttachmentComponent
                  key={attachment.document_id}
                  name={attachment.name}
                  options={
                    <>
                      <IconButton
                        variant="ghost"
                        color="tertiary"
                        type="button"
                        icon={<IconFolderAdd />}
                        onClick={() => handleSaveToWorkspace(attachment)}
                      />
                      <IconButton
                        variant="ghost"
                        color="tertiary"
                        type="button"
                        icon={<IconDownload />}
                        onClick={() =>
                          handleDownload(
                            attachment.document_id,
                            attachment.origin,
                          )
                        }
                      />
                    </>
                  }
                />
              ))}
            </Flex>
          )}
        </Flex>
      </Flex>
    </>
  );
}
