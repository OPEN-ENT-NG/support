import { useSchools } from '~/services/queries/schools';
import { useTickets, useTicketsPerPage } from '~/services/queries/tickets';
import EmptyTicketsTable from './components/EmptyTicketsTable';
import { TicketsFilters } from './components/TicketsFilters';
import TicketsTable from './components/TicketsTable';
import { Flex, LoadingScreen } from '@edifice.io/react';
import { Pagination } from 'antd';
import { useCallback, useState } from 'react';
import { TicketFiltersState } from '~/models';
import { useFilteredTickets } from './hooks/useFilteredTickets';
import { TicketsTypeSelector } from './components/TicketsTypeSelector';

export function TicketsList() {
  const [filters, setFilters] = useState<TicketFiltersState>({
    search: '',
    status: [1, 2, 3, 4, 5],
    schools: [],
    type: 'all',
  });
  const [page, setPage] = useState(1);
  const { tickets, isPending: ticketsPending } = useTickets(
    page,
    filters.status,
    filters.type,
    filters.schools,
  );
  const { schools, isPending: schoolsPending } = useSchools();
  const { ticketsPerPage, isPending: ticketsPerPagePending } =
    useTicketsPerPage();

  const filteredTickets = useFilteredTickets(tickets, filters);
  const totalResults = tickets[0]?.total_results ?? 0;
  const isPending = ticketsPending || schoolsPending || ticketsPerPagePending;
  const isTableEmpty =
    !isPending &&
    (!filteredTickets || filteredTickets.length === 0 || schools.length === 0);

  const handleFiltersChange = useCallback((newFilters: TicketFiltersState) => {
    setFilters(newFilters);

    // Going back to page one when filters change
    setPage(1);
  }, []);

  return (
    <Flex direction="column" gap="12">
      <TicketsFilters
        schools={schools}
        filters={filters}
        onChange={handleFiltersChange}
      />
      <TicketsTypeSelector filters={filters} onChange={handleFiltersChange} />
      {isPending ? (
        <Flex className="w-100" justify="center" align="center">
          <LoadingScreen />
        </Flex>
      ) : isTableEmpty ? (
        <EmptyTicketsTable />
      ) : (
        <Flex direction="column" gap="4" align="center">
          <TicketsTable tickets={filteredTickets} schools={schools} />
          {ticketsPerPage !== undefined &&
            totalResults / ticketsPerPage > 1 && (
              <Pagination
                align="center"
                className="mt-4 mb-24"
                current={page}
                total={totalResults}
                onChange={setPage}
                showSizeChanger={false}
                pageSize={ticketsPerPage}
              />
            )}
        </Flex>
      )}
    </Flex>
  );
}
