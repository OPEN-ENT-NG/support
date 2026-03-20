import { Avatar, Flex, Heading } from '@edifice.io/react';
import { Ticket } from '~/models';
import { getAvatarURL } from '~/utils/getAvatarURL';
import FormattedDate from './FormattedDate';

export type TicketDetailsHeaderProps = {
  /**
   * Post to display
   */
  ticket: Ticket;
};

export const TicketDetailsHeader = ({ ticket }: TicketDetailsHeaderProps) => {
  const avatarUrl = getAvatarURL(ticket.owner);

  return (
    <Flex gap="16" direction="column">
      <Heading level="h4" headingStyle="h4">
        {ticket?.subject}
      </Heading>

      <Flex gap="12" align="center">
        <Avatar
          alt={ticket.owner_name}
          src={avatarUrl}
          variant="circle"
          size="md"
        />
        <Flex gap="8">
          <p className="small user-profile-relative">
            <strong>{ticket.owner_name}</strong>
          </p>
          <p className="small">
            <FormattedDate date={ticket.created} />
          </p>
        </Flex>
      </Flex>
    </Flex>
  );
};
