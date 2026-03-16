/* Import Table component from Edifice UI library */
import { Badge, Checkbox, Table, useIsAdml, useToast } from '@edifice.io/react';
import {
  IconDownload,
  IconFullScreen,
  IconGroupAvatar,
} from '@edifice.io/react/icons';
import { useQueryClient } from '@tanstack/react-query';
import { memo, useCallback, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  ApiProfile,
  ESCALATION_STATUS,
  School,
  Ticket,
  TicketStatusColors,
  getTicketStatusText,
  mapApiProfileToProfile,
} from '~/models';
import { escalateTickets, exportTickets } from '~/services/api';
import { ticketsQueryKeys } from '~/services/queries/tickets';
import { formaterDate } from '~/utils';
import { TicketsTableToolbar, ToolbarAction } from './TicketsTableToolbar';
import { useCanEscalate } from '~/hooks/useCanEscalate';

interface Column {
  header: string;
  accessorKey: keyof Ticket;
}

const columns: Column[] = [
  { header: 'ID', accessorKey: 'id' },
  { header: 'Statut', accessorKey: 'status' },
  { header: 'Sujet', accessorKey: 'short_desc' },
  { header: 'Catégorie', accessorKey: 'category_label' },
  { header: 'Structure du demandeur', accessorKey: 'school_id' },
  { header: 'Demandeur', accessorKey: 'owner_name' },
  { header: 'Profil', accessorKey: 'profile' },
  { header: 'Dernière modification', accessorKey: 'modified' },
  { header: 'Evénements', accessorKey: 'event_count' },
  { header: 'Escaladé', accessorKey: 'escalation_date' },
];

type TicketRowProps = {
  ticket: Ticket;
  school: School;
  ticketSelectionCallBack: (ticket: Ticket) => void;
  ticketSelected: boolean;
};

type TicketTableColumn = {
  id: keyof Ticket;
  header: string;
  cell?: (ticket: Ticket, school?: School) => React.ReactNode;
};

const ticketTableColumns: TicketTableColumn[] = [
  { id: 'id', header: 'ID' },
  {
    id: 'status',
    header: 'Statut',
    cell: (ticket: Ticket) => {
      return (
        <Badge
          variant={{
            color: TicketStatusColors[ticket.status],
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
    cell: (ticket: Ticket) => {
      return (
        <>
          <strong>
            {ticket.subject.length > 25
              ? ticket.subject.substring(0, 25) + '...'
              : ticket.subject}
          </strong>
        </>
      );
    },
  },
  {
    id: 'category_label',
    header: 'Catégorie',
    cell: (ticket: Ticket) =>
      ticket.category_label ? ticket.category_label : '-',
  },
  {
    id: 'school_id',
    header: 'Structure du demandeur',
    cell: (_: Ticket, school?: School) => <>{school?.name}</>,
  },
  { id: 'owner_name', header: 'Demandeur' },
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
    cell: (ticket: Ticket) => formaterDate(ticket.modified),
  },
  { id: 'event_count', header: 'Evénements' },
  {
    id: 'escalation_date',
    header: 'Escaladé',
    cell: (ticket: Ticket) =>
      ticket.escalation_date ? formaterDate(ticket.escalation_date) : '-',
  },
];

const TicketsTableRow = memo(function TicketsTableRow({
  ticket,
  school,
  ticketSelectionCallBack,
  ticketSelected,
}: TicketRowProps) {
  const navigate = useNavigate();

  return (
    <Table.Tr>
      <Table.Td>
        <Checkbox
          onChange={() => ticketSelectionCallBack(ticket)}
          checked={ticketSelected}
        />
      </Table.Td>
      {ticketTableColumns.map((column) => (
        <Table.Td
          role="button"
          key={column.id}
          onClick={() => navigate(`/tickets/${ticket.id}`)}
        >
          {column.cell ? column.cell(ticket, school) : ticket[column.id]}
        </Table.Td>
      ))}
    </Table.Tr>
  );
});

type TicketTableHeaderProps = {
  columns: Column[];
  handleSelectAll: () => void;
  selectedTickets: Ticket[];
  totalCount: number;
  actions: ToolbarAction[];
};

function TicketsTableHeader(props: TicketTableHeaderProps) {
  const { columns, handleSelectAll, selectedTickets, totalCount, actions } =
    props;

  const selectedCount = selectedTickets.length;
  const allSelected = selectedCount === totalCount && totalCount > 0;
  const indeterminate = selectedCount > 0 && !allSelected;

  return (
    <Table.Thead>
      <Table.Tr className="align-middle">
        <Table.Th>
          <Checkbox
            className="m-auto"
            checked={allSelected}
            indeterminate={indeterminate}
            onChange={() => handleSelectAll()}
          />
        </Table.Th>{' '}
        {columns.map((column) => (
          <Table.Th key={column.accessorKey}>{column.header}</Table.Th>
        ))}
      </Table.Tr>
      {selectedCount > 0 && (
        <TicketsTableToolbar
          selectedTickets={selectedTickets}
          actions={actions}
        />
      )}
    </Table.Thead>
  );
}

export type TicketsTableProps = {
  tickets?: Ticket[];
  schools?: School[];
};

function TicketsTable({ tickets = [], schools = [] }: TicketsTableProps) {
  const navigate = useNavigate();
  const { isAdml } = useIsAdml();
  const [selectedTickets, setSelectedTickets] = useState<Ticket[]>([]);
  const escalateWorkflow = useCanEscalate();
  const toast = useToast();
  const queryClient = useQueryClient();

  const selectedTicketIds = useMemo(
    () => new Set(selectedTickets.map((t) => t.id)),
    [selectedTickets],
  );

  const schoolById = useMemo(
    () => new Map(schools.map((s) => [s.id, s])),
    [schools],
  );

  const actions = useMemo<ToolbarAction[]>(
    () => [
      {
        id: 'open',
        label: 'Ouvrir',
        icon: <IconFullScreen />,
        onClick: () => {
          navigate(`/tickets/${selectedTickets[0].id}`);
        },
        isVisible: selectedTickets.length === 1,
      },
      {
        id: 'export',
        label: 'Exporter les tickets sélectionnés',
        icon: <IconDownload />,
        onClick: () => {
          exportTickets(selectedTickets.map((t) => t.id.toString()));
        },
        isVisible: isAdml ?? false,
      },
      {
        id: 'transfer',
        label: 'Transférer au support Édifice',
        icon: <IconGroupAvatar />,
        onClick: async () => {
          try {
            setSelectedTickets([]);
            await escalateTickets(selectedTickets.map((t) => t.id.toString()));
            queryClient.invalidateQueries({
              queryKey: [ticketsQueryKeys.all()],
            });
            toast.success('Les tickets ont été transférés avec succès');
          } catch (error) {
            console.error(error);
            toast.error(
              "Une erreur s'est produite lors du transfert des tickets",
            );
          }
        },
        isVisible:
          selectedTickets.every(
            (t) => t.escalation_status === ESCALATION_STATUS.NOT_DONE,
          ) &&
          (escalateWorkflow ?? false),
      },
    ],
    [selectedTickets, navigate, isAdml, escalateWorkflow, toast, queryClient],
  );

  const handleSelectAll = useCallback(() => {
    setSelectedTickets((prev) =>
      prev.length === tickets.length ? [] : tickets,
    );
  }, [tickets]);

  const handleTicketSelection = useCallback((ticket: Ticket) => {
    setSelectedTickets((prevSelected) => {
      if (prevSelected.find((t) => t.id === ticket.id)) {
        return prevSelected.filter((t) => t.id !== ticket.id);
      } else {
        return [...prevSelected, ticket];
      }
    });
  }, []);

  return (
    <>
      <Table>
        <TicketsTableHeader
          columns={columns}
          handleSelectAll={handleSelectAll}
          selectedTickets={selectedTickets}
          totalCount={tickets.length}
          actions={actions}
        />
        <Table.Tbody>
          {tickets.map((ticket) => (
            <TicketsTableRow
              key={ticket.id}
              ticket={ticket}
              ticketSelectionCallBack={handleTicketSelection}
              ticketSelected={selectedTicketIds.has(ticket.id)}
              school={schoolById.get(ticket.school_id) as School}
            />
          ))}
        </Table.Tbody>
      </Table>
    </>
  );
}

export default TicketsTable;
