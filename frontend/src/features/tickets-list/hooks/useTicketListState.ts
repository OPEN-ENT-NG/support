import { useCallback, useState } from 'react';
import { SortableTicketField, TicketFiltersState } from '~/models';
import { useSchools } from '~/services/queries/schools';
import { useTickets, useTicketsPerPage } from '~/services/queries/tickets';

const DEFAULT_FILTERS: TicketFiltersState = {
  search: '',
  status: [],
  schools: [],
  type: 'all',
  sortBy: 'modified',
  order: 'DESC',
};

export function useTicketListState() {
  const [filters, setFilters] = useState<TicketFiltersState>(DEFAULT_FILTERS);
  const [page, setPage] = useState(1);

  const { tickets, isPending: ticketsPending } = useTickets(
    page,
    filters.status,
    filters.type,
    filters.schools,
    filters.search,
    filters.sortBy,
    filters.order,
  );
  const { schools, isPending: schoolsPending } = useSchools();
  const { ticketsPerPage, isPending: ticketsPerPagePending } = useTicketsPerPage();

  const totalResults = tickets[0]?.total_results ?? 0;
  const isPending = ticketsPending || schoolsPending || ticketsPerPagePending;
  const isTableEmpty =
    !isPending && (!tickets || tickets.length === 0 || schools.length === 0);

  const handleSort = useCallback((field: SortableTicketField) => {
    setFilters((prev) => ({
      ...prev,
      sortBy: field,
      order: prev.sortBy === field && prev.order === 'DESC' ? 'ASC' : 'DESC',
    }));
    setPage(1);
  }, []);

  const handleFiltersChange = useCallback((newFilters: TicketFiltersState) => {
    setFilters(newFilters);
    setPage(1);
  }, []);

  return {
    filters,
    page,
    setPage,
    tickets,
    schools,
    ticketsPerPage,
    totalResults,
    isPending,
    isTableEmpty,
    handleSort,
    handleFiltersChange,
  };
}
