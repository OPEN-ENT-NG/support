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

function StatusCell({ ticket }: { ticket: Ticket }) {
  const { t } = useI18n();
  return (
    <Badge
      className={TICKET_STATUS_CLASS[ticket.status]}
      variant={{ type: 'beta' }}
    >
      {t(getTicketStatusText(ticket.status))}
    </Badge>
  );
}

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
  width?: string;
};

export const ticketTableColumns: TicketTableColumn[] = [
  {
    id: 'id',
    headerKey: 'support.ticket.table.id',
    sortBy: 'id',
    width: '45px',
  },
  {
    id: 'status',
    headerKey: 'support.ticket.table.status',
    sortBy: 'status',
    cell: (ticket: Ticket) => <StatusCell ticket={ticket} />,
    width: '150px',
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
    width: '140px',
  },
  {
    id: 'category_label',
    headerKey: 'support.ticket.table.category',
    sortBy: 'category_label',
    cell: (ticket: Ticket) =>
      ticket.category_label ? ticket.category_label : '-',
    width: '92px',
  },
  {
    id: 'school_id',
    headerKey: 'support.ticket.table.school',
    sortBy: 'school_id',
    cell: (_: Ticket, school?: School) => <>{school?.name}</>,
    width: '110px',
  },
  {
    id: 'owner_name',
    headerKey: 'support.ticket.table.author',
    sortBy: 'owner',
    width: '110px',
  },
  {
    id: 'profile',
    headerKey: 'support.ticket.table.profile',
    cell: (ticket: Ticket) => <ProfileCell ticket={ticket} />,
    width: '75px',
  },
  {
    id: 'modified',
    headerKey: 'support.ticket.table.last.modified',
    sortBy: 'modified',
    cell: (ticket: Ticket) => formatDate(ticket.modified),
    width: '110px',
  },
  {
    id: 'event_count',
    headerKey: 'support.ticket.table.event.count',
    sortBy: 'event_count',
    width: '70px',
  },
  {
    id: 'escalation_date',
    headerKey: 'support.ticket.table.last.update.of.escalated.ticket',
    sortBy: 'escalation_date',
    cell: (ticket: Ticket) =>
      ticket.escalation_date ? formatDate(ticket.escalation_date) : '-',
    width: '90px',
  },
];
