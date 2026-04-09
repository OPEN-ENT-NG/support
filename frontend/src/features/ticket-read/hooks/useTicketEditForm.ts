import { useToast } from '@edifice.io/react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useRef } from 'react';
import { useForm } from 'react-hook-form';
import { Ticket, TicketApiCode } from '~/models';
import { useI18n } from '~/hooks/usei18n';
import { updateTicket } from '~/services';
import { ticketsQueryKeys } from '~/services/queries/tickets';
import type { TicketEditFormValues } from '../components/TicketEditForm';

export function useTicketEditForm(ticket: Ticket | undefined) {
  const { t } = useI18n();
  const toast = useToast();
  const queryClient = useQueryClient();

  const {
    control,
    formState: { errors },
    handleSubmit,
    reset,
  } = useForm<TicketEditFormValues>({
    mode: 'onTouched',
    defaultValues: {
      school_id: ticket?.school_id ?? '',
      category: ticket?.category ?? '',
      status: ticket ? String(ticket.status) : '',
    },
  });

  useEffect(() => {
    if (ticket) {
      reset({
        school_id: ticket.school_id ?? '',
        category: ticket.category ?? '',
        status: String(ticket.status),
      });
    }
  }, [ticket, reset]);

  const mutation = useMutation({
    mutationFn: (formData: TicketEditFormValues) =>
      updateTicket(ticket!.id, {
        school_id: formData.school_id,
        category: formData.category,
        status: Number(formData.status) as TicketApiCode,
      }),
    onSuccess: () => {
      toast.success(t('support.ticket.update.success'));
      queryClient.invalidateQueries({ queryKey: [ticketsQueryKeys.all()] });
    },
    onError: (error) => {
      console.error('Error updating ticket:', error);
      toast.error(t('support.ticket.update.error'));
    },
  });

  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current);
    };
  }, []);

  const onSubmit = useCallback(() => {
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => {
      handleSubmit((data) => mutation.mutate(data))();
    }, 1000);
  }, [handleSubmit, mutation]);

  return {
    control,
    errors,
    isPending: mutation.isPending,
    onSubmit,
  };
}
