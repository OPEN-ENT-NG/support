import { Flex, LoadingScreen, useEdificeClient } from '@edifice.io/react';
import { QueryClient } from '@tanstack/react-query';
import { TicketCreate } from '~/features/ticket-create/TicketCreate';
import { getSchools } from '~/services/api';
import { schoolsQueryKeys } from '~/services/queries/schools';

export const loader = (queryClient: QueryClient) => async () => {
  await queryClient.prefetchQuery({
    queryKey: schoolsQueryKeys.all,
    queryFn: () => getSchools(),
  });
  return null;
};

export const Component = () => {
  const { init } = useEdificeClient();

  if (!init) return <LoadingScreen position={false} />;

  return (
    <Flex direction="column" className="mt-24 mx-16">
      <TicketCreate />
    </Flex>
  );
};
