import { Table } from '@edifice.io/react';
import { IconArrowDown, IconArrowUp } from '@edifice.io/react/icons';
import { SortableTicketField, SortOrder } from '~/models';
import { ticketTableColumns } from './TicketTableColumns';

type TicketTableHeaderProps = {
  canMultiSelect: boolean;
  sortBy: SortableTicketField;
  order: SortOrder;
  onSort: (field: SortableTicketField) => void;
};

function TicketsTableHeader({
  canMultiSelect,
  sortBy,
  order,
  onSort,
}: TicketTableHeaderProps) {
  return (
    <Table.Thead>
      <Table.Tr className="align-middle">
        {canMultiSelect && <Table.Th />}
        {ticketTableColumns.map((column) => (
          <Table.Th
            key={column.id}
            onClick={column.sortBy ? () => onSort(column.sortBy!) : undefined}
            style={column.sortBy ? { cursor: 'pointer', userSelect: 'none' } : undefined}
          >
            <span className="d-inline-flex align-items-center gap-4">
              {column.header}
              {column.sortBy && sortBy === column.sortBy && (
                order === 'ASC' ? <IconArrowUp width={14} height={14} /> : <IconArrowDown width={14} height={14} />
              )}
            </span>
          </Table.Th>
        ))}
      </Table.Tr>
    </Table.Thead>
  );
}

export default TicketsTableHeader;
