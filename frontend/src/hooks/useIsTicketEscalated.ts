import { useParams } from 'react-router-dom';
import { ESCALATION_STATUS } from '~/models/ticket';
import { useTicket } from '~/services/queries/tickets';

export function useIsTicketEscalated(): boolean {
  const { ticketId } = useParams();
  const ticket = useTicket(ticketId);

  return ticket?.escalation_status === ESCALATION_STATUS.SUCCESSFUL;
}
