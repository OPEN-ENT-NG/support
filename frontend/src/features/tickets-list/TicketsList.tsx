import { Flex, LoadingScreen, Pagination, useIsAdmlcOrAdmc } from '@edifice.io/react';
import { EmptyTicketsTable } from './components/EmptyTicketsTable';
import { TicketsFilters } from './components/TicketsFilters';
import { TicketsTable } from './components/TicketsTable';
import { TicketsTypeSelector } from './components/TicketsTypeSelector';
import { useTicketListState } from './hooks/useTicketListState';

export function TicketsList() {
  const { isAdmlcOrAdmc } = useIsAdmlcOrAdmc();
  const {
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
  } = useTicketListState();

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
          <TicketsTable
            tickets={tickets}
            schools={schools}
            sortBy={filters.sortBy}
            order={filters.order}
            onSort={handleSort}
          />
          {ticketsPerPage !== undefined && (
            <Pagination
              className="mt-4 mb-24"
              current={page}
              total={totalResults}
              onChange={setPage}
              pageSize={ticketsPerPage}
            />
          )}
        </Flex>
      )}
    </Flex>
  );
}
