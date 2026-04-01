import { Table } from '@edifice.io/react';
import { ticketTableColumns } from './TicketTableColumns';

type TicketTableHeaderProps = {
  isAdmlcOrAdmc: boolean;
};

function TicketsTableHeader({ isAdmlcOrAdmc }: TicketTableHeaderProps) {
  return (
    <Table.Thead>
      <Table.Tr className="align-middle">
        {isAdmlcOrAdmc && <Table.Th />}
        {ticketTableColumns.map((column) => (
          <Table.Th key={column.id}>{column.header}</Table.Th>
        ))}
      </Table.Tr>
    </Table.Thead>
  );
}

export default TicketsTableHeader;
