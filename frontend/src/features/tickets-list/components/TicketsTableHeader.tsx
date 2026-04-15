import { Flex, IconButton, Table } from '@edifice.io/react';
import {
  IconArrowDown,
  IconArrowUp,
  IconListOrder,
} from '@edifice.io/react/icons';
import { SortableTicketField, SortOrder } from '~/models';
import { useI18n } from '~/hooks/usei18n';
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
  const { t } = useI18n();

  const getSortIcon = (field: SortableTicketField) => {
    if (sortBy === field) {
      return order === 'ASC' ? <IconArrowUp /> : <IconArrowDown />;
    }
    return <IconListOrder style={{ width: 18, height: 18 }} />;
  };

  return (
    <Table.Thead>
      <Table.Tr className="align-middle">
        {/*If user is allowed to multi-select show a checkbox in the header*/}
        {canMultiSelect && <Table.Th />}

        {ticketTableColumns.map((column) => (
          <Table.Th
            key={column.headerKey}
            className={!column.sortBy ? 'align-content-center' : undefined}
          >
            <div style={{ width: column.width, wordBreak: 'break-word' }}>
              {column.sortBy ? (
                <Flex align="center" gap="8">
                  {t(column.headerKey)}
                  <IconButton
                    variant="ghost"
                    color="tertiary"
                    size="sm"
                    type="button"
                    icon={getSortIcon(column.sortBy)}
                    style={{
                      color: sortBy === column.sortBy ? '#4A4A4A' : '#B0B0B0',
                      padding: 0,
                    }}
                    onClick={() => onSort(column.sortBy!)}
                    aria-label={t(column.headerKey)}
                  />
                </Flex>
              ) : (
                t(column.headerKey)
              )}
            </div>
          </Table.Th>
        ))}
      </Table.Tr>
    </Table.Thead>
  );
}

export { TicketsTableHeader };
