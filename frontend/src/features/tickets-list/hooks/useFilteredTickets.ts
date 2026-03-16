import { useMemo } from 'react';
import { Ticket, TicketFiltersState } from '~/models';

export function useFilteredTickets(
  tickets: Ticket[],
  filters: TicketFiltersState,
) {
  return useMemo(
    () =>
      tickets.filter(
        (ticket) =>
          !filters.search ||
          ticket.description
            .toLowerCase()
            .includes(filters.search.toLowerCase()) ||
          ticket.id === Number(filters.search),
      ),
    [tickets, filters.search],
  );
}
