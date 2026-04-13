import { Badge, Flex } from '@edifice.io/react';
import DOMPurify from 'dompurify';
import { IconInfoCircle } from '@edifice.io/react/icons';
import { useI18n } from '~/hooks/usei18n';
import {
  getTicketStatusText,
  TICKET_STATUS_CLASS,
  TicketEvent as TicketEventModel,
} from '~/models';
import { FormattedDate } from './FormattedDate';

export interface TicketEventProps {
  event: TicketEventModel;
}

export function TicketEvent({ event }: TicketEventProps) {
  const { t } = useI18n();

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
        <div
          dangerouslySetInnerHTML={{ __html: DOMPurify.sanitize(event.event) }}
        />
        {event.status !== -1 && (
          <p>
            {event.username && (
              <>
                {t('support.ticket.read.event.by')}{' '}
                <a href={`/userbook/annuaire#${event.user_id}`}>
                  <span className="small user-profile-relative">
                    <strong>{event.username}</strong>
                  </span>
                </a>
                .{' '}
              </>
            )}
            {t('support.ticket.read.event.status')}{' '}
            <Badge
              className={TICKET_STATUS_CLASS[event.status]}
              variant={{
                type: 'beta',
              }}
            >
              {t(getTicketStatusText(event.status))}
            </Badge>
          </p>
        )}
      </Flex>
    </Flex>
  );
}
