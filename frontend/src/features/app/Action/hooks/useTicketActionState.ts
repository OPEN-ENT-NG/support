import { useParams } from 'react-router-dom';
import { ESCALATION_STATUS, NEW_OR_OPEN_STATUSES } from '~/models/ticket';
import { useTicket } from '~/services/queries/tickets';

export function useTicketActionState() {
  const { ticketId } = useParams();
  const ticket = useTicket(ticketId);

  const isEscalated =
    ticket?.escalation_status === ESCALATION_STATUS.SUCCESSFUL;

  const isNewOrOpen = NEW_OR_OPEN_STATUSES.includes(
    ticket?.status as (typeof NEW_OR_OPEN_STATUSES)[number],
  );

  return { isEscalated, isNewOrOpen };
}
