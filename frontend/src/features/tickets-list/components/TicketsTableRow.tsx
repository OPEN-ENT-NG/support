import { Checkbox, Table } from '@edifice.io/react';
import { memo } from 'react';
import { useNavigate } from 'react-router-dom';
import { School, Ticket } from '~/models';
import { ticketTableColumns } from './TicketTableColumns';

type TicketRowProps = {
  ticket: Ticket;
  school: School;
  ticketSelectionCallBack: (ticket: Ticket) => void;
  ticketSelected: boolean;
  isAdmlcOrAdmc: boolean;
};

const TicketsTableRow = memo(function TicketsTableRow({
  ticket,
  school,
  ticketSelectionCallBack,
  ticketSelected,
  isAdmlcOrAdmc,
}: TicketRowProps) {
  const navigate = useNavigate();

  return (
    <Table.Tr>
      {isAdmlcOrAdmc && (
        <Table.Td>
          <Checkbox
            onChange={() => ticketSelectionCallBack(ticket)}
            checked={ticketSelected}
          />
        </Table.Td>
      )}
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

export default TicketsTableRow;
