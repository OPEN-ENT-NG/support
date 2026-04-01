import { Button, Checkbox, Flex } from '@edifice.io/react';
import { ReactNode } from 'react';
import { Ticket } from '~/models';

export type ToolbarAction = {
  id: string;
  label: string;
  icon: ReactNode;
  onClick: () => void;
  isVisible?: boolean;
};

export type TicketsTableToolbarProps = {
  selectedTickets: Ticket[];
  totalCount: number;
  handleSelectAll: () => void;
  actions: ToolbarAction[];
};

export function TicketsTableToolbar({
  selectedTickets,
  totalCount,
  handleSelectAll,
  actions,
}: TicketsTableToolbarProps) {
  const selectedCount = selectedTickets.length;
  const allSelected = selectedCount === totalCount && totalCount > 0;
  const indeterminate = selectedCount > 0 && !allSelected;
  const visibleActions = actions.filter((action) => action.isVisible !== false);

  return (
    <tr>
      <td colSpan={11}>
        <Flex className="tickets-table-toolbar" align="center" gap="16">
          <Checkbox
            checked={allSelected}
            indeterminate={indeterminate}
            onChange={() => handleSelectAll()}
          />
          <span>({selectedCount})</span>
          {visibleActions.map((action) => (
            <Button
              key={action.id}
              color="tertiary"
              variant="ghost"
              onClick={action.onClick}
            >
              {action.icon}
              {action.label}
            </Button>
          ))}
        </Flex>
      </td>
    </tr>
  );
}
