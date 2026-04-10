import { Badge } from '@edifice.io/react';
import {
  ApiProfile,
  School,
  SortableTicketField,
  Ticket,
  TICKET_STATUS_CLASS,
  getTicketStatusText,
  mapApiProfileToProfile,
} from '~/models';
import { useI18n } from '~/hooks/usei18n';
import { formatDate } from '~/utils';

function ProfileCell({ ticket }: { ticket: Ticket }) {
  const { t } = useI18n();

  const profile = mapApiProfileToProfile(ticket.profile as ApiProfile);
  const className = `user-profile-${profile?.toLowerCase()}`;

  if (!profile) return <>{ticket.profile ?? ''}</>;
  return (
    <strong>
      <span className={className}>{t(profile)}</span>
    </strong>
  );
}

export type TicketTableColumn = {
  id: keyof Ticket;
  headerKey: string;
  sortBy?: SortableTicketField;
  cell?: (ticket: Ticket, school?: School) => React.ReactNode;
};

export const ticketTableColumns: TicketTableColumn[] = [
  { id: 'id', headerKey: 'support.ticket.table.id', sortBy: 'id' },
  {
    id: 'status',
    headerKey: 'support.ticket.table.status',
    sortBy: 'status',
    cell: (ticket: Ticket) => {
      return (
        <Badge
          className={TICKET_STATUS_CLASS[ticket.status]}
          variant={{
            type: 'beta',
          }}
        >
          {getTicketStatusText(ticket.status).charAt(0).toUpperCase() +
            getTicketStatusText(ticket.status).slice(1)}
        </Badge>
      );
    },
  },
  {
    id: 'short_desc',
    headerKey: 'support.ticket.table.subject',
    sortBy: 'subject',
    cell: (ticket: Ticket) => {
      return (
        <strong>
          {ticket.subject.slice(0, 25)}
          {ticket.subject.length > 25 ? '...' : ''}
        </strong>
      );
    },
  },
  {
    id: 'category_label',
    headerKey: 'support.ticket.table.category',
    sortBy: 'category_label',
    cell: (ticket: Ticket) =>
      ticket.category_label ? ticket.category_label : '-',
  },
  {
    id: 'school_id',
    headerKey: 'support.ticket.table.school',
    sortBy: 'school_id',
    cell: (_: Ticket, school?: School) => <>{school?.name}</>,
  },
  {
    id: 'owner_name',
    headerKey: 'support.ticket.table.author',
    sortBy: 'owner',
  },
  {
    id: 'profile',
    headerKey: 'support.ticket.table.profile',
    cell: (ticket: Ticket) => <ProfileCell ticket={ticket} />,
  },
  {
    id: 'modified',
    headerKey: 'support.ticket.table.last.modified',
    sortBy: 'modified',
    cell: (ticket: Ticket) => formatDate(ticket.modified),
  },
  {
    id: 'event_count',
    headerKey: 'support.ticket.table.event.count',
    sortBy: 'event_count',
  },
  {
    id: 'escalation_date',
    headerKey: 'support.ticket.table.last.update.of.escalated.ticket',
    cell: (ticket: Ticket) =>
      ticket.escalation_date ? formatDate(ticket.escalation_date) : '-',
  },
];
