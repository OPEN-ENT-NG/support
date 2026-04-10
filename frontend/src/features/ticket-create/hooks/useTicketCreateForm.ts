import { useToast } from '@edifice.io/react';
import { EditorRef } from '@edifice.io/react/editor';
import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef, useState } from 'react';
import { useForm } from 'react-hook-form';
import { useNavigate } from 'react-router-dom';
import { CreateTicket, TicketAttachment } from '~/models';
import { useI18n } from '~/hooks/usei18n';
import { createTicket, escalateTickets } from '~/services';
import { ticketsQueryKeys } from '~/services/queries/tickets';
import { buildAttachmentsFromEditor } from '~/utils';
import { TicketCreateForm } from '../components/TicketCreateForm';
import { useSchools } from '~/services/queries/schools';

export function useTicketCreateForm() {
  const { t } = useI18n();
  const [isPending, setIsPending] = useState(false);
  const attachmentsRef = useRef<TicketAttachment[]>([]);
  const editorRef = useRef<EditorRef>(null);
  const navigate = useNavigate();
  const toast = useToast();
  const queryClient = useQueryClient();
  const { schools } = useSchools();

  const {
    register,
    setValue,
    reset,
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

  useEffect(() => {
    const school_id = schools.length === 1 ? schools[0].id : '';
    reset((prev) => ({ ...prev, school_id }));
  }, [schools, reset]);

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
        toast.success(t('support.ticket.create.and.escalate.success'));
      } else {
        toast.success(t('support.ticket.create.success'));
      }
      await queryClient.invalidateQueries({
        queryKey: ticketsQueryKeys.lists(),
      });
      navigate(`/tickets/${newTicketResult?.id}`);
    } catch (error) {
      console.error('Error creating ticket:', error);
      toast.error(t('support.ticket.create.error'));
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
