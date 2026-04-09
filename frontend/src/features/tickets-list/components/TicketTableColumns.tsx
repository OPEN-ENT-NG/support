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
import { formaterDate } from '~/utils';

export type TicketTableColumn = {
  id: keyof Ticket;
  header: string;
  sortBy?: SortableTicketField;
  cell?: (ticket: Ticket, school?: School) => React.ReactNode;
};

export const ticketTableColumns: TicketTableColumn[] = [
  { id: 'id', header: 'ID', sortBy: 'id' },
  {
    id: 'status',
    header: 'Statut',
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
    header: 'Sujet',
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
    header: 'Catégorie',
    sortBy: 'category_label',
    cell: (ticket: Ticket) =>
      ticket.category_label ? ticket.category_label : '-',
  },
  {
    id: 'school_id',
    header: 'Structure du demandeur',
    sortBy: 'school_id',
    cell: (_: Ticket, school?: School) => <>{school?.name}</>,
  },
  { id: 'owner_name', header: 'Demandeur', sortBy: 'owner' },
  {
    id: 'profile',
    header: 'Profil',
    cell: (ticket: Ticket) => {
      const profile = mapApiProfileToProfile(
        ticket.profile as ApiProfile,
      )?.toLowerCase();

      const className = `user-profile-${profile}`;

      if (!profile) return <>{ticket.profile ?? ''}</>;
      return (
        <strong>
          <span className={className}>{ticket.profile}</span>
        </strong>
      );
    },
  },
  {
    id: 'modified',
    header: 'Dernière modification',
    sortBy: 'modified',
    cell: (ticket: Ticket) => formaterDate(ticket.modified),
  },
  { id: 'event_count', header: 'Evénements', sortBy: 'event_count' },
  {
    id: 'escalation_date',
    header: 'Escaladé',
    cell: (ticket: Ticket) =>
      ticket.escalation_date ? formaterDate(ticket.escalation_date) : '-',
  },
];
