import { Button, Flex, LoadingScreen } from '@edifice.io/react';
import { IconArrowLeft } from '@edifice.io/react/icons';
import { useNavigate, useParams } from 'react-router-dom';
import { useTicketFormOptions } from '~/hooks';
import { TicketComment, TicketEvent } from '~/models';
import { useUserProfile } from '~/services/queries';
import {
  useBugTrackerIssue,
  useTicket,
  useTicketAttachments,
  useTicketComments,
  useTicketEvents,
} from '~/services/queries/tickets';
import { getAvatarURL } from '~/utils/getAvatarURL';
import { useTicketEditForm } from '../hooks/useTicketEditForm';
import { TicketCard } from './TicketCard';
import TicketCommentForm from './TicketCommentForm';
import TicketEditForm from './TicketEditForm';
import TicketTimeline from './TicketTimeline';

export type TimelineItem =
  | { type: 'event'; date: string; data: TicketEvent }
  | { type: 'comment'; date: string; data: TicketComment };

function sortTimelineItems(
  events: TicketEvent[],
  comments: TicketComment[],
): TimelineItem[] {
  return [
    ...events.map((event) => ({
      type: 'event' as const,
      date: event.event_date,
      data: event,
    })),
    ...comments.map((comment) => ({
      type: 'comment' as const,
      date: comment.created,
      data: comment,
    })),
  ].sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime());
}

export function Ticket() {
  const { ticketId } = useParams();
  const ticket = useTicket(ticketId);
  const userProfile = useUserProfile(ticket?.owner);
  const { categories, schoolOptions } = useTicketFormOptions();
  const { control, errors, isPending, onSubmit } = useTicketEditForm(ticket);
  const navigate = useNavigate();

  const attachments = useTicketAttachments(ticketId);
  const comments = useTicketComments(ticketId);
  const events = useTicketEvents(ticketId);
  const bugTrackerIssue = useBugTrackerIssue(ticketId);

  if (ticket === undefined) {
    return <LoadingScreen />;
  }

  const avatarUrl = getAvatarURL(ticket.owner);
  const timelineItems = sortTimelineItems(events, comments);

  return (
    <Flex className="w-100 h-100">
      <div
        className="d-none d-lg-block border-end position-sticky top-0 h-100 overflow-y-auto"
        style={{ flex: '0 0 25%' }}
      >
        <Flex direction="column" align="start" gap="8">
          <Flex
            justify="start"
            className="ps-16 pe-16 pt-12 pb-12 w-100"
            style={{ borderBottom: 'solid 1px #E4E4E4' }}
          >
            <Button
              color="tertiary"
              variant="ghost"
              leftIcon={<IconArrowLeft />}
              onClick={() => navigate('/')}
              size="md"
              className="p-0"
            >
              Retour
            </Button>
          </Flex>
          <TicketEditForm
            control={control}
            onSubmit={onSubmit}
            errors={errors}
            ticket={ticket}
            userProfile={userProfile?.profile}
            categories={categories}
            schoolOptions={schoolOptions}
            bugTrackerIssueId={bugTrackerIssue?.id}
            isPending={isPending}
          />
        </Flex>
      </div>
      <Flex
        direction="column"
        className="overflow-y-auto"
        style={{ flex: 1, minWidth: 0 }}
      >
        <TicketCard
          ticket={ticket}
          attachments={attachments}
          bugTrackerIssue={bugTrackerIssue}
        />
        <TicketTimeline timelineItems={timelineItems} />
        <TicketCommentForm ticket={ticket} avatarUrl={avatarUrl} />
      </Flex>
    </Flex>
  );
}
