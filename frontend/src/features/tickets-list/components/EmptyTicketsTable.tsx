import { EmptyScreen } from '@edifice.io/react';

import illuEmptyAdminThreads from '@images/emptyscreen/illu-actualites.svg';

export default function EmptyTicketsTable() {
  return (
    <EmptyScreen
      imageSrc={illuEmptyAdminThreads}
      imageAlt="No tickets"
      title="No tickets available"
      text="There are currently no tickets to display."
    />
  );
}
