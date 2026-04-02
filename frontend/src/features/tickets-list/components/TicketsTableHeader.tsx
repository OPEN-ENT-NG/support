import { Table } from '@edifice.io/react';
import { ticketTableColumns } from './TicketTableColumns';

type TicketTableHeaderProps = {
  canMultiSelect: boolean;
};

function TicketsTableHeader({ canMultiSelect }: TicketTableHeaderProps) {
  return (
    <Table.Thead>
      <Table.Tr className="align-middle">
        {canMultiSelect && <Table.Th />}
        {ticketTableColumns.map((column) => (
          <Table.Th key={column.id}>{column.header}</Table.Th>
        ))}
      </Table.Tr>
    </Table.Thead>
  );
}

export default TicketsTableHeader;
