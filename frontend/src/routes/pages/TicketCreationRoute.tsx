import { LoadingScreen, useEdificeClient } from '@edifice.io/react';
import { TicketCreate } from '~/features/ticket-create/components/TicketCreate';

export const loader = async () => {
  return null;
};

export const Component = () => {
  const { init } = useEdificeClient();

  if (!init) return <LoadingScreen position={false} />;

  return (
    <div className="d-flex flex-column mt-24 ms-md-24 me-md-16">
      <TicketCreate />
    </div>
  );
};
