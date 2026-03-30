import { TimelineItem } from '../TicketRead';
import { TicketEvent } from './TicketEvents';
import TicketComment from './TicketComment';

type TicketTimelineProps = {
  timelineItems: TimelineItem[];
};

export default function TicketTimeline({ timelineItems }: TicketTimelineProps) {
  return (
    <>
      {timelineItems.map((item) => {
        switch (item.type) {
          case 'event':
            return (
              <TicketEvent
                key={`${item.type}-${item.date}`}
                event={item.data}
              />
            );

          case 'comment':
            return (
              <TicketComment
                key={`${item.type}-${item.date}`}
                comment={item.data}
              />
            );

          default:
            return null;
        }
      })}
    </>
  );
}
