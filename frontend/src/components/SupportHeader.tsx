import { AppHeader, Breadcrumb } from '@edifice.io/react';

export default function SupportHeader() {
  const displayApp = {
    name: 'Assistance',
    displayName: 'Assistance',
    icon: 'IconNeoAssistance',
    address: '/',
    display: true,
    isExternal: false,
    scope: [],
  };

  return (
    <AppHeader>
      <Breadcrumb app={displayApp} />
    </AppHeader>
  );
}
