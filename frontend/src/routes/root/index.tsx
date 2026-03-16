import {
  AppHeader,
  Breadcrumb,
  Layout,
  LoadingScreen,
  useEdificeClient,
} from '@edifice.io/react';

import { matchPath, Outlet } from 'react-router-dom';

import { basename } from '..';
import { AppActionHeader } from '~/features/app/Action/AppActionHeader';

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

  if (!init || !currentApp) return <LoadingScreen position={false} />;

  return (
    <div className="d-print-block d-flex flex-column vh-100 flex-grow-1">
      <Layout>
        <div className="d-print-none">
          <AppHeader render={AppActionHeader}>
            <Breadcrumb app={currentApp!} />
          </AppHeader>
        </div>
        <div className="flex-fill overflow-y-auto position-relative">
          <Outlet />
        </div>
      </Layout>
    </div>
  );
};
