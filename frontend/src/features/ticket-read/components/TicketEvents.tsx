import { Badge, Flex } from '@edifice.io/react';
import { Editor } from '@edifice.io/react/editor';
import { IconInfoCircle } from '@edifice.io/react/icons';
import {
  getTicketStatusText,
  TicketEvent as TicketEventModel,
  TicketStatusColors,
} from '~/models';
import { formaterDate } from '~/utils';

export interface TicketEventProps {
  event: TicketEventModel;
}

export function TicketEvent({ event }: TicketEventProps) {
  return (
    <Flex
      className="pt-24 pb-16 ps-24 pe-32"
      gap="16"
      style={{ borderBottom: 'solid 1px #E4E4E4' }}
    >
      <IconInfoCircle height={36} width={36} />
      <Flex direction="column" gap="4">
        <p>
          <span className="small">{formaterDate(event.event_date)}</span>
        </p>

        {event.status === -1 ? (
          <Editor content={event.event} mode="read" variant="ghost" />
        ) : (
          <p>
            {event.event} par{' '}
            <span className="small user-profile-relative">
              <strong>{event.username}</strong>
            </span>
            . Le status est{' '}
            <Badge
              variant={{
                color: TicketStatusColors[event.status],
                type: 'beta',
              }}
            >
              {getTicketStatusText(event.status)}
            </Badge>
          </p>
        )}
      </Flex>
    </Flex>
  );
}
