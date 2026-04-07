import { useSchools } from '~/services/queries/schools';
import { useTickets, useTicketsPerPage } from '~/services/queries/tickets';
import EmptyTicketsTable from './components/EmptyTicketsTable';
import { TicketsFilters } from './components/TicketsFilters';
import TicketsTable from './components/TicketsTable';
import { Flex, LoadingScreen, useIsAdmlcOrAdmc } from '@edifice.io/react';
import { Pagination } from 'antd';
import { useCallback, useState } from 'react';
import { TicketFiltersState } from '~/models';
import { TicketsTypeSelector } from './components/TicketsTypeSelector';

export function TicketsList() {
  const { isAdmlcOrAdmc } = useIsAdmlcOrAdmc();

  const [filters, setFilters] = useState<TicketFiltersState>({
    search: '',
    status: [],
    schools: [],
    type: 'all',
  });
  const [page, setPage] = useState(1);
  const { tickets, isPending: ticketsPending } = useTickets(
    page,
    filters.status,
    filters.type,
    filters.schools,
    filters.search,
  );
  const { schools, isPending: schoolsPending } = useSchools();
  const { ticketsPerPage, isPending: ticketsPerPagePending } =
    useTicketsPerPage();

  const totalResults = tickets[0]?.total_results ?? 0;
  const isPending = ticketsPending || schoolsPending || ticketsPerPagePending;
  const isTableEmpty =
    !isPending && (!tickets || tickets.length === 0 || schools.length === 0);

  const handleFiltersChange = useCallback((newFilters: TicketFiltersState) => {
    setFilters(newFilters);

    // Going back to page one when filters change
    setPage(1);
  }, []);

  return (
    <Flex direction="column" gap="12" className="mb-24">
      <TicketsFilters
        schools={schools}
        filters={filters}
        onChange={handleFiltersChange}
      />
      {isAdmlcOrAdmc && (
        <TicketsTypeSelector filters={filters} onChange={handleFiltersChange} />
      )}
      {isPending ? (
        <Flex className="w-100" justify="center" align="center">
          <LoadingScreen />
        </Flex>
      ) : isTableEmpty ? (
        <EmptyTicketsTable />
      ) : (
        <Flex direction="column" gap="4" align="center">
          <TicketsTable tickets={tickets} schools={schools} />
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
