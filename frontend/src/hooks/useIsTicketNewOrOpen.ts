import { useParams } from 'react-router-dom';
import { useTicket } from '~/services/queries/tickets';

export function useIsTicketNewOrOpen(): boolean {
  const { ticketId } = useParams();
  const ticket = useTicket(ticketId);

  return ticket?.status === 1 || ticket?.status === 2;
}
