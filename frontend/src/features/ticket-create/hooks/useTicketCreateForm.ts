import { useToast } from '@edifice.io/react';
import { EditorRef } from '@edifice.io/react/editor';
import { useQueryClient } from '@tanstack/react-query';
import { useRef, useState } from 'react';
import { useForm } from 'react-hook-form';
import { useNavigate } from 'react-router-dom';
import { CreateTicket, TicketAttachment } from '~/models';
import { createTicket, escalateTickets } from '~/services';
import { ticketsQueryKeys } from '~/services/queries/tickets';
import { buildAttachmentsFromEditor } from '~/utils';
import { TicketCreateForm } from '../components/TicketCreateForm';

export function useTicketCreateForm() {
  const [isPending, setIsPending] = useState(false);
  const attachmentsRef = useRef<TicketAttachment[]>([]);
  const editorRef = useRef<EditorRef>(null);
  const navigate = useNavigate();
  const toast = useToast();
  const queryClient = useQueryClient();

  const {
    register,
    setValue,
    formState: { errors, isValid, isDirty },
    handleSubmit,
  } = useForm<TicketCreateForm>({
    mode: 'onTouched',
    defaultValues: {
      category: '',
      school_id: '',
      subject: '',
      description: '<p></p>',
    },
  });

  const handleCreateTicket = async (
    formData: TicketCreateForm,
    escalate = false,
  ) => {
    setIsPending(true);

    const newTicket: CreateTicket = { ...formData };

    const editorAttachments = await buildAttachmentsFromEditor(editorRef);
    const allAttachments = [...editorAttachments, ...attachmentsRef.current];
    if (allAttachments.length > 0) {
      newTicket.attachments = allAttachments;
    }

    try {
      const newTicketResult = await createTicket(newTicket);

      if (escalate && newTicketResult?.id) {
        await escalateTickets([newTicketResult.id.toString()]);
        toast.success('Ticket créé et transmis au support ENT avec succès');
      } else {
        toast.success('Ticket créé avec succès');
      }
      await queryClient.invalidateQueries({
        queryKey: ticketsQueryKeys.list(),
      });
      navigate(`/tickets/${newTicketResult?.id}`);
    } catch (error) {
      console.error('Error creating ticket:', error);
      toast.error('Erreur lors de la création du ticket');
    } finally {
      setIsPending(false);
    }
  };

  return {
    register,
    setValue,
    errors,
    isValid,
    isDirty,
    isPending,
    editorRef,
    attachmentsRef,
    onSubmit: handleSubmit((data) => handleCreateTicket(data)),
    onSubmitAndEscalate: handleSubmit((data) => handleCreateTicket(data, true)),
  };
}
