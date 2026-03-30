import { Badge, Flex } from '@edifice.io/react';
import { IconInfoCircle } from '@edifice.io/react/icons';
import {
  getTicketStatusText,
  TICKET_STATUS_CLASS,
  TicketEvent as TicketEventModel,
} from '~/models';
import FormattedDate from './FormattedDate';

export interface TicketEventProps {
  event: TicketEventModel;
}

export function TicketEvent({ event }: TicketEventProps) {
  return (
    <Flex className="pt-24 pb-16 ps-24 pe-32 border-bottom-light" gap="16">
      <IconInfoCircle height={36} width={36} />
      <Flex direction="column" gap="4">
        <p className="small">
          <FormattedDate date={event.event_date} />
        </p>
        {/*
          We are using `dangerouslySetInnerHTML` here because using Editor causes comment layout shift.
          Even though this is not the cleanest nor the safest solution,
          It applies only to comments coming from Zendesk written by our support team, risks are limited.
        */}
        <div dangerouslySetInnerHTML={{ __html: event.event }} />
        {event.status !== -1 && (
          <p>
            {event.username && (
              <>
                par{' '}
                <span className="small user-profile-relative">
                  <strong>{event.username}</strong>
                </span>
                .{' '}
              </>
            )}
            Le status est{' '}
            <Badge
              className={TICKET_STATUS_CLASS[event.status]}
              variant={{
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
