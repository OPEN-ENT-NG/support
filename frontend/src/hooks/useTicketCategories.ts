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

  const categories = useMemo(() => {
    const seen = new Set<string>(); // used to avoid duplicate categories
    return (applications ?? [])
      .filter((app) => app.address)
      .filter((app) => {
        const normalizedAppName = app.displayName.toLowerCase().trim();
        if (seen.has(normalizedAppName)) return false;
        seen.add(normalizedAppName);
        return true;
      })
      .map((app) => ({ label: t(app.displayName), value: app.address }))
      .sort((a, b) => a.label.localeCompare(b.label));
  }, [applications, t]);

  return categories;
}
