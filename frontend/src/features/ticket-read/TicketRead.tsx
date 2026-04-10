import {
  Button,
  Flex,
  LoadingScreen,
  useBreakpoint,
  useUser,
} from '@edifice.io/react';
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
import { useI18n } from '~/hooks/usei18n';
import { useTicketEditForm } from './hooks/useTicketEditForm';
import { TicketCard } from './components/TicketCard';
import { TicketCommentForm } from './components/TicketCommentForm';
import { TicketEditForm } from './components/TicketEditForm';
import { TicketTimeline } from './components/TicketTimeline';

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

export function TicketRead() {
  const { lg } = useBreakpoint();
  const { ticketId } = useParams();
  const ticket = useTicket(ticketId);
  const userProfile = useUserProfile(ticket?.owner);
  const { avatar } = useUser();
  const { categories, schoolOptions } = useTicketFormOptions();
  const { t } = useI18n();
  const { control, errors, isPending, onSubmit } = useTicketEditForm(ticket);
  const navigate = useNavigate();

  const attachments = useTicketAttachments(ticketId);
  const comments = useTicketComments(ticketId);
  const events = useTicketEvents(ticketId);
  const bugTrackerIssue = useBugTrackerIssue(ticketId);

  if (ticket === undefined) {
    return <LoadingScreen />;
  }

  const timelineItems = sortTimelineItems(events, comments);

  const handleCancelClick = () => {
    navigate('/');
  };

  return (
    <Flex className="w-100 h-100">
      <div className="d-none d-lg-block border-end position-sticky top-0 h-100 ticket-sidebar">
        <Flex direction="column" align="start" gap="8">
          <Flex
            justify="start"
            className="ps-16 pe-16 pt-12 pb-12 w-100 border-bottom-light"
          >
            <Button
              color="tertiary"
              variant="ghost"
              leftIcon={<IconArrowLeft />}
              onClick={() => navigate('/')}
              size="sm"
            >
              {t('support.ticket.read.back')}
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
      <Flex direction="column" className="overflow-y-auto flex-grow-1 min-w-0">
        {!lg && (
          <div className="border-bottom-light py-8 px-16 w-100 position-sticky top-0 bg-white z-1">
            <Button
              color="tertiary"
              variant="ghost"
              size="sm"
              leftIcon={<IconArrowLeft />}
              onClick={handleCancelClick}
            >
              {t('support.ticket.read.back')}
            </Button>
          </div>
        )}
        <TicketCard
          ticket={ticket}
          attachments={attachments}
          bugTrackerIssue={bugTrackerIssue}
        />
        <TicketTimeline timelineItems={timelineItems} />
        <TicketCommentForm ticket={ticket} avatarUrl={avatar} />
      </Flex>
    </Flex>
  );
}
