import { Flex, LoadingScreen, useEdificeClient } from '@edifice.io/react';
import { QueryClient } from '@tanstack/react-query';
import { TicketRead } from '~/features/ticket-read/TicketRead';

import { ticketsQueryKeys } from '~/services/queries/tickets';
import {
  getAttachmentById,
  getTicketById,
  getUserInfo,
  getUserProfile,
} from '~/services/api';
import { userQueryKeys } from '~/services/queries';
/** Prefetch ticket data in React Query cache based on route param */
export const loader =
  (queryClient: QueryClient) =>
  async ({ params }: { params: { ticketId?: string } }) => {
    const ticketId = params.ticketId;
    if (ticketId) {
      const ticket = await queryClient.ensureQueryData({
        queryKey: ticketsQueryKeys.byId(ticketId),
        queryFn: () => getTicketById(ticketId),
      });

      const ownerId = ticket[0]?.owner;
      await Promise.all([
        ownerId &&
          queryClient.ensureQueryData({
            queryKey: userQueryKeys.profileById(String(ownerId)),
            queryFn: () => getUserProfile(String(ownerId)),
          }),
        queryClient.ensureQueryData({
          queryKey: userQueryKeys.appsByUserId(),
          queryFn: () => getUserInfo(),
        }),
        queryClient.ensureQueryData({
          queryKey: ticketsQueryKeys.attachmentsById(String(ticket[0].id)),
          queryFn: () => getAttachmentById(String(ticket[0].id)),
        }),
      ]);
    }
    return null;
  };

export const Component = () => {
  const { init } = useEdificeClient();

  if (!init) return <LoadingScreen position={false} />;

  return (
    <Flex direction="column" className="h-100">
      <TicketRead />
    </Flex>
  );
};
