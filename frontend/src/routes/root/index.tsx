import {
  AppHeader,
  Breadcrumb,
  Flex,
  Layout,
  LoadingScreen,
  useEdificeClient,
} from '@edifice.io/react';

import { matchPath, Outlet, useMatch } from 'react-router-dom';

import { AppActionHeader } from '~/features/app/Action/AppActionHeader';
import { basename } from '..';

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
  const { init, currentApp } = useEdificeClient();
  const isTicketDetails = useMatch('tickets/:ticketId');

  if (!init || !currentApp) return <LoadingScreen position={false} />;

  return (
    <Flex className="d-print-block flex-grow-1 vh-100" direction="column">
      <Layout>
        <div className="d-print-none">
          <AppHeader render={AppActionHeader}>
            <Breadcrumb app={currentApp!} />
          </AppHeader>
        </div>
        <div
          className={`flex-fill overflow-y-auto position-relative ${isTicketDetails ? ' mx-n16' : ''}`}
        >
          <Outlet />
        </div>
      </Layout>
    </Flex>
  );
};
