import { Button, Flex } from '@edifice.io/react';
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
  actions: ToolbarAction[];
};

export function TicketsTableToolbar({
  selectedTickets,
  actions,
}: TicketsTableToolbarProps) {
  const selectedCount = selectedTickets.length;
  const visibleActions = actions.filter((action) => action.isVisible !== false);

  return (
    <tr>
      <td colSpan={11}>
        <Flex align="center" gap="4">
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
