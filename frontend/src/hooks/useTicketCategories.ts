import { useEdificeClient } from '@edifice.io/react';
import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';

export type TicketCategory = {
  label: string;
  value: string;
};

export function useTicketCategories(): TicketCategory[] {
  const { applications } = useEdificeClient();
  const { t } = useTranslation();

  const categories = useMemo(
    () =>
      (applications ?? [])
        .filter((app) => app.address)
        .map((app) => ({ label: t(app.displayName), value: app.address }))
        .sort((a, b) => a.label.localeCompare(b.label)),
    [applications, t],
  );

  return categories;
}
