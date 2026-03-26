import { Avatar, Button, Flex, Loading, useToast } from '@edifice.io/react';
import { Editor, EditorRef } from '@edifice.io/react/editor';
import { IconUndo } from '@edifice.io/react/icons';
import { useMutation } from '@tanstack/react-query';
import { useRef, useState } from 'react';
import TicketAddAttachment from '~/features/ticket-create/components/TicketAttachment';
import { Ticket, TicketAttachment } from '~/models';
import { queryClient } from '~/providers';
import { updateTicket } from '~/services';
import { ticketsQueryKeys } from '~/services/queries';
import { buildAttachmentsFromEditor } from '~/utils';

type TicketCommentFormProps = {
  ticket: Ticket;
  avatarUrl: string;
};

export default function TicketCommentForm({
  ticket,
  avatarUrl,
}: TicketCommentFormProps) {
  const [isEmpty, setIsEmpty] = useState(true);

  const contentRef = useRef('');
  const tiptapRef = useRef<any>(null);
  const editorRef = useRef<EditorRef>(null);
  const uploadedAttachmentsRef = useRef<TicketAttachment[]>([]);
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
      uploadedAttachmentsRef.current = [];
      setIsEmpty(true);
      queryClient.invalidateQueries({ queryKey: [ticketsQueryKeys.all()] });
    },
    onError: (error) => {
      console.error('Error commenting ticket:', error);
      toast.error("Erreur lors de l'envoi du commentaire");
    },
  });

  const handleSubmit = async () => {
    const editorAttachments = await buildAttachmentsFromEditor(editorRef);
    const attachments = [
      ...editorAttachments,
      ...uploadedAttachmentsRef.current,
    ];

    mutation.mutate(attachments);
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
            placeholder="Saisissez votre réponse ici"
            mode="edit"
            visibility="public"
            onContentChange={({ editor }) => {
              tiptapRef.current = editor;
              contentRef.current = editor.getHTML();
              setIsEmpty(editor.isEmpty);
            }}
          />
        </div>
        <TicketAddAttachment
          onChange={(attachments) => {
            uploadedAttachmentsRef.current = attachments;
          }}
        />
        <Flex justify="end">
          <Button
            color="secondary"
            type="button"
            variant="outline"
            leftIcon={<IconUndo />}
            onClick={handleSubmit}
            disabled={isEmpty || mutation.isPending}
          >
            {mutation.isPending ? <Loading isLoading /> : 'Répondre'}
          </Button>
        </Flex>
      </Flex>
    </Flex>
  );
}
