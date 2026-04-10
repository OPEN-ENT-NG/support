import { Table, useIsAdmlcOrAdmc, useToast } from '@edifice.io/react';
import { useI18n } from '~/hooks/usei18n';
import { SortableTicketField, SortOrder } from '~/models';
import {
  IconDownload,
  IconFullScreen,
  IconGroupAvatar,
} from '@edifice.io/react/icons';
import { useQueryClient } from '@tanstack/react-query';
import { useCallback, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { ESCALATION_STATUS, NEW_OR_OPEN_STATUSES, School, Ticket } from '~/models';
import { escalateTickets, exportTickets } from '~/services/api';
import { ticketsQueryKeys } from '~/services/queries/tickets';
import { useCanEscalate } from '~/hooks/useCanEscalate';
import { useMultiSelect } from '~/hooks/useMultiSelect';
import { TicketsTableToolbar, ToolbarAction } from './TicketsTableToolbar';
import { TicketsTableHeader } from './TicketsTableHeader';
import { TicketsTableRow } from './TicketsTableRow';

export type TicketsTableProps = {
  tickets?: Ticket[];
  schools?: School[];
  sortBy: SortableTicketField;
  order: SortOrder;
  onSort: (field: SortableTicketField) => void;
};

function TicketsTable({ tickets = [], schools = [], sortBy, order, onSort }: TicketsTableProps) {
  const navigate = useNavigate();
  const { isAdmlcOrAdmc } = useIsAdmlcOrAdmc();
  const escalateWorkflow = useCanEscalate();
  const { t } = useI18n();
  const toast = useToast();
  const queryClient = useQueryClient();
  const canMultiSelect = isAdmlcOrAdmc || escalateWorkflow;

  const getTicketId = useCallback((ticket: Ticket) => ticket.id, []);
  const {
    selected: selectedTickets,
    selectedIds: selectedTicketIds,
    toggle: handleTicketSelection,
    toggleAll: handleSelectAll,
    clear: clearSelection,
  } = useMultiSelect(tickets, getTicketId);

  const schoolById = useMemo(
    () => new Map(schools.map((s) => [s.id, s])),
    [schools],
  );

  const actions = useMemo<ToolbarAction[]>(
    () => [
      {
        id: 'open',
        label: t('support.ticket.table.open'),
        icon: <IconFullScreen />,
        onClick: () => {
          navigate(`/tickets/${selectedTickets[0].id}`);
        },
        isVisible: selectedTickets.length === 1,
      },
      {
        id: 'export',
        label: t('support.ticket.export.selected'),
        icon: <IconDownload />,
        onClick: () => {
          exportTickets(selectedTickets.map((t) => t.id.toString()));
        },
        isVisible: selectedTickets.length > 0 && escalateWorkflow,
      },
      {
        id: 'transfer',
        label: t('support.ticket.table.transfer'),
        icon: <IconGroupAvatar />,
        onClick: async () => {
          try {
            clearSelection();
            await escalateTickets(selectedTickets.map((t) => t.id.toString()));
            queryClient.invalidateQueries({
              queryKey: ticketsQueryKeys.lists(),
            });
            toast.success(t('support.ticket.table.transfer.success'));
          } catch (error) {
            console.error(error);
            toast.error(t('support.ticket.table.transfer.error'));
          }
        },
        isVisible:
          selectedTickets.length > 0 &&
          selectedTickets.every(
            (t) => t.escalation_status === ESCALATION_STATUS.NOT_DONE,
          ) &&
          selectedTickets.every((t) => (NEW_OR_OPEN_STATUSES as readonly number[]).includes(t.status)) &&
          (escalateWorkflow ?? false),
      },
    ],
    [
      selectedTickets,
      navigate,
      isAdmlcOrAdmc,
      escalateWorkflow,
      toast,
      queryClient,
      clearSelection,
    ],
  );

  return (
    <div className="overflow-x-auto w-100">
      <Table>
        <TicketsTableHeader
          canMultiSelect={canMultiSelect}
          sortBy={sortBy}
          order={order}
          onSort={onSort}
        />
        <Table.Tbody>
          {canMultiSelect && (
            <TicketsTableToolbar
              selectedTickets={selectedTickets}
              totalCount={tickets.length}
              handleSelectAll={handleSelectAll}
              actions={actions}
            />
          )}
          {tickets.map((ticket) => (
            <TicketsTableRow
              key={ticket.id}
              ticket={ticket}
              ticketSelectionCallBack={handleTicketSelection}
              ticketSelected={selectedTicketIds.has(ticket.id)}
              school={schoolById.get(ticket.school_id) as School}
              canMultiSelect={canMultiSelect}
            />
          ))}
        </Table.Tbody>
      </Table>
    </div>
  );
}

export { TicketsTable };
