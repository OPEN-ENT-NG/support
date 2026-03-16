import { QueryClient } from '@tanstack/react-query';
import { Outlet, RouteObject, createBrowserRouter } from 'react-router-dom';

import { NotFound } from './errors/not-found';
import { PageError } from './errors/page-error';

const routes = (queryClient: QueryClient): RouteObject[] => [
  /* Main route */
  {
    element: <Outlet />,
    children: [
      {
        path: '/',
        async lazy() {
          const { loader, Component } = await import('~/routes/root');
          return {
            loader,
            Component,
          };
        },
        errorElement: <PageError />,
        children: [
          {
            index: true,
            async lazy() {
              const { loader, Component } =
                await import('~/routes/pages/TicketsListRoute');
              return {
                loader,
                Component,
              };
            },
            errorElement: <PageError />,
          },
          {
            path: 'tickets/:ticketId',
            async lazy() {
              const { loader, Component } =
                await import('~/routes/pages/TicketDetailsRoute');
              return {
                loader: loader(queryClient),
                Component,
              };
            },
            errorElement: <PageError />,
          },
          {
            path: 'tickets/new',
            async lazy() {
              const { loader, Component } =
                await import('~/routes/pages/TicketCreationRoute');
              return {
                loader,
                Component,
              };
            },
            errorElement: <PageError />,
          },
        ],
      },
      /* 404 Page */
      {
        path: '*',
        element: <NotFound />,
      },
    ],
  },
];

export const basename = import.meta.env.PROD ? '/support' : '/';

export const router = (queryClient: QueryClient) =>
  createBrowserRouter(routes(queryClient), {
    basename,
  });
