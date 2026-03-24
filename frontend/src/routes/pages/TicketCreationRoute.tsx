import { Flex, LoadingScreen, useEdificeClient } from '@edifice.io/react';
import { TicketCreate } from '~/features/ticket-create/components/TicketCreate';

export const loader = async () => {
  return null;
};

export const Component = () => {
  const { init } = useEdificeClient();

  if (!init) return <LoadingScreen position={false} />;

  return (
    <Flex direction="column" className="mt-24 ms-md-24 me-md-16">
      <TicketCreate />
    </Flex>
  );
};
