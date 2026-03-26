import { Attachment, Flex, IconButton } from '@edifice.io/react';
import { useMemo } from 'react';
import { Editor } from '@edifice.io/react/editor';
import { IconDownload, IconFolderAdd } from '@edifice.io/react/icons';
import { ApiAttachment, BugTrackerIssue, Ticket } from '~/models/ticket';
import { TicketDetailsHeader } from './TicketDetailsHeader';

export interface TicketCardProps {
  ticket: Ticket;
  attachments: ApiAttachment[];
  bugTrackerIssue?: BugTrackerIssue;
}

type Attachment = {
  document_id: string;
  name: string;
  size: number;
  created: string;
  origin: 'workspace' | 'gridfs';
};

export function TicketCard({
  ticket,
  attachments,
  bugTrackerIssue,
}: TicketCardProps) {
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

  const handleSaveToWorkspace = (attachmentId: string) => {
    console.log('Saving attachment to workspace:', attachmentId);
  };

  const allAttachments = useMemo<Attachment[]>(
    () => [
      ...attachments.map((attachment) => ({
        document_id: attachment.document_id,
        name: attachment.name,
        size: attachment.size,
        created: attachment.created,
        origin: 'workspace' as const,
      })),
      ...(bugTrackerIssue?.attachments?.map((attachment) => ({
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
              <strong>Pièces jointes</strong>
            </p>
            {allAttachments.map((attachment) => (
              <Attachment
                key={attachment.document_id}
                name={attachment.name}
                options={
                  <>
                    <IconButton
                      variant="ghost"
                      color="tertiary"
                      type="button"
                      icon={<IconFolderAdd />}
                      onClick={() =>
                        handleSaveToWorkspace(attachment.document_id)
                      }
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
  );
}
