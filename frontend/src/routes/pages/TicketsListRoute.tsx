import { matchPath } from 'react-router-dom';

import { basename } from '..';
import { TicketsList } from '~/features/tickets-list/components/TicketsList';

/** Check old format URL and redirect if needed */
export const loader = async () => {
  const hashLocation = location.hash.substring(1);

  // Check if the URL is an old format (angular root with hash) and redirect to the new format
  if (hashLocation) {
    const isPath = matchPath('/view/:id', hashLocation);

    if (isPath) {
      // Redirect to the new format
      const redirectPath = `/id/${isPath?.params.id}`;
      location.replace(
        location.origin + basename.replace(/\/$/g, '') + redirectPath,
      );
    }
  }

  return null;
};

export const Component = () => {
  return (
    <div className="d-flex flex-column mt-24 ms-md-24 me-md-16">
      <TicketsList />
    </div>
  );
};
