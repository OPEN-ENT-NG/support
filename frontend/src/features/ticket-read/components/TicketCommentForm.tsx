import { Avatar, Button, Flex, Loading, useToast } from '@edifice.io/react';
import { Editor, EditorRef } from '@edifice.io/react/editor';
import { IconUndo } from '@edifice.io/react/icons';
import { useMutation } from '@tanstack/react-query';
import { useRef, useState } from 'react';
import { Ticket, TicketAttachment } from '~/models';
import { useI18n } from '~/hooks/usei18n';
import { queryClient } from '~/services/queryClient';
import { updateTicket } from '~/services';
import { ticketsQueryKeys } from '~/services/queries';
import { buildAttachmentsFromEditor } from '~/utils';

type TiptapEditor = { commands: { clearContent: () => void } };

type TicketCommentFormProps = {
  ticket: Ticket;
  avatarUrl: string;
};

export function TicketCommentForm({
  ticket,
  avatarUrl,
}: TicketCommentFormProps) {
  const { t } = useI18n();
  const [isEmpty, setIsEmpty] = useState(true);

  const contentRef = useRef('');
  const tiptapRef = useRef<TiptapEditor | null>(null);
  const editorRef = useRef<EditorRef>(null);
  const toast = useToast();

  const mutation = useMutation({
    mutationFn: (attachments: TicketAttachment[]) =>
      updateTicket(ticket!.id, {
        newComment: contentRef.current,
        attachments,
      }),
    onSuccess: () => {
      contentRef.current = '';
      tiptapRef.current?.commands.clearContent();
      setIsEmpty(true);
      queryClient.invalidateQueries({
        queryKey: ticketsQueryKeys.byId(String(ticket.id)),
      });
      queryClient.invalidateQueries({
        queryKey: ticketsQueryKeys.byIdWithComment(String(ticket.id)),
      });
    },
    onError: (error) => {
      console.error('Error commenting ticket:', error);
      toast.error(t('support.ticket.read.comment.error'));
    },
  });

  const handleSubmit = async () => {
    const editorAttachments = await buildAttachmentsFromEditor(editorRef);
    mutation.mutate(editorAttachments);
  };

  return (
    <Flex gap="16" className="pt-24 pb-24 ps-24 pe-24">
      <Avatar
        alt={ticket.owner_name}
        src={avatarUrl}
        variant="circle"
        size="md"
      />
      <Flex className="mb-48 flex-grow-1 min-w-0" direction="column" gap="16">
        <div className="editor-container">
          <Editor
            ref={editorRef}
            content=""
            placeholder={t('support.ticket.read.comment.placeholder')}
            mode="edit"
            visibility="public"
            onContentChange={({ editor }) => {
              tiptapRef.current = editor;
              contentRef.current = editor.getHTML();
              setIsEmpty(editor.isEmpty);
            }}
          />
        </div>
        <Flex justify="end">
          <Button
            color="secondary"
            type="button"
            variant="outline"
            leftIcon={<IconUndo />}
            onClick={handleSubmit}
            disabled={isEmpty || mutation.isPending}
          >
            {mutation.isPending ? (
              <Loading isLoading />
            ) : (
              t('support.ticket.read.comment.reply')
            )}
          </Button>
        </Flex>
      </Flex>
    </Flex>
  );
}
