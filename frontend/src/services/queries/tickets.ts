import { useQuery } from '@tanstack/react-query';
import { Ticket, TicketApiCode, TicketType } from '~/models';
import {
  getAttachmentById,
  getBugTrackerIssueById,
  getCommentsById,
  getEventsById,
  getTicketById,
  getTickets,
  getTicketsPerPage,
} from '../api';

/**
 * Allows to generate query keys for tickets-related queries
 */
export const ticketsQueryKeys = {
  all: () => ['tickets'] as const,
  ticketsPerPage: () =>
    [ticketsQueryKeys.all(), 'config', 'ticketsPerPage'] as const,
  byId: (ticketId: string) => [ticketsQueryKeys.all(), ticketId] as const,
  byIdWithComment: (ticketId: string) =>
    [ticketsQueryKeys.all(), ticketId, 'comments'] as const,
  byIdWithEvents: (ticketId: string) =>
    [ticketsQueryKeys.all(), ticketId, 'events'] as const,
  attachmentsById: (ticketId: string) =>
    [ticketsQueryKeys.all(), ticketId, 'attachments'] as const,
  list: (
    page?: number,
    status?: TicketApiCode[],
    type?: TicketType,
    schools?: string[],
  ) =>
    [ticketsQueryKeys.all(), 'list', { page, status, type, schools }] as const,
  bugTrackerIssue: (ticketId: string | undefined) =>
    [ticketsQueryKeys.all(), ticketId, 'bugTrackerIssue'] as const,
};

export const useTickets = (
  page: number,
  status: TicketApiCode[],
  type: TicketType,
  schools: string[],
) => {
  const { data, isPending } = useQuery({
    queryKey: ticketsQueryKeys.list(page, status, type, schools),
    queryFn: () => getTickets(page, status, type, schools),
  });
  return { tickets: (data as Ticket[]) ?? [], isPending };
};

export const useTicket = (ticketId: string | undefined) => {
  const { data } = useQuery({
    queryKey: ticketsQueryKeys.byId(ticketId!),
    queryFn: () => getTicketById(ticketId!),
    select: (data) => data[0] as Ticket,
    enabled: !!ticketId,
  });
  return data;
};

export const useTicketComments = (ticketId: string | undefined) => {
  const { data } = useQuery({
    queryKey: ticketsQueryKeys.byIdWithComment(ticketId!),
    queryFn: () => getCommentsById(ticketId!),
    select: (data) =>
      [...data].sort(
        (a, b) => new Date(a.created).getTime() - new Date(b.created).getTime(),
      ),
    enabled: !!ticketId,
  });
  return data ?? [];
};

export const useTicketEvents = (ticketId: string | undefined) => {
  const { data } = useQuery({
    queryKey: ticketsQueryKeys.byIdWithEvents(ticketId!),
    queryFn: () => getEventsById(ticketId!),
    select: (data) =>
      [...data].sort(
        (a, b) =>
          new Date(a.event_date).getTime() - new Date(b.event_date).getTime(),
      ),
    enabled: !!ticketId,
  });
  return data ?? [];
};

export const useTicketAttachments = (ticketId: string | undefined) => {
  const { data } = useQuery({
    queryKey: ticketsQueryKeys.attachmentsById(ticketId!),
    queryFn: () => getAttachmentById(ticketId!),
    enabled: !!ticketId,
  });
  return data ?? [];
};

export const useBugTrackerIssue = (ticketId: string | undefined) => {
  const { data } = useQuery({
    queryKey: ticketsQueryKeys.bugTrackerIssue(ticketId!),
    queryFn: () => getBugTrackerIssueById(ticketId!),
    enabled: !!ticketId,
  });
  return data;
};

export const useTicketsPerPage = () => {
  const { data, isPending } = useQuery({
    queryKey: ticketsQueryKeys.ticketsPerPage(),
    queryFn: getTicketsPerPage,
    staleTime: Infinity,
  });
  return { ticketsPerPage: data, isPending };
};
