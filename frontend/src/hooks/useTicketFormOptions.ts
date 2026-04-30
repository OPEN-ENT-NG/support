import { useMemo } from 'react';
import { useTicketCategories } from '~/hooks/useTicketCategories';
import { useSchools } from '~/services/queries/schools';

export function useTicketFormOptions() {
  const categories = useTicketCategories();
  const { schools } = useSchools();

  const schoolOptions = useMemo(
    () =>
      schools.map((school) => ({
        label: school.name,
        value: school.id,
      })),
    [schools],
  );

  return { categories, schoolOptions };
}
